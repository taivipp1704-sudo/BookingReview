package com.claritycam.platform.repository.finance;

import com.claritycam.platform.model.finance.AssetCostAllocation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetCostAllocationRepository extends JpaRepository<AssetCostAllocation, String> {
  List<AssetCostAllocation> findByAssetIdOrderByAllocatedAtAsc(String assetId);
  List<AssetCostAllocation> findAllByOrderByAllocatedAtAsc();
}
