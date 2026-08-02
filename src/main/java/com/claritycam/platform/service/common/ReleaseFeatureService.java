package com.claritycam.platform.service.common;

import com.claritycam.platform.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ReleaseFeatureService {
  private final boolean bookingEnabled;
  private final boolean earlyAccessRegistrationEnabled;

  public ReleaseFeatureService(
      @Value("${claritycam.features.booking-enabled:false}") boolean bookingEnabled,
      @Value("${claritycam.features.early-access-registration-enabled:true}")
      boolean earlyAccessRegistrationEnabled) {
    this.bookingEnabled = bookingEnabled;
    this.earlyAccessRegistrationEnabled = earlyAccessRegistrationEnabled;
  }

  public boolean isBookingEnabled() {
    return bookingEnabled;
  }

  public boolean isEarlyAccessRegistrationEnabled() {
    return earlyAccessRegistrationEnabled;
  }

  public void requireBookingEnabled() {
    if (!bookingEnabled) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
          "Tính năng đặt thuê chưa mở. Bạn có thể đăng ký giữ chỗ trước.");
    }
  }

  public void requireEarlyAccessRegistrationEnabled() {
    if (!earlyAccessRegistrationEnabled) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
          "Đăng ký giữ chỗ đang tạm dừng. Vui lòng thử lại sau.");
    }
  }
}
