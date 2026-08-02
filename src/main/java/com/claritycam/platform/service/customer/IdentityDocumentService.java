package com.claritycam.platform.service.customer;

import com.claritycam.platform.model.customer.IdentityUpload;
import com.claritycam.platform.repository.customer.IdentityUploadRepository;
import com.claritycam.platform.exception.ApiException;
import com.claritycam.platform.infrastructure.storage.IdentityObjectStorage;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class IdentityDocumentService {
  private static final int MAX_BYTES = 5 * 1024 * 1024;
  private static final long MAX_PIXELS = 30_000_000L;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final IdentityObjectStorage objectStorage;
  private final SecretKeySpec encryptionKey;
  private final IdentityUploadRepository uploads;
  private final int retentionDays;

  public IdentityDocumentService(
      @Value("${claritycam.identity-encryption-key}") String encryptionSecret,
      @Value("${claritycam.identity-retention-days:30}") int retentionDays,
      IdentityUploadRepository uploads,
      IdentityObjectStorage objectStorage) {
    this.encryptionKey = new SecretKeySpec(sha256(encryptionSecret), "AES");
    this.retentionDays = Math.max(1, retentionDays);
    this.uploads = uploads;
    this.objectStorage = objectStorage;
  }

  @Transactional
  public UploadReceipt storePair(MultipartFile front, MultipartFile back, String ownerPhone) {
    byte[] normalizedFront = normalizeImage(front, "mÃƒÂ¡Ã‚ÂºÃ‚Â·t trÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºc");
    byte[] normalizedBack = normalizeImage(back, "mÃƒÂ¡Ã‚ÂºÃ‚Â·t sau");
    String frontKey = UUID.randomUUID() + ".bin";
    String backKey = UUID.randomUUID() + ".bin";
    try {
      writeEncrypted(frontKey, normalizedFront);
      writeEncrypted(backKey, normalizedBack);
      LocalDateTime now = LocalDateTime.now();
      IdentityUpload upload = uploads.save(new IdentityUpload(UUID.randomUUID().toString(), fingerprint(ownerPhone),
          frontKey, backKey, now, now.plusMinutes(15)));
      return new UploadReceipt(upload.getId(), upload.getExpiresAt());
    } catch (RuntimeException error) {
      deleteQuietly(frontKey);
      deleteQuietly(backKey);
      if (error instanceof ApiException apiException) throw apiException;
      throw new IllegalStateException("KhÃƒÆ’Ã‚Â´ng thÃƒÂ¡Ã‚Â»Ã†â€™ lÃƒâ€ Ã‚Â°u tÃƒÆ’Ã‚Â i liÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡u xÃƒÆ’Ã‚Â¡c thÃƒÂ¡Ã‚Â»Ã‚Â±c.", error);
    }
  }

  @Transactional
  public ClaimedDocuments claim(String uploadToken, String ownerPhone) {
    IdentityUpload upload = uploads.findById(uploadToken)
        .orElseThrow(() -> ApiException.badRequest("PhiÃƒÆ’Ã‚Âªn tÃƒÂ¡Ã‚ÂºÃ‚Â£i CCCD khÃƒÆ’Ã‚Â´ng tÃƒÂ¡Ã‚Â»Ã¢â‚¬Å“n tÃƒÂ¡Ã‚ÂºÃ‚Â¡i hoÃƒÂ¡Ã‚ÂºÃ‚Â·c Ãƒâ€žÃ¢â‚¬ËœÃƒÆ’Ã‚Â£ hÃƒÂ¡Ã‚ÂºÃ‚Â¿t hÃƒÂ¡Ã‚ÂºÃ‚Â¡n."));
    LocalDateTime now = LocalDateTime.now();
    if (!upload.isUsableBy(fingerprint(ownerPhone), now)) {
      throw ApiException.forbidden("ÃƒÂ¡Ã‚ÂºÃ‚Â¢nh CCCD khÃƒÆ’Ã‚Â´ng thuÃƒÂ¡Ã‚Â»Ã¢â€žÂ¢c phiÃƒÆ’Ã‚Âªn Ãƒâ€žÃ¢â‚¬ËœÃƒâ€žÃ†â€™ng nhÃƒÂ¡Ã‚ÂºÃ‚Â­p nÃƒÆ’Ã‚Â y hoÃƒÂ¡Ã‚ÂºÃ‚Â·c Ãƒâ€žÃ¢â‚¬ËœÃƒÆ’Ã‚Â£ Ãƒâ€žÃ¢â‚¬ËœÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Â£c sÃƒÂ¡Ã‚Â»Ã‚Â­ dÃƒÂ¡Ã‚Â»Ã‚Â¥ng.");
    }
    upload.consume(now);
    uploads.save(upload);
    return new ClaimedDocuments(upload.getFrontStorageKey(), upload.getBackStorageKey());
  }

  public StoredImage read(String storageKey) {
    if (storageKey == null || !storageKey.matches("[0-9a-fA-F-]{36}\\.bin")) {
      throw ApiException.notFound("KhÃƒÆ’Ã‚Â´ng tÃƒÆ’Ã‚Â¬m thÃƒÂ¡Ã‚ÂºÃ‚Â¥y ÃƒÂ¡Ã‚ÂºÃ‚Â£nh xÃƒÆ’Ã‚Â¡c thÃƒÂ¡Ã‚Â»Ã‚Â±c.");
    }
    try {
      byte[] payload = objectStorage.get(storageKey);
      if (payload.length < 13) throw new IllegalStateException("TÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡p xÃƒÆ’Ã‚Â¡c thÃƒÂ¡Ã‚Â»Ã‚Â±c khÃƒÆ’Ã‚Â´ng hÃƒÂ¡Ã‚Â»Ã‚Â£p lÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡.");
      byte[] iv = java.util.Arrays.copyOfRange(payload, 0, 12);
      byte[] ciphertext = java.util.Arrays.copyOfRange(payload, 12, payload.length);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(128, iv));
      cipher.updateAAD(storageKey.getBytes(StandardCharsets.UTF_8));
      return new StoredImage(cipher.doFinal(ciphertext), "image/jpeg");
    } catch (ApiException error) {
      throw error;
    } catch (Exception error) {
      throw new IllegalStateException("KhÃƒÆ’Ã‚Â´ng thÃƒÂ¡Ã‚Â»Ã†â€™ Ãƒâ€žÃ¢â‚¬ËœÃƒÂ¡Ã‚Â»Ã‚Âc ÃƒÂ¡Ã‚ÂºÃ‚Â£nh xÃƒÆ’Ã‚Â¡c thÃƒÂ¡Ã‚Â»Ã‚Â±c.", error);
    }
  }

  @Scheduled(fixedDelayString = "${claritycam.identity-cleanup-ms:3600000}")
  @Transactional
  public void cleanupExpired() {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime retainedSince = now.minusDays(retentionDays);
    uploads.findAll().stream()
        .filter(upload -> upload.getConsumedAt() == null
            ? !upload.getExpiresAt().isAfter(now)
            : upload.getConsumedAt().isBefore(retainedSince))
        .forEach(upload -> {
          deleteQuietly(upload.getFrontStorageKey());
          deleteQuietly(upload.getBackStorageKey());
          uploads.delete(upload);
        });
  }

  private byte[] normalizeImage(MultipartFile file, String label) {
    if (file == null || file.isEmpty()) throw ApiException.badRequest("Vui lÃƒÆ’Ã‚Â²ng tÃƒÂ¡Ã‚ÂºÃ‚Â£i Ãƒâ€žÃ¢â‚¬ËœÃƒÂ¡Ã‚Â»Ã‚Â§ ÃƒÂ¡Ã‚ÂºÃ‚Â£nh CCCD.");
    if (file.getSize() > MAX_BYTES) throw ApiException.badRequest("ÃƒÂ¡Ã‚ÂºÃ‚Â¢nh CCCD " + label + " khÃƒÆ’Ã‚Â´ng Ãƒâ€žÃ¢â‚¬ËœÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Â£c vÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Â£t quÃƒÆ’Ã‚Â¡ 5 MB.");
    try {
      byte[] source = file.getBytes();
      BufferedImage decoded;
      try (ImageInputStream imageInput = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
        var readers = ImageIO.getImageReaders(imageInput);
        if (!readers.hasNext()) throw ApiException.badRequest("TÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡p " + label + " khÃƒÆ’Ã‚Â´ng phÃƒÂ¡Ã‚ÂºÃ‚Â£i ÃƒÂ¡Ã‚ÂºÃ‚Â£nh JPG hoÃƒÂ¡Ã‚ÂºÃ‚Â·c PNG hÃƒÂ¡Ã‚Â»Ã‚Â£p lÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡.");
        ImageReader reader = readers.next();
        try {
          reader.setInput(imageInput, true, true);
          String format = reader.getFormatName().toUpperCase();
          if (!format.equals("JPEG") && !format.equals("JPG") && !format.equals("PNG")) {
            throw ApiException.badRequest("ÃƒÂ¡Ã‚ÂºÃ‚Â¢nh CCCD chÃƒÂ¡Ã‚Â»Ã¢â‚¬Â° hÃƒÂ¡Ã‚Â»Ã¢â‚¬â€ trÃƒÂ¡Ã‚Â»Ã‚Â£ JPG hoÃƒÂ¡Ã‚ÂºÃ‚Â·c PNG.");
          }
          int width = reader.getWidth(0);
          int height = reader.getHeight(0);
          if (width < 200 || height < 120 || (long) width * height > MAX_PIXELS) {
            throw ApiException.badRequest("KÃƒÆ’Ã‚Â­ch thÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºc ÃƒÂ¡Ã‚ÂºÃ‚Â£nh CCCD " + label + " khÃƒÆ’Ã‚Â´ng hÃƒÂ¡Ã‚Â»Ã‚Â£p lÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡.");
          }
          decoded = reader.read(0);
        } finally {
          reader.dispose();
        }
      }
      BufferedImage rgb = new BufferedImage(decoded.getWidth(), decoded.getHeight(), BufferedImage.TYPE_INT_RGB);
      Graphics2D graphics = rgb.createGraphics();
      graphics.drawImage(decoded, 0, 0, null);
      graphics.dispose();
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      if (!ImageIO.write(rgb, "jpg", output)) throw new IOException("JPEG encoder unavailable");
      return output.toByteArray();
    } catch (ApiException error) {
      throw error;
    } catch (IOException error) {
      throw ApiException.badRequest("KhÃƒÆ’Ã‚Â´ng thÃƒÂ¡Ã‚Â»Ã†â€™ Ãƒâ€žÃ¢â‚¬ËœÃƒÂ¡Ã‚Â»Ã‚Âc ÃƒÂ¡Ã‚ÂºÃ‚Â£nh CCCD " + label + ".");
    }
  }

  private void writeEncrypted(String storageKey, byte[] plaintext) {
    try {
      byte[] iv = new byte[12];
      RANDOM.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(128, iv));
      cipher.updateAAD(storageKey.getBytes(StandardCharsets.UTF_8));
      byte[] ciphertext = cipher.doFinal(plaintext);
      ByteArrayOutputStream payload = new ByteArrayOutputStream(iv.length + ciphertext.length);
      payload.write(iv);
      payload.write(ciphertext);
      objectStorage.put(storageKey, payload.toByteArray());
    } catch (Exception error) {
      throw new IllegalStateException("KhÃƒÆ’Ã‚Â´ng thÃƒÂ¡Ã‚Â»Ã†â€™ mÃƒÆ’Ã‚Â£ hÃƒÆ’Ã‚Â³a ÃƒÂ¡Ã‚ÂºÃ‚Â£nh xÃƒÆ’Ã‚Â¡c thÃƒÂ¡Ã‚Â»Ã‚Â±c.", error);
    }
  }

  private void deleteQuietly(String storageKey) {
    if (storageKey == null) return;
    try { objectStorage.delete(storageKey); } catch (Exception ignored) { }
  }

  @Transactional
  public UploadReceipt storeSingle(MultipartFile file, String ownerPhone) {
    byte[] normalized = normalizeImage(file, "bÃƒÂ¡Ã‚ÂºÃ‚Â±ng chÃƒÂ¡Ã‚Â»Ã‚Â©ng thanh toÃƒÆ’Ã‚Â¡n");
    String storageKey = UUID.randomUUID() + ".bin";
    try {
      writeEncrypted(storageKey, normalized);
      LocalDateTime now = LocalDateTime.now();
      IdentityUpload upload = uploads.save(new IdentityUpload(UUID.randomUUID().toString(), fingerprint(ownerPhone),
          storageKey, storageKey, now, now.plusMinutes(15)));
      return new UploadReceipt(upload.getId(), upload.getExpiresAt());
    } catch (RuntimeException error) {
      deleteQuietly(storageKey);
      if (error instanceof ApiException apiException) throw apiException;
      throw new IllegalStateException("KhÃƒÆ’Ã‚Â´ng thÃƒÂ¡Ã‚Â»Ã†â€™ lÃƒâ€ Ã‚Â°u bÃƒÂ¡Ã‚ÂºÃ‚Â±ng chÃƒÂ¡Ã‚Â»Ã‚Â©ng thanh toÃƒÆ’Ã‚Â¡n.", error);
    }
  }

  private static byte[] sha256(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (Exception error) {
      throw new IllegalStateException("SHA-256 is unavailable", error);
    }
  }

  private static String fingerprint(String value) {
    return HexFormat.of().formatHex(sha256(value));
  }

  public record UploadReceipt(String uploadToken, LocalDateTime expiresAt) {}
  public record ClaimedDocuments(String frontStorageKey, String backStorageKey) {}
  public record StoredImage(byte[] bytes, String contentType) {}
}
