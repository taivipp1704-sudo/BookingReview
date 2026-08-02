package com.claritycam.platform.service.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ClientAddressResolver {
  public String resolve(HttpServletRequest request) {
    String address = request.getRemoteAddr();
    return address == null || address.isBlank() ? "unknown" : address;
  }
}
