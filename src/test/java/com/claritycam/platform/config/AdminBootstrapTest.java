package com.claritycam.platform.config;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.claritycam.platform.auth.AdminUser;
import com.claritycam.platform.auth.AdminUserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminBootstrapTest {
  @Test
  void resetsExistingConfiguredAdminPasswordAndReactivatesAccount() throws Exception {
    AdminUserRepository users = mock(AdminUserRepository.class);
    PasswordEncoder encoder = new BCryptPasswordEncoder();
    AdminUser existing = new AdminUser(
        "USR-ADMIN-001",
        "admin@example.com",
        encoder.encode("old-password-123"),
        "MANAGER",
        false);
    when(users.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(existing));

    CommandLineRunner runner = new AdminBootstrap().bootstrapAdmin(
        users,
        encoder,
        " Admin@Example.com ",
        "new-password-456");
    runner.run();

    assertTrue(existing.isActive());
    assertTrue(encoder.matches("new-password-456", existing.getPasswordHash()));
    verify(users).save(existing);
  }
}
