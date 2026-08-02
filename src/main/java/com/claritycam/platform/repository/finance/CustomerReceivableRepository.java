package com.claritycam.platform.repository.finance;

import com.claritycam.platform.model.finance.CustomerReceivable;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerReceivableRepository extends JpaRepository<CustomerReceivable, String> {
  List<CustomerReceivable> findByBookingIdOrderByCreatedAtAsc(String bookingId);
  List<CustomerReceivable> findByStateIn(List<String> states);
}
