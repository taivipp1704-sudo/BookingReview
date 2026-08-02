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
        promotions.save(new Promotion("PROMO-DEMO-T7", "T7SALE", "Ưu đãi thứ Bảy", BigDecimal.valueOf(15), true,
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
          product("ACC-005", "L4", "Đầu đọc thẻ đa năng", "Other", "Accessory", 0, true, "https://placehold.co/400x300/f8f8f6/a1a1aa?text=Card+Reader", "Type-C USB 3.1", "QUANTITY", ""),
          product("ACC-006", "L4", "Cáp HDMI to Micro", "Other", "Accessory", 0, true, "https://placehold.co/400x300/f8f8f6/a1a1aa?text=Cable", "1.5m", "QUANTITY", ""),
          product("ACC-007", "L5", "Vali chống sốc Pelican", "Pelican", "Transport", 200_000, false, "https://placehold.co/400x300/f8f8f6/a1a1aa?text=Pelican", "Waterproof IP67", "QUANTITY", "")));
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
          booking("ORD-202601", "Tráº§n Gia Báº£o", "0933111222", BookingState.PENDING_REVIEW, 4_200_000, tomorrow, tomorrow.plusDays(2), "BND-001", "Khách muốn nhận máy sớm nếu kho sẵn sàng.", List.of(new BookingLine("GEAR-001", null, 1), new BookingLine("ACC-003", null, 2))),
          booking("ORD-202602", "Nguyễn Hưng Vy", "0901234567", BookingState.TEMP_HOLD, 1_800_000, tomorrow.plusDays(3), tomorrow.plusDays(4), null, "Chờ khách bổ sung thông tin cọc.", List.of(new BookingLine("GEAR-002", "R5-001", 1))),
          booking("ORD-202604", "Studio Đất Phương Nam", "0912345678", BookingState.IN_USE, 2_100_000, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1), null, "Đang sử dụng theo hợp đồng.", List.of(new BookingLine("GEAR-001", "FX3-002", 1)))));
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
            asset.getSerialId(), "OPENING_BALANCE", 1, null, "Số dư serial đầu kỳ", "SYSTEM_MIGRATION")));
    stock.findAll().forEach(item -> baseline.add(new InventoryLedgerEntry("MIGRATION-BASELINE", item.getProductId(),
        null, "OPENING_BALANCE", item.getTotalQty(), item.getTotalQty(), "Số dư số lượng đầu kỳ",
        "SYSTEM_MIGRATION")));
    ledger.saveAll(baseline);
  }

  private static void enrichProductDetails(ProductRepository products) {
    Map<String, String> details = Map.ofEntries(
        Map.entry("GEAR-001", "{\"description\":\"Máy quay Cinema Line full-frame, quay 4K 120p, chống rung cảm biến 5 trục và hai khe thẻ CFexpress Type A/SD.\",\"usageGuide\":\"Lắp pin và thẻ nhớ, gắn ống kính E-mount, chọn định dạng XAVC phù hợp rồi kiểm tra nhiệt độ và dung lượng trước khi quay.\",\"connectionGuide\":\"Xuất hình qua HDMI Type-A; âm thanh qua XLR/TRS trên tay cầm hoặc jack 3.5 mm; truyền dữ liệu qua USB-C, Wi-Fi hoặc Bluetooth.\",\"compatibleAccessories\":[\"ACC-002\",\"ACC-003\",\"ACC-004\",\"ACC-006\",\"ACC-007\"],\"sourceUrl\":\"https://www.sony.com/electronics/support/camcorders-and-video-cameras-interchangeable-lens-camcorders/ilme-fx3\"}"),
        Map.entry("GEAR-002", "{\"description\":\"Máy ảnh mirrorless full-frame 45 MP, ngàm RF, hỗ trợ quay RAW và Dual Pixel CMOS AF.\",\"usageGuide\":\"Sạc pin, lắp thẻ và ống kính RF; có thể dùng ống EF/EF-S qua ngàm chuyển EF-EOS R. Chọn chế độ chụp/quay và kiểm tra giới hạn nhiệt trước phiên dài.\",\"connectionGuide\":\"Kết nối Wi-Fi 2.4/5 GHz và Bluetooth 5.0; dùng USB/HDMI theo hướng dẫn Canon để truyền dữ liệu hoặc monitor.\",\"compatibleAccessories\":[\"ACC-002\",\"ACC-004\",\"ACC-006\",\"ACC-007\"],\"sourceUrl\":\"https://cam.start.canon/en/C003/manual/html/index.html\"}"),
        Map.entry("GEAR-003", "{\"description\":\"Drone ba camera dòng Mavic 3 Pro với camera Hasselblad 4/3 CMOS và các tiêu cự tele hỗ trợ nhiều bối cảnh.\",\"usageGuide\":\"Sạc pin và tay điều khiển, lắp cánh đúng ký hiệu, cập nhật firmware, hiệu chuẩn khi được yêu cầu và kiểm tra khu vực bay trước khi cất cánh.\",\"connectionGuide\":\"Ghép với DJI RC/RC Pro qua hệ thống truyền hình ảnh; dùng DJI Fly để kích hoạt, cập nhật và quản lý chuyến bay. Sạc qua bộ sạc USB-C tương thích.\",\"compatibleAccessories\":[\"ACC-007\"],\"sourceUrl\":\"https://www.dji.com/downloads/products/mavic-3-pro\"}"),
        Map.entry("GEAR-004", "{\"description\":\"Ống kính zoom 24-70 mm khẩu độ cố định F2.8 thuộc dòng Art, dành cho máy mirrorless full-frame.\",\"usageGuide\":\"Gắn đúng phiên bản ngàm lên thân máy khi đã tắt nguồn; tháo nắp trước/sau, kiểm tra bề mặt kính và dùng khóa zoom khi vận chuyển.\",\"connectionGuide\":\"Phiên bản E-mount dùng với thân Sony E; phiên bản L-Mount dùng với thân L-Mount. Không cố lắp khác ngàm và cập nhật firmware theo hướng dẫn Sigma.\",\"compatibleAccessories\":[\"GEAR-001\",\"ACC-007\"],\"sourceUrl\":\"https://www.sigma-global.com/en/lenses/a019_24_70_28\"}"),
        Map.entry("ACC-001", "{\"description\":\"Gimbal chuyên nghiệp tải trọng cao, hỗ trợ Bluetooth 5.0, USB-C và ứng dụng DJI Ronin.\",\"usageGuide\":\"Gắn máy, cân bằng đủ ba trục trước khi bật nguồn; khóa/mở trục đúng thứ tự và chạy Auto Tune theo tải thực tế.\",\"connectionGuide\":\"Kết nối DJI Ronin qua Bluetooth; dùng cáp camera control USB-C phù hợp model máy. Sạc grip bằng bộ sạc PD/QC tối đa 24 W.\",\"compatibleAccessories\":[\"GEAR-001\",\"GEAR-002\",\"GEAR-004\"],\"sourceUrl\":\"https://repair.dji.com/help/content?customId=01700006898&documentType=&lang=en&spaceId=17\"}"),
        Map.entry("ACC-002", "{\"description\":\"Hệ micro không dây hai kênh gồm hai transmitter và một receiver, truyền số 2.4 GHz.\",\"usageGuide\":\"Sạc các bộ phận, bật receiver/transmitter, kiểm tra pairing và mức gain; gắn mic cài áo nếu cần và theo dõi peak trước khi ghi.\",\"connectionGuide\":\"Kết nối camera bằng ngõ 3.5 mm TRS; kết nối máy tính/điện thoại qua USB-C và cáp RØDE tương thích. Cấu hình bằng RØDE Central.\",\"compatibleAccessories\":[\"GEAR-001\",\"GEAR-002\"],\"sourceUrl\":\"https://rode.com/en-us/products/wirelessgoii\"}"),
        Map.entry("ACC-003", "{\"description\":\"Pin sạc Sony Z-series dùng cho các thân máy tương thích NP-FZ100.\",\"usageGuide\":\"Sạc bằng thân máy hoặc bộ sạc tương thích; tránh ngắn mạch, nhiệt cao và kiểm tra mức pin trước khi giao nhận.\",\"connectionGuide\":\"Lắp đúng chiều vào khoang pin của thiết bị hỗ trợ NP-FZ100; không dùng cho thiết bị khác chuẩn pin.\",\"compatibleAccessories\":[\"GEAR-001\"],\"sourceUrl\":\"https://www.sony.com/electronics/support/camera-camcorder-accessories-batteries-chargers/np-fz100\"}"),
        Map.entry("ACC-004", "{\"description\":\"Thẻ nhớ CFexpress dung lượng 160 GB cho thiết bị hỗ trợ đúng chuẩn thẻ.\",\"usageGuide\":\"Format thẻ trong máy trước phiên quay, sao lưu dữ liệu trước khi format lại và không tháo thẻ khi đèn ghi đang sáng.\",\"connectionGuide\":\"Cắm trực tiếp vào khe CFexpress tương thích hoặc dùng đầu đọc đúng chuẩn; không ép vào khe SD.\",\"compatibleAccessories\":[\"GEAR-001\",\"GEAR-002\"],\"sourceUrl\":\"\"}"),
        Map.entry("ACC-005", "{\"description\":\"Đầu đọc thẻ đa năng USB-C phục vụ sao chép dữ liệu sau buổi quay.\",\"usageGuide\":\"Cắm thẻ đúng chiều, chờ hệ điều hành nhận ổ đĩa và eject an toàn trước khi rút.\",\"connectionGuide\":\"Kết nối cổng USB-C/USB 3.x phù hợp; tốc độ thực tế phụ thuộc chuẩn thẻ, đầu đọc và máy tính.\",\"compatibleAccessories\":[\"ACC-004\"],\"sourceUrl\":\"\"}"),
        Map.entry("ACC-006", "{\"description\":\"Cáp HDMI sang Micro HDMI dài 1,5 m dùng monitor hoặc thiết bị capture tương thích.\",\"usageGuide\":\"Tắt hoặc giảm tải thiết bị trước khi cắm, không bẻ gập đầu cáp và cố định cáp để tránh kéo hỏng cổng.\",\"connectionGuide\":\"Đầu Micro HDMI vào thiết bị nguồn tương thích, đầu HDMI tiêu chuẩn vào monitor/capture. Kiểm tra đúng loại cổng trước khi lắp.\",\"compatibleAccessories\":[\"GEAR-002\"],\"sourceUrl\":\"\"}"),
        Map.entry("ACC-007", "{\"description\":\"Vali vận chuyển chống sốc và chống nước cho bộ máy/phụ kiện.\",\"usageGuide\":\"Sắp xếp thiết bị trong foam, đóng đủ khóa và cân bằng trọng lượng; kiểm tra gioăng trước khi đi môi trường ẩm.\",\"connectionGuide\":\"Không có kết nối điện; chọn kích thước foam phù hợp bộ thiết bị và không để pin rời chạm cực.\",\"compatibleAccessories\":[\"GEAR-001\",\"GEAR-002\",\"GEAR-003\",\"GEAR-004\"],\"sourceUrl\":\"\"}"));
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
