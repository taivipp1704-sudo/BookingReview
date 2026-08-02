import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Small production maintenance utility for TiDB/MySQL.
 *
 * Secrets are read only from environment variables. Backup files are written
 * below the git-ignored backups directory unless BACKUP_DIR is provided.
 */
public final class TidbProductionAdmin {
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9_]+$ ".trim());
    private static final Pattern SAFE_USER = Pattern.compile("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)?$");
    private static final Set<Integer> NUMERIC_TYPES = Set.of(
            java.sql.Types.BIGINT, java.sql.Types.BIT, java.sql.Types.BOOLEAN,
            java.sql.Types.DECIMAL, java.sql.Types.DOUBLE, java.sql.Types.FLOAT,
            java.sql.Types.INTEGER, java.sql.Types.NUMERIC, java.sql.Types.REAL,
            java.sql.Types.SMALLINT, java.sql.Types.TINYINT);
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private TidbProductionAdmin() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: backup-verify | baseline-flyway | repair-flyway | create-users | verify-users");
        }

        Class.forName("com.mysql.cj.jdbc.Driver");
        String jdbcUrl = required("DB_URL");
        String username = required("DB_USERNAME");
        String password = required("DB_PASSWORD");

        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            switch (args[0]) {
                case "backup-verify" -> backupAndVerify(connection, jdbcUrl, username, password);
                case "baseline-flyway" -> baselineFlyway(connection, username);
                case "repair-flyway" -> repairFlyway(connection);
                case "create-users" -> createUsers(connection);
                case "verify-users" -> verifyUsers(connection);
                default -> throw new IllegalArgumentException("Unknown command: " + args[0]);
            }
        }
    }

    private static void repairFlyway(Connection connection) throws Exception {
        int failedRows;
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT COUNT(*) FROM `flyway_schema_history` WHERE `success` = 0")) {
            rows.next();
            failedRows = rows.getInt(1);
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM `flyway_schema_history` WHERE `success` = 0");
        }
        System.out.println("FLYWAY_REPAIR_OK removed_failed_rows=" + failedRows);
    }

    private static void baselineFlyway(Connection connection, String installedBy) throws Exception {
        String createHistory = """
                CREATE TABLE IF NOT EXISTS `flyway_schema_history` (
                  `installed_rank` INT NOT NULL,
                  `version` VARCHAR(50),
                  `description` VARCHAR(200) NOT NULL,
                  `type` VARCHAR(20) NOT NULL,
                  `script` VARCHAR(1000) NOT NULL,
                  `checksum` INT,
                  `installed_by` VARCHAR(100) NOT NULL,
                  `installed_on` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  `execution_time` INT NOT NULL,
                  `success` BOOL NOT NULL,
                  CONSTRAINT `flyway_schema_history_pk` PRIMARY KEY (`installed_rank`),
                  INDEX `flyway_schema_history_s_idx` (`success`)
                ) ENGINE=InnoDB
                """;
        execute(connection, createHistory);

        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM `flyway_schema_history`")) {
            rows.next();
            if (rows.getLong(1) == 0) {
                String safeInstalledBy = escapeSql(installedBy);
                execute(connection, "INSERT INTO `flyway_schema_history` "
                        + "(`installed_rank`,`version`,`description`,`type`,`script`,`checksum`,"
                        + "`installed_by`,`execution_time`,`success`) VALUES "
                        + "(1,'0','<< Flyway Baseline >>','BASELINE','<< Flyway Baseline >>',NULL,'"
                        + safeInstalledBy + "',0,1)");
            }
        }
        System.out.println("FLYWAY_BASELINE_OK version=0");
    }

    private static void backupAndVerify(
            Connection source, String jdbcUrl, String username, String password) throws Exception {
        String sourceDatabase = source.getCatalog();
        if (sourceDatabase == null || sourceDatabase.isBlank()) {
            sourceDatabase = databaseFromUrl(jdbcUrl);
        }
        requireIdentifier(sourceDatabase);

        Path backupDir = Path.of(System.getenv().getOrDefault("BACKUP_DIR", "backups"));
        Files.createDirectories(backupDir);
        String stamp = STAMP.format(Instant.now());
        Path dump = backupDir.resolve("production-" + sourceDatabase + "-" + stamp + ".sql");
        Path manifest = backupDir.resolve("production-" + sourceDatabase + "-" + stamp + ".manifest.txt");

        Map<String, Long> sourceCounts = writeDump(source, sourceDatabase, dump);
        String sha256 = sha256(dump);
        Files.writeString(manifest,
                "created_at_utc=" + Instant.now() + System.lineSeparator()
                        + "database=" + sourceDatabase + System.lineSeparator()
                        + "tables=" + sourceCounts.size() + System.lineSeparator()
                        + "rows=" + sourceCounts.values().stream().mapToLong(Long::longValue).sum()
                        + System.lineSeparator()
                        + "sha256=" + sha256 + System.lineSeparator(),
                StandardCharsets.UTF_8);

        if (!sha256.equals(sha256(dump))) {
            throw new IllegalStateException("Backup checksum verification failed");
        }

        String restoreDatabase = "restore_check_" + stamp.replace("-", "_");
        requireIdentifier(restoreDatabase);
        boolean created = false;
        try {
            execute(source, "CREATE DATABASE `" + restoreDatabase + "`");
            created = true;
            String restoreUrl = replaceDatabase(jdbcUrl, restoreDatabase);
            try (Connection restored = DriverManager.getConnection(restoreUrl, username, password)) {
                restoreDump(restored, dump);
                Map<String, Long> restoredCounts = tableCounts(restored, restoreDatabase);
                if (!sourceCounts.equals(restoredCounts)) {
                    throw new IllegalStateException(
                            "Restore verification failed: row counts do not match");
                }
            }
        } finally {
            if (created) {
                execute(source, "DROP DATABASE `" + restoreDatabase + "`");
            }
        }

        System.out.println("BACKUP_OK path=" + dump.toAbsolutePath());
        System.out.println("MANIFEST path=" + manifest.toAbsolutePath());
        System.out.println("SHA256 " + sha256);
        System.out.println("TABLES " + sourceCounts.size());
        System.out.println("ROWS " + sourceCounts.values().stream().mapToLong(Long::longValue).sum());
        System.out.println("RESTORE_CHECK_OK temporary_database_removed=true");
    }

    private static Map<String, Long> writeDump(
            Connection connection, String database, Path destination) throws Exception {
        List<String> tables = baseTables(connection, database);
        Map<String, Long> counts = new LinkedHashMap<>();
        try (BufferedWriter writer = Files.newBufferedWriter(destination, StandardCharsets.UTF_8)) {
            writer.write("-- Logical backup generated by TidbProductionAdmin\n");
            writer.write("SET FOREIGN_KEY_CHECKS=0;\n");
            for (String table : tables) {
                String createSql = showCreateTable(connection, table)
                        .replace("\r", " ").replace("\n", " ");
                writer.write("DROP TABLE IF EXISTS `" + table + "`;\n");
                writer.write(createSql + ";\n");
                long rows = writeRows(connection, table, writer);
                counts.put(table, rows);
            }
            writer.write("SET FOREIGN_KEY_CHECKS=1;\n");
        }
        return counts;
    }

    private static long writeRows(Connection connection, String table, BufferedWriter writer)
            throws SQLException, IOException {
        long count = 0;
        try (Statement statement = connection.createStatement(
                ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            statement.setFetchSize(Integer.MIN_VALUE);
            try (ResultSet rows = statement.executeQuery("SELECT * FROM `" + table + "`")) {
                ResultSetMetaData meta = rows.getMetaData();
                int columns = meta.getColumnCount();
                while (rows.next()) {
                    writer.write("INSERT INTO `" + table + "` VALUES (");
                    for (int column = 1; column <= columns; column++) {
                        if (column > 1) {
                            writer.write(',');
                        }
                        writer.write(sqlLiteral(rows, meta, column));
                    }
                    writer.write(");\n");
                    count++;
                }
            }
        }
        return count;
    }

    private static String sqlLiteral(ResultSet row, ResultSetMetaData meta, int column)
            throws SQLException {
        Object value = row.getObject(column);
        if (value == null) {
            return "NULL";
        }
        int type = meta.getColumnType(column);
        if (value instanceof byte[] bytes) {
            StringBuilder hex = new StringBuilder("X'");
            for (byte b : bytes) {
                hex.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            }
            return hex.append('\'').toString();
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue ? "1" : "0";
        }
        if (NUMERIC_TYPES.contains(type) && value instanceof Number number) {
            return number instanceof BigDecimal ? number.toString() : String.valueOf(number);
        }
        return "'" + escapeSql(String.valueOf(value)) + "'";
    }

    private static String escapeSql(String value) {
        return value.replace("\\", "\\\\")
                .replace("\u0000", "\\0")
                .replace("\b", "\\b")
                .replace("\t", "\\t")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\u001a", "\\Z")
                .replace("'", "\\'");
    }

    private static void restoreDump(Connection connection, Path dump) throws Exception {
        try (BufferedReader reader = Files.newBufferedReader(dump, StandardCharsets.UTF_8);
             Statement statement = connection.createStatement()) {
            String line;
            while ((line = reader.readLine()) != null) {
                String sql = line.trim();
                if (sql.isEmpty() || sql.startsWith("--")) {
                    continue;
                }
                if (!sql.endsWith(";")) {
                    throw new IllegalStateException("Invalid backup statement: missing terminator");
                }
                statement.execute(sql.substring(0, sql.length() - 1));
            }
        }
    }

    private static void createUsers(Connection connection) throws Exception {
        String database = connection.getCatalog();
        if (database == null || database.isBlank()) {
            database = databaseFromUrl(required("DB_URL"));
        }
        requireIdentifier(database);
        String appUser = required("APP_DB_USER");
        String migrationUser = required("FLYWAY_DB_USER");
        requireUser(appUser);
        requireUser(migrationUser);

        upsertUser(connection, appUser, required("APP_DB_PASSWORD"));
        upsertUser(connection, migrationUser, required("FLYWAY_DB_PASSWORD"));
        execute(connection, "GRANT SELECT, INSERT, UPDATE, DELETE ON `" + database
                + "`.* TO '" + appUser + "'@'%'");
        execute(connection, "GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES ON `"
                + database + "`.* TO '" + migrationUser + "'@'%'");
        System.out.println("USERS_OK app=" + appUser + " migration=" + migrationUser);
    }

    private static void verifyUsers(Connection admin) throws Exception {
        String database = admin.getCatalog();
        if (database == null || database.isBlank()) {
            database = databaseFromUrl(required("DB_URL"));
        }
        verifyUserConnection("APP_DB_USER", "APP_DB_PASSWORD", database, false);
        verifyUserConnection("FLYWAY_DB_USER", "FLYWAY_DB_PASSWORD", database, true);
        System.out.println("USER_PRIVILEGES_OK");
    }

    private static void verifyUserConnection(
            String userKey, String passwordKey, String database, boolean ddlExpected) throws Exception {
        String user = required(userKey);
        String externalUser = externalUsername(required("DB_USERNAME"), user);
        try (Connection connection = DriverManager.getConnection(
                required("DB_URL"), externalUser, required(passwordKey));
             Statement statement = connection.createStatement()) {
            statement.executeQuery("SELECT 1").close();
            String probe = "privilege_probe_" + user.toLowerCase(Locale.ROOT);
            if (ddlExpected) {
                statement.execute("CREATE TABLE `" + probe + "` (id INT PRIMARY KEY)");
                statement.execute("DROP TABLE `" + probe + "`");
            } else {
                try {
                    statement.execute("CREATE TABLE `" + probe + "` (id INT PRIMARY KEY)");
                    statement.execute("DROP TABLE `" + probe + "`");
                    throw new IllegalStateException("Runtime user unexpectedly has DDL permission");
                } catch (SQLException expected) {
                    // Runtime credentials must not be able to create schema objects.
                }
            }
        }
    }

    private static void upsertUser(Connection connection, String user, String password)
            throws SQLException {
        String escapedPassword = escapeSql(password);
        execute(connection, "CREATE USER IF NOT EXISTS '" + user + "'@'%' IDENTIFIED BY '"
                + escapedPassword + "'");
        execute(connection, "ALTER USER '" + user + "'@'%' IDENTIFIED BY '"
                + escapedPassword + "'");
    }

    private static String externalUsername(String currentUsername, String sqlUser) {
        if (sqlUser.contains(".")) {
            return sqlUser;
        }
        int separator = currentUsername.lastIndexOf('.');
        return separator < 0 ? sqlUser : currentUsername.substring(0, separator + 1) + sqlUser;
    }

    private static List<String> baseTables(Connection connection, String database) throws SQLException {
        List<String> tables = new ArrayList<>();
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet rows = meta.getTables(database, null, "%", new String[]{"TABLE"})) {
            while (rows.next()) {
                String name = rows.getString("TABLE_NAME");
                requireIdentifier(name);
                tables.add(name);
            }
        }
        tables.sort(String::compareTo);
        return tables;
    }

    private static Map<String, Long> tableCounts(Connection connection, String database)
            throws SQLException {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : baseTables(connection, database)) {
            try (Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM `" + table + "`")) {
                rows.next();
                counts.put(table, rows.getLong(1));
            }
        }
        return counts;
    }

    private static String showCreateTable(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery("SHOW CREATE TABLE `" + table + "`")) {
            if (!row.next()) {
                throw new IllegalStateException("Missing CREATE statement for " + table);
            }
            return row.getString(2);
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String replaceDatabase(String jdbcUrl, String database) {
        int query = jdbcUrl.indexOf('?');
        String suffix = query < 0 ? "" : jdbcUrl.substring(query);
        String base = query < 0 ? jdbcUrl : jdbcUrl.substring(0, query);
        int slash = base.lastIndexOf('/');
        return base.substring(0, slash + 1) + database + suffix;
    }

    private static String databaseFromUrl(String jdbcUrl) {
        String base = jdbcUrl.substring(0, jdbcUrl.indexOf('?') < 0
                ? jdbcUrl.length() : jdbcUrl.indexOf('?'));
        return base.substring(base.lastIndexOf('/') + 1);
    }

    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing environment variable: " + key);
        }
        return value;
    }

    private static void requireIdentifier(String value) {
        if (value == null || !SAFE_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Unsafe SQL identifier");
        }
    }

    private static void requireUser(String value) {
        if (value == null || !SAFE_USER.matcher(value).matches()) {
            throw new IllegalArgumentException("Unsafe SQL user name");
        }
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) {
            hex.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        }
        return hex.toString();
    }
}
