package com.claritycam.platform.repository.finance;

import com.claritycam.platform.model.finance.AssetRevenueAllocation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRevenueAllocationRepository extends JpaRepository<AssetRevenueAllocation, String> {
  List<AssetRevenueAllocation> findByBookingIdOrderByProductIdAscAssetIdAsc(String bookingId);
  void deleteByBookingId(String bookingId);
}
