package com.claritycam.platform.repository.finance;

import com.claritycam.platform.model.finance.CommercialSnapshotLine;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercialSnapshotLineRepository extends JpaRepository<CommercialSnapshotLine, String> {
  List<CommercialSnapshotLine> findByBookingIdOrderByIdAsc(String bookingId);
}
