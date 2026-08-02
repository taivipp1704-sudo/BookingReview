package com.claritycam.platform.service.audit;

import com.claritycam.platform.model.audit.AuditLog;
import com.claritycam.platform.repository.audit.AuditLogRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
  private final AuditLogRepository auditLogs;

  public AuditService(AuditLogRepository auditLogs) {
    this.auditLogs = auditLogs;
  }

  public void record(String actor, String action, String targetType, String targetId, String note) {
    auditLogs.save(new AuditLog(actor, action, targetType, targetId, note == null ? "" : note.trim()));
  }

  public List<AuditLog> history(String targetType, String targetId) {
    return auditLogs.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(targetType, targetId);
  }
}
