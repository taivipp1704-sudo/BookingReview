package com.claritycam.platform.service.promotion;

import com.claritycam.platform.model.promotion.Promotion;
import com.claritycam.platform.repository.promotion.PromotionRepository;
import com.claritycam.platform.exception.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PromotionService {
  private final PromotionRepository promotions;

  public PromotionService(PromotionRepository promotions) {
    this.promotions = promotions;
  }

  public Application apply(String promotionCode, LocalDateTime pickupTime, LocalDateTime returnTime,
      BigDecimal subtotal) {
    if (promotionCode == null || promotionCode.isBlank()) return Application.none();
    Promotion promotion = promotions.findByCodeIgnoreCase(promotionCode.trim())
        .filter(Promotion::isActive)
        .orElseThrow(() -> ApiException.badRequest("MÃƒÆ’Ã‚Â£ giÃƒÂ¡Ã‚ÂºÃ‚Â£m giÃƒÆ’Ã‚Â¡ khÃƒÆ’Ã‚Â´ng tÃƒÂ¡Ã‚Â»Ã¢â‚¬Å“n tÃƒÂ¡Ã‚ÂºÃ‚Â¡i hoÃƒÂ¡Ã‚ÂºÃ‚Â·c Ãƒâ€žÃ¢â‚¬ËœÃƒÆ’Ã‚Â£ ngÃƒÂ¡Ã‚Â»Ã‚Â«ng ÃƒÆ’Ã‚Â¡p dÃƒÂ¡Ã‚Â»Ã‚Â¥ng."));

    long totalMinutes = Math.max(1, Duration.between(pickupTime, returnTime).toMinutes());
    List<DailyDiscount> breakdown = new ArrayList<>();
    BigDecimal allocated = BigDecimal.ZERO;
    BigDecimal discount = BigDecimal.ZERO;
    boolean anyEligible = false;
    LocalDateTime cursor = pickupTime;

    while (cursor.isBefore(returnTime)) {
      LocalDateTime nextMidnight = cursor.toLocalDate().plusDays(1).atStartOfDay();
      LocalDateTime segmentEnd = nextMidnight.isBefore(returnTime) ? nextMidnight : returnTime;
      long minutes = Math.max(1, Duration.between(cursor, segmentEnd).toMinutes());
      boolean last = !segmentEnd.isBefore(returnTime);
      BigDecimal baseAmount = last
          ? subtotal.subtract(allocated)
          : subtotal.multiply(BigDecimal.valueOf(minutes))
              .divide(BigDecimal.valueOf(totalMinutes), 0, RoundingMode.HALF_UP);
      allocated = allocated.add(baseAmount);
      LocalDate date = cursor.toLocalDate();
      boolean eligible = promotion.appliesTo(date);
      BigDecimal dayDiscount = eligible
          ? baseAmount.multiply(promotion.getDiscountPercent()).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
          : BigDecimal.ZERO;
      anyEligible = anyEligible || eligible;
      discount = discount.add(dayDiscount);
      breakdown.add(new DailyDiscount(date, cursor.toLocalTime(), segmentEnd.toLocalTime(), baseAmount,
          eligible, dayDiscount));
      cursor = segmentEnd;
    }

    if (!anyEligible) {
      throw ApiException.badRequest("MÃƒÆ’Ã‚Â£ " + promotion.getCode() + " khÃƒÆ’Ã‚Â´ng ÃƒÆ’Ã‚Â¡p dÃƒÂ¡Ã‚Â»Ã‚Â¥ng cho ngÃƒÆ’Ã‚Â y thuÃƒÆ’Ã‚Âª Ãƒâ€žÃ¢â‚¬ËœÃƒÆ’Ã‚Â£ chÃƒÂ¡Ã‚Â»Ã‚Ân.");
    }
    return new Application(promotion.getCode(), promotion.getName(), promotion.getDiscountPercent(),
        discount.min(subtotal), breakdown);
  }

  public record DailyDiscount(LocalDate date, LocalTime fromTime, LocalTime toTime, BigDecimal baseAmount,
                              boolean eligible, BigDecimal discountAmount) {}
  public record Application(String code, String name, BigDecimal discountPercent, BigDecimal discountAmount,
                            List<DailyDiscount> breakdown) {
    static Application none() {
      return new Application(null, null, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
    }
  }
}
