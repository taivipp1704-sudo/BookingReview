package com.claritycam.platform.controller.customer;

import com.claritycam.platform.config.PasswordPolicy;
import com.claritycam.platform.exception.ApiException;
import com.claritycam.platform.model.customer.CustomerAccount;
import com.claritycam.platform.repository.booking.BookingRepository;
import com.claritycam.platform.repository.customer.CustomerAccountRepository;
import com.claritycam.platform.service.audit.AuditService;
import com.claritycam.platform.service.booking.BookingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/customer-accounts")
public class AdminCustomerAccountController {
  private final CustomerAccountRepository accounts;
  private final BookingRepository bookings;
  private final BookingService bookingService;
  private final PasswordEncoder passwordEncoder;
  private final AuditService audit;

  public AdminCustomerAccountController(CustomerAccountRepository accounts, BookingRepository bookings,
      BookingService bookingService, PasswordEncoder passwordEncoder, AuditService audit) {
    this.accounts = accounts;
    this.bookings = bookings;
    this.bookingService = bookingService;
    this.passwordEncoder = passwordEncoder;
    this.audit = audit;
  }

  @GetMapping
  AccountPage list(@RequestParam(defaultValue = "") String query,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    int safePage = Math.max(0, page);
    int safeSize = Math.max(1, Math.min(100, size));
    Page<CustomerAccount> result = accounts.search(query == null ? "" : query.trim(),
        PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
    List<AccountResponse> items = result.getContent().stream().map(this::response).toList();
    return new AccountPage(items, result.getTotalElements(), result.getNumber(), result.getSize(),
        result.getTotalPages());
  }

  @PatchMapping("/{id}")
  AccountResponse update(@PathVariable String id, @Valid @RequestBody UpdateRequest body,
      Authentication authentication) {
    CustomerAccount account = require(id);
    String email = normalizeEmail(body.email());
    if (email != null) {
      accounts.findByEmailIgnoreCase(email)
          .filter(existing -> !existing.getId().equals(id))
          .ifPresent(existing -> { throw ApiException.badRequest("Email đã thuộc một tài khoản khác."); });
    }
    account.updateProfile(body.name(), email);
    account.setActive(body.active());
    CustomerAccount saved = accounts.save(account);
    audit.record(authentication.getName(), "CUSTOMER_ACCOUNT_UPDATED", "CUSTOMER_ACCOUNT", id,
        saved.isActive() ? "ACTIVE" : "LOCKED");
    return response(saved);
  }

  @PostMapping("/{id}/password-reset")
  AccountResponse resetPassword(@PathVariable String id, @Valid @RequestBody PasswordResetRequest body,
      Authentication authentication) {
    CustomerAccount account = require(id);
    account.resetPassword(passwordEncoder.encode(body.temporaryPassword()));
    CustomerAccount saved = accounts.save(account);
    audit.record(authentication.getName(), "CUSTOMER_PASSWORD_RESET", "CUSTOMER_ACCOUNT", id,
        "TEMPORARY_PASSWORD_ISSUED");
    return response(saved);
  }

  @PostMapping("/{id}/onboarding-reset")
  AccountResponse resetOnboarding(@PathVariable String id, Authentication authentication) {
    CustomerAccount account = require(id);
    account.resetOnboarding();
    CustomerAccount saved = accounts.save(account);
    audit.record(authentication.getName(), "CUSTOMER_ONBOARDING_RESET", "CUSTOMER_ACCOUNT", id,
        "ADMIN_REQUEST");
    return response(saved);
  }

  @GetMapping("/{id}/identity/{side}")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
  ResponseEntity<byte[]> identityDocument(@PathVariable String id, @PathVariable String side,
      Authentication authentication) {
    CustomerAccount account = require(id);
    var booking = bookings
        .findFirstByPhoneNormalizedAndIdentityFrontReferenceIsNotNullAndIdentityBackReferenceIsNotNullOrderByCreatedAtDesc(
            account.getPhoneNormalized())
        .orElseThrow(() -> ApiException.notFound("Khách hàng chưa có đủ ảnh CCCD."));
    var image = bookingService.identityDocument(booking.getId(), side);
    audit.record(authentication.getName(), "CUSTOMER_IDENTITY_DOCUMENT_VIEWED", "CUSTOMER_ACCOUNT", id,
        side.toLowerCase());
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(image.contentType()))
        .header("Cache-Control", "no-store, private, max-age=0")
        .header("Pragma", "no-cache")
        .header("X-Content-Type-Options", "nosniff")
        .body(image.bytes());
  }

  private CustomerAccount require(String id) {
    return accounts.findById(id)
        .orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản khách hàng."));
  }

  private AccountResponse response(CustomerAccount account) {
    return new AccountResponse(account.getId(), account.getName(), account.getEmail(),
        account.getPhoneNormalized(), account.isActive(), account.getCreatedAt(), account.getLastLoginAt(),
        account.getOnboardingVersion(), account.getOnboardingCompletedAt(),
        account.isMustChangePassword(), account.getPasswordHash() != null,
        bookings.countByPhoneNormalized(account.getPhoneNormalized()),
        bookings.existsByPhoneNormalizedAndIdentityFrontReferenceIsNotNullAndIdentityBackReferenceIsNotNull(
            account.getPhoneNormalized()));
  }

  private String normalizeEmail(String email) {
    return email == null || email.isBlank() ? null : email.trim().toLowerCase();
  }

  public record UpdateRequest(@NotBlank @Size(max = 180) String name,
                              @Email @Size(max = 255) String email,
                              boolean active) {}
  public record PasswordResetRequest(
      @NotBlank @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
      String temporaryPassword) {}
  public record AccountResponse(String id, String name, String email, String phone, boolean active,
                                LocalDateTime createdAt, LocalDateTime lastLoginAt,
                                int onboardingVersion, LocalDateTime onboardingCompletedAt,
                                boolean mustChangePassword, boolean passwordConfigured,
                                long bookingCount, boolean identityDocumentsAvailable) {}
  public record AccountPage(List<AccountResponse> items, long total, int page, int size,
                            int totalPages) {}
}
