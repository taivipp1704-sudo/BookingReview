package com.claritycam.platform.repository.inventory;

import com.claritycam.platform.model.inventory.InventoryLedgerEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryLedgerRepository extends JpaRepository<InventoryLedgerEntry, String> {
  List<InventoryLedgerEntry> findTop500ByOrderByCreatedAtDesc();
}
