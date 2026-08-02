package com.claritycam.platform.infrastructure.storage;

import com.claritycam.platform.exception.ApiException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "claritycam.identity-storage-provider",
    havingValue = "local",
    matchIfMissing = true)
public class LocalIdentityObjectStorage implements IdentityObjectStorage {
  private final Path root;

  public LocalIdentityObjectStorage(
      @Value("${claritycam.identity-storage:./private-data/identity}") String root) {
    this.root = Path.of(root).toAbsolutePath().normalize();
  }

  @Override
  public void put(String storageKey, byte[] payload) {
    try {
      Files.createDirectories(root);
      Files.write(safePath(storageKey), payload, StandardOpenOption.CREATE_NEW);
    } catch (IOException error) {
      throw new IllegalStateException("Khong the luu tai lieu xac thuc.", error);
    }
  }

  @Override
  public byte[] get(String storageKey) {
    Path path = safePath(storageKey);
    try {
      if (!Files.exists(path)) {
        throw ApiException.notFound("Anh xac thuc da het thoi han luu tru.");
      }
      return Files.readAllBytes(path);
    } catch (ApiException error) {
      throw error;
    } catch (IOException error) {
      throw new IllegalStateException("Khong the doc tai lieu xac thuc.", error);
    }
  }

  @Override
  public void delete(String storageKey) {
    try {
      Files.deleteIfExists(safePath(storageKey));
    } catch (IOException error) {
      throw new IllegalStateException("Khong the xoa tai lieu xac thuc.", error);
    }
  }

  private Path safePath(String storageKey) {
    Path resolved = root.resolve(storageKey).normalize();
    if (!resolved.startsWith(root)) {
      throw ApiException.badRequest("Tham chieu tai lieu khong hop le.");
    }
    return resolved;
  }
}
