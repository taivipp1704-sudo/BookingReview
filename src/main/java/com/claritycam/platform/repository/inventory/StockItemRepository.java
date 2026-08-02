package com.claritycam.platform.repository.inventory;

import com.claritycam.platform.model.inventory.StockItem;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockItemRepository extends JpaRepository<StockItem, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select item from StockItem item where item.productId = :productId")
  Optional<StockItem> findByIdForUpdate(@Param("productId") String productId);
}
