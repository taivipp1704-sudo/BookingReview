package com.claritycam.platform.repository.catalog;

import com.claritycam.platform.model.catalog.BundleVersion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BundleVersionRepository extends JpaRepository<BundleVersion, String> {
  List<BundleVersion> findByBundleIdOrderByVersionNumberDesc(String bundleId);
  boolean existsByBundleId(String bundleId);
  void deleteByBundleId(String bundleId);
}
