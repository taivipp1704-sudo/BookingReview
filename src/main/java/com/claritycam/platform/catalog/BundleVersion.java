package com.claritycam.platform.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "bundle_versions")
public class BundleVersion {
  @Id
  private String id;
  private String bundleId;
  private int versionNumber;
  private String status;

  @Lob
  @Column(columnDefinition = "TEXT")
  private String snapshotJson;

  private String publishedBy;
  private LocalDateTime publishedAt;

  protected BundleVersion() {}

  public BundleVersion(String bundleId, int versionNumber, String status, String snapshotJson, String publishedBy) {
    this.id = "BVR-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    this.bundleId = bundleId;
    this.versionNumber = versionNumber;
    this.status = status;
    this.snapshotJson = snapshotJson;
    this.publishedBy = publishedBy;
    this.publishedAt = LocalDateTime.now();
  }

  public String getId() { return id; }
  public String getBundleId() { return bundleId; }
  public int getVersionNumber() { return versionNumber; }
  public String getStatus() { return status; }
  public String getSnapshotJson() { return snapshotJson; }
  public String getPublishedBy() { return publishedBy; }
  public LocalDateTime getPublishedAt() { return publishedAt; }
}
