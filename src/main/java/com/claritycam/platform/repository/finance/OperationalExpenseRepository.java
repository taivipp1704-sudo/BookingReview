package com.claritycam.platform.repository.finance;

import com.claritycam.platform.model.finance.OperationalExpense;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationalExpenseRepository extends JpaRepository<OperationalExpense, String> {
  Optional<OperationalExpense> findBySourceFingerprint(String sourceFingerprint);
  List<OperationalExpense> findAllByOrderByCreatedAtDesc();
  List<OperationalExpense> findByStateIn(List<String> states);
}
