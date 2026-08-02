package com.claritycam.platform.repository.finance;

import com.claritycam.platform.model.finance.Payment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, String> {
  Optional<Payment> findByProviderTransactionId(String providerTransactionId);
  Optional<Payment> findByIdempotencyKey(String idempotencyKey);
  List<Payment> findByBookingIdOrderByReceivedAtAsc(String bookingId);
  List<Payment> findByStatus(String status);
}
