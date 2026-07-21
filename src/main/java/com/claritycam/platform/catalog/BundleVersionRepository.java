package com.claritycam.platform.catalog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BundleVersionRepository extends JpaRepository<BundleVersion, String> {
  List<BundleVersion> findByBundleIdOrderByVersionNumberDesc(String bundleId);
  boolean existsByBundleId(String bundleId);
}
