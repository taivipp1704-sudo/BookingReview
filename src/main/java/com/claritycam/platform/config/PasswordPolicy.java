package com.claritycam.platform.config;

import java.util.regex.Pattern;

public final class PasswordPolicy {
  public static final String REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{12,128}$";
  public static final String MESSAGE =
      "Mật khẩu phải dài 12-128 ký tự và có chữ hoa, chữ thường, số, ký tự đặc biệt";
  private static final Pattern COMPILED = Pattern.compile(REGEX);

  private PasswordPolicy() {}

  public static boolean isValid(String password) {
    return password != null && COMPILED.matcher(password).matches();
  }
}
