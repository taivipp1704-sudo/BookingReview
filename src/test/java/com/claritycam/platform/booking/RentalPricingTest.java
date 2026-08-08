package com.claritycam.platform.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.claritycam.platform.service.booking.RentalPricing;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class RentalPricingTest {
  private static final LocalDateTime START = LocalDateTime.of(2027, 5, 5, 8, 0);
  private static final BigDecimal HOURLY = BigDecimal.valueOf(100_000);
  private static final BigDecimal HALF_DAY = BigDecimal.valueOf(500_000);
  private static final BigDecimal DAILY = BigDecimal.valueOf(1_000_000);
  private static final BigDecimal TWO_DAY = BigDecimal.valueOf(1_800_000);
  private static final BigDecimal MULTI_DAY = BigDecimal.valueOf(2_600_000);
  private static final BigDecimal EXTRA_DAY = BigDecimal.valueOf(700_000);

  @Test
  void automaticProductPricingCoversHourlyHalfDayAndDailyDecisions() {
    assertCharge(product(HOURLY, BigDecimal.valueOf(900_000), DAILY, TWO_DAY, MULTI_DAY,
        EXTRA_DAY, 3, START.plusHours(2), null), "HOURLY", 200_000, 2, 0);
    assertCharge(product(BigDecimal.ZERO, HALF_DAY, DAILY, TWO_DAY, MULTI_DAY,
        EXTRA_DAY, 3, START.plusHours(8), null), "HALF_DAY", 500_000, 1, 0);
    assertCharge(product(HOURLY, HALF_DAY, DAILY, TWO_DAY, MULTI_DAY,
        EXTRA_DAY, 3, START.plusHours(8), null), "HALF_DAY", 500_000, 1, 0);
    assertCharge(product(HOURLY, BigDecimal.valueOf(900_000), DAILY, TWO_DAY, MULTI_DAY,
        EXTRA_DAY, 3, START.plusHours(8), null), "HOURLY", 800_000, 8, 0);
    assertCharge(product(BigDecimal.ZERO, BigDecimal.ZERO, DAILY, TWO_DAY, MULTI_DAY,
        EXTRA_DAY, 3, START.plusHours(8), null), "DAILY", 1_000_000, 1, 0);
  }

  @Test
  void automaticProductPricingCoversDayPackagesAndFallbacks() {
    assertCharge(product(HOURLY, HALF_DAY, DAILY, TWO_DAY, MULTI_DAY,
        EXTRA_DAY, 3, START.plusDays(2), null), "TWO_DAY", 1_800_000, 1, 0);
    assertCharge(product(HOURLY, HALF_DAY, DAILY, BigDecimal.ZERO, MULTI_DAY,
        EXTRA_DAY, 3, START.plusDays(2), null), "TWO_DAY", 2_000_000, 1, 0);
    assertCharge(product(HOURLY, HALF_DAY, DAILY, TWO_DAY, MULTI_DAY,
        EXTRA_DAY, 4, START.plusDays(3), null), "DAILY", 3_000_000, 3, 0);
    assertCharge(product(HOURLY, HALF_DAY, DAILY, TWO_DAY, MULTI_DAY,
        EXTRA_DAY, 3, START.plusDays(5), null), "MULTI_DAY", 4_000_000, 1, 2);
    assertCharge(product(HOURLY, HALF_DAY, DAILY, TWO_DAY, BigDecimal.ZERO,
        BigDecimal.ZERO, 3, START.plusDays(5), null), "MULTI_DAY", 5_000_000, 1, 2);
  }

  @Test
  void requestedProductModesUseExactSelectionAndConfiguredFallbacks() {
    assertCharge(product(HOURLY, HALF_DAY, DAILY, TWO_DAY, MULTI_DAY, EXTRA_DAY,
        3, START.plusHours(2), "HOURLY"), "HOURLY", 200_000, 2, 0);
    assertCharge(product(HOURLY, HALF_DAY, DAILY, TWO_DAY, MULTI_DAY, EXTRA_DAY,
        3, START.plusHours(2), "HALF_DAY"), "HALF_DAY", 500_000, 1, 0);
    assertCharge(product(HOURLY, HALF_DAY, DAILY, TWO_DAY, MULTI_DAY, EXTRA_DAY,
        3, START.plusHours(2), "DAILY"), "DAILY", 1_000_000, 1, 0);
    assertCharge(product(HOURLY, HALF_DAY, DAILY, TWO_DAY, MULTI_DAY, EXTRA_DAY,
        3, START.plusDays(2), "TWO_DAY"), "TWO_DAY", 1_800_000, 1, 0);
    assertCharge(product(HOURLY, HALF_DAY, DAILY, BigDecimal.ZERO, MULTI_DAY, EXTRA_DAY,
        3, START.plusDays(2), "TWO_DAY"), "TWO_DAY", 2_000_000, 1, 0);
    assertCharge(product(HOURLY, HALF_DAY, DAILY, TWO_DAY, MULTI_DAY, EXTRA_DAY,
        3, START.plusDays(5), "MULTI_DAY"), "MULTI_DAY", 4_000_000, 1, 2);
    assertCharge(product(HOURLY, HALF_DAY, DAILY, TWO_DAY, BigDecimal.ZERO, BigDecimal.ZERO,
        1, START.plusDays(1), "MULTI_DAY"), "MULTI_DAY", 2_000_000, 1, 0);
  }

  @Test
  void unavailableRequestedHourlyAndHalfDayModesFallBackToAutomaticPricing() {
    assertEquals("DAILY", product(null, HALF_DAY, DAILY, TWO_DAY, MULTI_DAY, EXTRA_DAY,
        3, START.plusDays(1), "HOURLY").pricingMode());
    assertEquals("HOURLY", product(HOURLY, null, DAILY, TWO_DAY, MULTI_DAY, EXTRA_DAY,
        3, START.plusHours(2), "HALF_DAY").pricingMode());
  }

  @Test
  void unsupportedProductModeFailsFast() {
    assertThrows(IllegalArgumentException.class, () -> product(HOURLY, HALF_DAY, DAILY,
        TWO_DAY, MULTI_DAY, EXTRA_DAY, 3, START.plusDays(1), "UNKNOWN"));
  }

  @Test
  void nonPositiveDurationAndNegativePricesAreNormalized() {
    var charge = product(BigDecimal.valueOf(-1), BigDecimal.valueOf(-1), BigDecimal.valueOf(-1),
        BigDecimal.valueOf(-1), BigDecimal.valueOf(-1), BigDecimal.valueOf(-1),
        1, START.minusHours(1), null);
    assertCharge(charge, "DAILY", 0, 1, 0);
  }

  @Test
  void bundlePricingCoversRequestedModesAndFailures() {
    assertCharge(RentalPricing.calculate(HOURLY, DAILY, MULTI_DAY, 3,
        START, START.plusMinutes(61), "HOURLY"), "HOURLY", 200_000, 2, 0);
    assertCharge(RentalPricing.calculate(HOURLY, DAILY, MULTI_DAY, 3,
        START, START.plusDays(1), "DAILY"), "DAILY", 1_000_000, 1, 0);
    assertCharge(RentalPricing.calculate(HOURLY, DAILY, MULTI_DAY, 3,
        START, START.plusDays(5), "MULTI_DAY"), "MULTI_DAY", 4_600_000, 1, 2);
    assertCharge(RentalPricing.calculate(HOURLY, DAILY, BigDecimal.ZERO, 1,
        START, START.plusDays(1), "MULTI_DAY"), "MULTI_DAY", 2_000_000, 1, 0);
    assertThrows(IllegalArgumentException.class, () -> RentalPricing.calculate(HOURLY,
        DAILY, MULTI_DAY, 3, START, START.plusDays(1), "UNKNOWN"));
  }

  @Test
  void unavailableRequestedBundleHourlyModeFallsBackToDaily() {
    assertEquals("DAILY", RentalPricing.calculate(null, DAILY, MULTI_DAY, 3,
        START, START.plusDays(1), "HOURLY").pricingMode());
  }

  @Test
  void automaticBundlePricingCoversHourlyPackageAndDailyPaths() {
    assertCharge(RentalPricing.calculate(HOURLY, DAILY, MULTI_DAY, 3,
        START, START.plusHours(2)), "HOURLY", 200_000, 2, 0);
    assertCharge(RentalPricing.calculate(HOURLY, DAILY, MULTI_DAY, 3,
        START, START.plusDays(7)), "MULTI_DAY", 6_200_000, 2, 1);
    assertCharge(RentalPricing.calculate(HOURLY, DAILY, BigDecimal.ZERO, 3,
        START, START.plusDays(3)), "DAILY", 3_000_000, 3, 0);
    assertCharge(RentalPricing.calculate(BigDecimal.ZERO, DAILY, MULTI_DAY, 3,
        START, START.plusHours(2)), "DAILY", 1_000_000, 1, 0);
  }

  private RentalPricing.Charge product(BigDecimal hourly, BigDecimal halfDay,
      BigDecimal daily, BigDecimal twoDay, BigDecimal multiDay, BigDecimal extraDay,
      int multiDayDays, LocalDateTime returned, String mode) {
    return RentalPricing.calculateProduct(hourly, halfDay, daily, twoDay, multiDay, extraDay,
        multiDayDays, START, returned, mode);
  }

  private void assertCharge(RentalPricing.Charge charge, String mode, long total,
      long units, int extraDays) {
    assertEquals(mode, charge.pricingMode());
    assertEquals(0, charge.total().compareTo(BigDecimal.valueOf(total)));
    assertEquals(units, charge.billableUnits());
    assertEquals(extraDays, charge.extraDays());
  }
}
