package com.claritycam.platform.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.claritycam.platform.service.common.ClientAddressResolver;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientAddressResolverTest {
  private final ClientAddressResolver resolver = new ClientAddressResolver();

  @Test
  void usesOriginalClientAddressForwardedByProxy() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("10.0.0.10");
    request.addHeader("X-Forwarded-For", "203.0.113.44, 10.0.0.10");

    assertEquals("203.0.113.44", resolver.resolve(request));
  }

  @Test
  void fallsBackToRemoteAddressWithoutForwardedHeader() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("10.0.0.10");

    assertEquals("10.0.0.10", resolver.resolve(request));
  }
}
