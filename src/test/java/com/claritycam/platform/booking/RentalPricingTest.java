package com.claritycam.platform.booking;

import com.claritycam.platform.model.booking.Booking;
import com.claritycam.platform.service.booking.RentalPricing;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class RentalPricingTest {
  private static final LocalDateTime START = LocalDateTime.of(2027, 5, 5, 8, 0);

  @Test
  void usesHalfDayWhenItIsCheaperThanHourlyTotal() {
    var charge = price(START.plusHours(8));
    assertEquals("HALF_DAY", charge.pricingMode());
    assertEquals(0, charge.total().compareTo(BigDecimal.valueOf(500_000)));
  }

  @Test
  void usesConfiguredTwoDayPackage() {
    var charge = price(START.plusDays(2));
    assertEquals("TWO_DAY", charge.pricingMode());
    assertEquals(0, charge.total().compareTo(BigDecimal.valueOf(1_800_000)));
  }

  @Test
  void addsConfiguredExtraDayAfterThreeDayPackage() {
    var charge = price(START.plusDays(5));
    assertEquals("MULTI_DAY", charge.pricingMode());
    assertEquals(2, charge.extraDays());
    assertEquals(0, charge.total().compareTo(BigDecimal.valueOf(4_000_000)));
  }

  @Test
  void selectedMultiDayPackageKeepsItsPriceWhenReturnClockIsLater() {
    var charge = RentalPricing.calculateProduct(
        BigDecimal.valueOf(100_000), BigDecimal.valueOf(500_000), BigDecimal.valueOf(1_000_000),
        BigDecimal.valueOf(1_800_000), BigDecimal.valueOf(2_600_000), BigDecimal.valueOf(700_000),
        3, START, START.plusDays(3).plusHours(1), "MULTI_DAY");

    assertEquals("MULTI_DAY", charge.pricingMode());
    assertEquals(0, charge.extraDays());
    assertEquals(0, charge.total().compareTo(BigDecimal.valueOf(2_600_000)));
  }

  private RentalPricing.Charge price(LocalDateTime returned) {
    return RentalPricing.calculateProduct(
        BigDecimal.valueOf(100_000), BigDecimal.valueOf(500_000), BigDecimal.valueOf(1_000_000),
        BigDecimal.valueOf(1_800_000), BigDecimal.valueOf(2_600_000), BigDecimal.valueOf(700_000),
        START, returned);
  }
}
