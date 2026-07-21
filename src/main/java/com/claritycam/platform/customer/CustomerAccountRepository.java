package com.claritycam.platform.customer;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerAccountRepository extends JpaRepository<CustomerAccount, String> {
  Optional<CustomerAccount> findByPhoneNormalized(String phoneNormalized);
}
