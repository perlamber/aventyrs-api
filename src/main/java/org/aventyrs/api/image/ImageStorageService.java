package org.aventyrs.api.image;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Stores images through SeaweedFS's S3 gateway (authenticated with {@code AWS_ACCESS_KEY_ID}/
 * {@code AWS_SECRET_ACCESS_KEY}) rather than the Filer HTTP API directly, so writes require the
 * configured credentials. SeaweedFS backs each S3 bucket with a filer directory at
 * {@code /buckets/<bucket>/...}, and the filer serves that path back over plain HTTP with no
 * auth of its own — so the URL handed to callers points at the filer, not the S3 endpoint.
 */
@Service
public class ImageStorageService {

    private final S3Client s3Client;
    private final String bucket;
    private final String publicBaseUrl;

    public ImageStorageService(
            @Value("${seaweedfs.s3.endpoint}") String s3Endpoint,
            @Value("${seaweedfs.s3.access-key}") String accessKey,
            @Value("${seaweedfs.s3.secret-key}") String secretKey,
            @Value("${seaweedfs.s3.bucket}") String bucket,
            @Value("${seaweedfs.filer-url}") String filerUrl) {
        this.bucket = bucket;
        this.publicBaseUrl = filerUrl + "/buckets/" + bucket;
        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(s3Endpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(true)
                .build();
    }

    @PostConstruct
    void ensureBucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (S3Exception ex) {
            if (ex.statusCode() != 404) {
                throw ex;
            }
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        }
    }

    public String upload(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Only image uploads are supported");
        }

        String key = "images/" + UUID.randomUUID() + extensionOf(file.getOriginalFilename());

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new ImageStorageException("Failed to read uploaded file", ex);
        }

        try {
            s3Client.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                    RequestBody.fromBytes(bytes));
        } catch (S3Exception ex) {
            throw new ImageStorageException("Failed to store image in SeaweedFS", ex);
        }

        return publicBaseUrl + "/" + key;
    }

    private String extensionOf(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dot = originalFilename.lastIndexOf('.');
        return dot >= 0 ? originalFilename.substring(dot) : "";
    }
}
