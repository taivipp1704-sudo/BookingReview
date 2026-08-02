package com.claritycam.platform.service.catalog;

import com.claritycam.platform.model.booking.Booking;
import com.claritycam.platform.model.catalog.Product;
import com.claritycam.platform.repository.booking.BookingRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ProductBookingCountService {
  private final BookingRepository bookings;

  public ProductBookingCountService(BookingRepository bookings) {
    this.bookings = bookings;
  }

  public List<Product> includeBookingCounts(List<Product> products) {
    Map<String, Long> actualCounts = bookings.countBookingsByProduct().stream()
        .collect(Collectors.toMap(
            BookingRepository.ProductBookingCount::getProductId,
            BookingRepository.ProductBookingCount::getBookingCount));
    products.forEach(product -> product.updateBookingCount(actualCounts.getOrDefault(product.getId(), 0L)));
    return products;
  }
}
