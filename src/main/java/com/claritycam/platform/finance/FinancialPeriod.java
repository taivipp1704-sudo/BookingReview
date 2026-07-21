package com.claritycam.platform.finance;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "financial_periods")
public class FinancialPeriod {
  @Id private String id;
  private LocalDate startsOn;
  private LocalDate endsOn;
  private String state;
  private LocalDateTime closedAt;
  private String closedBy;
  @Version private long version;

  protected FinancialPeriod() {}

  public FinancialPeriod(String id, LocalDate startsOn, LocalDate endsOn) {
    this.id = id;
    this.startsOn = startsOn;
    this.endsOn = endsOn;
    this.state = "OPEN";
  }

  public void softLock(String actor) {
    this.state = "SOFT_LOCKED";
    this.closedAt = LocalDateTime.now();
    this.closedBy = actor;
  }

  public void hardLock(String actor) {
    this.state = "HARD_LOCKED";
    this.closedAt = LocalDateTime.now();
    this.closedBy = actor;
  }

  public void reopen() {
    this.state = "OPEN";
    this.closedAt = null;
    this.closedBy = null;
  }

  public String getId() { return id; }
  public LocalDate getStartsOn() { return startsOn; }
  public LocalDate getEndsOn() { return endsOn; }
  public String getState() { return state; }
  public LocalDateTime getClosedAt() { return closedAt; }
  public String getClosedBy() { return closedBy; }
  public long getVersion() { return version; }
}
