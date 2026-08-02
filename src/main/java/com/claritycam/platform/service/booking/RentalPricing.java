package com.claritycam.platform.service.booking;

import com.claritycam.platform.model.booking.Booking;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public final class RentalPricing {
  private static final long MINUTES_PER_HOUR = 60;
  private static final long MINUTES_PER_DAY = 1_440;

  private RentalPricing() {}

  public static Charge calculateProduct(BigDecimal hourlyPrice, BigDecimal halfDayPrice,
      BigDecimal dailyPrice, BigDecimal twoDayPrice, BigDecimal threeDayPrice,
      BigDecimal extraDayPrice, LocalDateTime pickupTime, LocalDateTime returnTime) {
    return calculateProduct(hourlyPrice, halfDayPrice, dailyPrice, twoDayPrice, threeDayPrice,
        extraDayPrice, 3, pickupTime, returnTime, null);
  }

  public static Charge calculateProduct(BigDecimal hourlyPrice, BigDecimal halfDayPrice,
      BigDecimal dailyPrice, BigDecimal twoDayPrice, BigDecimal multiDayPrice,
      BigDecimal extraDayPrice, int multiDayDays, LocalDateTime pickupTime,
      LocalDateTime returnTime, String requestedMode) {
    long minutes = Math.max(1, Duration.between(pickupTime, returnTime).toMinutes());
    long hours = ceilDivide(minutes, MINUTES_PER_HOUR);
    BigDecimal hourly = nonNegative(hourlyPrice);
    BigDecimal halfDay = nonNegative(halfDayPrice);
    BigDecimal daily = nonNegative(dailyPrice);
    BigDecimal twoDay = nonNegative(twoDayPrice);
    BigDecimal multiDay = nonNegative(multiDayPrice);
    BigDecimal extraDay = nonNegative(extraDayPrice);
    int packageDays = Math.max(2, multiDayDays);

    if (("HOURLY".equals(requestedMode) && hourly.signum() == 0)
        || ("HALF_DAY".equals(requestedMode) && halfDay.signum() == 0)) {
      requestedMode = null;
    }

    if (requestedMode != null) {
      return switch (requestedMode) {
        case "HOURLY" -> new Charge("HOURLY", hourly, hours, 0,
            hourly.multiply(BigDecimal.valueOf(hours)));
        case "HALF_DAY" -> new Charge("HALF_DAY", halfDay, 1, 0, halfDay);
        case "DAILY" -> new Charge("DAILY", daily, 1, 0, daily);
        case "TWO_DAY" -> {
          BigDecimal total = twoDay.signum() > 0 ? twoDay : daily.multiply(BigDecimal.valueOf(2));
          yield new Charge("TWO_DAY", total, 1, 0, total);
        }
        case "MULTI_DAY" -> {
          BigDecimal base = multiDay.signum() > 0
              ? multiDay
              : daily.multiply(BigDecimal.valueOf(packageDays));
          int selectedDays = Math.max(packageDays,
              Math.toIntExact(ChronoUnit.DAYS.between(pickupTime.toLocalDate(), returnTime.toLocalDate())));
          int extraDays = Math.max(0, selectedDays - packageDays);
          BigDecimal extraRate = extraDay.signum() > 0 ? extraDay : daily;
          yield new Charge("MULTI_DAY", base, 1, extraDays,
              base.add(extraRate.multiply(BigDecimal.valueOf(extraDays))));
        }
        default -> throw new IllegalArgumentException("Unsupported rental pricing mode: " + requestedMode);
      };
    }

    if (minutes <= 12 * MINUTES_PER_HOUR && (hourly.signum() > 0 || halfDay.signum() > 0)) {
      BigDecimal hourlyTotal = hourly.signum() > 0 ? hourly.multiply(BigDecimal.valueOf(hours)) : null;
      if (halfDay.signum() > 0 && (hourlyTotal == null || halfDay.compareTo(hourlyTotal) <= 0)) {
        return new Charge("HALF_DAY", halfDay, 1, 0, halfDay);
      }
      return new Charge("HOURLY", hourly, hours, 0, hourlyTotal);
    }

    long days = ceilDivide(minutes, MINUTES_PER_DAY);
    if (days == 1) return new Charge("DAILY", daily, 1, 0, daily);
    if (days == 2) {
      BigDecimal total = twoDay.signum() > 0 ? twoDay : daily.multiply(BigDecimal.valueOf(2));
      return new Charge("TWO_DAY", total, 1, 0, total);
    }
    if (days < packageDays) {
      BigDecimal total = daily.multiply(BigDecimal.valueOf(days));
      return new Charge("DAILY", daily, days, 0, total);
    }

    BigDecimal base = multiDay.signum() > 0 ? multiDay : daily.multiply(BigDecimal.valueOf(packageDays));
    int extraDays = Math.toIntExact(days - packageDays);
    BigDecimal extraRate = extraDay.signum() > 0 ? extraDay : daily;
    BigDecimal total = base.add(extraRate.multiply(BigDecimal.valueOf(extraDays)));
    return new Charge("MULTI_DAY", base, 1, extraDays, total);
  }

  public static Charge calculate(BigDecimal hourlyPrice, BigDecimal dailyPrice, BigDecimal multiDayPrice,
      int multiDayDays, LocalDateTime pickupTime, LocalDateTime returnTime) {
    return calculate(hourlyPrice, dailyPrice, multiDayPrice, multiDayDays, pickupTime, returnTime, null);
  }

  public static Charge calculate(BigDecimal hourlyPrice, BigDecimal dailyPrice, BigDecimal multiDayPrice,
      int multiDayDays, LocalDateTime pickupTime, LocalDateTime returnTime, String requestedMode) {
    long minutes = Math.max(1, Duration.between(pickupTime, returnTime).toMinutes());
    BigDecimal hourly = nonNegative(hourlyPrice);
    BigDecimal daily = nonNegative(dailyPrice);
    BigDecimal multiDay = nonNegative(multiDayPrice);
    int packageDays = Math.max(2, multiDayDays);

    if ("HOURLY".equals(requestedMode) && hourly.signum() == 0) {
      requestedMode = null;
    }

    if (requestedMode != null) {
      return switch (requestedMode) {
        case "HOURLY" -> {
          long hours = ceilDivide(minutes, MINUTES_PER_HOUR);
          yield new Charge("HOURLY", hourly, hours, 0, hourly.multiply(BigDecimal.valueOf(hours)));
        }
        case "DAILY" -> new Charge("DAILY", daily, 1, 0, daily);
        case "MULTI_DAY" -> {
          BigDecimal base = multiDay.signum() > 0
              ? multiDay
              : daily.multiply(BigDecimal.valueOf(packageDays));
          int selectedDays = Math.max(packageDays,
              Math.toIntExact(ChronoUnit.DAYS.between(pickupTime.toLocalDate(), returnTime.toLocalDate())));
          int extraDays = Math.max(0, selectedDays - packageDays);
          yield new Charge("MULTI_DAY", base, 1, extraDays,
              base.add(daily.multiply(BigDecimal.valueOf(extraDays))));
        }
        default -> throw new IllegalArgumentException("Unsupported bundle pricing mode: " + requestedMode);
      };
    }

    if (minutes < MINUTES_PER_DAY && hourly.signum() > 0) {
      long hours = ceilDivide(minutes, MINUTES_PER_HOUR);
      return new Charge("HOURLY", hourly, hours, 0, hourly.multiply(BigDecimal.valueOf(hours)));
    }

    long days = ceilDivide(minutes, MINUTES_PER_DAY);
    if (days >= packageDays && multiDay.signum() > 0) {
      long packages = days / packageDays;
      int extraDays = (int) (days % packageDays);
      BigDecimal total = multiDay.multiply(BigDecimal.valueOf(packages))
          .add(daily.multiply(BigDecimal.valueOf(extraDays)));
      return new Charge("MULTI_DAY", multiDay, packages, extraDays, total);
    }

    return new Charge("DAILY", daily, days, 0, daily.multiply(BigDecimal.valueOf(days)));
  }

  private static BigDecimal nonNegative(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value.max(BigDecimal.ZERO);
  }

  private static long ceilDivide(long value, long divisor) {
    return Math.max(1, (value + divisor - 1) / divisor);
  }

  public record Charge(String pricingMode, BigDecimal unitPrice, long billableUnits, int extraDays,
                       BigDecimal total) {}
}
