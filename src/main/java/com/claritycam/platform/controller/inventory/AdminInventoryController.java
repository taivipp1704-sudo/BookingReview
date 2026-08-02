package com.claritycam.platform.controller.inventory;

import com.claritycam.platform.model.inventory.InventoryAsset;
import com.claritycam.platform.model.inventory.InventoryLedgerEntry;
import com.claritycam.platform.model.inventory.StockItem;
import com.claritycam.platform.repository.catalog.ProductRepository;
import com.claritycam.platform.repository.inventory.InventoryAssetRepository;
import com.claritycam.platform.repository.inventory.StockItemRepository;
import com.claritycam.platform.service.inventory.InventoryLedgerService;
import com.claritycam.platform.service.audit.AuditService;
import com.claritycam.platform.exception.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/admin/inventory")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','WAREHOUSE','TECH')")
public class AdminInventoryController {
  private final InventoryAssetRepository assets;
  private final StockItemRepository stock;
  private final ProductRepository products;
  private final AuditService audit;
  private final InventoryLedgerService ledger;

  public AdminInventoryController(InventoryAssetRepository assets, StockItemRepository stock,
      ProductRepository products, AuditService audit, InventoryLedgerService ledger) {
    this.assets = assets;
    this.stock = stock;
    this.products = products;
    this.audit = audit;
    this.ledger = ledger;
  }

  @PostMapping("/assets")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  InventoryAsset createAsset(@Valid @RequestBody AssetCreateRequest request, Authentication authentication) {
    if (assets.existsById(request.serialId())) {
      throw ApiException.badRequest("Serial nÃƒÆ’Ã‚Â y Ãƒâ€žÃ¢â‚¬ËœÃƒÆ’Ã‚Â£ tÃƒÂ¡Ã‚Â»Ã¢â‚¬Å“n tÃƒÂ¡Ã‚ÂºÃ‚Â¡i.");
    }
    requireProduct(request.productId());
    InventoryAsset asset = assets.save(new InventoryAsset(request.serialId().trim(), request.productId().trim(),
        request.status(), 0, LocalDate.now(), 0));
    ledger.append(null, asset.getProductId(), asset.getSerialId(), "ASSET_RECEIPT", 1, null,
        "NhÃƒÂ¡Ã‚ÂºÃ‚Â­p serial mÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºi", authentication.getName());
    audit.record(authentication.getName(), "INVENTORY_ASSET_CREATED", "ASSET", asset.getSerialId(), asset.getProductId());
    return asset;
  }

  @PatchMapping("/assets/{serialId}/status")
  @Transactional
  InventoryAsset updateAssetStatus(@PathVariable String serialId, @Valid @RequestBody AssetStatusRequest request,
      Authentication authentication) {
    InventoryAsset asset = assets.findById(serialId).orElseThrow(() -> ApiException.notFound("KhÃƒÆ’Ã‚Â´ng tÃƒÆ’Ã‚Â¬m thÃƒÂ¡Ã‚ÂºÃ‚Â¥y serial thiÃƒÂ¡Ã‚ÂºÃ‚Â¿t bÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¹."));
    String previousStatus = asset.getStatus();
    asset.updateStatus(request.status());
    InventoryAsset saved = assets.save(asset);
    ledger.append(null, saved.getProductId(), saved.getSerialId(), "ASSET_STATUS_CHANGED", 0, null,
        previousStatus + " -> " + request.status(), authentication.getName());
    audit.record(authentication.getName(), "INVENTORY_ASSET_STATUS_UPDATED", "ASSET", saved.getSerialId(), request.status());
    return saved;
  }

  @DeleteMapping("/assets/{serialId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional
  void deleteAsset(@PathVariable String serialId, Authentication authentication) {
    InventoryAsset asset = assets.findById(serialId).orElseThrow(() -> ApiException.notFound("KhÃƒÆ’Ã‚Â´ng tÃƒÆ’Ã‚Â¬m thÃƒÂ¡Ã‚ÂºÃ‚Â¥y serial thiÃƒÂ¡Ã‚ÂºÃ‚Â¿t bÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¹."));
    if ("IN_USE".equals(asset.getStatus())) throw ApiException.badRequest("KhÃƒÆ’Ã‚Â´ng thÃƒÂ¡Ã‚Â»Ã†â€™ xÃƒÆ’Ã‚Â³a thiÃƒÂ¡Ã‚ÂºÃ‚Â¿t bÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¹ Ãƒâ€žÃ¢â‚¬Ëœang Ãƒâ€žÃ¢â‚¬ËœÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Â£c thuÃƒÆ’Ã‚Âª.");
    asset.updateStatus("RETIRED");
    assets.save(asset);
    ledger.append(null, asset.getProductId(), asset.getSerialId(), "ASSET_RETIRED", -1, null,
        "LÃƒâ€ Ã‚Â°u trÃƒÂ¡Ã‚Â»Ã‚Â¯ serial, khÃƒÆ’Ã‚Â´ng xÃƒÆ’Ã‚Â³a dÃƒÂ¡Ã‚Â»Ã‚Â¯ liÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡u", authentication.getName());
    audit.record(authentication.getName(), "INVENTORY_ASSET_RETIRED", "ASSET", serialId, asset.getProductId());
  }

  @PatchMapping("/stock/{productId}")
  @Transactional
  StockItem updateStock(@PathVariable String productId, @Valid @RequestBody StockAdjustmentRequest request,
      Authentication authentication) {
    requireProduct(productId);
    StockItem item = stock.findById(productId).orElseGet(() -> new StockItem(productId, 0, 0));
    if (request.totalQty() < item.getInUseQty()) {
      throw ApiException.badRequest("TÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¢ng tÃƒÂ¡Ã‚Â»Ã¢â‚¬Å“n kho khÃƒÆ’Ã‚Â´ng thÃƒÂ¡Ã‚Â»Ã†â€™ thÃƒÂ¡Ã‚ÂºÃ‚Â¥p hÃƒâ€ Ã‚Â¡n sÃƒÂ¡Ã‚Â»Ã¢â‚¬Ëœ lÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Â£ng Ãƒâ€žÃ¢â‚¬Ëœang Ãƒâ€žÃ¢â‚¬ËœÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Â£c thuÃƒÆ’Ã‚Âª.");
    }
    int delta = request.totalQty() - item.getTotalQty();
    item.updateTotalQty(request.totalQty());
    StockItem saved = stock.save(item);
    ledger.append(null, saved.getProductId(), null, "STOCK_ADJUSTMENT", delta, saved.getTotalQty(),
        request.reason(), authentication.getName());
    audit.record(authentication.getName(), "INVENTORY_STOCK_UPDATED", "STOCK", saved.getProductId(), request.reason());
    return saved;
  }

  @GetMapping("/ledger")
  List<InventoryLedgerEntry> ledger() {
    return ledger.recent();
  }

  private void requireProduct(String productId) {
    if (!products.existsById(productId)) {
      throw ApiException.notFound("ThiÃƒÂ¡Ã‚ÂºÃ‚Â¿t bÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¹ khÃƒÆ’Ã‚Â´ng tÃƒÂ¡Ã‚Â»Ã¢â‚¬Å“n tÃƒÂ¡Ã‚ÂºÃ‚Â¡i trong danh mÃƒÂ¡Ã‚Â»Ã‚Â¥c.");
    }
  }

  public record AssetCreateRequest(
      @NotBlank @Size(max = 96) String serialId,
      @NotBlank @Size(max = 96) String productId,
      @NotBlank @Pattern(regexp = "AVAILABLE|IN_USE|REPAIR|RETIRED") String status) {}

  public record AssetStatusRequest(
      @NotBlank @Pattern(regexp = "AVAILABLE|IN_USE|REPAIR|RETIRED") String status) {}

  public record StockAdjustmentRequest(@NotNull @Min(0) Integer totalQty,
                                       @NotBlank @Size(max = 300) String reason) {}
}
