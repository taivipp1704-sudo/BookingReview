package com.claritycam.platform.repository.finance;

import com.claritycam.platform.model.finance.FinanceOutboxEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceOutboxEventRepository extends JpaRepository<FinanceOutboxEvent, String> {
  List<FinanceOutboxEvent> findByAggregateIdOrderByCreatedAtAsc(String aggregateId);
}
