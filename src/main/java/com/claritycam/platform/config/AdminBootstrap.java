package com.claritycam.platform.config;

import com.claritycam.platform.auth.AdminUser;
import com.claritycam.platform.auth.AdminUserRepository;
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
      if (users.count() > 0) return;
      if (email == null || email.isBlank()) {
        throw new IllegalStateException("CLARITYCAM_ADMIN_EMAIL is required when BOOTSTRAP_ADMIN=true");
      }
      if (password == null || password.length() < 12 || "change-me-now".equals(password)) {
        throw new IllegalStateException(
            "CLARITYCAM_ADMIN_PASSWORD must contain at least 12 characters when BOOTSTRAP_ADMIN=true");
      }
      users.save(new AdminUser("USR-ADMIN-001", email.trim(), passwordEncoder.encode(password), "ADMIN", true));
    };
  }
}

