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
      AdminUser existing = users.findByEmailIgnoreCase(normalizedEmail).orElse(null);
      if (existing != null) {
        existing.update("ADMIN", true, passwordEncoder.encode(password));
        users.save(existing);
        return;
      }
      if (users.count() > 0) {
        throw new IllegalStateException(
            "CLARITYCAM_ADMIN_EMAIL does not match an existing admin account");
      }
      users.save(new AdminUser("USR-ADMIN-001", normalizedEmail, passwordEncoder.encode(password), "ADMIN", true));
    };
  }
}
