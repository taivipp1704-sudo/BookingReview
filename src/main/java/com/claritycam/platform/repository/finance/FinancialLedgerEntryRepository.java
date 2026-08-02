package com.claritycam.platform.repository.finance;

import com.claritycam.platform.model.finance.FinancialLedgerEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialLedgerEntryRepository extends JpaRepository<FinancialLedgerEntry, String> {
  List<FinancialLedgerEntry> findByBookingIdOrderByPostedAtAsc(String bookingId);
  List<FinancialLedgerEntry> findByDocumentIdOrderByIdAsc(String documentId);
  List<FinancialLedgerEntry> findAllByOrderByPostedAtDesc();
}
