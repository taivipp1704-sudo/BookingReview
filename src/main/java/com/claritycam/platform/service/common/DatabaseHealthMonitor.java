package com.claritycam.platform.service.common;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class DatabaseHealthMonitor {
  private final JdbcTemplate jdbc;
  private final OperationalAlertService alerts;
  private final AtomicInteger failures = new AtomicInteger();
  private final AtomicBoolean incidentOpen = new AtomicBoolean();

  public DatabaseHealthMonitor(JdbcTemplate jdbc, OperationalAlertService alerts) {
    this.jdbc = jdbc;
    this.alerts = alerts;
  }

  @Scheduled(fixedDelayString = "${claritycam.monitoring.database-check-ms:60000}")
  void checkDatabase() {
    try {
      jdbc.queryForObject("SELECT 1", Integer.class);
      failures.set(0);
      if (incidentOpen.compareAndSet(true, false)) {
        alerts.alert("DATABASE_RECOVERED", "Kết nối cơ sở dữ liệu đã phục hồi.");
      }
    } catch (Exception error) {
      if (failures.incrementAndGet() >= 3 && incidentOpen.compareAndSet(false, true)) {
        alerts.alert("DATABASE_UNAVAILABLE", "Cơ sở dữ liệu lỗi qua 3 lần kiểm tra liên tiếp.");
      }
    }
  }
}
