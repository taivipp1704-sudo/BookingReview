package com.claritycam.platform.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.claritycam.platform.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SmsDeliveryServiceTest {
  private static final String ENDPOINT =
      "https://rest.esms.vn/MainService.svc/json/SendMultipleMessage_V4_post_json/";

  @Test
  void sendsOtpUsingApprovedEsmsTemplate() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    SmsDeliveryService service = esmsService(builder, true);

    server.expect(requestTo(ENDPOINT))
        .andExpect(method(HttpMethod.POST))
        .andExpect(jsonPath("$.ApiKey").value("api-key"))
        .andExpect(jsonPath("$.SecretKey").value("secret-key"))
        .andExpect(jsonPath("$.Phone").value("0901234567"))
        .andExpect(jsonPath("$.Content").value("123456 la ma xac thuc CLARITYCAM, hieu luc 5 phut."))
        .andExpect(jsonPath("$.Brandname").value("CLARITYCAM"))
        .andExpect(jsonPath("$.SmsType").value("2"))
        .andExpect(jsonPath("$.Sandbox").value("1"))
        .andExpect(jsonPath("$.RequestId").value("challenge-id"))
        .andRespond(withSuccess("{\"CodeResult\":\"100\",\"SMSID\":\"sms-id\"}", MediaType.APPLICATION_JSON));

    service.sendOtp("0901234567", "123456", 5, "challenge-id");
    server.verify();
  }

  @Test
  void rejectsProviderErrorWithoutLeakingProviderDetails() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    SmsDeliveryService service = esmsService(builder, false);

    server.expect(requestTo(ENDPOINT))
        .andRespond(withSuccess("{\"CodeResult\":\"101\",\"ErrorMessage\":\"Authorize Failed\"}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> service.sendOtp("0901234567", "123456", 5, "challenge-id"))
        .isInstanceOfSatisfying(ApiException.class, exception -> {
          assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
          assertThat(exception.getMessage()).doesNotContain("Authorize Failed");
        });
    server.verify();
  }

  private SmsDeliveryService esmsService(RestClient.Builder builder, boolean sandbox) {
    return new SmsDeliveryService(
        builder,
        "esms",
        ENDPOINT,
        "api-key",
        "secret-key",
        "CLARITYCAM",
        "{OTP} la ma xac thuc CLARITYCAM, hieu luc {MINUTES} phut.",
        "2",
        "0",
        sandbox);
  }
}
