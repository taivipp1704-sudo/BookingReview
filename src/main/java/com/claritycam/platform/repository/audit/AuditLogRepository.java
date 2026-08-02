package com.claritycam.platform.repository.audit;

import com.claritycam.platform.model.audit.AuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
  List<AuditLog> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, String targetId);
}
