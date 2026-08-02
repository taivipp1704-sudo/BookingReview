# Database Security Runbook

## Release mode

- `BOOKING_ENABLED=false` keeps quote, hold, identity upload, payment proof and booking creation closed at the API layer.
- Early access stores only customer name, normalized phone number, consent version and waitlist slot.
- The approved Excel import exposes products in read-only preview mode while financial policies remain `DRAFT`.
- Catalog and anonymous booking-calendar endpoints require an authenticated customer session.

## Accounts and secrets

Use separate MySQL users for runtime and schema maintenance. Never use `root` from the application.

```sql
CREATE USER 'claritycam_app'@'%' IDENTIFIED BY '<RANDOM_RUNTIME_PASSWORD>';
GRANT SELECT, INSERT, UPDATE, DELETE ON claritycam_platform.* TO 'claritycam_app'@'%';

CREATE USER 'claritycam_migration'@'%' IDENTIFIED BY '<RANDOM_MIGRATION_PASSWORD>';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP
  ON claritycam_platform.* TO 'claritycam_migration'@'%';
```

Store credentials in Render environment variables. Do not put real values in Git, frontend variables, screenshots or support messages. Rotate any secret that has ever appeared in source control.

Configure `DB_USERNAME` / `DB_PASSWORD` with `claritycam_app` and `FLYWAY_USERNAME` / `FLYWAY_PASSWORD` with `claritycam_migration`. Production uses `JPA_DDL_AUTO=validate`, so Hibernate checks the schema but cannot change it with the runtime account.

For the first deployment against an existing database, take and restore-test a backup before enabling Flyway. If schema validation fails, stop the deployment and reconcile the schema in a staging clone; do not temporarily grant DDL rights to the runtime account.

## Backup before imports or migrations

1. Create an encrypted database backup from the hosting provider.
2. Record backup time, schema version and operator.
3. Restore that backup into a separate test database.
4. Verify account count, waitlist count, catalog count and a sample of records.
5. Run `GET /api/admin/imports/amy-catalog/preview` before applying the approved Excel import.
6. Apply with confirmation `IMPORT_APPROVED_EXCEL` only after the restore test succeeds.

Keep daily backups for 14 days and weekly backups for 8 weeks. Run a restore drill at least every quarter.

## Production checks

- TLS is required for MySQL and public HTTP traffic.
- SQL and parameter logging remain disabled.
- Database access is never exposed to React or browser code.
- Production demo data and demo OTP are disabled.
- Review duplicate registrations, rate-limit events and unexpected `5xx` responses daily during early access.
- Monitor `/actuator/health/readiness`; configure `ALERT_WEBHOOK_URL` to receive database, server-error and rate-limit alerts outside Render logs.
- Do not collect CCCD or payment images until booking is enabled and retention/storage controls are verified.
