package com.claritycam.platform.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminUserRepository extends JpaRepository<AdminUser, String> {
  Optional<AdminUser> findByEmailIgnoreCase(String email);
}
