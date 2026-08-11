package com.claritycam.platform.service.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ClientAddressResolver {
  public String resolve(HttpServletRequest request) {
    // Vercel forwards the original browser address in this header. Using the
    // proxy address would make every visitor share one rate-limit bucket.
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      String client = forwarded.split(",", 2)[0].trim();
      if (!client.isBlank()) return client;
    }
    String address = request.getRemoteAddr();
    return address == null || address.isBlank() ? "unknown" : address;
  }
}
