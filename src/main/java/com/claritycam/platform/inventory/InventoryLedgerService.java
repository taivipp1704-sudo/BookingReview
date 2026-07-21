package com.claritycam.platform.inventory;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryLedgerService {
  private final InventoryLedgerRepository entries;

  public InventoryLedgerService(InventoryLedgerRepository entries) {
    this.entries = entries;
  }

  @Transactional
  public InventoryLedgerEntry append(String documentId, String productId, String serialId, String movementType,
      int quantityDelta, Integer balanceAfter, String reason, String actor) {
    String normalizedDocumentId = documentId == null || documentId.isBlank()
        ? "DOC-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase()
        : documentId.trim();
    return entries.save(new InventoryLedgerEntry(normalizedDocumentId, productId, serialId, movementType,
        quantityDelta, balanceAfter, reason, actor));
  }

  public List<InventoryLedgerEntry> recent() {
    return entries.findTop500ByOrderByCreatedAtDesc();
  }
}
