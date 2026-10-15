package org.aventyrs.api.image;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
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
 *
 * <p>Only PNG, JPEG, GIF, and BMP images are accepted; the format is detected from the file's
 * own bytes (magic numbers), not the client-supplied Content-Type or filename.
 */
@Service
public class ImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(ImageStorageService.class);

    /** Maps each supported image format to the file extension used for its storage key. */
    private static final Map<String, String> ALLOWED_IMAGE_TYPES = Map.of(
            "image/png", ".png",
            "image/jpeg", ".jpg",
            "image/gif", ".gif",
            "image/bmp", ".bmp");

    private final S3Client s3Client;
    private final String s3Endpoint;
    private final String bucket;
    private final String publicBaseUrl;

    public ImageStorageService(
            @Value("${seaweedfs.s3.endpoint}") String s3Endpoint,
            @Value("${seaweedfs.s3.access-key}") String accessKey,
            @Value("${seaweedfs.s3.secret-key}") String secretKey,
            @Value("${seaweedfs.s3.bucket}") String bucket,
            @Value("${seaweedfs.filer-url}") String filerUrl) {
        this.s3Endpoint = s3Endpoint;
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
                log.error("Can't reach bucket '{}' at {} (S3 status {})", bucket, s3Endpoint, ex.statusCode(), ex);
                throw ex;
            }
            log.warn("Bucket '{}' missing at {}; creating it", bucket, s3Endpoint);
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        }
    }

    public String upload(MultipartFile file) {
        if (file.isEmpty()) {
            log.warn("Rejected image upload: empty file '{}'", file.getOriginalFilename());
            throw new IllegalArgumentException("File must not be empty");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new ImageStorageException("Failed to read uploaded file", ex);
        }

        // Sniffed from the file's own bytes rather than the client-supplied Content-Type/filename,
        // which are trivially spoofable and would otherwise make this an unrestricted file upload.
        String contentType = detectImageType(bytes);
        // Map.of rejects a null key, so an unrecognised file must not reach get().
        String extension = contentType == null ? null : ALLOWED_IMAGE_TYPES.get(contentType);
        if (extension == null) {
            log.warn("Rejected image upload '{}': {} bytes, client content type {}, starts with {}",
                    file.getOriginalFilename(), bytes.length, file.getContentType(),
                    HexFormat.of().formatHex(bytes, 0, Math.min(bytes.length, 8)));
            throw new IllegalArgumentException("Only PNG, JPEG, GIF, and BMP images are supported");
        }

        String key = "images/" + UUID.randomUUID() + extension;

        try {
            s3Client.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                    RequestBody.fromBytes(bytes));
        } catch (S3Exception ex) {
            log.error("putObject {} to bucket '{}' at {} failed (S3 status {})",
                    key, bucket, s3Endpoint, ex.statusCode(), ex);
            throw new ImageStorageException("Failed to store image in SeaweedFS", ex);
        } catch (SdkClientException ex) {
            // No HTTP answer at all: SeaweedFS down, wrong endpoint, connection refused...
            log.error("putObject {} to bucket '{}' at {} failed: {}", key, bucket, s3Endpoint, ex.getMessage(), ex);
            throw new ImageStorageException("Failed to reach SeaweedFS", ex);
        }

        String url = publicBaseUrl + "/" + key;
        log.debug("Stored image {} ({}, {} bytes) -> {}", key, contentType, bytes.length, url);
        return url;
    }

    /** Returns the detected {@code image/*} content type, or {@code null} if none of the supported formats match. */
    private static String detectImageType(byte[] bytes) {
        if (bytes.length >= 8
                && bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47
                && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A) {
            return "image/png";
        }
        if (bytes.length >= 3
                && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF) {
            return "image/jpeg";
        }
        if (bytes.length >= 6
                && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == '8'
                && (bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a') {
            return "image/gif";
        }
        if (bytes.length >= 2 && bytes[0] == 'B' && bytes[1] == 'M') {
            return "image/bmp";
        }
        return null;
    }
}
