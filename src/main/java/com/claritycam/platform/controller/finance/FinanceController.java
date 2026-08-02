package com.claritycam.platform.controller.finance;

import com.claritycam.platform.model.finance.FinancialLedgerEntry;
import com.claritycam.platform.service.finance.FinanceSettlementService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finance")
public class FinanceController {
  private final FinanceSettlementService finance;

  public FinanceController(FinanceSettlementService finance) {
    this.finance = finance;
  }

  @GetMapping("/entries")
  List<FinancialLedgerEntry> entries() {
    return finance.ledgerEntries();
  }

  @GetMapping("/summary")
  FinanceSettlementService.FinanceDashboard summary() {
    return finance.dashboard();
  }
}
