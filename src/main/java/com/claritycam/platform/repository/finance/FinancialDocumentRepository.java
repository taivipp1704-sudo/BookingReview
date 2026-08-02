package com.claritycam.platform.repository.finance;

import com.claritycam.platform.model.finance.FinancialDocument;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialDocumentRepository extends JpaRepository<FinancialDocument, String> {
  Optional<FinancialDocument> findByIdempotencyKey(String idempotencyKey);
  List<FinancialDocument> findByBookingIdOrderByPostedAtDesc(String bookingId);
  boolean existsByReversalOfDocumentId(String reversalOfDocumentId);
}
