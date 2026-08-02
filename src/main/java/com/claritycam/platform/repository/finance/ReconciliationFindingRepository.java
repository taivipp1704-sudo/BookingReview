package com.claritycam.platform.repository.finance;

import com.claritycam.platform.model.finance.ReconciliationFinding;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationFindingRepository extends JpaRepository<ReconciliationFinding, String> {
  List<ReconciliationFinding> findByBookingIdAndStateOrderByDetectedAtAsc(String bookingId, String state);
  List<ReconciliationFinding> findByStateOrderByDetectedAtDesc(String state);
  boolean existsByBookingIdAndCodeAndState(String bookingId, String code, String state);
  long countBySeverityAndState(String severity, String state);
}
