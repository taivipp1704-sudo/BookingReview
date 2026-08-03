package com.claritycam.platform.config;

import com.claritycam.platform.repository.auth.AdminUserRepository;
import com.claritycam.platform.model.auth.AdminUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminBootstrap {
  private static final String PRIMARY_ADMIN_ID = "USR-ADMIN-001";

  @Bean
  @ConditionalOnProperty(name = "claritycam.bootstrap-admin", havingValue = "true")
  CommandLineRunner bootstrapAdmin(
      AdminUserRepository users,
      PasswordEncoder passwordEncoder,
      @Value("${claritycam.admin.email}") String email,
      @Value("${claritycam.admin.password}") String password) {
    return args -> {
      if (email == null || email.isBlank()) {
        throw new IllegalStateException("CLARITYCAM_ADMIN_EMAIL is required when BOOTSTRAP_ADMIN=true");
      }
      if (!PasswordPolicy.isValid(password) || "change-me-now".equals(password)) {
        throw new IllegalStateException(
            "CLARITYCAM_ADMIN_PASSWORD must be 12-128 characters with uppercase, lowercase, number and symbol");
      }
      String normalizedEmail = email.trim().toLowerCase();
      String passwordHash = passwordEncoder.encode(password);
      AdminUser existing = users.findByEmailIgnoreCase(normalizedEmail).orElse(null);
      if (existing != null) {
        if (!PRIMARY_ADMIN_ID.equals(existing.getId())) {
          throw new IllegalStateException(
              "CLARITYCAM_ADMIN_EMAIL belongs to a non-primary admin account");
        }
        existing.update("ADMIN", true, passwordHash);
        users.save(existing);
        return;
      }

      AdminUser primaryAdmin = users.findById(PRIMARY_ADMIN_ID).orElse(null);
      if (primaryAdmin != null) {
        primaryAdmin.replaceCredentials(normalizedEmail, passwordHash);
        users.save(primaryAdmin);
        return;
      }

      if (users.count() > 0) {
        throw new IllegalStateException(
            "Primary admin account is missing; refusing to replace another staff account");
      }
      users.save(new AdminUser(PRIMARY_ADMIN_ID, normalizedEmail, passwordHash, "ADMIN", true));
    };
  }
}
