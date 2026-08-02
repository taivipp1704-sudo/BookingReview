package com.claritycam.platform.common;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.claritycam.platform.exception.ApiException;
import com.claritycam.platform.service.common.ReleaseFeatureService;
import org.junit.jupiter.api.Test;

class ReleaseFeatureServiceTest {
  @Test
  void previewModeAllowsRegistrationButBlocksBooking() {
    ReleaseFeatureService features = new ReleaseFeatureService(false, true);

    assertDoesNotThrow(features::requireEarlyAccessRegistrationEnabled);
    assertThrows(ApiException.class, features::requireBookingEnabled);
  }
}
