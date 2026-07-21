package com.claritycam.platform.customer;

import com.claritycam.platform.common.ApiException;
import com.claritycam.platform.customer.storage.IdentityObjectStorage;
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
    byte[] normalizedFront = normalizeImage(front, "mặt trước");
    byte[] normalizedBack = normalizeImage(back, "mặt sau");
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
      throw new IllegalStateException("Không thể lưu tài liệu xác thực.", error);
    }
  }

  @Transactional
  public ClaimedDocuments claim(String uploadToken, String ownerPhone) {
    IdentityUpload upload = uploads.findById(uploadToken)
        .orElseThrow(() -> ApiException.badRequest("Phiên tải CCCD không tồn tại hoặc đã hết hạn."));
    LocalDateTime now = LocalDateTime.now();
    if (!upload.isUsableBy(fingerprint(ownerPhone), now)) {
      throw ApiException.forbidden("Ảnh CCCD không thuộc phiên đăng nhập này hoặc đã được sử dụng.");
    }
    upload.consume(now);
    uploads.save(upload);
    return new ClaimedDocuments(upload.getFrontStorageKey(), upload.getBackStorageKey());
  }

  public StoredImage read(String storageKey) {
    if (storageKey == null || !storageKey.matches("[0-9a-fA-F-]{36}\\.bin")) {
      throw ApiException.notFound("Không tìm thấy ảnh xác thực.");
    }
    try {
      byte[] payload = objectStorage.get(storageKey);
      if (payload.length < 13) throw new IllegalStateException("Tệp xác thực không hợp lệ.");
      byte[] iv = java.util.Arrays.copyOfRange(payload, 0, 12);
      byte[] ciphertext = java.util.Arrays.copyOfRange(payload, 12, payload.length);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(128, iv));
      cipher.updateAAD(storageKey.getBytes(StandardCharsets.UTF_8));
      return new StoredImage(cipher.doFinal(ciphertext), "image/jpeg");
    } catch (ApiException error) {
      throw error;
    } catch (Exception error) {
      throw new IllegalStateException("Không thể đọc ảnh xác thực.", error);
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
    if (file == null || file.isEmpty()) throw ApiException.badRequest("Vui lòng tải đủ ảnh CCCD.");
    if (file.getSize() > MAX_BYTES) throw ApiException.badRequest("Ảnh CCCD " + label + " không được vượt quá 5 MB.");
    try {
      byte[] source = file.getBytes();
      BufferedImage decoded;
      try (ImageInputStream imageInput = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
        var readers = ImageIO.getImageReaders(imageInput);
        if (!readers.hasNext()) throw ApiException.badRequest("Tệp " + label + " không phải ảnh JPG hoặc PNG hợp lệ.");
        ImageReader reader = readers.next();
        try {
          reader.setInput(imageInput, true, true);
          String format = reader.getFormatName().toUpperCase();
          if (!format.equals("JPEG") && !format.equals("JPG") && !format.equals("PNG")) {
            throw ApiException.badRequest("Ảnh CCCD chỉ hỗ trợ JPG hoặc PNG.");
          }
          int width = reader.getWidth(0);
          int height = reader.getHeight(0);
          if (width < 200 || height < 120 || (long) width * height > MAX_PIXELS) {
            throw ApiException.badRequest("Kích thước ảnh CCCD " + label + " không hợp lệ.");
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
      throw ApiException.badRequest("Không thể đọc ảnh CCCD " + label + ".");
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
      throw new IllegalStateException("Không thể mã hóa ảnh xác thực.", error);
    }
  }

  private void deleteQuietly(String storageKey) {
    if (storageKey == null) return;
    try { objectStorage.delete(storageKey); } catch (Exception ignored) { }
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
