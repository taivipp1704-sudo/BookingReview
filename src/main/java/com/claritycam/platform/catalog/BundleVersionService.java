package com.claritycam.platform.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BundleVersionService {
  private final BundleVersionRepository versions;
  private final ObjectMapper objectMapper;

  public BundleVersionService(BundleVersionRepository versions, ObjectMapper objectMapper) {
    this.versions = versions;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public BundleVersion publish(RentalBundle bundle, String actor) {
    return versions.save(new BundleVersion(bundle.getId(), bundle.getCurrentVersion(),
        bundle.isActive() ? "PUBLISHED" : "ARCHIVED", snapshot(bundle), actor));
  }

  @Transactional
  public void ensureBaseline(RentalBundle bundle) {
    if (!versions.existsByBundleId(bundle.getId())) publish(bundle, "SYSTEM_MIGRATION");
  }

  public List<BundleVersion> history(String bundleId) {
    return versions.findByBundleIdOrderByVersionNumberDesc(bundleId);
  }

  private String snapshot(RentalBundle bundle) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("id", bundle.getId());
    value.put("version", bundle.getCurrentVersion());
    value.put("name", bundle.getName());
    value.put("hourlyPrice", bundle.getHourlyPrice());
    value.put("dailyPrice", bundle.getDailyPrice());
    value.put("multiDayPrice", bundle.getMultiDayPrice());
    value.put("multiDayDays", bundle.getMultiDayDays());
    value.put("active", bundle.isActive());
    value.put("imageUrl", bundle.getImageUrl());
    value.put("detailImageUrl", bundle.getDetailImageUrl());
    value.put("note", bundle.getNote());
    value.put("items", bundle.getItems().stream().map(line -> Map.of(
        "productId", line.getProductId(), "quantity", line.getQuantity())).toList());
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException error) {
      throw new IllegalStateException("Không thể tạo snapshot combo.", error);
    }
  }
}
