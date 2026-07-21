package com.claritycam.platform.catalog;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bundles")
public class RentalBundle {
  @Id
  private String id;
  private String name;
  private BigDecimal hourlyPrice;
  private BigDecimal dailyPrice;
  private BigDecimal multiDayPrice;
  private int multiDayDays;
  private boolean active;
  private String imageUrl;
  private String detailImageUrl;
  @Column(length = 1000)
  private String note;
  private int currentVersion;

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "bundle_id")
  private List<BundleLine> items = new ArrayList<>();

  protected RentalBundle() {}

  public RentalBundle(String id, String name, BigDecimal dailyPrice, boolean active, String imageUrl, List<BundleLine> items) {
    this(id, name, dailyPrice, active, imageUrl, imageUrl, items);
  }

  public RentalBundle(String id, String name, BigDecimal dailyPrice, boolean active, String imageUrl,
      String detailImageUrl, List<BundleLine> items) {
    this(id, name, dailyPrice, active, imageUrl, detailImageUrl, "", items);
  }

  public RentalBundle(String id, String name, BigDecimal dailyPrice, boolean active, String imageUrl,
      String detailImageUrl, String note, List<BundleLine> items) {
    this.id = id;
    this.name = name.trim();
    this.hourlyPrice = BigDecimal.ZERO;
    this.dailyPrice = dailyPrice;
    this.multiDayPrice = BigDecimal.ZERO;
    this.multiDayDays = 3;
    this.active = active;
    this.imageUrl = imageUrl == null ? "" : imageUrl.trim();
    this.detailImageUrl = detailImageUrl == null || detailImageUrl.isBlank()
        ? this.imageUrl
        : detailImageUrl.trim();
    this.note = note == null ? "" : note.trim();
    this.currentVersion = 1;
    this.items.addAll(items);
  }

  public void apply(String name, BigDecimal hourlyPrice, BigDecimal dailyPrice, BigDecimal multiDayPrice,
      Integer multiDayDays, boolean active, String imageUrl, String detailImageUrl, String note,
      List<BundleLine> items) {
    this.name = name.trim();
    if (hourlyPrice != null) this.hourlyPrice = hourlyPrice;
    this.dailyPrice = dailyPrice;
    if (multiDayPrice != null) this.multiDayPrice = multiDayPrice;
    if (multiDayDays != null) this.multiDayDays = multiDayDays;
    this.active = active;
    this.imageUrl = imageUrl == null ? "" : imageUrl.trim();
    this.detailImageUrl = detailImageUrl == null || detailImageUrl.isBlank() ? this.imageUrl : detailImageUrl.trim();
    this.note = note == null ? "" : note.trim();
    this.items.clear();
    this.items.addAll(items);
  }

  public void deactivate() { this.active = false; }

  public void publishNextVersion() {
    this.currentVersion = getCurrentVersion() + 1;
  }

  public void configurePricing(BigDecimal hourlyPrice, BigDecimal multiDayPrice, int multiDayDays) {
    this.hourlyPrice = hourlyPrice == null ? BigDecimal.ZERO : hourlyPrice.max(BigDecimal.ZERO);
    this.multiDayPrice = multiDayPrice == null ? BigDecimal.ZERO : multiDayPrice.max(BigDecimal.ZERO);
    this.multiDayDays = Math.max(2, multiDayDays);
  }

  public String getId() { return id; }
  public String getName() { return name; }
  public BigDecimal getHourlyPrice() { return hourlyPrice == null ? BigDecimal.ZERO : hourlyPrice; }
  public BigDecimal getDailyPrice() { return dailyPrice; }
  public BigDecimal getMultiDayPrice() { return multiDayPrice == null ? BigDecimal.ZERO : multiDayPrice; }
  public int getMultiDayDays() { return multiDayDays < 2 ? 3 : multiDayDays; }
  public boolean isActive() { return active; }
  public String getImageUrl() { return imageUrl; }
  public String getDetailImageUrl() { return detailImageUrl == null || detailImageUrl.isBlank() ? imageUrl : detailImageUrl; }
  public String getNote() { return note == null ? "" : note; }
  public int getCurrentVersion() { return currentVersion < 1 ? 1 : currentVersion; }
  public List<BundleLine> getItems() { return items; }
}
