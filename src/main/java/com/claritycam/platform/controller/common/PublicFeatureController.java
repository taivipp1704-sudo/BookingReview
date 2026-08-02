package com.claritycam.platform.controller.common;

import com.claritycam.platform.service.common.ReleaseFeatureService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/features")
public class PublicFeatureController {
  private final ReleaseFeatureService features;

  public PublicFeatureController(ReleaseFeatureService features) {
    this.features = features;
  }

  @GetMapping
  FeatureResponse features() {
    return new FeatureResponse(features.isBookingEnabled(), features.isEarlyAccessRegistrationEnabled(),
        features.isEarlyAccessRegistrationEnabled() ? "ACCOUNT_PREVIEW" : "CLOSED");
  }

  public record FeatureResponse(boolean bookingEnabled, boolean earlyAccessRegistrationEnabled,
      String customerRegistrationMode) {}
}
