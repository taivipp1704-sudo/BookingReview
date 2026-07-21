package com.claritycam.platform.customer.storage;

public interface IdentityObjectStorage {
  void put(String storageKey, byte[] payload);

  byte[] get(String storageKey);

  void delete(String storageKey);
}
