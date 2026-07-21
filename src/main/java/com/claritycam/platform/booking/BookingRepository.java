package com.claritycam.platform.booking;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.Collection;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface BookingRepository extends JpaRepository<Booking, String> {
  @Query(value = """
      select line.product_id as productId, count(distinct line.booking_id) as bookingCount
      from booking_lines line
      join bookings booking on booking.id = line.booking_id
      where booking.state <> 'REJECTED'
      group by line.product_id
      """, nativeQuery = true)
  List<ProductBookingCount> countBookingsByProduct();

  @Query("select distinct booking from Booking booking left join fetch booking.items order by booking.createdAt desc")
  List<Booking> findAllWithItemsOrderByCreatedAtDesc();

  @Query("select distinct booking from Booking booking left join fetch booking.items where booking.id = :id")
  Optional<Booking> findByIdWithItems(@Param("id") String id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select distinct booking from Booking booking left join fetch booking.items where booking.id = :id")
  Optional<Booking> findByIdWithItemsForUpdate(@Param("id") String id);

  @Query("select distinct booking from Booking booking left join fetch booking.items where booking.state in :states and booking.pickupTime < :to and booking.returnTime > :from")
  List<Booking> findOverlappingWithItems(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
      @Param("states") Collection<BookingState> states);

  @Query("select distinct booking from Booking booking left join fetch booking.items where booking.phoneNormalized = :phone order by booking.createdAt desc")
  List<Booking> findByPhoneWithItems(@Param("phone") String phone);

  interface ProductBookingCount {
    String getProductId();
    long getBookingCount();
  }
}
