package com.claritycam.platform.config;

import com.claritycam.platform.repository.auth.AdminUserRepository;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.claritycam.platform.model.auth.AdminUser;
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
        "New-password#456");
    runner.run();

    assertTrue(existing.isActive());
    assertTrue(encoder.matches("New-password#456", existing.getPasswordHash()));
    verify(users).save(existing);
  }

  @Test
  void replacesOnlyPrimaryAdminCredentialsWhenConfiguredEmailChanges() throws Exception {
    AdminUserRepository users = mock(AdminUserRepository.class);
    PasswordEncoder encoder = new BCryptPasswordEncoder();
    AdminUser primary = new AdminUser(
        "USR-ADMIN-001",
        "old-admin@example.com",
        encoder.encode("Old-password#123"),
        "ADMIN",
        true);
    when(users.findByEmailIgnoreCase("new-admin@example.com")).thenReturn(Optional.empty());
    when(users.findById("USR-ADMIN-001")).thenReturn(Optional.of(primary));

    CommandLineRunner runner = new AdminBootstrap().bootstrapAdmin(
        users,
        encoder,
        "New-Admin@Example.com",
        "New-password#456");
    runner.run();

    assertEquals("new-admin@example.com", primary.getEmail());
    assertEquals("ADMIN", primary.getRole());
    assertTrue(primary.isActive());
    assertTrue(encoder.matches("New-password#456", primary.getPasswordHash()));
    verify(users).save(primary);
  }

  @Test
  void refusesToPromoteNonPrimaryAccountThroughBootstrap() {
    AdminUserRepository users = mock(AdminUserRepository.class);
    PasswordEncoder encoder = new BCryptPasswordEncoder();
    AdminUser staff = new AdminUser(
        "USR-STAFF-001",
        "staff@example.com",
        encoder.encode("Staff-password#123"),
        "STAFF",
        true);
    when(users.findByEmailIgnoreCase("staff@example.com")).thenReturn(Optional.of(staff));

    CommandLineRunner runner = new AdminBootstrap().bootstrapAdmin(
        users,
        encoder,
        "staff@example.com",
        "New-password#456");

    assertThrows(IllegalStateException.class, runner::run);
  }
}
