package com.claritycam.platform.repository.finance;

import com.claritycam.platform.model.finance.FinancialPeriod;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialPeriodRepository extends JpaRepository<FinancialPeriod, String> {}
