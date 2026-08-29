package com.claritycam.platform.controller.booking;

import com.claritycam.platform.model.booking.BookingFeedback;
import com.claritycam.platform.service.booking.BookingFeedbackService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/feedback")
public class AdminBookingFeedbackController {
  private final BookingFeedbackService feedbackService;

  public AdminBookingFeedbackController(BookingFeedbackService feedbackService) {
    this.feedbackService = feedbackService;
  }

  @GetMapping
  List<FeedbackResponse> list() {
    return feedbackService.listAll().stream().map(FeedbackResponse::from).toList();
  }

  public record FeedbackResponse(String id, String bookingId, String phone, int rating, String comment,
                                 LocalDateTime createdAt, LocalDateTime updatedAt) {
    static FeedbackResponse from(BookingFeedback feedback) {
      return new FeedbackResponse(feedback.getId(), feedback.getBookingId(), feedback.getPhoneNormalized(),
          feedback.getRating(), feedback.getComment(), feedback.getCreatedAt(), feedback.getUpdatedAt());
    }
  }
}
