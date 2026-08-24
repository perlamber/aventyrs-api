package org.aventyrs.api.image;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
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
 *
 * <p>Only PNG, JPEG, GIF, and BMP images are accepted; the format is detected from the file's
 * own bytes (magic numbers), not the client-supplied Content-Type or filename.
 */
@Service
public class ImageStorageService {

    /** Maps each supported image format to the file extension used for its storage key. */
    private static final Map<String, String> ALLOWED_IMAGE_TYPES = Map.of(
            "image/png", ".png",
            "image/jpeg", ".jpg",
            "image/gif", ".gif",
            "image/bmp", ".bmp");

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

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new ImageStorageException("Failed to read uploaded file", ex);
        }

        // Sniffed from the file's own bytes rather than the client-supplied Content-Type/filename,
        // which are trivially spoofable and would otherwise make this an unrestricted file upload.
        String contentType = detectImageType(bytes);
        String extension = ALLOWED_IMAGE_TYPES.get(contentType);
        if (extension == null) {
            throw new IllegalArgumentException("Only PNG, JPEG, GIF, and BMP images are supported");
        }

        String key = "images/" + UUID.randomUUID() + extension;

        try {
            s3Client.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                    RequestBody.fromBytes(bytes));
        } catch (S3Exception ex) {
            throw new ImageStorageException("Failed to store image in SeaweedFS", ex);
        }

        return publicBaseUrl + "/" + key;
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
