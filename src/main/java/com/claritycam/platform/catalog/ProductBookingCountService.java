package com.claritycam.platform.catalog;

import com.claritycam.platform.booking.BookingRepository;
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
