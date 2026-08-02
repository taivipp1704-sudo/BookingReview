package com.claritycam.platform.repository.inventory;

import com.claritycam.platform.model.inventory.InventoryAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryAssetRepository extends JpaRepository<InventoryAsset, String> {
  long countByProductIdAndStatus(String productId, String status);
  long countByProductId(String productId);
  long countByProductIdAndStatusIn(String productId, Collection<String> statuses);
  List<InventoryAsset> findByProductIdAndStatusOrderBySerialIdAsc(String productId, String status);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select asset from InventoryAsset asset where asset.productId = :productId and asset.status = :status order by asset.serialId asc")
  List<InventoryAsset> findByProductIdAndStatusForUpdate(@Param("productId") String productId,
                                                          @Param("status") String status);
}
