package com.claritycam.platform.repository.finance;

import com.claritycam.platform.model.finance.CommercialSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercialSnapshotRepository extends JpaRepository<CommercialSnapshot, String> {}
