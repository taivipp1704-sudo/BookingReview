package com.claritycam.platform.store;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreBranchRepository extends JpaRepository<StoreBranch, String> {
  List<StoreBranch> findAllByOrderBySortOrderAscNameAsc();
  List<StoreBranch> findByActiveTrueOrderBySortOrderAscNameAsc();
}
