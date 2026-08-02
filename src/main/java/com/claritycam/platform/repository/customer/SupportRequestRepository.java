package com.claritycam.platform.repository.customer;

import com.claritycam.platform.model.customer.SupportRequest;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportRequestRepository extends JpaRepository<SupportRequest,String> {
  List<SupportRequest> findByPhoneNormalizedOrderByCreatedAtDesc(String phoneNormalized);
  List<SupportRequest> findAllByOrderByCreatedAtDesc();
}
