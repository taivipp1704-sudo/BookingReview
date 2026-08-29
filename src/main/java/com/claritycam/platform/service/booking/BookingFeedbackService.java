package com.claritycam.platform.service.booking;

import com.claritycam.platform.exception.ApiException;
import com.claritycam.platform.model.booking.Booking;
import com.claritycam.platform.model.booking.BookingFeedback;
import com.claritycam.platform.model.booking.BookingState;
import com.claritycam.platform.repository.booking.BookingFeedbackRepository;
import com.claritycam.platform.repository.booking.BookingRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingFeedbackService {
  private final BookingFeedbackRepository feedbacks;
  private final BookingRepository bookings;

  public BookingFeedbackService(BookingFeedbackRepository feedbacks, BookingRepository bookings) {
    this.feedbacks = feedbacks;
    this.bookings = bookings;
  }

  @Transactional
  public BookingFeedback submit(String bookingId, String phone, int rating, String comment) {
    if (rating < 1 || rating > 5) {
      throw ApiException.badRequest("Vui lòng chọn số sao đánh giá từ 1 đến 5.");
    }
    Booking booking = bookings.findById(bookingId)
        .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn thuê."));
    if (!booking.getPhoneNormalized().equals(phone)) {
      throw ApiException.notFound("Không tìm thấy đơn thuê.");
    }
    if (booking.getState() != BookingState.COMPLETED) {
      throw ApiException.badRequest("Chỉ có thể đánh giá sau khi đơn thuê đã hoàn tất.");
    }
    String trimmedComment = comment == null ? "" : comment.trim();
    BookingFeedback feedback = feedbacks.findByBookingId(bookingId).orElse(null);
    if (feedback == null) {
      feedback = new BookingFeedback(
          "FB-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(),
          bookingId, phone, rating, trimmedComment);
    } else {
      feedback.update(rating, trimmedComment);
    }
    return feedbacks.save(feedback);
  }

  public Optional<BookingFeedback> forBooking(String bookingId, String phone) {
    return feedbacks.findByBookingId(bookingId)
        .filter(feedback -> feedback.getPhoneNormalized().equals(phone));
  }

  public List<BookingFeedback> listAll() {
    return feedbacks.findAllByOrderByCreatedAtDesc();
  }
}
