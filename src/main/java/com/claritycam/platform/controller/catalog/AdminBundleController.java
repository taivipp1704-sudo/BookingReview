package com.claritycam.platform.controller.catalog;

import com.claritycam.platform.model.catalog.BundleLine;
import com.claritycam.platform.model.catalog.BundleVersion;
import com.claritycam.platform.model.catalog.RentalBundle;
import com.claritycam.platform.repository.catalog.BundleRepository;
import com.claritycam.platform.repository.catalog.ProductRepository;
import com.claritycam.platform.service.catalog.BundleVersionService;
import com.claritycam.platform.service.audit.AuditService;
import com.claritycam.platform.exception.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/catalog/bundles")
public class AdminBundleController {
  private static final Set<String> LEGACY_MOCK_BUNDLE_IDS = Set.of("BND-001", "BND-002");

  private final BundleRepository bundles;
  private final ProductRepository products;
  private final AuditService audit;
  private final BundleVersionService versionService;

  public AdminBundleController(BundleRepository bundles, ProductRepository products, AuditService audit,
      BundleVersionService versionService) {
    this.bundles = bundles; this.products = products; this.audit = audit; this.versionService = versionService;
  }

  @GetMapping
  List<RentalBundle> list() {
    return bundles.findAllWithItems().stream()
        .filter(bundle -> !LEGACY_MOCK_BUNDLE_IDS.contains(bundle.getId()))
        .sorted(Comparator.comparing(RentalBundle::getName))
        .toList();
  }

  @PostMapping @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
  @Transactional
  RentalBundle create(@Valid @RequestBody BundlePayload payload, Authentication authentication) {
    validateProducts(payload.items());
    RentalBundle saved = bundles.save(new RentalBundle("BND-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
        payload.name(), payload.dailyPrice(), payload.active(), payload.imageUrl(), payload.detailImageUrl(),
        payload.note(), lines(payload.items())));
    saved.configurePricing(payload.hourlyPrice(), payload.multiDayPrice(), normalizedMultiDayDays(payload.multiDayDays()));
    saved = bundles.save(saved);
    versionService.publish(saved, authentication.getName());
    audit.record(authentication.getName(), "BUNDLE_CREATED", "BUNDLE", saved.getId(), saved.getName()); return saved;
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
  @Transactional
  RentalBundle update(@PathVariable String id, @Valid @RequestBody BundlePayload payload, Authentication authentication) {
    validateProducts(payload.items());
    RentalBundle bundle = bundles.findByIdWithItemsForUpdate(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy combo."));
    bundle.apply(payload.name(), payload.hourlyPrice(), payload.dailyPrice(), payload.multiDayPrice(),
        payload.multiDayDays(), payload.active(), payload.imageUrl(), payload.detailImageUrl(), payload.note(),
        lines(payload.items()));
    bundle.publishNextVersion();
    RentalBundle saved = bundles.save(bundle);
    versionService.publish(saved, authentication.getName());
    audit.record(authentication.getName(), "BUNDLE_VERSION_PUBLISHED", "BUNDLE", id,
        saved.getName() + " v" + saved.getCurrentVersion()); return saved;
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
  @Transactional
  RentalBundle archive(@PathVariable String id, Authentication authentication) {
    RentalBundle bundle = bundles.findByIdWithItemsForUpdate(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy combo."));
    bundle.deactivate();
    bundle.publishNextVersion();
    RentalBundle saved = bundles.save(bundle);
    versionService.publish(saved, authentication.getName());
    audit.record(authentication.getName(), "BUNDLE_ARCHIVED", "BUNDLE", id, saved.getName()); return saved;
  }

  @GetMapping("/{id}/versions")
  List<BundleVersion> versions(@PathVariable String id) {
    if (!bundles.existsById(id)) throw ApiException.notFound("Không tìm thấy combo.");
    return versionService.history(id);
  }

  private void validateProducts(List<BundleItemPayload> items) { for (BundleItemPayload item : items) if (!products.existsById(item.productId())) throw ApiException.badRequest("Sản phẩm không tồn tại: " + item.productId()); }
  private List<BundleLine> lines(List<BundleItemPayload> items) { return items.stream().map(item -> new BundleLine(item.productId(), item.quantity())).toList(); }
  private static int normalizedMultiDayDays(Integer value) { return value == null ? 3 : value; }
  public record BundlePayload(@NotBlank String name, @DecimalMin("0") BigDecimal hourlyPrice,
                              @DecimalMin("0") BigDecimal dailyPrice, @DecimalMin("0") BigDecimal multiDayPrice,
                              @Min(2) Integer multiDayDays, boolean active, String imageUrl, String detailImageUrl,
                              @jakarta.validation.constraints.Size(max = 1000) String note,
                              @NotEmpty List<@Valid BundleItemPayload> items) {}
  public record BundleItemPayload(@NotBlank String productId, @Min(1) int quantity) {}
}
