package com.claritycam.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SystemSmokeTest {
  @LocalServerPort int port;
  TestRestTemplate http;
  @Autowired ObjectMapper objectMapper;

  @BeforeEach
  void createCookieAwareHttpClient() {
    http = new TestRestTemplate(
        new RestTemplateBuilder().rootUri("http://127.0.0.1:" + port),
        null,
        null,
        TestRestTemplate.HttpClientOption.ENABLE_COOKIES);
  }

  @Test
  void runningServerExposesPublicReadModelsAndHealth() throws Exception {
    assertEquals(HttpStatus.OK, http.getForEntity("/actuator/health", String.class).getStatusCode());

    JsonNode features = getJson("/api/features");
    assertTrue(features.has("bookingEnabled"));
    assertTrue(features.has("earlyAccessRegistrationEnabled"));

    assertEquals(HttpStatus.UNAUTHORIZED,
        http.getForEntity("/api/catalog/products", String.class).getStatusCode());

    registerCustomer();
    JsonNode products = getJson("/api/catalog/products");
    assertTrue(products.isArray());
    assertTrue(products.size() > 0);

    JsonNode stores = getJson("/api/stores");
    assertTrue(stores.isArray());
  }

  @Test
  void runningServerRejectsAnonymousAdminAccessAndAddsSecurityHeaders() {
    ResponseEntity<String> publicResponse = http.getForEntity("/api/features", String.class);
    assertEquals("DENY", publicResponse.getHeaders().getFirst("X-Frame-Options"));
    assertNotNull(publicResponse.getHeaders().getFirst("Content-Security-Policy"));
    assertEquals("nosniff", publicResponse.getHeaders().getFirst("X-Content-Type-Options"));

    ResponseEntity<String> adminResponse = http.getForEntity("/api/admin/bookings", String.class);
    assertEquals(HttpStatus.UNAUTHORIZED, adminResponse.getStatusCode());
    assertEquals("no-referrer", adminResponse.getHeaders().getFirst("Referrer-Policy"));
  }

  private JsonNode getJson(String path) throws Exception {
    ResponseEntity<String> response = http.getForEntity(path, String.class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    return objectMapper.readTree(response.getBody());
  }

  private void registerCustomer() throws Exception {
    ResponseEntity<String> csrfResponse = http.getForEntity("/api/auth/csrf", String.class);
    assertEquals(HttpStatus.OK, csrfResponse.getStatusCode());
    JsonNode csrf = objectMapper.readTree(csrfResponse.getBody());
    cookie(csrfResponse, "XSRF-TOKEN");

    HttpHeaders registerHeaders = new HttpHeaders();
    registerHeaders.setContentType(MediaType.APPLICATION_JSON);
    registerHeaders.set(csrf.path("headerName").asText("X-XSRF-TOKEN"), csrf.path("token").asText());
    String body = """
        {"phone":"0907009090","name":"System Smoke","email":"system-smoke@example.com",
         "password":"System-test#2026","consentAccepted":true}
        """;
    ResponseEntity<String> registerResponse = http.postForEntity("/api/customer/account/register",
        new HttpEntity<>(body, registerHeaders), String.class);
    assertEquals(HttpStatus.CREATED, registerResponse.getStatusCode());
  }

  private String cookie(ResponseEntity<?> response, String name) {
    return response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE).stream()
        .map(value -> value.split(";", 2)[0])
        .filter(value -> value.startsWith(name + "="))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing cookie " + name));
  }
}
