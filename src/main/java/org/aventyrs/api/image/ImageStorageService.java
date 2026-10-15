package org.aventyrs.api.image;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.HexFormat;
import java.util.UUID;
import org.aventyrs.api.common.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Stores and reads images through SeaweedFS's S3 gateway (authenticated with {@code AWS_ACCESS_KEY_ID}/
 * {@code AWS_SECRET_ACCESS_KEY}). The filer's own HTTP API stays private — it accepts writes and
 * deletes with no auth — so images are served back by {@link ImageController} on the API's port.
 * Callers get, and documents store, the relative path {@code /api/images/<uuid>.<ext>}; the client
 * resolves it against its API base URL, so stored URLs survive a host or port change.
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

    /** Path prefix of every URL {@link #upload} returns; {@link ImageController} serves it. */
    static final String URL_PREFIX = "/api/images/";

    /** The only names {@link #load} reads: the keys {@link #upload} generates, nothing else in the bucket. */
    private static final Pattern STORED_NAME = Pattern.compile("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}\\.(png|jpg|gif|bmp)");

    private static final String KEY_PREFIX = "images/";

    private final S3Client s3Client;
    private final String s3Endpoint;
    private final String bucket;

    @Autowired
    public ImageStorageService(
            @Value("${seaweedfs.s3.endpoint}") String s3Endpoint,
            @Value("${seaweedfs.s3.access-key}") String accessKey,
            @Value("${seaweedfs.s3.secret-key}") String secretKey,
            @Value("${seaweedfs.s3.bucket}") String bucket) {
        this(S3Client.builder()
                .endpointOverride(URI.create(s3Endpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(true)
                .build(), s3Endpoint, bucket);
    }

    ImageStorageService(S3Client s3Client, String s3Endpoint, String bucket) {
        this.s3Client = s3Client;
        this.s3Endpoint = s3Endpoint;
        this.bucket = bucket;
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

        String name = UUID.randomUUID() + extension;
        String key = KEY_PREFIX + name;

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

        String url = URL_PREFIX + name;
        log.debug("Stored image {} ({}, {} bytes) -> {}", key, contentType, bytes.length, url);
        return url;
    }

    /**
     * The image {@link #upload} stored under {@code name} (the last segment of its URL). Any other
     * name is not ours to serve, and reads as not found rather than reaching the bucket.
     */
    public StoredImage load(String name) {
        if (name == null || !STORED_NAME.matcher(name).matches()) {
            throw new NotFoundException("Image not found");
        }
        String key = KEY_PREFIX + name;
        try {
            ResponseBytes<GetObjectResponse> object =
                    s3Client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build());
            return new StoredImage(object.asByteArray(), object.response().contentType());
        } catch (NoSuchKeyException ex) {
            throw new NotFoundException("Image not found");
        } catch (S3Exception ex) {
            log.error("getObject {} from bucket '{}' at {} failed (S3 status {})",
                    key, bucket, s3Endpoint, ex.statusCode(), ex);
            throw new ImageStorageException("Failed to read image from SeaweedFS", ex);
        } catch (SdkClientException ex) {
            log.error("getObject {} from bucket '{}' at {} failed: {}", key, bucket, s3Endpoint, ex.getMessage(), ex);
            throw new ImageStorageException("Failed to reach SeaweedFS", ex);
        }
    }

    /** A stored image's bytes and the content type it was uploaded with. */
    public record StoredImage(byte[] bytes, String contentType) {
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
