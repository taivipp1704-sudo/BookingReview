package com.claritycam.platform.inventory;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryLedgerRepository extends JpaRepository<InventoryLedgerEntry, String> {
  List<InventoryLedgerEntry> findTop500ByOrderByCreatedAtDesc();
}
