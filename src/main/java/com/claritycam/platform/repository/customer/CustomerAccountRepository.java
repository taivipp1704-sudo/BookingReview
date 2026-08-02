package com.claritycam.platform.repository.customer;

import com.claritycam.platform.model.customer.CustomerAccount;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerAccountRepository extends JpaRepository<CustomerAccount, String> {
  Optional<CustomerAccount> findByPhoneNormalized(String phoneNormalized);
  Optional<CustomerAccount> findByEmailIgnoreCase(String email);

  @Query("""
      select account from CustomerAccount account
      where :query = ''
         or lower(account.name) like lower(concat('%', :query, '%'))
         or account.phoneNormalized like concat('%', :query, '%')
         or lower(account.email) like lower(concat('%', :query, '%'))
      """)
  Page<CustomerAccount> search(@Param("query") String query, Pageable pageable);
}
