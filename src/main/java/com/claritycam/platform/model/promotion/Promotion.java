package com.claritycam.platform.model.promotion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "promotions")
public class Promotion {
  @Id
  private String id;

  @Column(nullable = false, unique = true, length = 40)
  private String code;
  private String name;
  private BigDecimal discountPercent;
  private boolean active;
  private LocalDate startDate;
  private LocalDate endDate;
  private String applicableWeekdays;
  private String dayParity;

  protected Promotion() {}

  public Promotion(String id, String code, String name, BigDecimal discountPercent, boolean active,
      LocalDate startDate, LocalDate endDate, Set<DayOfWeek> applicableWeekdays, String dayParity) {
    this.id = id;
    apply(code, name, discountPercent, active, startDate, endDate, applicableWeekdays, dayParity);
  }

  public void apply(String code, String name, BigDecimal discountPercent, boolean active,
      LocalDate startDate, LocalDate endDate, Set<DayOfWeek> applicableWeekdays, String dayParity) {
    this.code = code.trim().toUpperCase();
    this.name = name.trim();
    this.discountPercent = discountPercent;
    this.active = active;
    this.startDate = startDate;
    this.endDate = endDate;
    this.applicableWeekdays = applicableWeekdays == null ? "" : applicableWeekdays.stream()
        .sorted().map(Enum::name).collect(Collectors.joining(","));
    this.dayParity = dayParity == null || dayParity.isBlank() ? "ALL" : dayParity.trim().toUpperCase();
  }

  public void deactivate() { this.active = false; }

  public boolean appliesTo(LocalDate date) {
    if (!active || date.isBefore(startDate) || date.isAfter(endDate)) return false;
    Set<DayOfWeek> weekdays = getApplicableWeekdays();
    if (!weekdays.isEmpty() && !weekdays.contains(date.getDayOfWeek())) return false;
    return switch (dayParity) {
      case "ODD" -> date.getDayOfMonth() % 2 == 1;
      case "EVEN" -> date.getDayOfMonth() % 2 == 0;
      default -> true;
    };
  }

  public Set<DayOfWeek> getApplicableWeekdays() {
    if (applicableWeekdays == null || applicableWeekdays.isBlank()) return Set.of();
    return Arrays.stream(applicableWeekdays.split(",")).map(DayOfWeek::valueOf).collect(Collectors.toSet());
  }

  public String getId() { return id; }
  public String getCode() { return code; }
  public String getName() { return name; }
  public BigDecimal getDiscountPercent() { return discountPercent; }
  public boolean isActive() { return active; }
  public LocalDate getStartDate() { return startDate; }
  public LocalDate getEndDate() { return endDate; }
  public String getDayParity() { return dayParity; }
}
