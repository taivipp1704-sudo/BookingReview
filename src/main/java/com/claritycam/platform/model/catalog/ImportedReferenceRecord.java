package com.claritycam.platform.model.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "imported_reference_records")
public class ImportedReferenceRecord {
  @Id
  @Column(length = 160)
  private String id;

  @Column(nullable = false, length = 40)
  private String category;

  @Column(nullable = false, length = 180)
  private String label;

  @Column(nullable = false, length = 24)
  private String reviewStatus;

  @Column(nullable = false, length = 255)
  private String sourceFile;

  @Column(nullable = false, columnDefinition = "LONGTEXT")
  private String payload;

  @Column(nullable = false)
  private LocalDateTime importedAt;

  protected ImportedReferenceRecord() {}

  public ImportedReferenceRecord(String id, String category, String label, String sourceFile, String payload) {
    this.id = id;
    this.category = category;
    this.label = label;
    this.reviewStatus = "DRAFT";
    this.sourceFile = sourceFile;
    this.payload = payload;
    this.importedAt = LocalDateTime.now();
  }

  public void refresh(String label, String sourceFile, String payload) {
    this.label = label;
    this.sourceFile = sourceFile;
    this.payload = payload;
    this.reviewStatus = "DRAFT";
    this.importedAt = LocalDateTime.now();
  }

  public String getId() { return id; }
  public String getCategory() { return category; }
  public String getLabel() { return label; }
  public String getReviewStatus() { return reviewStatus; }
  public String getSourceFile() { return sourceFile; }
  public String getPayload() { return payload; }
  public LocalDateTime getImportedAt() { return importedAt; }
}
