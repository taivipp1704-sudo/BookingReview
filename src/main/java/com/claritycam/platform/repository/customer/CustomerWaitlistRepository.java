package com.claritycam.platform.repository.customer;

import com.claritycam.platform.model.customer.CustomerWaitlistEntry;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerWaitlistRepository extends JpaRepository<CustomerWaitlistEntry, Long> {
  Optional<CustomerWaitlistEntry> findByPhoneNormalized(String phoneNormalized);
  List<CustomerWaitlistEntry> findAllByOrderByCreatedAtDesc();
}
