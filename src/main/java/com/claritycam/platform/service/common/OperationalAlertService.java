package com.claritycam.platform.service.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OperationalAlertService {
  private static final Logger log = LoggerFactory.getLogger(OperationalAlertService.class);

  private final ObjectMapper objectMapper;
  private final HttpClient client;
  private final String webhookUrl;
  private final Duration cooldown;
  private final ConcurrentHashMap<String, Instant> lastSent = new ConcurrentHashMap<>();

  public OperationalAlertService(ObjectMapper objectMapper,
      @Value("${claritycam.monitoring.alert-webhook-url:}") String webhookUrl,
      @Value("${claritycam.monitoring.alert-cooldown-minutes:15}") long cooldownMinutes) {
    this.objectMapper = objectMapper;
    this.webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
    this.cooldown = Duration.ofMinutes(Math.max(1, cooldownMinutes));
    this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  }

  public void alert(String code, String summary) {
    if (!shouldSend(code)) return;
    log.error("OPERATIONAL_ALERT code={} summary={}", code, summary);
    if (webhookUrl.isBlank()) return;
    try {
      String body = objectMapper.writeValueAsString(Map.of(
          "event", "AMY_DIGITAL_API_ALERT",
          "code", code,
          "summary", summary,
          "timestamp", Instant.now().toString()));
      HttpRequest request = HttpRequest.newBuilder(URI.create(webhookUrl))
          .timeout(Duration.ofSeconds(8))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(body))
          .build();
      client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
          .exceptionally(error -> {
            log.warn("Alert webhook delivery failed: {}", error.getMessage());
            return null;
          });
    } catch (Exception error) {
      log.warn("Could not prepare alert webhook: {}", error.getMessage());
    }
  }

  private synchronized boolean shouldSend(String code) {
    Instant now = Instant.now();
    Instant previous = lastSent.get(code);
    if (previous != null && previous.plus(cooldown).isAfter(now)) return false;
    lastSent.put(code, now);
    return true;
  }
}
