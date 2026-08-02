package com.claritycam.platform.controller.promotion;

import com.claritycam.platform.model.promotion.Promotion;
import com.claritycam.platform.repository.promotion.PromotionRepository;
import com.claritycam.platform.service.audit.AuditService;
import com.claritycam.platform.exception.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/promotions")
public class PromotionController {
  private final PromotionRepository promotions;
  private final AuditService audit;

  public PromotionController(PromotionRepository promotions, AuditService audit) {
    this.promotions = promotions;
    this.audit = audit;
  }

  @GetMapping
  List<Promotion> list() {
    return promotions.findAll().stream().sorted(Comparator.comparing(Promotion::getStartDate).reversed()).toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER','SALES')")
  Promotion create(@Valid @RequestBody Payload payload, Authentication authentication) {
    validate(payload, null);
    Promotion saved = promotions.save(new Promotion("PROMO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
        payload.code(), payload.name(), payload.discountPercent(), payload.active(), payload.startDate(),
        payload.endDate(), payload.applicableWeekdays(), payload.dayParity()));
    audit.record(authentication.getName(), "PROMOTION_CREATED", "PROMOTION", saved.getId(), saved.getCode());
    return saved;
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER','SALES')")
  Promotion update(@PathVariable String id, @Valid @RequestBody Payload payload, Authentication authentication) {
    Promotion promotion = promotions.findById(id).orElseThrow(() -> ApiException.notFound("KhÃƒÆ’Ã‚Â´ng tÃƒÆ’Ã‚Â¬m thÃƒÂ¡Ã‚ÂºÃ‚Â¥y mÃƒÆ’Ã‚Â£ giÃƒÂ¡Ã‚ÂºÃ‚Â£m giÃƒÆ’Ã‚Â¡."));
    validate(payload, promotion.getCode());
    promotion.apply(payload.code(), payload.name(), payload.discountPercent(), payload.active(), payload.startDate(),
        payload.endDate(), payload.applicableWeekdays(), payload.dayParity());
    Promotion saved = promotions.save(promotion);
    audit.record(authentication.getName(), "PROMOTION_UPDATED", "PROMOTION", id, saved.getCode());
    return saved;
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER','SALES')")
  Promotion archive(@PathVariable String id, Authentication authentication) {
    Promotion promotion = promotions.findById(id).orElseThrow(() -> ApiException.notFound("KhÃƒÆ’Ã‚Â´ng tÃƒÆ’Ã‚Â¬m thÃƒÂ¡Ã‚ÂºÃ‚Â¥y mÃƒÆ’Ã‚Â£ giÃƒÂ¡Ã‚ÂºÃ‚Â£m giÃƒÆ’Ã‚Â¡."));
    promotion.deactivate();
    Promotion saved = promotions.save(promotion);
    audit.record(authentication.getName(), "PROMOTION_ARCHIVED", "PROMOTION", id, saved.getCode());
    return saved;
  }

  private void validate(Payload payload, String currentCode) {
    if (payload.endDate().isBefore(payload.startDate())) {
      throw ApiException.badRequest("NgÃƒÆ’Ã‚Â y kÃƒÂ¡Ã‚ÂºÃ‚Â¿t thÃƒÆ’Ã‚Âºc phÃƒÂ¡Ã‚ÂºÃ‚Â£i bÃƒÂ¡Ã‚ÂºÃ‚Â±ng hoÃƒÂ¡Ã‚ÂºÃ‚Â·c sau ngÃƒÆ’Ã‚Â y bÃƒÂ¡Ã‚ÂºÃ‚Â¯t Ãƒâ€žÃ¢â‚¬ËœÃƒÂ¡Ã‚ÂºÃ‚Â§u.");
    }
    boolean duplicate = promotions.findByCodeIgnoreCase(payload.code().trim())
        .map(found -> currentCode == null || !found.getCode().equalsIgnoreCase(currentCode)).orElse(false);
    if (duplicate) throw ApiException.badRequest("MÃƒÆ’Ã‚Â£ giÃƒÂ¡Ã‚ÂºÃ‚Â£m giÃƒÆ’Ã‚Â¡ Ãƒâ€žÃ¢â‚¬ËœÃƒÆ’Ã‚Â£ tÃƒÂ¡Ã‚Â»Ã¢â‚¬Å“n tÃƒÂ¡Ã‚ÂºÃ‚Â¡i.");
  }

  public record Payload(
      @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{3,40}") String code,
      @NotBlank String name,
      @NotNull @DecimalMin("1") @DecimalMax("90") BigDecimal discountPercent,
      boolean active,
      @NotNull LocalDate startDate,
      @NotNull LocalDate endDate,
      Set<DayOfWeek> applicableWeekdays,
      @NotBlank @Pattern(regexp = "ALL|ODD|EVEN") String dayParity) {}
}
