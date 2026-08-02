package com.claritycam.platform.repository.catalog;

import com.claritycam.platform.model.catalog.ImportedReferenceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportedReferenceRecordRepository extends JpaRepository<ImportedReferenceRecord, String> {}
