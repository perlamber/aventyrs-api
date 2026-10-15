package org.aventyrs.api.image;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.aventyrs.api.common.NotFoundException;
import org.aventyrs.api.image.ImageStorageService.StoredImage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class ImageStorageServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2};
    private static final String NAME = "fa2f28e2-98c7-4f8f-9e96-3cf1917cfbf5.png";

    private final S3Client s3 = mock(S3Client.class);
    private final ImageStorageService service = new ImageStorageService(s3, "http://s3", "bucket");

    @Test
    void uploadReturnsARelativeApiPathNamedLikeTheKey() {
        String url = service.upload(new MockMultipartFile("file", "map.png", "image/png", PNG));

        ArgumentCaptor<PutObjectRequest> put = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(put.capture(), any(RequestBody.class));
        assertTrue(url.matches("/api/images/[0-9a-f-]{36}\\.png"), url);
        assertEquals("images/" + url.substring("/api/images/".length()), put.getValue().key());
        assertEquals("image/png", put.getValue().contentType());
    }

    @Test
    void loadReadsTheKeyBehindTheName() {
        when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(
                ResponseBytes.fromByteArray(GetObjectResponse.builder().contentType("image/png").build(), PNG));

        StoredImage image = service.load(NAME);

        ArgumentCaptor<GetObjectRequest> get = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3).getObjectAsBytes(get.capture());
        assertEquals("images/" + NAME, get.getValue().key());
        assertEquals("bucket", get.getValue().bucket());
        assertArrayEquals(PNG, image.bytes());
        assertEquals("image/png", image.contentType());
    }

    @Test
    void loadRefusesNamesUploadNeverGeneratesWithoutReachingTheBucket() {
        for (String name : new String[] {"../secret.png", "x.png", NAME.replace(".png", ".exe"),
                NAME + "/..", "", null}) {
            assertThrows(NotFoundException.class, () -> service.load(name), String.valueOf(name));
        }
        verifyNoInteractions(s3);
    }

    @Test
    void missingKeyIsNotFound() {
        when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(NoSuchKeyException.builder().build());

        assertThrows(NotFoundException.class, () -> service.load(NAME));
    }
}
