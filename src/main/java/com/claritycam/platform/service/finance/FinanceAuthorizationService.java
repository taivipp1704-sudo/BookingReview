package com.claritycam.platform.service.finance;

import com.claritycam.platform.exception.ApiException;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class FinanceAuthorizationService {
  private static final Map<String, Set<String>> CAPABILITIES = Map.of(
      "ADMIN", Set.of("FINANCE_VIEW", "PAYMENT_RECORD", "CHARGE_CONFIRM", "REFUND_APPROVE",
          "REFUND_EXECUTE", "SETTLEMENT_APPROVE", "FINANCIAL_CLOSE", "RECONCILE", "EXPENSE_SUBMIT",
          "EXPENSE_APPROVE", "EXPENSE_PAY", "DOCUMENT_REVERSE", "PERIOD_CLOSE", "PERIOD_REOPEN"),
      "MANAGER", Set.of("FINANCE_VIEW", "PAYMENT_RECORD", "CHARGE_CONFIRM", "REFUND_APPROVE",
          "REFUND_EXECUTE", "SETTLEMENT_APPROVE", "FINANCIAL_CLOSE", "RECONCILE", "EXPENSE_SUBMIT",
          "EXPENSE_APPROVE", "EXPENSE_PAY", "PERIOD_CLOSE"));

  public void require(Authentication authentication, String capability) {
    boolean allowed = authentication != null && authentication.getAuthorities().stream()
        .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
        .anyMatch(role -> CAPABILITIES.getOrDefault(role, Set.of()).contains(capability));
    if (!allowed) throw ApiException.forbidden("TÃƒÆ’Ã‚Â i khoÃƒÂ¡Ã‚ÂºÃ‚Â£n khÃƒÆ’Ã‚Â´ng cÃƒÆ’Ã‚Â³ quyÃƒÂ¡Ã‚Â»Ã‚Ân " + capability + ".");
  }
}
