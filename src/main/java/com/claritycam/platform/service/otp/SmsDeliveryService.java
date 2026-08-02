package com.claritycam.platform.service.otp;

import com.claritycam.platform.exception.ApiException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class SmsDeliveryService {
  private static final String ESMS_PROVIDER = "esms";

  private final RestClient restClient;
  private final String provider;
  private final String apiKey;
  private final String secretKey;
  private final String brandname;
  private final String template;
  private final String smsType;
  private final String isUnicode;
  private final boolean sandbox;

  public SmsDeliveryService(
      RestClient.Builder restClientBuilder,
      @Value("${claritycam.sms.provider:none}") String provider,
      @Value("${claritycam.sms.esms.api-url:https://rest.esms.vn/MainService.svc/json/SendMultipleMessage_V4_post_json/}") String apiUrl,
      @Value("${claritycam.sms.esms.api-key:}") String apiKey,
      @Value("${claritycam.sms.esms.secret-key:}") String secretKey,
      @Value("${claritycam.sms.esms.brandname:}") String brandname,
      @Value("${claritycam.sms.esms.template:}") String template,
      @Value("${claritycam.sms.esms.sms-type:2}") String smsType,
      @Value("${claritycam.sms.esms.is-unicode:0}") String isUnicode,
      @Value("${claritycam.sms.esms.sandbox:false}") boolean sandbox) {
    this.restClient = restClientBuilder.baseUrl(apiUrl).build();
    this.provider = provider == null ? "none" : provider.trim().toLowerCase(Locale.ROOT);
    this.apiKey = apiKey;
    this.secretKey = secretKey;
    this.brandname = brandname;
    this.template = template;
    this.smsType = smsType;
    this.isUnicode = isUnicode;
    this.sandbox = sandbox;
  }

  public boolean isEnabled() {
    return !"none".equals(provider);
  }

  public void sendOtp(String phone, String code, int expiryMinutes, String requestId) {
    if (!ESMS_PROVIDER.equals(provider)) {
      if ("none".equals(provider)) {
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "DÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¹ch vÃƒÂ¡Ã‚Â»Ã‚Â¥ gÃƒÂ¡Ã‚Â»Ã‚Â­i SMS OTP chÃƒâ€ Ã‚Â°a Ãƒâ€žÃ¢â‚¬ËœÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Â£c cÃƒÂ¡Ã‚ÂºÃ‚Â¥u hÃƒÆ’Ã‚Â¬nh.");
      }
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "NhÃƒÆ’Ã‚Â  cung cÃƒÂ¡Ã‚ÂºÃ‚Â¥p SMS khÃƒÆ’Ã‚Â´ng Ãƒâ€žÃ¢â‚¬ËœÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Â£c hÃƒÂ¡Ã‚Â»Ã¢â‚¬â€ trÃƒÂ¡Ã‚Â»Ã‚Â£.");
    }
    validateEsmsConfiguration();

    String content = template
        .replace("{OTP}", code)
        .replace("{MINUTES}", Integer.toString(expiryMinutes));
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("ApiKey", apiKey);
    payload.put("SecretKey", secretKey);
    payload.put("Phone", phone);
    payload.put("Content", content);
    payload.put("Brandname", brandname);
    payload.put("SmsType", smsType);
    payload.put("IsUnicode", isUnicode);
    payload.put("Sandbox", sandbox ? "1" : "0");
    payload.put("RequestId", requestId);

    try {
      Map<String, Object> response = restClient.post()
          .body(payload)
          .retrieve()
          .body(new ParameterizedTypeReference<>() {});
      String resultCode = response == null ? null : String.valueOf(response.get("CodeResult"));
      if (!"100".equals(resultCode)) {
        throw new ApiException(HttpStatus.BAD_GATEWAY, "NhÃƒÆ’Ã‚Â  cung cÃƒÂ¡Ã‚ÂºÃ‚Â¥p SMS tÃƒÂ¡Ã‚Â»Ã‚Â« chÃƒÂ¡Ã‚Â»Ã¢â‚¬Ëœi yÃƒÆ’Ã‚Âªu cÃƒÂ¡Ã‚ÂºÃ‚Â§u gÃƒÂ¡Ã‚Â»Ã‚Â­i OTP.");
      }
    } catch (ApiException exception) {
      throw exception;
    } catch (RestClientException exception) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "KhÃƒÆ’Ã‚Â´ng thÃƒÂ¡Ã‚Â»Ã†â€™ kÃƒÂ¡Ã‚ÂºÃ‚Â¿t nÃƒÂ¡Ã‚Â»Ã¢â‚¬Ëœi tÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºi nhÃƒÆ’Ã‚Â  cung cÃƒÂ¡Ã‚ÂºÃ‚Â¥p SMS.");
    }
  }

  private void validateEsmsConfiguration() {
    if (!StringUtils.hasText(apiKey)
        || !StringUtils.hasText(secretKey)
        || !StringUtils.hasText(brandname)
        || !StringUtils.hasText(template)
        || !template.contains("{OTP}")) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CÃƒÂ¡Ã‚ÂºÃ‚Â¥u hÃƒÆ’Ã‚Â¬nh eSMS chÃƒâ€ Ã‚Â°a Ãƒâ€žÃ¢â‚¬ËœÃƒÂ¡Ã‚ÂºÃ‚Â§y Ãƒâ€žÃ¢â‚¬ËœÃƒÂ¡Ã‚Â»Ã‚Â§ hoÃƒÂ¡Ã‚ÂºÃ‚Â·c mÃƒÂ¡Ã‚ÂºÃ‚Â«u tin thiÃƒÂ¡Ã‚ÂºÃ‚Â¿u {OTP}.");
    }
  }
}
