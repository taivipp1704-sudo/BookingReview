package com.claritycam.platform.config;

import java.util.regex.Pattern;

/**
 * Mã PIN 6 số là phương thức đăng nhập nhanh thứ hai, song song với mật khẩu đầy
 * đủ ({@link PasswordPolicy}) — dành cho khách thuê không thường xuyên, dễ quên
 * mật khẩu phức tạp. Vì không gian mã chỉ có 1 triệu tổ hợp, PIN không thay thế
 * mật khẩu và được bảo vệ thêm bằng giới hạn tần suất (RateLimitService) cùng cơ
 * chế tự vô hiệu hoá sau nhiều lần sai (CustomerAccount.registerPinFailure).
 */
public final class PinPolicy {
  public static final String REGEX = "^\\d{6}$";
  public static final String MESSAGE = "Mã PIN phải gồm đúng 6 chữ số";
  private static final Pattern COMPILED = Pattern.compile(REGEX);

  private PinPolicy() {}

  public static boolean isValid(String pin) {
    return pin != null && COMPILED.matcher(pin).matches();
  }
}
