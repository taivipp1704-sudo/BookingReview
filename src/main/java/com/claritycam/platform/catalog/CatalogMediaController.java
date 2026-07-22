package com.claritycam.platform.catalog;

import com.claritycam.platform.common.ApiException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class CatalogMediaController {
  private static final int MAX_BYTES = 8 * 1024 * 1024;
  private static final int TARGET_WIDTH = 1200;
  private static final int TARGET_HEIGHT = 900;
  private static final long MAX_PIXELS = 40_000_000L;
  private final Path root;

  public CatalogMediaController(
      @Value("${claritycam.catalog-image-storage:./public-data/catalog}") String storageRoot) {
    this.root = Path.of(storageRoot).toAbsolutePath().normalize();
    try {
      Files.createDirectories(root);
    } catch (IOException error) {
      throw new IllegalStateException("Không thể khởi tạo kho ảnh catalog.", error);
    }
  }

  @PostMapping(value = "/admin/media/catalog-images", consumes = "multipart/form-data")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
  Map<String, String> upload(@RequestPart("file") MultipartFile file) {
    byte[] normalized = normalize(file);
    String name = UUID.randomUUID().toString() + ".jpg";
    try {
      Files.write(root.resolve(name), normalized);
    } catch (IOException error) {
      throw new IllegalStateException("Không thể lưu ảnh catalog.", error);
    }
    return Map.of("url", "/api/media/catalog/" + name);
  }

  @GetMapping("/media/catalog/{name}")
  ResponseEntity<byte[]> read(@PathVariable String name) {
    if (!name.matches("[0-9a-fA-F-]{36}\\.jpg")) {
      throw ApiException.notFound("Không tìm thấy ảnh.");
    }
    Path file = root.resolve(name).normalize();
    if (!file.startsWith(root) || !Files.isRegularFile(file)) {
      throw ApiException.notFound("Không tìm thấy ảnh.");
    }
    try {
      return ResponseEntity.ok()
          .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(30)).cachePublic())
          .contentType(MediaType.IMAGE_JPEG)
          .body(Files.readAllBytes(file));
    } catch (IOException error) {
      throw new IllegalStateException("Không thể đọc ảnh catalog.", error);
    }
  }

  private byte[] normalize(MultipartFile file) {
    if (file == null || file.isEmpty()) throw ApiException.badRequest("Vui lòng chọn ảnh JPG hoặc PNG.");
    if (file.getSize() > MAX_BYTES) throw ApiException.badRequest("Ảnh không được vượt quá 8 MB.");
    try {
      byte[] source = file.getBytes();
      BufferedImage decoded;
      try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
        var readers = ImageIO.getImageReaders(input);
        if (!readers.hasNext()) throw ApiException.badRequest("Tệp tải lên không phải ảnh hợp lệ.");
        ImageReader reader = readers.next();
        try {
          reader.setInput(input, true, true);
          String format = reader.getFormatName().toUpperCase();
          if (!format.equals("JPEG") && !format.equals("JPG") && !format.equals("PNG")) {
            throw ApiException.badRequest("Chỉ hỗ trợ ảnh JPG hoặc PNG.");
          }
          int width = reader.getWidth(0);
          int height = reader.getHeight(0);
          if (width < 240 || height < 180 || (long) width * height > MAX_PIXELS) {
            throw ApiException.badRequest("Kích thước ảnh không hợp lệ.");
          }
          decoded = reader.read(0);
        } finally {
          reader.dispose();
        }
      }

      BufferedImage canvas = new BufferedImage(TARGET_WIDTH, TARGET_HEIGHT, BufferedImage.TYPE_INT_RGB);
      Graphics2D graphics = canvas.createGraphics();
      graphics.setColor(Color.WHITE);
      graphics.fillRect(0, 0, TARGET_WIDTH, TARGET_HEIGHT);
      graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      double scale = Math.min((double) TARGET_WIDTH / decoded.getWidth(), (double) TARGET_HEIGHT / decoded.getHeight());
      int width = Math.max(1, (int) Math.round(decoded.getWidth() * scale));
      int height = Math.max(1, (int) Math.round(decoded.getHeight() * scale));
      graphics.drawImage(decoded, (TARGET_WIDTH - width) / 2, (TARGET_HEIGHT - height) / 2, width, height, null);
      graphics.dispose();
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      ImageIO.write(canvas, "jpg", output);
      return output.toByteArray();
    } catch (ApiException error) {
      throw error;
    } catch (IOException error) {
      throw ApiException.badRequest("Không thể đọc ảnh tải lên.");
    }
  }
}
