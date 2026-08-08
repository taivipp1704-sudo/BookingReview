package com.claritycam.platform.service.catalog;

import com.claritycam.platform.exception.ApiException;
import com.claritycam.platform.model.catalog.ImportedReferenceRecord;
import com.claritycam.platform.model.catalog.Product;
import com.claritycam.platform.model.inventory.InventoryAsset;
import com.claritycam.platform.repository.catalog.ImportedReferenceRecordRepository;
import com.claritycam.platform.repository.catalog.ProductRepository;
import com.claritycam.platform.repository.inventory.InventoryAssetRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApprovedCatalogImportService {
  public static final String CONFIRMATION = "IMPORT_APPROVED_EXCEL";
  private static final String RESOURCE = "import/amy-approved-catalog.json";
  private static final String SOURCE_FILE = "AMY_DIGITAL_Tong_hop_may_phu_kien_gia_den_bu.xlsx";
  private static final Map<String, String> PRODUCT_IMAGES = Map.ofEntries(
      Map.entry("CANON-R50", "/catalog/products/canon-r50.jpg"),
      Map.entry("CANON-M50", "/catalog/products/canon-m50.jpg"),
      Map.entry("CANON-G7X-M2", "/catalog/products/canon-g7x-m2.webp"),
      Map.entry("CANON-M100", "/catalog/products/canon-m100.jpg"),
      Map.entry("CANON-M200", "/catalog/products/canon-m200.jpg"),
      Map.entry("CANON-M10", "/catalog/products/canon-m10.jpg"),
      Map.entry("FUJI-XA5", "/catalog/products/fuji-xa5.jpg"),
      Map.entry("FUJI-XM5", "/catalog/products/fuji-xm5.jpg"),
      Map.entry("CANON-IXY-600F", "/catalog/products/canon-ixy-600f.webp"),
      Map.entry("CANON-IXY-650", "/catalog/products/canon-ixy-600f.webp"),
      Map.entry("CANON-IXY-650F", "/catalog/products/canon-ixy-600f.webp"),
      Map.entry("CANON-M6", "/catalog/products/canon-m6.jpg"),
      Map.entry("POCKET-3", "/catalog/products/pocket-3.jpg"));

  private final ObjectMapper objectMapper;
  private final ProductRepository products;
  private final InventoryAssetRepository assets;
  private final ImportedReferenceRecordRepository references;

  public ApprovedCatalogImportService(ObjectMapper objectMapper, ProductRepository products,
      InventoryAssetRepository assets, ImportedReferenceRecordRepository references) {
    this.objectMapper = objectMapper;
    this.products = products;
    this.assets = assets;
    this.references = references;
  }

  public ImportPreview preview() {
    JsonNode root = readRoot();
    return new ImportPreview(dataRows(root, "products"), dataRows(root, "physicalKits"),
        dataRows(root, "kitConfigurations") + dataRows(root, "accessoryDemand")
            + dataRows(root, "assetCodeRules"),
        dataRows(root, "compensationDrafts") + dataRows(root, "damageFeeDrafts")
            + dataRows(root, "policyDrafts"),
        "Sản phẩm được mở ở chế độ xem trước; chính sách tài chính chỉ được lưu DRAFT và booking vẫn bị khóa.");
  }

  @Transactional
  public ImportResult apply(String confirmation) {
    if (!CONFIRMATION.equals(confirmation)) {
      throw ApiException.badRequest("Sai mã xác nhận import. Hãy xem bản preview trước khi áp dụng.");
    }
    JsonNode root = readRoot();
    Map<String, Map<String, String>> configs = table(root.path("kitConfigurations")).stream()
        .collect(Collectors.toMap(row -> row.get("Tên máy"), row -> row, (left, right) -> left));
    int importedProducts = importProducts(root.path("products"), configs);
    int importedAssets = importAssets(root.path("physicalKits"));
    int draftRecords = 0;
    draftRecords += importReferences(root, "kitConfigurations", "KIT_CONFIG", "Tên máy");
    draftRecords += importReferences(root, "accessoryDemand", "ACCESSORY_DEMAND", "Tên linh kiện/phụ kiện");
    draftRecords += importReferences(root, "assetCodeRules", "ASSET_CODE_RULE", "Đối tượng");
    draftRecords += importReferences(root, "compensationDrafts", "COMPENSATION", "Mã nhóm");
    draftRecords += importReferences(root, "damageFeeDrafts", "DAMAGE_FEE", "Tình trạng cụ thể");
    draftRecords += importReferences(root, "policyDrafts", "POLICY", "Rule_ID");
    return new ImportResult(importedProducts, importedAssets, draftRecords, "ACTIVE_PREVIEW");
  }

  private int importProducts(JsonNode tableNode, Map<String, Map<String, String>> configs) {
    int count = 0;
    for (Map<String, String> row : table(tableNode)) {
      String name = row.get("Tên máy");
      String id = "CAM-" + slug(name);
      String imageUrl = PRODUCT_IMAGES.getOrDefault(slug(name), "");
      Map<String, String> config = configs.getOrDefault(name, Map.of());
      Optional<Product> existing = products.findById(id);
      if (existing.isPresent()) {
        Product product = existing.get();
        boolean changed = false;
        if (!product.isActive()) {
          product.activateForPreview();
          changed = true;
        }
        if (!imageUrl.isBlank() && !imageUrl.equals(product.getImageUrl())) {
          product.updateImageUrl(imageUrl);
          changed = true;
        }
        if (changed) {
          products.save(product);
          count++;
        }
        continue;
      }
      Product product = new Product(id, "IMPORT", name, brand(name),
          config.getOrDefault("Nhóm thiết bị", "Camera"), money(row, "Giá 1 ngày"), false, true, imageUrl,
          config.getOrDefault("Ống kính tiêu chuẩn", "Đang cập nhật"), "SERIALIZED", slug(name));
      product.activateForPreview();
      product.configurePricing(BigDecimal.ZERO, money(row, "Giá 3 ngày"), 3);
      product.configureCommercialTerms(money(row, "Giá 6 tiếng"), money(row, "Giá 2 ngày"),
          money(row, "Giá ngày tiếp theo"), money(row, "Phí cọc"), BigDecimal.ZERO,
          money(row, "Phí trả trễ / giờ"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
          money(row, "Giá trị máy"));
      try {
        product.updateCustomAttributes(objectMapper.writeValueAsString(Map.of(
            "source", SOURCE_FILE,
            "importStatus", "ACTIVE_PREVIEW",
            "declaredQuantity", integer(row, "Số lượng"),
            "kitConfiguration", config)));
      } catch (IOException error) {
        throw new IllegalStateException("Không thể chuẩn hóa dữ liệu sản phẩm import.", error);
      }
      products.save(product);
      count++;
    }
    return count;
  }

  private int importAssets(JsonNode tableNode) {
    int count = 0;
    for (Map<String, String> row : table(tableNode)) {
      String serialId = row.get("Mã bộ");
      if (assets.existsById(serialId)) continue;
      assets.save(new InventoryAsset(serialId, "CAM-" + slug(row.get("Tên máy")), "AVAILABLE", 0,
          LocalDate.now(), 0));
      count++;
    }
    return count;
  }

  private int importReferences(JsonNode root, String field, String category, String labelField) {
    int count = 0;
    for (Map<String, String> row : table(root.path(field))) {
      String label = row.getOrDefault(labelField, category);
      String id = category + ":" + slug(label);
      String payload;
      try {
        payload = objectMapper.writeValueAsString(row);
      } catch (IOException error) {
        throw new IllegalStateException("Không thể chuẩn hóa dữ liệu tham chiếu import.", error);
      }
      Optional<ImportedReferenceRecord> existing = references.findById(id);
      ImportedReferenceRecord record = existing.orElseGet(
          () -> new ImportedReferenceRecord(id, category, label, SOURCE_FILE, payload));
      if (existing.isPresent()) record.refresh(label, SOURCE_FILE, payload);
      references.save(record);
      count++;
    }
    return count;
  }

  private JsonNode readRoot() {
    try (var input = new ClassPathResource(RESOURCE).getInputStream()) {
      return objectMapper.readTree(input);
    } catch (IOException error) {
      throw new IllegalStateException("Không thể đọc gói import Excel đã duyệt.", error);
    }
  }

  private static int dataRows(JsonNode root, String field) {
    return Math.max(0, root.path(field).size() - 1);
  }

  private static List<Map<String, String>> table(JsonNode node) {
    if (!node.isArray() || node.size() < 2) return List.of();
    List<String> headers = IntStream.range(0, node.get(0).size())
        .mapToObj(index -> node.get(0).get(index).asText()).toList();
    return IntStream.range(1, node.size()).mapToObj(rowIndex -> {
      Map<String, String> row = new HashMap<>();
      JsonNode source = node.get(rowIndex);
      for (int column = 0; column < headers.size(); column++) {
        JsonNode value = source.path(column);
        row.put(headers.get(column), value.isMissingNode() || value.isNull() ? "" : value.asText());
      }
      return row;
    }).toList();
  }

  private static BigDecimal money(Map<String, String> row, String field) {
    String value = row.getOrDefault(field, "0").replaceAll("[^0-9.-]", "");
    return value.isBlank() ? BigDecimal.ZERO : new BigDecimal(value);
  }

  private static int integer(Map<String, String> row, String field) {
    try {
      return Integer.parseInt(row.getOrDefault(field, "0"));
    } catch (NumberFormatException ignored) {
      return 0;
    }
  }

  private static String brand(String name) {
    return name == null || name.isBlank() ? "Other" : name.trim().split("\\s+")[0];
  }

  private static String slug(String value) {
    if (value == null) return "UNKNOWN";
    return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toUpperCase(Locale.ROOT)
        .replaceAll("[^A-Z0-9]+", "-")
        .replaceAll("(^-|-$)", "");
  }

  public record ImportPreview(int products, int physicalKits, int operationalReferences,
      int financialPolicyDrafts, String safetyMode) {}

  public record ImportResult(int productsCreated, int assetsCreated, int draftRecordsUpserted,
      String status) {}
}
