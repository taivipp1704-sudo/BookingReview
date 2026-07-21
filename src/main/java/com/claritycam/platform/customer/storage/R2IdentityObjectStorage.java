package com.claritycam.platform.customer.storage;

import com.claritycam.platform.common.ApiException;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
@ConditionalOnProperty(name = "claritycam.identity-storage-provider", havingValue = "r2")
public class R2IdentityObjectStorage implements IdentityObjectStorage {
  private final S3Client client;
  private final String bucket;

  public R2IdentityObjectStorage(
      @Value("${claritycam.r2.endpoint}") String endpoint,
      @Value("${claritycam.r2.bucket}") String bucket,
      @Value("${claritycam.r2.access-key-id}") String accessKeyId,
      @Value("${claritycam.r2.secret-access-key}") String secretAccessKey) {
    this.bucket = bucket;
    this.client = S3Client.builder()
        .endpointOverride(URI.create(endpoint))
        .region(Region.of("auto"))
        .credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
        .build();
  }

  @Override
  public void put(String storageKey, byte[] payload) {
    client.putObject(
        PutObjectRequest.builder()
            .bucket(bucket)
            .key(storageKey)
            .contentType("application/octet-stream")
            .build(),
        RequestBody.fromBytes(payload));
  }

  @Override
  public byte[] get(String storageKey) {
    try {
      ResponseBytes<?> response = client.getObjectAsBytes(
          GetObjectRequest.builder().bucket(bucket).key(storageKey).build());
      return response.asByteArray();
    } catch (NoSuchKeyException error) {
      throw ApiException.notFound("Anh xac thuc da het thoi han luu tru.");
    }
  }

  @Override
  public void delete(String storageKey) {
    client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(storageKey).build());
  }
}
