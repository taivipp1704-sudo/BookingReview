package com.claritycam.platform.model.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Đánh giá của khách cho một đơn thuê đã hoàn tất: số sao 1-5 và nhận xét tự do
 * (không bắt buộc). Mỗi đơn chỉ giữ một bản ghi — gửi lại sẽ ghi đè bản ghi cũ.
 */
@Entity
@Table(name = "booking_feedback")
public class BookingFeedback {
  @Id private String id;
  @Column(name = "booking_id", unique = true, nullable = false) private String bookingId;
  @Column(nullable = false) private String phoneNormalized;
  @Column(nullable = false) private int rating;
  @Column(length = 2000, nullable = false) private String comment;
  @Column(nullable = false) private LocalDateTime createdAt;
  @Column(nullable = false) private LocalDateTime updatedAt;

  protected BookingFeedback() {}

  public BookingFeedback(String id, String bookingId, String phoneNormalized, int rating, String comment) {
    this.id = id;
    this.bookingId = bookingId;
    this.phoneNormalized = phoneNormalized;
    this.createdAt = LocalDateTime.now();
    update(rating, comment);
  }

  public void update(int rating, String comment) {
    this.rating = Math.max(1, Math.min(5, rating));
    this.comment = comment == null ? "" : comment.trim();
    this.updatedAt = LocalDateTime.now();
  }

  public String getId() { return id; }
  public String getBookingId() { return bookingId; }
  public String getPhoneNormalized() { return phoneNormalized; }
  public int getRating() { return rating; }
  public String getComment() { return comment; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
}
