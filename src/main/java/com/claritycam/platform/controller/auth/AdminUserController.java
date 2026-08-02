package com.claritycam.platform.controller.auth;

import com.claritycam.platform.model.auth.AdminUser;
import com.claritycam.platform.repository.auth.AdminUserRepository;
import com.claritycam.platform.service.audit.AuditService;
import com.claritycam.platform.exception.ApiException;
import com.claritycam.platform.config.PasswordPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
  private final AdminUserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final AuditService audit;

  public AdminUserController(AdminUserRepository users, PasswordEncoder passwordEncoder, AuditService audit) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.audit = audit;
  }

  @GetMapping
  List<UserResponse> list() {
    return users.findAll().stream().sorted(Comparator.comparing(AdminUser::getEmail))
        .map(UserResponse::from).toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  UserResponse create(@Valid @RequestBody CreateUserRequest request, Authentication authentication) {
    String email = request.email().trim().toLowerCase();
    if (users.findByEmailIgnoreCase(email).isPresent()) {
      throw ApiException.badRequest("Email nhÃƒÆ’Ã‚Â¢n sÃƒÂ¡Ã‚Â»Ã‚Â± Ãƒâ€žÃ¢â‚¬ËœÃƒÆ’Ã‚Â£ tÃƒÂ¡Ã‚Â»Ã¢â‚¬Å“n tÃƒÂ¡Ã‚ÂºÃ‚Â¡i.");
    }
    AdminUser saved = users.save(new AdminUser("USR-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(),
        email, passwordEncoder.encode(request.password()), request.role(), true));
    audit.record(authentication.getName(), "STAFF_USER_CREATED", "ADMIN_USER", saved.getId(), saved.getRole());
    return UserResponse.from(saved);
  }

  @PatchMapping("/{id}")
  UserResponse update(@PathVariable String id, @Valid @RequestBody UpdateUserRequest request,
      Authentication authentication) {
    AdminUser user = users.findById(id).orElseThrow(() -> ApiException.notFound("KhÃƒÆ’Ã‚Â´ng tÃƒÆ’Ã‚Â¬m thÃƒÂ¡Ã‚ÂºÃ‚Â¥y tÃƒÆ’Ã‚Â i khoÃƒÂ¡Ã‚ÂºÃ‚Â£n nhÃƒÆ’Ã‚Â¢n sÃƒÂ¡Ã‚Â»Ã‚Â±."));
    if (user.getEmail().equalsIgnoreCase(authentication.getName()) && !request.active()) {
      throw ApiException.badRequest("KhÃƒÆ’Ã‚Â´ng thÃƒÂ¡Ã‚Â»Ã†â€™ tÃƒÂ¡Ã‚Â»Ã‚Â± khÃƒÆ’Ã‚Â³a tÃƒÆ’Ã‚Â i khoÃƒÂ¡Ã‚ÂºÃ‚Â£n Ãƒâ€žÃ¢â‚¬Ëœang Ãƒâ€žÃ¢â‚¬ËœÃƒâ€žÃ†â€™ng nhÃƒÂ¡Ã‚ÂºÃ‚Â­p.");
    }
    String passwordHash = request.password() == null || request.password().isBlank()
        ? null : passwordEncoder.encode(request.password());
    user.update(request.role(), request.active(), passwordHash);
    AdminUser saved = users.save(user);
    audit.record(authentication.getName(), "STAFF_USER_UPDATED", "ADMIN_USER", saved.getId(), saved.getRole());
    return UserResponse.from(saved);
  }

  public record CreateUserRequest(@NotBlank @Email String email,
                                  @NotBlank @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE) String password,
                                  @NotBlank @Pattern(regexp = "ADMIN|MANAGER|SALES|OPS|WAREHOUSE|TECH") String role) {}
  public record UpdateUserRequest(
                                  @NotBlank @Pattern(regexp = "ADMIN|MANAGER|SALES|OPS|WAREHOUSE|TECH") String role,
                                  boolean active,
                                  @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE) String password) {}
  public record UserResponse(String id, String email, String role, boolean active) {
    static UserResponse from(AdminUser user) {
      return new UserResponse(user.getId(), user.getEmail(), user.getRole(), user.isActive());
    }
  }
}
