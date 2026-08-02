package com.claritycam.platform.service.customer;

import com.claritycam.platform.model.customer.CustomerAccount;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

@Service
public class CustomerSessionAccessService {
  private final CustomerAccountService accounts;

  public CustomerSessionAccessService(CustomerAccountService accounts) {
    this.accounts = accounts;
  }

  public CustomerAccount require(HttpServletRequest request) {
    String phone = request.getSession(false) == null ? null
        : (String) request.getSession(false).getAttribute(CustomerAccountService.SESSION_PHONE);
    return accounts.require(phone);
  }
}
