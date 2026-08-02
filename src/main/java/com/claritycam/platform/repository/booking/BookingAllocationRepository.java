package com.claritycam.platform.repository.booking;

import com.claritycam.platform.model.booking.AllocationState;
import com.claritycam.platform.model.booking.Booking;
import com.claritycam.platform.model.booking.BookingAllocation;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingAllocationRepository extends JpaRepository<BookingAllocation, String> {
  List<BookingAllocation> findByBookingIdOrderByCreatedAtAsc(String bookingId);
  List<BookingAllocation> findByBookingIdAndStateIn(String bookingId, Collection<AllocationState> states);
  boolean existsBySerialIdAndStateIn(String serialId, Collection<AllocationState> states);
}
