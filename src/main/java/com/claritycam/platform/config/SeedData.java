package com.claritycam.platform.config;

import com.claritycam.platform.repository.auth.AdminUserRepository;
import com.claritycam.platform.repository.booking.BookingRepository;
import com.claritycam.platform.repository.catalog.ProductRepository;
import com.claritycam.platform.repository.inventory.InventoryAssetRepository;
import com.claritycam.platform.repository.inventory.StockItemRepository;
import com.claritycam.platform.repository.promotion.PromotionRepository;
import com.claritycam.platform.service.booking.BookingOperationsService;
import com.claritycam.platform.service.catalog.BundleVersionService;
import com.claritycam.platform.model.auth.AdminUser;
import com.claritycam.platform.model.booking.Booking;
import com.claritycam.platform.model.booking.BookingLine;
import com.claritycam.platform.model.booking.BookingState;
import com.claritycam.platform.model.catalog.BundleLine;
import com.claritycam.platform.repository.catalog.BundleRepository;
import com.claritycam.platform.model.catalog.Product;
import com.claritycam.platform.model.catalog.RentalBundle;
import com.claritycam.platform.model.inventory.InventoryAsset;
import com.claritycam.platform.model.inventory.InventoryLedgerEntry;
import com.claritycam.platform.repository.inventory.InventoryLedgerRepository;
import com.claritycam.platform.model.inventory.StockItem;
import com.claritycam.platform.model.promotion.Promotion;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SeedData {
  private static final Map<String, Long> DEMO_BOOKING_COUNT_BASES = Map.ofEntries(
      Map.entry("GEAR-001", 148L),
      Map.entry("GEAR-002", 96L),
      Map.entry("GEAR-003", 84L),
      Map.entry("GEAR-004", 72L),
      Map.entry("ACC-001", 65L),
      Map.entry("ACC-002", 111L),
      Map.entry("ACC-003", 176L),
      Map.entry("ACC-004", 58L),
      Map.entry("ACC-005", 39L),
      Map.entry("ACC-006", 91L),
      Map.entry("ACC-007", 47L));

  @Bean
  CommandLineRunner seed(
      ProductRepository products,
      BundleRepository bundles,
      BookingRepository bookings,
      InventoryAssetRepository assets,
      StockItemRepository stock,
      InventoryLedgerRepository inventoryLedger,
      AdminUserRepository users,
      PromotionRepository promotions,
      BundleVersionService bundleVersions,
      BookingOperationsService bookingOperations,
      PasswordEncoder passwordEncoder,
      @Value("${claritycam.seed-demo-data}") boolean enabled,
      @Value("${claritycam.admin.email}") String adminEmail,
      @Value("${claritycam.admin.password}") String adminPassword) {
    return args -> {
      if (!enabled) {
        return;
      }
      if (users.count() == 0) {
        users.save(new AdminUser("USR-ADMIN-001", adminEmail, passwordEncoder.encode(adminPassword), "ADMIN", true));
      }
      if (promotions.count() == 0) {
        promotions.save(new Promotion("PROMO-DEMO-T7", "T7SALE", "Ã†Â¯u Ã„â€˜ÃƒÂ£i thÃ¡Â»Â© BÃ¡ÂºÂ£y", BigDecimal.valueOf(15), true,
            LocalDate.now().minusMonths(1), LocalDate.now().plusYears(1), Set.of(DayOfWeek.SATURDAY), "ALL"));
      }
      if (products.count() > 0) {
        migrateTrackingModes(products);
        seedBookingCountBaselines(products);
        seedPricingRates(products, bundles);
        enrichProductDetails(products);
        seedAccessoryAssets(assets);
        bundles.findAllWithItems().forEach(bundleVersions::ensureBaseline);
        seedInventoryLedger(assets, stock, inventoryLedger);
        bookings.findAllWithItemsOrderByCreatedAtDesc().forEach(bookingOperations::ensureMigrationBaseline);
        return;
      }

      products.saveAll(List.of(
          product("GEAR-001", "L1", "Sony FX3 Cinema Line", "Sony", "Cinema", 2_100_000, false, "https://images.unsplash.com/photo-1516035069371-29a1b244cc32?auto=format&fit=crop&q=80&w=800", "Full-frame 4K 120p", "SERIALIZED", "FX3"),
          product("GEAR-002", "L1", "Canon EOS R5", "Canon", "Mirrorless", 1_800_000, false, "https://images.unsplash.com/photo-1502920917128-1aa500764cbd?auto=format&fit=crop&q=80&w=800", "8K RAW, 45MP", "SERIALIZED", "R5"),
          product("GEAR-003", "L1", "DJI Mavic 3 Pro", "DJI", "Drone", 2_400_000, false, "https://images.unsplash.com/photo-1527977966376-1c8408f9f108?auto=format&fit=crop&q=80&w=800", "Hasselblad 4/3 CMOS", "SERIALIZED", "MAVIC"),
          product("GEAR-004", "L1", "Sigma 24-70mm f/2.8 Art", "Sigma", "Lens", 750_000, false, "https://images.unsplash.com/photo-1617005082833-1e0e8fd1e9df?auto=format&fit=crop&q=80&w=800", "E-Mount Art Lens", "SERIALIZED", "SIG"),
          product("ACC-001", "L2", "Gimbal DJI RS3 Pro", "DJI", "Support", 600_000, false, "https://placehold.co/400x300/f8f8f6/a1a1aa?text=RS3+Pro", "Payload 4.5kg", "SERIALIZED", "RS3"),
          product("ACC-002", "L2", "Rode Wireless GO II", "Rode", "Audio", 300_000, false, "https://images.unsplash.com/photo-1590845947376-2638caa89309?auto=format&fit=crop&q=80&w=800", "Dual wireless mic", "SERIALIZED", "RODE"),
          product("ACC-003", "L3", "Pin Sony NP-FZ100", "Sony", "Battery/Card", 100_000, false, "https://placehold.co/400x300/f8f8f6/a1a1aa?text=NP-FZ100", "2280mAh", "QUANTITY", ""),
          product("ACC-004", "L3", "The CFexpress 160GB", "Sony", "Battery/Card", 300_000, false, "https://placehold.co/400x300/f8f8f6/a1a1aa?text=CFexpress", "Write 800MB/s", "QUANTITY", ""),
          product("ACC-005", "L4", "Ã„ÂÃ¡ÂºÂ§u Ã„â€˜Ã¡Â»Âc thÃ¡ÂºÂ» Ã„â€˜a nÃ„Æ’ng", "Other", "Accessory", 0, true, "https://placehold.co/400x300/f8f8f6/a1a1aa?text=Card+Reader", "Type-C USB 3.1", "QUANTITY", ""),
          product("ACC-006", "L4", "CÃƒÂ¡p HDMI to Micro", "Other", "Accessory", 0, true, "https://placehold.co/400x300/f8f8f6/a1a1aa?text=Cable", "1.5m", "QUANTITY", ""),
          product("ACC-007", "L5", "Vali chÃ¡Â»â€˜ng sÃ¡Â»â€˜c Pelican", "Pelican", "Transport", 200_000, false, "https://placehold.co/400x300/f8f8f6/a1a1aa?text=Pelican", "Waterproof IP67", "QUANTITY", "")));
      seedBookingCountBaselines(products);
      enrichProductDetails(products);

      bundles.saveAll(List.of(
          new RentalBundle("BND-001", "Indie Filmmaker Pro", BigDecimal.valueOf(3_750_000), true,
              "https://images.unsplash.com/photo-1590845947376-2638caa89309?auto=format&fit=crop&q=80&w=800",
              List.of(new BundleLine("GEAR-001", 1), new BundleLine("GEAR-004", 1), new BundleLine("ACC-001", 1), new BundleLine("ACC-002", 1), new BundleLine("ACC-003", 2))),
          new RentalBundle("BND-002", "Creator Starter", BigDecimal.valueOf(1_950_000), true,
              "https://placehold.co/600x400/f8f8f6/a1a1aa?text=Creator+Starter",
              List.of(new BundleLine("GEAR-002", 1), new BundleLine("ACC-002", 1), new BundleLine("ACC-003", 2)))));
      seedPricingRates(products, bundles);
      bundles.findAllWithItems().forEach(bundleVersions::ensureBaseline);

      assets.saveAll(List.of(
          new InventoryAsset("FX3-001", "GEAR-001", "AVAILABLE", 65, LocalDate.now().minusDays(8), 420),
          new InventoryAsset("FX3-002", "GEAR-001", "IN_USE", 30, LocalDate.now().minusDays(6), 200),
          new InventoryAsset("R5-001", "GEAR-002", "AVAILABLE", 48, LocalDate.now().minusDays(4), 300),
          new InventoryAsset("MAVIC-001", "GEAR-003", "MAINTENANCE", 55, LocalDate.now().minusDays(2), 180),
          new InventoryAsset("SIG-001", "GEAR-004", "AVAILABLE", 31, LocalDate.now().minusDays(11), 0),
          new InventoryAsset("RS3-001", "ACC-001", "AVAILABLE", 18, LocalDate.now().minusDays(3), 40),
          new InventoryAsset("RS3-002", "ACC-001", "AVAILABLE", 9, LocalDate.now().minusDays(2), 20),
          new InventoryAsset("RODE-001", "ACC-002", "AVAILABLE", 22, LocalDate.now().minusDays(4), 15),
          new InventoryAsset("RODE-002", "ACC-002", "AVAILABLE", 7, LocalDate.now().minusDays(2), 8)));

      stock.saveAll(List.of(
          new StockItem("ACC-003", 50, 3),
          new StockItem("ACC-004", 20, 0),
          new StockItem("ACC-005", 30, 0),
          new StockItem("ACC-006", 40, 0),
          new StockItem("ACC-007", 10, 0)));
      seedInventoryLedger(assets, stock, inventoryLedger);

      LocalDateTime tomorrow = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
      bookings.saveAll(List.of(
          booking("ORD-202601", "TrÃ¡ÂºÂ§n Gia BÃ¡ÂºÂ£o", "0933111222", BookingState.PENDING_REVIEW, 4_200_000, tomorrow, tomorrow.plusDays(2), "BND-001", "KhÃƒÂ¡ch muÃ¡Â»â€˜n nhÃ¡ÂºÂ­n mÃƒÂ¡y sÃ¡Â»â€ºm nÃ¡ÂºÂ¿u kho sÃ¡ÂºÂµn sÃƒÂ ng.", List.of(new BookingLine("GEAR-001", null, 1), new BookingLine("ACC-003", null, 2))),
          booking("ORD-202602", "NguyÃ¡Â»â€¦n HÃ†Â°ng Vy", "0901234567", BookingState.TEMP_HOLD, 1_800_000, tomorrow.plusDays(3), tomorrow.plusDays(4), null, "ChÃ¡Â»Â khÃƒÂ¡ch bÃ¡Â»â€¢ sung thÃƒÂ´ng tin cÃ¡Â»Âc.", List.of(new BookingLine("GEAR-002", "R5-001", 1))),
          booking("ORD-202604", "Studio Ã„ÂÃ¡ÂºÂ¥t PhÃ†Â°Ã†Â¡ng Nam", "0912345678", BookingState.IN_USE, 2_100_000, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1), null, "Ã„Âang sÃ¡Â»Â­ dÃ¡Â»Â¥ng theo hÃ¡Â»Â£p Ã„â€˜Ã¡Â»â€œng.", List.of(new BookingLine("GEAR-001", "FX3-002", 1)))));
      bookings.findAllWithItemsOrderByCreatedAtDesc().forEach(bookingOperations::ensureMigrationBaseline);

    };
  }

  private static Product product(String id, String level, String name, String brand, String category, int price,
      boolean included, String imageUrl, String specs, String trackingMode, String prefix) {
    return new Product(id, level, name, brand, category, BigDecimal.valueOf(price), included, true, imageUrl, specs, trackingMode, prefix);
  }

  private static void seedBookingCountBaselines(ProductRepository products) {
    List<Product> demoProducts = products.findAll().stream()
        .filter(product -> DEMO_BOOKING_COUNT_BASES.containsKey(product.getId()))
        .toList();
    if (demoProducts.isEmpty() || demoProducts.stream().anyMatch(product -> product.getBookingCountBase() > 0)) {
      return;
    }
    demoProducts.forEach(product -> product.updateBookingCountBase(DEMO_BOOKING_COUNT_BASES.get(product.getId())));
    products.saveAll(demoProducts);
  }

  private static void migrateTrackingModes(ProductRepository products) {
    List<Product> legacy = products.findAll().stream()
        .filter(product -> "BULK".equals(product.getTrackingMode()))
        .toList();
    legacy.forEach(product -> product.migrateTrackingMode("QUANTITY"));
    products.saveAll(legacy);
  }

  private static void seedPricingRates(ProductRepository products, BundleRepository bundles) {
    List<Product> productList = products.findAll();
    boolean productsNeedRates = productList.stream()
        .noneMatch(product -> product.getHourlyPrice().signum() > 0 || product.getMultiDayPrice().signum() > 0);
    if (productsNeedRates) {
      productList.forEach(product -> product.configurePricing(
          hourlyRate(product.getDailyPrice()), multiDayPackage(product.getDailyPrice()), 3));
      products.saveAll(productList);
    }
    boolean productsNeedTerms = productList.stream().noneMatch(product ->
        product.getHalfDayPrice().signum() > 0 || product.getEquipmentDeposit().signum() > 0);
    if (productsNeedTerms) {
      productList.forEach(product -> product.configureCommercialTerms(
          percent(product.getDailyPrice(), 60), percent(product.getDailyPrice(), 185),
          percent(product.getDailyPrice(), 80), product.getDailyPrice().multiply(BigDecimal.valueOf(2)),
          percent(product.getDailyPrice(), 20), percent(product.getDailyPrice(), 12.5),
          percent(product.getDailyPrice(), 20), percent(product.getDailyPrice(), 30),
          BigDecimal.valueOf(100), product.getDailyPrice().multiply(BigDecimal.TEN)));
      products.saveAll(productList);
    }

    boolean commitmentTermsChanged = false;
    for (Product product : productList) {
      boolean missingCommitmentTerms = product.getIdentityViolationFee().signum() == 0
          && product.getUnauthorizedTransferFee().signum() == 0
          && product.getImpactPenaltyPercent().signum() == 0
          && product.getDamageLiabilityLimit().signum() == 0;
      if (missingCommitmentTerms) {
        product.configureCommercialTerms(
          product.getHalfDayPrice(), product.getTwoDayPrice(), product.getExtraDayPrice(),
          product.getEquipmentDeposit(), product.getBookingDeposit(), product.getLateFeePerHour(),
          percent(product.getDailyPrice(), 20), percent(product.getDailyPrice(), 30),
          BigDecimal.valueOf(100), product.getDailyPrice().multiply(BigDecimal.TEN));
        commitmentTermsChanged = true;
      }
    }
    if (commitmentTermsChanged) {
      products.saveAllAndFlush(productList);
    }

    List<RentalBundle> bundleList = bundles.findAll();
    boolean bundlesNeedRates = bundleList.stream()
        .noneMatch(bundle -> bundle.getHourlyPrice().signum() > 0 || bundle.getMultiDayPrice().signum() > 0);
    if (bundlesNeedRates) {
      bundleList.forEach(bundle -> bundle.configurePricing(
          hourlyRate(bundle.getDailyPrice()), multiDayPackage(bundle.getDailyPrice()), 3));
      bundles.saveAll(bundleList);
    }
  }

  private static BigDecimal hourlyRate(BigDecimal dailyPrice) {
    return dailyPrice.signum() == 0 ? BigDecimal.ZERO
        : dailyPrice.divide(BigDecimal.valueOf(8), 0, RoundingMode.HALF_UP);
  }

  private static BigDecimal multiDayPackage(BigDecimal dailyPrice) {
    return dailyPrice.multiply(BigDecimal.valueOf(3)).multiply(BigDecimal.valueOf(0.9))
        .setScale(0, RoundingMode.HALF_UP);
  }

  private static BigDecimal percent(BigDecimal value, double percent) {
    return value.multiply(BigDecimal.valueOf(percent)).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
  }

  private static void seedAccessoryAssets(InventoryAssetRepository assets) {
    List<InventoryAsset> accessoryAssets = List.of(
        new InventoryAsset("RS3-001", "ACC-001", "AVAILABLE", 18, LocalDate.now().minusDays(3), 40),
        new InventoryAsset("RS3-002", "ACC-001", "AVAILABLE", 9, LocalDate.now().minusDays(2), 20),
        new InventoryAsset("RODE-001", "ACC-002", "AVAILABLE", 22, LocalDate.now().minusDays(4), 15),
        new InventoryAsset("RODE-002", "ACC-002", "AVAILABLE", 7, LocalDate.now().minusDays(2), 8));
    accessoryAssets.stream().filter(asset -> !assets.existsById(asset.getSerialId())).forEach(assets::save);
  }

  private static void seedInventoryLedger(InventoryAssetRepository assets, StockItemRepository stock,
      InventoryLedgerRepository ledger) {
    if (ledger.count() > 0) return;
    List<InventoryLedgerEntry> baseline = new java.util.ArrayList<>();
    assets.findAll().stream().filter(asset -> !"RETIRED".equals(asset.getStatus()))
        .forEach(asset -> baseline.add(new InventoryLedgerEntry("MIGRATION-BASELINE", asset.getProductId(),
            asset.getSerialId(), "OPENING_BALANCE", 1, null, "SÃ¡Â»â€˜ dÃ†Â° serial Ã„â€˜Ã¡ÂºÂ§u kÃ¡Â»Â³", "SYSTEM_MIGRATION")));
    stock.findAll().forEach(item -> baseline.add(new InventoryLedgerEntry("MIGRATION-BASELINE", item.getProductId(),
        null, "OPENING_BALANCE", item.getTotalQty(), item.getTotalQty(), "SÃ¡Â»â€˜ dÃ†Â° sÃ¡Â»â€˜ lÃ†Â°Ã¡Â»Â£ng Ã„â€˜Ã¡ÂºÂ§u kÃ¡Â»Â³",
        "SYSTEM_MIGRATION")));
    ledger.saveAll(baseline);
  }

  private static void enrichProductDetails(ProductRepository products) {
    Map<String, String> details = Map.ofEntries(
        Map.entry("GEAR-001", "{\"description\":\"MÃƒÂ¡y quay Cinema Line full-frame, quay 4K 120p, chÃ¡Â»â€˜ng rung cÃ¡ÂºÂ£m biÃ¡ÂºÂ¿n 5 trÃ¡Â»Â¥c vÃƒÂ  hai khe thÃ¡ÂºÂ» CFexpress Type A/SD.\",\"usageGuide\":\"LÃ¡ÂºÂ¯p pin vÃƒÂ  thÃ¡ÂºÂ» nhÃ¡Â»â€º, gÃ¡ÂºÂ¯n Ã¡Â»â€˜ng kÃƒÂ­nh E-mount, chÃ¡Â»Ân Ã„â€˜Ã¡Â»â€¹nh dÃ¡ÂºÂ¡ng XAVC phÃƒÂ¹ hÃ¡Â»Â£p rÃ¡Â»â€œi kiÃ¡Â»Æ’m tra nhiÃ¡Â»â€¡t Ã„â€˜Ã¡Â»â„¢ vÃƒÂ  dung lÃ†Â°Ã¡Â»Â£ng trÃ†Â°Ã¡Â»â€ºc khi quay.\",\"connectionGuide\":\"XuÃ¡ÂºÂ¥t hÃƒÂ¬nh qua HDMI Type-A; ÃƒÂ¢m thanh qua XLR/TRS trÃƒÂªn tay cÃ¡ÂºÂ§m hoÃ¡ÂºÂ·c jack 3.5 mm; truyÃ¡Â»Ân dÃ¡Â»Â¯ liÃ¡Â»â€¡u qua USB-C, Wi-Fi hoÃ¡ÂºÂ·c Bluetooth.\",\"compatibleAccessories\":[\"ACC-002\",\"ACC-003\",\"ACC-004\",\"ACC-006\",\"ACC-007\"],\"sourceUrl\":\"https://www.sony.com/electronics/support/camcorders-and-video-cameras-interchangeable-lens-camcorders/ilme-fx3\"}"),
        Map.entry("GEAR-002", "{\"description\":\"MÃƒÂ¡y Ã¡ÂºÂ£nh mirrorless full-frame 45 MP, ngÃƒÂ m RF, hÃ¡Â»â€” trÃ¡Â»Â£ quay RAW vÃƒÂ  Dual Pixel CMOS AF.\",\"usageGuide\":\"SÃ¡ÂºÂ¡c pin, lÃ¡ÂºÂ¯p thÃ¡ÂºÂ» vÃƒÂ  Ã¡Â»â€˜ng kÃƒÂ­nh RF; cÃƒÂ³ thÃ¡Â»Æ’ dÃƒÂ¹ng Ã¡Â»â€˜ng EF/EF-S qua ngÃƒÂ m chuyÃ¡Â»Æ’n EF-EOS R. ChÃ¡Â»Ân chÃ¡ÂºÂ¿ Ã„â€˜Ã¡Â»â„¢ chÃ¡Â»Â¥p/quay vÃƒÂ  kiÃ¡Â»Æ’m tra giÃ¡Â»â€ºi hÃ¡ÂºÂ¡n nhiÃ¡Â»â€¡t trÃ†Â°Ã¡Â»â€ºc phiÃƒÂªn dÃƒÂ i.\",\"connectionGuide\":\"KÃ¡ÂºÂ¿t nÃ¡Â»â€˜i Wi-Fi 2.4/5 GHz vÃƒÂ  Bluetooth 5.0; dÃƒÂ¹ng USB/HDMI theo hÃ†Â°Ã¡Â»â€ºng dÃ¡ÂºÂ«n Canon Ã„â€˜Ã¡Â»Æ’ truyÃ¡Â»Ân dÃ¡Â»Â¯ liÃ¡Â»â€¡u hoÃ¡ÂºÂ·c monitor.\",\"compatibleAccessories\":[\"ACC-002\",\"ACC-004\",\"ACC-006\",\"ACC-007\"],\"sourceUrl\":\"https://cam.start.canon/en/C003/manual/html/index.html\"}"),
        Map.entry("GEAR-003", "{\"description\":\"Drone ba camera dÃƒÂ²ng Mavic 3 Pro vÃ¡Â»â€ºi camera Hasselblad 4/3 CMOS vÃƒÂ  cÃƒÂ¡c tiÃƒÂªu cÃ¡Â»Â± tele hÃ¡Â»â€” trÃ¡Â»Â£ nhiÃ¡Â»Âu bÃ¡Â»â€˜i cÃ¡ÂºÂ£nh.\",\"usageGuide\":\"SÃ¡ÂºÂ¡c pin vÃƒÂ  tay Ã„â€˜iÃ¡Â»Âu khiÃ¡Â»Æ’n, lÃ¡ÂºÂ¯p cÃƒÂ¡nh Ã„â€˜ÃƒÂºng kÃƒÂ½ hiÃ¡Â»â€¡u, cÃ¡ÂºÂ­p nhÃ¡ÂºÂ­t firmware, hiÃ¡Â»â€¡u chuÃ¡ÂºÂ©n khi Ã„â€˜Ã†Â°Ã¡Â»Â£c yÃƒÂªu cÃ¡ÂºÂ§u vÃƒÂ  kiÃ¡Â»Æ’m tra khu vÃ¡Â»Â±c bay trÃ†Â°Ã¡Â»â€ºc khi cÃ¡ÂºÂ¥t cÃƒÂ¡nh.\",\"connectionGuide\":\"GhÃƒÂ©p vÃ¡Â»â€ºi DJI RC/RC Pro qua hÃ¡Â»â€¡ thÃ¡Â»â€˜ng truyÃ¡Â»Ân hÃƒÂ¬nh Ã¡ÂºÂ£nh; dÃƒÂ¹ng DJI Fly Ã„â€˜Ã¡Â»Æ’ kÃƒÂ­ch hoÃ¡ÂºÂ¡t, cÃ¡ÂºÂ­p nhÃ¡ÂºÂ­t vÃƒÂ  quÃ¡ÂºÂ£n lÃƒÂ½ chuyÃ¡ÂºÂ¿n bay. SÃ¡ÂºÂ¡c qua bÃ¡Â»â„¢ sÃ¡ÂºÂ¡c USB-C tÃ†Â°Ã†Â¡ng thÃƒÂ­ch.\",\"compatibleAccessories\":[\"ACC-007\"],\"sourceUrl\":\"https://www.dji.com/downloads/products/mavic-3-pro\"}"),
        Map.entry("GEAR-004", "{\"description\":\"Ã¡Â»Âng kÃƒÂ­nh zoom 24-70 mm khÃ¡ÂºÂ©u Ã„â€˜Ã¡Â»â„¢ cÃ¡Â»â€˜ Ã„â€˜Ã¡Â»â€¹nh F2.8 thuÃ¡Â»â„¢c dÃƒÂ²ng Art, dÃƒÂ nh cho mÃƒÂ¡y mirrorless full-frame.\",\"usageGuide\":\"GÃ¡ÂºÂ¯n Ã„â€˜ÃƒÂºng phiÃƒÂªn bÃ¡ÂºÂ£n ngÃƒÂ m lÃƒÂªn thÃƒÂ¢n mÃƒÂ¡y khi Ã„â€˜ÃƒÂ£ tÃ¡ÂºÂ¯t nguÃ¡Â»â€œn; thÃƒÂ¡o nÃ¡ÂºÂ¯p trÃ†Â°Ã¡Â»â€ºc/sau, kiÃ¡Â»Æ’m tra bÃ¡Â»Â mÃ¡ÂºÂ·t kÃƒÂ­nh vÃƒÂ  dÃƒÂ¹ng khÃƒÂ³a zoom khi vÃ¡ÂºÂ­n chuyÃ¡Â»Æ’n.\",\"connectionGuide\":\"PhiÃƒÂªn bÃ¡ÂºÂ£n E-mount dÃƒÂ¹ng vÃ¡Â»â€ºi thÃƒÂ¢n Sony E; phiÃƒÂªn bÃ¡ÂºÂ£n L-Mount dÃƒÂ¹ng vÃ¡Â»â€ºi thÃƒÂ¢n L-Mount. KhÃƒÂ´ng cÃ¡Â»â€˜ lÃ¡ÂºÂ¯p khÃƒÂ¡c ngÃƒÂ m vÃƒÂ  cÃ¡ÂºÂ­p nhÃ¡ÂºÂ­t firmware theo hÃ†Â°Ã¡Â»â€ºng dÃ¡ÂºÂ«n Sigma.\",\"compatibleAccessories\":[\"GEAR-001\",\"ACC-007\"],\"sourceUrl\":\"https://www.sigma-global.com/en/lenses/a019_24_70_28\"}"),
        Map.entry("ACC-001", "{\"description\":\"Gimbal chuyÃƒÂªn nghiÃ¡Â»â€¡p tÃ¡ÂºÂ£i trÃ¡Â»Âng cao, hÃ¡Â»â€” trÃ¡Â»Â£ Bluetooth 5.0, USB-C vÃƒÂ  Ã¡Â»Â©ng dÃ¡Â»Â¥ng DJI Ronin.\",\"usageGuide\":\"GÃ¡ÂºÂ¯n mÃƒÂ¡y, cÃƒÂ¢n bÃ¡ÂºÂ±ng Ã„â€˜Ã¡Â»Â§ ba trÃ¡Â»Â¥c trÃ†Â°Ã¡Â»â€ºc khi bÃ¡ÂºÂ­t nguÃ¡Â»â€œn; khÃƒÂ³a/mÃ¡Â»Å¸ trÃ¡Â»Â¥c Ã„â€˜ÃƒÂºng thÃ¡Â»Â© tÃ¡Â»Â± vÃƒÂ  chÃ¡ÂºÂ¡y Auto Tune theo tÃ¡ÂºÂ£i thÃ¡Â»Â±c tÃ¡ÂºÂ¿.\",\"connectionGuide\":\"KÃ¡ÂºÂ¿t nÃ¡Â»â€˜i DJI Ronin qua Bluetooth; dÃƒÂ¹ng cÃƒÂ¡p camera control USB-C phÃƒÂ¹ hÃ¡Â»Â£p model mÃƒÂ¡y. SÃ¡ÂºÂ¡c grip bÃ¡ÂºÂ±ng bÃ¡Â»â„¢ sÃ¡ÂºÂ¡c PD/QC tÃ¡Â»â€˜i Ã„â€˜a 24 W.\",\"compatibleAccessories\":[\"GEAR-001\",\"GEAR-002\",\"GEAR-004\"],\"sourceUrl\":\"https://repair.dji.com/help/content?customId=01700006898&documentType=&lang=en&spaceId=17\"}"),
        Map.entry("ACC-002", "{\"description\":\"HÃ¡Â»â€¡ micro khÃƒÂ´ng dÃƒÂ¢y hai kÃƒÂªnh gÃ¡Â»â€œm hai transmitter vÃƒÂ  mÃ¡Â»â„¢t receiver, truyÃ¡Â»Ân sÃ¡Â»â€˜ 2.4 GHz.\",\"usageGuide\":\"SÃ¡ÂºÂ¡c cÃƒÂ¡c bÃ¡Â»â„¢ phÃ¡ÂºÂ­n, bÃ¡ÂºÂ­t receiver/transmitter, kiÃ¡Â»Æ’m tra pairing vÃƒÂ  mÃ¡Â»Â©c gain; gÃ¡ÂºÂ¯n mic cÃƒÂ i ÃƒÂ¡o nÃ¡ÂºÂ¿u cÃ¡ÂºÂ§n vÃƒÂ  theo dÃƒÂµi peak trÃ†Â°Ã¡Â»â€ºc khi ghi.\",\"connectionGuide\":\"KÃ¡ÂºÂ¿t nÃ¡Â»â€˜i camera bÃ¡ÂºÂ±ng ngÃƒÂµ 3.5 mm TRS; kÃ¡ÂºÂ¿t nÃ¡Â»â€˜i mÃƒÂ¡y tÃƒÂ­nh/Ã„â€˜iÃ¡Â»â€¡n thoÃ¡ÂºÂ¡i qua USB-C vÃƒÂ  cÃƒÂ¡p RÃƒËœDE tÃ†Â°Ã†Â¡ng thÃƒÂ­ch. CÃ¡ÂºÂ¥u hÃƒÂ¬nh bÃ¡ÂºÂ±ng RÃƒËœDE Central.\",\"compatibleAccessories\":[\"GEAR-001\",\"GEAR-002\"],\"sourceUrl\":\"https://rode.com/en-us/products/wirelessgoii\"}"),
        Map.entry("ACC-003", "{\"description\":\"Pin sÃ¡ÂºÂ¡c Sony Z-series dÃƒÂ¹ng cho cÃƒÂ¡c thÃƒÂ¢n mÃƒÂ¡y tÃ†Â°Ã†Â¡ng thÃƒÂ­ch NP-FZ100.\",\"usageGuide\":\"SÃ¡ÂºÂ¡c bÃ¡ÂºÂ±ng thÃƒÂ¢n mÃƒÂ¡y hoÃ¡ÂºÂ·c bÃ¡Â»â„¢ sÃ¡ÂºÂ¡c tÃ†Â°Ã†Â¡ng thÃƒÂ­ch; trÃƒÂ¡nh ngÃ¡ÂºÂ¯n mÃ¡ÂºÂ¡ch, nhiÃ¡Â»â€¡t cao vÃƒÂ  kiÃ¡Â»Æ’m tra mÃ¡Â»Â©c pin trÃ†Â°Ã¡Â»â€ºc khi giao nhÃ¡ÂºÂ­n.\",\"connectionGuide\":\"LÃ¡ÂºÂ¯p Ã„â€˜ÃƒÂºng chiÃ¡Â»Âu vÃƒÂ o khoang pin cÃ¡Â»Â§a thiÃ¡ÂºÂ¿t bÃ¡Â»â€¹ hÃ¡Â»â€” trÃ¡Â»Â£ NP-FZ100; khÃƒÂ´ng dÃƒÂ¹ng cho thiÃ¡ÂºÂ¿t bÃ¡Â»â€¹ khÃƒÂ¡c chuÃ¡ÂºÂ©n pin.\",\"compatibleAccessories\":[\"GEAR-001\"],\"sourceUrl\":\"https://www.sony.com/electronics/support/camera-camcorder-accessories-batteries-chargers/np-fz100\"}"),
        Map.entry("ACC-004", "{\"description\":\"ThÃ¡ÂºÂ» nhÃ¡Â»â€º CFexpress dung lÃ†Â°Ã¡Â»Â£ng 160 GB cho thiÃ¡ÂºÂ¿t bÃ¡Â»â€¹ hÃ¡Â»â€” trÃ¡Â»Â£ Ã„â€˜ÃƒÂºng chuÃ¡ÂºÂ©n thÃ¡ÂºÂ».\",\"usageGuide\":\"Format thÃ¡ÂºÂ» trong mÃƒÂ¡y trÃ†Â°Ã¡Â»â€ºc phiÃƒÂªn quay, sao lÃ†Â°u dÃ¡Â»Â¯ liÃ¡Â»â€¡u trÃ†Â°Ã¡Â»â€ºc khi format lÃ¡ÂºÂ¡i vÃƒÂ  khÃƒÂ´ng thÃƒÂ¡o thÃ¡ÂºÂ» khi Ã„â€˜ÃƒÂ¨n ghi Ã„â€˜ang sÃƒÂ¡ng.\",\"connectionGuide\":\"CÃ¡ÂºÂ¯m trÃ¡Â»Â±c tiÃ¡ÂºÂ¿p vÃƒÂ o khe CFexpress tÃ†Â°Ã†Â¡ng thÃƒÂ­ch hoÃ¡ÂºÂ·c dÃƒÂ¹ng Ã„â€˜Ã¡ÂºÂ§u Ã„â€˜Ã¡Â»Âc Ã„â€˜ÃƒÂºng chuÃ¡ÂºÂ©n; khÃƒÂ´ng ÃƒÂ©p vÃƒÂ o khe SD.\",\"compatibleAccessories\":[\"GEAR-001\",\"GEAR-002\"],\"sourceUrl\":\"\"}"),
        Map.entry("ACC-005", "{\"description\":\"Ã„ÂÃ¡ÂºÂ§u Ã„â€˜Ã¡Â»Âc thÃ¡ÂºÂ» Ã„â€˜a nÃ„Æ’ng USB-C phÃ¡Â»Â¥c vÃ¡Â»Â¥ sao chÃƒÂ©p dÃ¡Â»Â¯ liÃ¡Â»â€¡u sau buÃ¡Â»â€¢i quay.\",\"usageGuide\":\"CÃ¡ÂºÂ¯m thÃ¡ÂºÂ» Ã„â€˜ÃƒÂºng chiÃ¡Â»Âu, chÃ¡Â»Â hÃ¡Â»â€¡ Ã„â€˜iÃ¡Â»Âu hÃƒÂ nh nhÃ¡ÂºÂ­n Ã¡Â»â€¢ Ã„â€˜Ã„Â©a vÃƒÂ  eject an toÃƒÂ n trÃ†Â°Ã¡Â»â€ºc khi rÃƒÂºt.\",\"connectionGuide\":\"KÃ¡ÂºÂ¿t nÃ¡Â»â€˜i cÃ¡Â»â€¢ng USB-C/USB 3.x phÃƒÂ¹ hÃ¡Â»Â£p; tÃ¡Â»â€˜c Ã„â€˜Ã¡Â»â„¢ thÃ¡Â»Â±c tÃ¡ÂºÂ¿ phÃ¡Â»Â¥ thuÃ¡Â»â„¢c chuÃ¡ÂºÂ©n thÃ¡ÂºÂ», Ã„â€˜Ã¡ÂºÂ§u Ã„â€˜Ã¡Â»Âc vÃƒÂ  mÃƒÂ¡y tÃƒÂ­nh.\",\"compatibleAccessories\":[\"ACC-004\"],\"sourceUrl\":\"\"}"),
        Map.entry("ACC-006", "{\"description\":\"CÃƒÂ¡p HDMI sang Micro HDMI dÃƒÂ i 1,5 m dÃƒÂ¹ng monitor hoÃ¡ÂºÂ·c thiÃ¡ÂºÂ¿t bÃ¡Â»â€¹ capture tÃ†Â°Ã†Â¡ng thÃƒÂ­ch.\",\"usageGuide\":\"TÃ¡ÂºÂ¯t hoÃ¡ÂºÂ·c giÃ¡ÂºÂ£m tÃ¡ÂºÂ£i thiÃ¡ÂºÂ¿t bÃ¡Â»â€¹ trÃ†Â°Ã¡Â»â€ºc khi cÃ¡ÂºÂ¯m, khÃƒÂ´ng bÃ¡ÂºÂ» gÃ¡ÂºÂ­p Ã„â€˜Ã¡ÂºÂ§u cÃƒÂ¡p vÃƒÂ  cÃ¡Â»â€˜ Ã„â€˜Ã¡Â»â€¹nh cÃƒÂ¡p Ã„â€˜Ã¡Â»Æ’ trÃƒÂ¡nh kÃƒÂ©o hÃ¡Â»Âng cÃ¡Â»â€¢ng.\",\"connectionGuide\":\"Ã„ÂÃ¡ÂºÂ§u Micro HDMI vÃƒÂ o thiÃ¡ÂºÂ¿t bÃ¡Â»â€¹ nguÃ¡Â»â€œn tÃ†Â°Ã†Â¡ng thÃƒÂ­ch, Ã„â€˜Ã¡ÂºÂ§u HDMI tiÃƒÂªu chuÃ¡ÂºÂ©n vÃƒÂ o monitor/capture. KiÃ¡Â»Æ’m tra Ã„â€˜ÃƒÂºng loÃ¡ÂºÂ¡i cÃ¡Â»â€¢ng trÃ†Â°Ã¡Â»â€ºc khi lÃ¡ÂºÂ¯p.\",\"compatibleAccessories\":[\"GEAR-002\"],\"sourceUrl\":\"\"}"),
        Map.entry("ACC-007", "{\"description\":\"Vali vÃ¡ÂºÂ­n chuyÃ¡Â»Æ’n chÃ¡Â»â€˜ng sÃ¡Â»â€˜c vÃƒÂ  chÃ¡Â»â€˜ng nÃ†Â°Ã¡Â»â€ºc cho bÃ¡Â»â„¢ mÃƒÂ¡y/phÃ¡Â»Â¥ kiÃ¡Â»â€¡n.\",\"usageGuide\":\"SÃ¡ÂºÂ¯p xÃ¡ÂºÂ¿p thiÃ¡ÂºÂ¿t bÃ¡Â»â€¹ trong foam, Ã„â€˜ÃƒÂ³ng Ã„â€˜Ã¡Â»Â§ khÃƒÂ³a vÃƒÂ  cÃƒÂ¢n bÃ¡ÂºÂ±ng trÃ¡Â»Âng lÃ†Â°Ã¡Â»Â£ng; kiÃ¡Â»Æ’m tra gioÃ„Æ’ng trÃ†Â°Ã¡Â»â€ºc khi Ã„â€˜i mÃƒÂ´i trÃ†Â°Ã¡Â»Âng Ã¡ÂºÂ©m.\",\"connectionGuide\":\"KhÃƒÂ´ng cÃƒÂ³ kÃ¡ÂºÂ¿t nÃ¡Â»â€˜i Ã„â€˜iÃ¡Â»â€¡n; chÃ¡Â»Ân kÃƒÂ­ch thÃ†Â°Ã¡Â»â€ºc foam phÃƒÂ¹ hÃ¡Â»Â£p bÃ¡Â»â„¢ thiÃ¡ÂºÂ¿t bÃ¡Â»â€¹ vÃƒÂ  khÃƒÂ´ng Ã„â€˜Ã¡Â»Æ’ pin rÃ¡Â»Âi chÃ¡ÂºÂ¡m cÃ¡Â»Â±c.\",\"compatibleAccessories\":[\"GEAR-001\",\"GEAR-002\",\"GEAR-003\",\"GEAR-004\"],\"sourceUrl\":\"\"}"));
    details.forEach((id, json) -> products.findById(id).ifPresent(product -> {
      if (product.getCustomAttributes() == null || product.getCustomAttributes().isBlank() || "{}".equals(product.getCustomAttributes().trim())) {
        product.updateCustomAttributes(json);
        products.save(product);
      }
    }));
  }

  private static Booking booking(String id, String customerName, String phone, BookingState state, int total,
      LocalDateTime pickup, LocalDateTime returns, String bundleId, String note, List<BookingLine> lines) {
    Booking booking = new Booking(id, customerName, phone, phone, BookingState.PENDING_REVIEW, BigDecimal.valueOf(total),
        BigDecimal.valueOf(total).multiply(BigDecimal.valueOf(0.30)), pickup, returns, bundleId, note, lines);
    if (state != BookingState.PENDING_REVIEW) {
      booking.changeState(state, "Seed data");
    }
    return booking;
  }
}
