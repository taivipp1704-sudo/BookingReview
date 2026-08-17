package com.claritycam.platform;

import com.claritycam.platform.model.booking.Booking;
import com.claritycam.platform.model.catalog.Product;
import com.claritycam.platform.model.finance.Payment;
import com.claritycam.platform.repository.booking.BookingAllocationRepository;
import com.claritycam.platform.repository.booking.BookingReservationRepository;
import com.claritycam.platform.repository.catalog.BundleVersionRepository;
import com.claritycam.platform.repository.catalog.ProductRepository;
import com.claritycam.platform.repository.inventory.InventoryAssetRepository;
import com.claritycam.platform.repository.inventory.StockItemRepository;
import com.claritycam.platform.repository.promotion.PromotionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.claritycam.platform.model.promotion.Promotion;
import com.claritycam.platform.model.booking.AllocationState;
import com.claritycam.platform.model.booking.ReservationState;
import com.claritycam.platform.repository.inventory.InventoryLedgerRepository;
import jakarta.servlet.http.Cookie;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingFlowIntegrationTest {
  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired PromotionRepository promotions;
  @Autowired InventoryLedgerRepository inventoryLedger;
  @Autowired InventoryAssetRepository inventoryAssets;
  @Autowired StockItemRepository stockItems;
  @Autowired BundleVersionRepository bundleVersions;
  @Autowired ProductRepository productRepository;
  @Autowired BookingReservationRepository reservations;
  @Autowired BookingAllocationRepository allocations;

  @Test
  void waveOneMigrationCreatesImmutableOperationalBaselines() {
    assertTrue(inventoryLedger.count() > 0);
    assertTrue(bundleVersions.existsByBundleId("BND-001"));
    assertTrue(reservations.existsByBookingIdAndState("ORD-202604", ReservationState.ACTIVE));
    assertTrue(allocations.findByBookingIdAndStateIn("ORD-202604",
        Set.of(AllocationState.IN_USE)).stream().findAny().isPresent());
  }

  @Test
  void publicScheduleExposesOnlyAnonymousOccupiedIntervals() throws Exception {
    Csrf csrf = csrf();
    MockHttpSession customer = customerSession(csrf, "0907000076", "Khách xem lịch");
    MvcResult result = mockMvc.perform(get("/api/catalog/GEAR-001/schedule")
            .session(customer)
            .param("from", LocalDateTime.now().minusDays(7).toString())
            .param("to", LocalDateTime.now().plusDays(7).toString()))
        .andExpect(status().isOk())
        .andReturn();

    JsonNode schedule = objectMapper.readTree(result.getResponse().getContentAsString());
    assertTrue(schedule.isArray());
    assertTrue(schedule.size() > 0);
    for (JsonNode block : schedule) {
      assertEquals(3, block.size());
      assertTrue(block.has("pickupTime"));
      assertTrue(block.has("returnTime"));
      assertTrue(block.has("reservedQuantity"));
      assertFalse(block.has("customerName"));
      assertFalse(block.has("phone"));
      assertFalse(block.has("bookingId"));
    }
  }

  @Test
  void customerCompletesCurrentOnboardingVersion() throws Exception {
    Csrf csrf = csrf();
    MockHttpSession customer = customerSession(csrf, "0907777123", "Khách onboarding");
    mockMvc.perform(get("/api/customer/account/me").session(customer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.onboardingVersion").value(0));
    mockMvc.perform(post("/api/customer/account/onboarding/complete")
            .session(customer).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
            .contentType(APPLICATION_JSON).content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.onboardingVersion").value(1))
        .andExpect(jsonPath("$.onboardingCompletedAt").isNotEmpty());
  }

  @Test
  void customerRegistrationAndLoginAreSeparateActions() throws Exception {
    Csrf csrf = csrf();
    String phone = "0907004004";

    mockMvc.perform(post("/api/customer/account/login")
            .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
            .contentType(APPLICATION_JSON)
            .content("{\"phone\":\"" + phone + "\",\"password\":\"short\"}"))
        .andExpect(status().isBadRequest());

    mockMvc.perform(post("/api/customer/account/login")
            .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
            .contentType(APPLICATION_JSON)
            .content("{\"phone\":\"" + phone + "\",\"password\":\"password-123\"}"))
        .andExpect(status().isUnauthorized());

    mockMvc.perform(post("/api/customer/account/register")
            .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
            .contentType(APPLICATION_JSON)
            .content("{\"phone\":\"" + phone + "\",\"name\":\"Khach moi\",\"email\":\"khach4004@example.com\",\"password\":\"password-123\",\"consentAccepted\":true}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.phone").value(phone))
        .andExpect(jsonPath("$.onboardingVersion").value(0));

    mockMvc.perform(post("/api/customer/account/login")
            .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
            .contentType(APPLICATION_JSON)
            .content("{\"phone\":\"" + phone + "\",\"password\":\"wrong-password\"}"))
        .andExpect(status().isUnauthorized());

    MvcResult secondLogin = mockMvc.perform(post("/api/customer/account/login")
            .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
            .contentType(APPLICATION_JSON)
            .content("{\"phone\":\"" + phone + "\",\"password\":\"password-123\"}"))
        .andExpect(status().isOk())
        .andReturn();

    MockHttpSession loginSession = (MockHttpSession) secondLogin.getRequest().getSession(false);
    mockMvc.perform(post("/api/customer/account/logout")
            .session(loginSession).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token()))
        .andExpect(status().isNoContent())
        .andExpect(header().string("Clear-Site-Data", "\"cache\", \"cookies\", \"storage\""));
    assertTrue(loginSession.isInvalid());
  }

  @Test
  void adminManagesCustomerAccountsWithoutExposingPasswordData() throws Exception {
    Csrf csrf = csrf();
    String phone = "0907888999";
    MockHttpSession customer = customerSession(csrf, phone, "Khách quản trị");
    MockHttpSession admin = adminSession(csrf);

    MvcResult accountList = mockMvc.perform(get("/api/admin/customer-accounts")
            .session(admin).param("query", phone))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].phone").value(phone))
        .andExpect(jsonPath("$.items[0].email").value("customer-" + phone + "@example.com"))
        .andExpect(jsonPath("$.items[0].passwordConfigured").value(true))
        .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist())
        .andExpect(jsonPath("$.items[0].password").doesNotExist())
        .andReturn();
    String accountId = objectMapper.readTree(accountList.getResponse().getContentAsString())
        .path("items").path(0).path("id").asText();

    mockMvc.perform(post("/api/admin/customer-accounts/{id}/password-reset", accountId)
            .session(admin).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
            .contentType(APPLICATION_JSON)
            .content("{\"temporaryPassword\":\"Temporary@2026Pass\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mustChangePassword").value(true))
        .andExpect(jsonPath("$.passwordHash").doesNotExist());

    mockMvc.perform(post("/api/customer/account/password/change")
            .session(customer).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
            .contentType(APPLICATION_JSON)
            .content("{\"currentPassword\":\"Temporary@2026Pass\",\"newPassword\":\"Private@2026Password\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mustChangePassword").value(false))
        .andExpect(jsonPath("$.passwordHash").doesNotExist());
  }

  @Test
  void publicWaitlistRegistrationIsDirectIdempotentAndPhoneMasked() throws Exception {
    Csrf csrf = csrf();
    String payload = "{\"name\":\"Khách đăng ký sớm\",\"phone\":\"0906123456\",\"consentAccepted\":true}";

    mockMvc.perform(post("/api/customer/waitlist")
            .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
            .contentType(APPLICATION_JSON).content(payload))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.slotCode").value(org.hamcrest.Matchers.matchesPattern("AMY-[0-9]{6}")))
        .andExpect(jsonPath("$.maskedPhone").value("090****456"))
        .andExpect(jsonPath("$.newlyCreated").value(true));

    mockMvc.perform(post("/api/customer/waitlist")
            .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
            .contentType(APPLICATION_JSON).content(payload))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.newlyCreated").value(false));
  }

  @Test
  void excelImportPreviewRequiresAdminAndKeepsFinancialRulesDraft() throws Exception {
    mockMvc.perform(get("/api/admin/imports/amy-catalog/preview"))
        .andExpect(status().isUnauthorized());

    Csrf csrf = csrf();
    MockHttpSession admin = adminSession(csrf);
    mockMvc.perform(get("/api/admin/imports/amy-catalog/preview").session(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.products").value(12))
        .andExpect(jsonPath("$.physicalKits").value(18))
        .andExpect(jsonPath("$.financialPolicyDrafts").isNumber())
        .andExpect(jsonPath("$.safetyMode").value(org.hamcrest.Matchers.containsString("DRAFT")));
  }

  @Test
  void approvedExcelImportIsAdminOnlyActiveForPreviewAndIdempotent() throws Exception {
    Csrf csrf = csrf();
    MockHttpSession admin = adminSession(csrf);
    String payload = "{\"confirmation\":\"IMPORT_APPROVED_EXCEL\"}";

    mockMvc.perform(post("/api/admin/imports/amy-catalog")
            .session(admin).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
            .contentType(APPLICATION_JSON).content(payload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.productsCreated").value(12))
        .andExpect(jsonPath("$.assetsCreated").value(18))
        .andExpect(jsonPath("$.draftRecordsUpserted")
            .value(org.hamcrest.Matchers.greaterThan(0)))
        .andExpect(jsonPath("$.status").value("ACTIVE_PREVIEW"));

    assertTrue(productRepository.findById("CAM-CANON-R50").orElseThrow().isActive());

    mockMvc.perform(post("/api/admin/imports/amy-catalog")
            .session(admin).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
            .contentType(APPLICATION_JSON).content(payload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.productsCreated").value(0))
        .andExpect(jsonPath("$.assetsCreated").value(0));
  }

  @Test
  void bundleUpdatePublishesANewImmutableVersion() throws Exception {
    Csrf csrf = csrf();
    MockHttpSession admin = adminSession(csrf);
    mockMvc.perform(patch("/api/admin/catalog/bundles/BND-001")
            .session(admin).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
            .contentType(APPLICATION_JSON)
            .content("{\"name\":\"Indie Filmmaker Pro\",\"hourlyPrice\":468750,\"dailyPrice\":3750000,\"multiDayPrice\":10125000,\"multiDayDays\":3,\"active\":true,\"imageUrl\":\"\",\"detailImageUrl\":\"\",\"items\":[{\"productId\":\"GEAR-001\",\"quantity\":1},{\"productId\":\"GEAR-004\",\"quantity\":1},{\"productId\":\"ACC-001\",\"quantity\":1}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currentVersion").value(2));
    mockMvc.perform(get("/api/admin/catalog/bundles/BND-001/versions").session(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].versionNumber").value(2))
        .andExpect(jsonPath("$[1].versionNumber").value(1));
  }

  @Test
  void roleBasedAccessPreventsTechFromReadingBookingsOrManagingUsers() throws Exception {
    Csrf csrf = csrf();
    MockHttpSession admin = adminSession(csrf);
    mockMvc.perform(post("/api/admin/users")
            .session(admin).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
            .contentType(APPLICATION_JSON)
            .content("{\"email\":\"tech.wave1@claritycam.local\",\"password\":\"Strong-Test#2026\",\"role\":\"TECH\"}"))
        .andExpect(status().isCreated());

    MvcResult login = mockMvc.perform(post("/api/auth/login")
            .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token()).contentType(APPLICATION_JSON)
            .content("{\"email\":\"tech.wave1@claritycam.local\",\"password\":\"Strong-Test#2026\"}"))
        .andExpect(status().isOk()).andReturn();
    MockHttpSession tech = (MockHttpSession) login.getRequest().getSession(false);
    mockMvc.perform(get("/api/admin/inventory/ledger").session(tech)).andExpect(status().isOk());
    mockMvc.perform(get("/api/admin/bookings").session(tech)).andExpect(status().isForbidden());
    mockMvc.perform(get("/api/admin/users").session(tech)).andExpect(status().isForbidden());
  }

  @Test
  void invalidAdminCredentialsReturnUnauthorizedInsteadOfServerError() throws Exception {
    Csrf csrf = csrf();
    mockMvc.perform(post("/api/auth/login")
            .with(request -> {
              request.setRemoteAddr("203.0.113.77");
              return request;
            })
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(APPLICATION_JSON)
            .content("{\"email\":\"missing-auth-test@claritycam.local\",\"password\":\"Definitely-Wrong#2026\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Email hoặc mật khẩu không đúng."));
  }

  @Test
  void quotesBundleUsingConfiguredPackagePrice() throws Exception {
    Csrf csrf = csrf();
    mockMvc.perform(post("/api/bookings/quote")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(APPLICATION_JSON)
            .content("{\"pickupTime\":\"2026-09-10T10:00:00\",\"returnTime\":\"2026-09-12T10:00:00\",\"bundleId\":\"BND-002\",\"items\":[{\"productId\":\"GEAR-002\",\"quantity\":1},{\"productId\":\"ACC-002\",\"quantity\":1},{\"productId\":\"ACC-003\",\"quantity\":2}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bundleName").value("Creator Starter"))
        .andExpect(jsonPath("$.totalAmount").value(3900000));
  }

  @Test
  void promotionOnlyDiscountsTheEligibleCalendarDay() throws Exception {
    promotions.save(new Promotion("PROMO-TEST-SAT", "SAT20", "Giảm riêng thứ Bảy", BigDecimal.valueOf(20),
        true, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31), Set.of(DayOfWeek.SATURDAY), "ALL"));
    Csrf csrf = csrf();
    mockMvc.perform(post("/api/bookings/quote")
            .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token()).contentType(APPLICATION_JSON)
            .content("{\"pickupTime\":\"2027-04-09T12:00:00\",\"returnTime\":\"2027-04-10T12:00:00\",\"promotionCode\":\"SAT20\",\"items\":[{\"productId\":\"GEAR-002\",\"quantity\":1}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.subtotalAmount").value(1800000))
        .andExpect(jsonPath("$.discountAmount").value(180000))
        .andExpect(jsonPath("$.totalAmount").value(1620000))
        .andExpect(jsonPath("$.promotionBreakdown[0].eligible").value(false))
        .andExpect(jsonPath("$.promotionBreakdown[1].eligible").value(true));
  }

  @Test
  void quotesShortRentalUsingConfiguredHalfDayRate() throws Exception {
    Csrf csrf = csrf();
    mockMvc.perform(post("/api/bookings/quote")
            .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token()).contentType(APPLICATION_JSON)
            .content("{\"pickupTime\":\"2027-05-10T08:00:00\",\"returnTime\":\"2027-05-10T14:00:00\",\"items\":[{\"productId\":\"GEAR-002\",\"quantity\":1}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rentalHours").value(6))
        .andExpect(jsonPath("$.lines[0].pricingMode").value("HALF_DAY"))
        .andExpect(jsonPath("$.lines[0].billableUnits").value(1))
        .andExpect(jsonPath("$.totalAmount").value(1080000))
        .andExpect(jsonPath("$.identityViolationFee").value(360000))
        .andExpect(jsonPath("$.unauthorizedTransferFee").value(540000))
        .andExpect(jsonPath("$.lateFeePerHour").value(225000))
        .andExpect(jsonPath("$.impactPenaltyPercent").value(100))
        .andExpect(jsonPath("$.damageLiabilityLimit").value(18000000));
  }

  @Test
  void quotesTwoDayRateOnceAndOnlyCollectsMandatoryReservationDeposit() throws Exception {
    Csrf csrf = csrf();
    mockMvc.perform(post("/api/bookings/quote")
            .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token()).contentType(APPLICATION_JSON)
            .content("{\"pickupTime\":\"2027-05-10T08:00:00\",\"returnTime\":\"2027-05-12T08:00:00\",\"rentalRate\":\"TWO_DAY\",\"items\":[{\"productId\":\"GEAR-002\",\"quantity\":1}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rentalDays").value(2))
        .andExpect(jsonPath("$.lines[0].pricingMode").value("TWO_DAY"))
        .andExpect(jsonPath("$.lines[0].billableUnits").value(1))
        .andExpect(jsonPath("$.totalAmount").value(3330000))
        .andExpect(jsonPath("$.bookingDeposit").value(50000))
        .andExpect(jsonPath("$.amountDueNow").value(50000));
  }

  @Test
  void quotesConfiguredMultiDayPackage() throws Exception {
    Csrf csrf = csrf();
    mockMvc.perform(post("/api/bookings/quote")
            .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token()).contentType(APPLICATION_JSON)
            .content("{\"pickupTime\":\"2027-05-10T08:00:00\",\"returnTime\":\"2027-05-13T08:00:00\",\"bundleId\":\"BND-002\",\"items\":[{\"productId\":\"GEAR-002\",\"quantity\":1},{\"productId\":\"ACC-002\",\"quantity\":1},{\"productId\":\"ACC-003\",\"quantity\":2}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rentalDays").value(3))
        .andExpect(jsonPath("$.bundlePricingMode").value("MULTI_DAY"))
        .andExpect(jsonPath("$.bundleBillableUnits").value(1))
        .andExpect(jsonPath("$.totalAmount").value(5265000));
  }

  @Test
  void anonymousClientCannotReserveInventory() throws Exception {
    Csrf csrf = csrf();
    mockMvc.perform(post("/api/bookings/hold")
            .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token()).contentType(APPLICATION_JSON)
            .content("{\"pickupTime\":\"2027-06-01T08:00:00\",\"returnTime\":\"2027-06-01T20:00:00\",\"items\":[{\"productId\":\"GEAR-002\",\"quantity\":1}]}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void repeatedHoldRequestsWithoutTokenReuseTheActiveCustomerHold() throws Exception {
    Csrf csrf = csrf();
    MockHttpSession customer = customerSession(csrf, "0903333333", "Khách thao tác nhanh");
    String payload = "{\"pickupTime\":\"2027-05-20T08:00:00\",\"returnTime\":\"2027-05-20T20:00:00\",\"items\":[{\"productId\":\"GEAR-002\",\"quantity\":1}]}";
    String firstToken = null;

    for (int attempt = 0; attempt < 12; attempt++) {
      MvcResult result = mockMvc.perform(post("/api/bookings/hold")
              .session(customer)
              .cookie(csrf.cookie())
              .header("X-XSRF-TOKEN", csrf.token())
              .contentType(APPLICATION_JSON)
              .content(payload))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.quote.available").value(true))
          .andReturn();
      String token = objectMapper.readTree(result.getResponse().getContentAsString()).path("holdToken").asText();
      if (firstToken == null) firstToken = token;
      assertEquals(firstToken, token);
    }

    releaseHold(csrf, customer, firstToken);
  }

  @Test
  void adminConfiguresBranchesAndCustomersOnlySeeActiveLocations() throws Exception {
    Csrf csrf = csrf();
    MockHttpSession admin = adminSession(csrf);
    MvcResult created = mockMvc.perform(post("/api/admin/stores")
            .session(admin).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
            .contentType(APPLICATION_JSON)
            .content("""
                {"name":"Chi nhánh kiểm thử","address":"123 Đường kiểm thử, TP.HCM",
                 "phone":"0909000000","note":"Dữ liệu test","active":true,"sortOrder":1}
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.startsWith("STORE-")))
        .andReturn();

    String branchId = objectMapper.readTree(created.getResponse().getContentAsString()).path("id").asText();
    mockMvc.perform(get("/api/stores"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(branchId))
        .andExpect(jsonPath("$[0].address").value("123 Đường kiểm thử, TP.HCM"))
        .andExpect(jsonPath("$[0].note").doesNotExist())
        .andExpect(jsonPath("$[0].createdAt").doesNotExist());
  }

  @Test
  void temporaryHoldExpiryIsTimezoneAwareAndFiveMinutesLong() throws Exception {
    Csrf csrf = csrf();
    MockHttpSession customer = customerSession(csrf, "0903333344", "Khách kiểm tra thời gian giữ");
    Instant requestedAt = Instant.now();
    MvcResult result = mockMvc.perform(post("/api/bookings/hold")
            .session(customer)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(APPLICATION_JSON)
            .content("{\"pickupTime\":\"2027-10-20T08:00:00\",\"returnTime\":\"2027-10-20T20:00:00\",\"items\":[{\"productId\":\"GEAR-002\",\"quantity\":1}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.quote.available").value(true))
        .andReturn();

    JsonNode payload = objectMapper.readTree(result.getResponse().getContentAsString());
    Instant expiresAt = Instant.parse(payload.path("expiresAt").asText());
    assertTrue(expiresAt.isAfter(requestedAt.plusSeconds(270)));
    assertTrue(expiresAt.isBefore(Instant.now().plusSeconds(330)));
    releaseHold(csrf, customer, payload.path("holdToken").asText());
  }

  @Test
  void verifiesPhoneCreatesBookingAndAuditsAdminStateChange() throws Exception {
    Csrf csrf = csrf();
    MockHttpSession customerSession = customerSession(csrf, "0901234567", "Nguyễn Văn A");
    long bookingCountBefore = bookingCountFor("GEAR-001", customerSession);

    MvcResult otpRequest = mockMvc.perform(post("/api/otp/request")
            .session(customerSession)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(APPLICATION_JSON)
            .content("{\"phone\":\"0901234567\",\"purpose\":\"BOOKING\"}"))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode otp = objectMapper.readTree(otpRequest.getResponse().getContentAsString());

    MvcResult verify = mockMvc.perform(post("/api/otp/verify")
            .session(customerSession)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(APPLICATION_JSON)
            .content("{\"challengeId\":\"" + otp.path("challengeId").asText() + "\",\"phone\":\"0901234567\",\"code\":\"" + otp.path("demoCode").asText() + "\",\"purpose\":\"BOOKING\"}"))
        .andExpect(status().isOk())
        .andReturn();
    String verificationToken = objectMapper.readTree(verify.getResponse().getContentAsString()).path("verificationToken").asText();
    String identityUploadToken = uploadIdentity(csrf, customerSession);
    String paymentProofUploadToken = uploadPaymentProof(csrf, customerSession);
    LocalDateTime pickup = LocalDateTime.now().plusDays(30).withHour(10).withMinute(0).withSecond(0).withNano(0);
    LocalDateTime returnAt = pickup.plusDays(2);
    LocalDateTime invalidEarlyPickup = pickup.minusDays(1).withHour(20);
    LocalDateTime validEarlyPickup = pickup.minusDays(1).withHour(21);

    MvcResult holdResult = mockMvc.perform(post("/api/bookings/hold")
            .session(customerSession)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(APPLICATION_JSON)
            .content("{\"pickupTime\":\"" + pickup + "\",\"returnTime\":\"" + returnAt + "\",\"items\":[{\"productId\":\"GEAR-001\",\"quantity\":1}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.quote.available").value(true))
        .andReturn();
    String holdToken = objectMapper.readTree(holdResult.getResponse().getContentAsString()).path("holdToken").asText();

    mockMvc.perform(post("/api/bookings")
            .session(customerSession)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(APPLICATION_JSON)
            .content("{\"customerName\":\"Nguyễn Văn A\",\"phone\":\"0901234567\",\"pickupTime\":\"" + pickup + "\",\"returnTime\":\"" + returnAt + "\",\"earlyPickupTime\":\"" + invalidEarlyPickup + "\",\"note\":\"Integration test\",\"holdToken\":\"" + holdToken + "\",\"identityUploadToken\":\"" + identityUploadToken + "\",\"paymentProofUploadToken\":\"" + paymentProofUploadToken + "\",\"items\":[{\"productId\":\"GEAR-001\",\"quantity\":1}]}"))
        .andExpect(status().isBadRequest());

    MvcResult bookingResult = mockMvc.perform(post("/api/bookings")
            .session(customerSession)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(APPLICATION_JSON)
            .content("{\"customerName\":\"Nguyễn Văn A\",\"phone\":\"0901234567\",\"pickupTime\":\"" + pickup + "\",\"returnTime\":\"" + returnAt + "\",\"earlyPickupTime\":\"" + validEarlyPickup + "\",\"note\":\"Integration test\",\"holdToken\":\"" + holdToken + "\",\"identityUploadToken\":\"" + identityUploadToken + "\",\"paymentProofUploadToken\":\"" + paymentProofUploadToken + "\",\"items\":[{\"productId\":\"GEAR-001\",\"quantity\":1}]}"))
        .andExpect(status().isCreated())
        .andReturn();
    String bookingId = objectMapper.readTree(bookingResult.getResponse().getContentAsString()).path("id").asText();
    assertEquals(bookingCountBefore + 1, bookingCountFor("GEAR-001", customerSession));

    mockMvc.perform(get("/api/customer/account/bookings").session(customerSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].identityDocumentsAvailable").value(true))
        .andExpect(jsonPath("$[0].paymentProofAvailable").value(true))
        .andExpect(jsonPath("$[0].items[0].productId").value("GEAR-001"))
        .andExpect(jsonPath("$[0].items[0].productName").value("Sony FX3 Cinema Line"))
        .andExpect(jsonPath("$[0].items[0].levelCode").value("L1"))
        .andExpect(jsonPath("$[0].items[0].serialId").doesNotExist())
        .andExpect(jsonPath("$[0].items[0].pricingRuleVersion").doesNotExist());

    MvcResult customerIdentity = mockMvc.perform(
            get("/api/customer/account/bookings/{id}/identity/front", bookingId).session(customerSession))
        .andExpect(status().isOk())
        .andReturn();
    assertEquals("image/jpeg", customerIdentity.getResponse().getContentType());
    assertTrue(customerIdentity.getResponse().getContentAsByteArray().length > 0);

    MvcResult customerPaymentProof = mockMvc.perform(
            get("/api/customer/account/bookings/{id}/payment-proof", bookingId).session(customerSession))
        .andExpect(status().isOk())
        .andReturn();
    assertEquals("image/jpeg", customerPaymentProof.getResponse().getContentType());
    assertTrue(customerPaymentProof.getResponse().getContentAsByteArray().length > 0);

    MockHttpSession otherCustomer = customerSession(csrf, "0909999999", "Khách khác");
    mockMvc.perform(get("/api/customer/account/bookings/{id}/identity/front", bookingId).session(otherCustomer))
        .andExpect(status().isForbidden());

    mockMvc.perform(post("/api/bookings/track")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(APPLICATION_JSON)
            .content("{\"bookingId\":\"" + bookingId + "\",\"phone\":\"0901234567\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].productId").value("GEAR-001"))
        .andExpect(jsonPath("$.items[0].productName").value("Sony FX3 Cinema Line"))
        .andExpect(jsonPath("$.items[0].levelCode").value("L1"));

    mockMvc.perform(post("/api/bookings/track")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(APPLICATION_JSON)
            .content("{\"bookingId\":\"" + bookingId + "\",\"phone\":\"0900000000\"}"))
        .andExpect(status().isForbidden());

    MvcResult login = mockMvc.perform(post("/api/auth/login")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(APPLICATION_JSON)
            .content("{\"email\":\"admin@claritycam.local\",\"password\":\"change-me-now\"}"))
        .andExpect(status().isOk())
        .andReturn();
    MockHttpSession adminSession = (MockHttpSession) login.getRequest().getSession(false);

    mockMvc.perform(get("/api/admin/bookings").session(adminSession))
        .andExpect(status().isOk());

    mockMvc.perform(patch("/api/admin/bookings/{id}/early-pickup", bookingId)
            .session(adminSession)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(APPLICATION_JSON)
            .content("{\"approved\":true,\"fee\":150000,\"reason\":\"Đã thỏa thuận nhận sớm\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.earlyPickupApproved").value(true));

    mockMvc.perform(patch("/api/admin/bookings/{id}/state", bookingId)
            .session(adminSession)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(APPLICATION_JSON)
            .content("{\"state\":\"TEMP_HOLD\",\"reason\":\"Đang kiểm tra cọc\"}"))
        .andExpect(status().isOk());

    mockMvc.perform(get("/api/admin/bookings/{id}/audit", bookingId).session(adminSession))
        .andExpect(status().isOk());

    MvcResult confirmedBooking = mockMvc.perform(patch("/api/admin/bookings/{id}/state", bookingId)
            .session(adminSession)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(APPLICATION_JSON)
            .content("{\"state\":\"CONFIRMED\",\"reason\":\"Đã xác nhận đơn\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("CONFIRMED"))
        .andReturn();

    var confirmedPayload = objectMapper.readTree(confirmedBooking.getResponse().getContentAsString());
    assertEquals(0, confirmedPayload.path("amountDueNow").decimalValue().compareTo(BigDecimal.valueOf(50_000)));
    String amountDueBeforeHandover = confirmedPayload.path("amountDueBeforeHandover")
        .decimalValue().toPlainString();
    mockMvc.perform(post("/api/admin/finance/payments")
            .session(adminSession)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(APPLICATION_JSON)
            .content("{\"bookingId\":\"" + bookingId + "\",\"amount\":" + amountDueBeforeHandover
                + ",\"method\":\"BANK_TRANSFER\",\"providerReference\":\"TEST-" + bookingId
                + "\",\"idempotencyKey\":\"test-payment-" + bookingId + "\",\"note\":\"Đã đối soát\"}"))
        .andExpect(status().isOk());

    MvcResult confirmedOperations = mockMvc.perform(get("/api/admin/bookings/{id}/operations", bookingId)
            .session(adminSession))
        .andExpect(status().isOk())
        .andReturn();
    assertTrue(objectMapper.readTree(confirmedOperations.getResponse().getContentAsString()).path("reservations")
        .findValuesAsText("type").contains("HARD"));

    mockMvc.perform(patch("/api/admin/bookings/{id}/state", bookingId)
            .session(adminSession)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(APPLICATION_JSON)
            .content("{\"state\":\"IN_USE\",\"reason\":\"Đã duyệt giao máy\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("IN_USE"));

    MvcResult inUseOperations = mockMvc.perform(get("/api/admin/bookings/{id}/operations", bookingId)
            .session(adminSession))
        .andExpect(status().isOk())
        .andReturn();
    assertTrue(objectMapper.readTree(inUseOperations.getResponse().getContentAsString()).path("allocations")
        .findValuesAsText("state").contains("IN_USE"));
    assertTrue(inventoryLedger.findTop500ByOrderByCreatedAtDesc().stream()
        .anyMatch(entry -> "CHECKOUT".equals(entry.getMovementType()) && bookingId.equals(
            entry.getDocumentId().replace("BOOKING-", "").replace("-CHECKOUT", ""))));

    mockMvc.perform(get("/api/catalog/availability").session(customerSession))
        .andExpect(status().isOk());
  }

  @Test
  void temporaryHoldRequiresThirtyMinutePreparationBuffer() throws Exception {
    Csrf csrf = csrf();
    MockHttpSession firstCustomer = customerSession(csrf, "0901111111", "Khách thứ nhất");
    MockHttpSession secondCustomer = customerSession(csrf, "0902222222", "Khách thứ hai");
    String firstPayload = "{\"pickupTime\":\"2027-04-10T08:00:00\",\"returnTime\":\"2027-04-10T20:00:00\",\"items\":[{\"productId\":\"GEAR-002\",\"quantity\":1}]}";
    MvcResult first = mockMvc.perform(post("/api/bookings/hold")
            .session(firstCustomer)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(APPLICATION_JSON)
            .content(firstPayload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.quote.available").value(true))
        .andReturn();
    String firstToken = objectMapper.readTree(first.getResponse().getContentAsString()).path("holdToken").asText();

    mockMvc.perform(post("/api/bookings/hold")
            .session(secondCustomer)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(APPLICATION_JSON)
            .content(firstPayload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.quote.available").value(false));

    mockMvc.perform(post("/api/bookings/hold")
            .session(secondCustomer)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(APPLICATION_JSON)
            .content("{\"pickupTime\":\"2027-04-10T20:00:00\",\"returnTime\":\"2027-04-11T08:00:00\",\"items\":[{\"productId\":\"GEAR-002\",\"quantity\":1}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.quote.available").value(false));

    MvcResult following = mockMvc.perform(post("/api/bookings/hold")
            .session(secondCustomer)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(APPLICATION_JSON)
            .content("{\"pickupTime\":\"2027-04-10T20:30:00\",\"returnTime\":\"2027-04-11T08:00:00\",\"items\":[{\"productId\":\"GEAR-002\",\"quantity\":1}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.quote.available").value(true))
        .andReturn();
    String followingToken = objectMapper.readTree(following.getResponse().getContentAsString()).path("holdToken").asText();

    releaseHold(csrf, firstCustomer, firstToken);
    releaseHold(csrf, secondCustomer, followingToken);
  }

  @Test
  void adminCreatesProductsTogetherWithTheirInitialInventory() throws Exception {
    Csrf csrf = csrf();
    MockHttpSession admin = adminSession(csrf);
    MvcResult branchResult = mockMvc.perform(post("/api/admin/stores")
            .session(admin).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
            .contentType(APPLICATION_JSON)
            .content("""
                {"name":"Kho sản phẩm kiểm thử","address":"456 Đường kiểm thử, TP.HCM",
                 "phone":"0909111222","note":"Gắn sản phẩm theo chi nhánh","active":true,"sortOrder":2}
                """))
        .andExpect(status().isCreated())
        .andReturn();
    String storeBranchId = objectMapper.readTree(branchResult.getResponse().getContentAsString()).path("id").asText();

    mockMvc.perform(post("/api/admin/catalog/products/with-inventory")
            .session(admin).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
            .contentType(APPLICATION_JSON)
            .content("""
                {
                  "productCode": "GEAR-TEST-001",
                  "product": {
                    "levelCode": "L1",
                    "name": "Test Serialized Camera",
                    "brand": "Test Brand",
                    "category": "Camera",
                    "hourlyPrice": 100000,
                    "dailyPrice": 800000,
                    "multiDayPrice": 2160000,
                    "multiDayDays": 3,
                    "included": false,
                    "active": true,
                    "imageUrl": "https://example.com/camera.jpg",
                    "specs": "Integration test camera",
                    "trackingMode": "SERIALIZED",
                    "serialPrefix": "TESTCAM",
                    "storeBranchId": "%s",
                    "bookingCountBase": 0,
                    "customAttributes": "{}"
                  },
                  "initialStockQty": 0,
                  "serialNumbers": ["TESTCAM-001", "TESTCAM-002"]
                }
                """.formatted(storeBranchId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value("GEAR-TEST-001"))
        .andExpect(jsonPath("$.storeBranchId").value(storeBranchId));

    assertEquals("GEAR-TEST-001", inventoryAssets.findById("TESTCAM-001").orElseThrow().getProductId());
    assertEquals("AVAILABLE", inventoryAssets.findById("TESTCAM-002").orElseThrow().getStatus());

    mockMvc.perform(post("/api/admin/catalog/products/with-inventory")
            .session(admin).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
            .contentType(APPLICATION_JSON)
            .content("""
                {
                  "productCode": "ACC-TEST-001",
                  "product": {
                    "levelCode": "L3",
                    "name": "Test Quantity Accessory",
                    "brand": "Test Brand",
                    "category": "Accessory",
                    "hourlyPrice": 25000,
                    "dailyPrice": 150000,
                    "multiDayPrice": 400000,
                    "multiDayDays": 3,
                    "included": false,
                    "active": true,
                    "imageUrl": "https://example.com/accessory.jpg",
                    "specs": "Integration test accessory",
                    "trackingMode": "QUANTITY",
                    "serialPrefix": "",
                    "bookingCountBase": 0,
                    "customAttributes": "{}"
                  },
                  "initialStockQty": 7,
                  "serialNumbers": []
                }
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value("ACC-TEST-001"));

    assertEquals(7, stockItems.findById("ACC-TEST-001").orElseThrow().getTotalQty());
  }

  private long bookingCountFor(String productId, MockHttpSession customer) throws Exception {
    MvcResult result = mockMvc.perform(get("/api/catalog/products").session(customer))
        .andExpect(status().isOk())
        .andReturn();
    for (JsonNode product : objectMapper.readTree(result.getResponse().getContentAsString())) {
      if (productId.equals(product.path("id").asText())) {
        return product.path("bookingCount").asLong();
      }
    }
    throw new AssertionError("Missing catalog product " + productId);
  }

  private MockHttpSession customerSession(Csrf csrf, String phone, String name) throws Exception {
    MockHttpSession session = new MockHttpSession();
    MvcResult login = mockMvc.perform(post("/api/customer/account/register")
            .session(session).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
            .contentType(APPLICATION_JSON)
            .content("{\"phone\":\"" + phone + "\",\"name\":\"" + name + "\",\"email\":\"customer-" + phone + "@example.com\",\"password\":\"test-password\",\"consentAccepted\":true}"))
        .andExpect(status().isCreated()).andReturn();
    return (MockHttpSession) login.getRequest().getSession(false);
  }

  private MockHttpSession adminSession(Csrf csrf) throws Exception {
    MvcResult login = mockMvc.perform(post("/api/auth/login")
            .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token()).contentType(APPLICATION_JSON)
            .content("{\"email\":\"admin@claritycam.local\",\"password\":\"change-me-now\"}"))
        .andExpect(status().isOk()).andReturn();
    return (MockHttpSession) login.getRequest().getSession(false);
  }

  private String uploadIdentity(Csrf csrf, MockHttpSession session) throws Exception {
    BufferedImage image = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(image, "jpg", output);
    MockMultipartFile front = new MockMultipartFile("front", "front.jpg", "image/jpeg", output.toByteArray());
    MockMultipartFile back = new MockMultipartFile("back", "back.jpg", "image/jpeg", output.toByteArray());
    MvcResult result = mockMvc.perform(multipart("/api/customer/account/identity-documents")
            .file(front).file(back).session(session).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token()))
        .andExpect(status().isOk()).andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).path("uploadToken").asText();
  }

  private String uploadPaymentProof(Csrf csrf, MockHttpSession session) throws Exception {
    BufferedImage image = new BufferedImage(720, 1280, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(image, "jpg", output);
    MockMultipartFile proof = new MockMultipartFile("file", "payment-proof.jpg", "image/jpeg", output.toByteArray());
    MvcResult result = mockMvc.perform(multipart("/api/customer/account/payment-proof")
            .file(proof).session(session).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token()))
        .andExpect(status().isOk()).andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).path("uploadToken").asText();
  }

  private void releaseHold(Csrf csrf, MockHttpSession session, String token) throws Exception {
    mockMvc.perform(post("/api/bookings/hold/release")
            .session(session).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
            .contentType(APPLICATION_JSON).content("{\"holdToken\":\"" + token + "\"}"))
        .andExpect(status().isNoContent());
  }

  private Csrf csrf() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
        .andExpect(status().isOk())
        .andReturn();
    Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
    String token = objectMapper.readTree(result.getResponse().getContentAsString()).path("token").asText();
    return new Csrf(cookie, token);
  }

  private record Csrf(Cookie cookie, String token) {}
}
