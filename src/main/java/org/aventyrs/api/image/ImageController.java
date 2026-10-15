package org.aventyrs.api.image;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import org.aventyrs.api.image.ImageStorageService.StoredImage;
import org.aventyrs.api.image.dto.ImageResponse;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/images")
@Tag(name = "Images")
public class ImageController {

    private final ImageStorageService service;

    public ImageController(ImageStorageService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageResponse> upload(@RequestParam("file") MultipartFile file) {
        String url = service.upload(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ImageResponse(url));
    }

    /**
     * Serves an uploaded image; public (see {@code SecurityConfig}), since JavaFX loads images with a
     * plain URL fetch that carries no token, and each name is an unguessable random key. A key's
     * bytes never change, so clients may cache it for good. The {@code Resource} body gives
     * {@code Range} support, which the client's disk cache uses to finish a cut-short download.
     */
    @GetMapping("/{name}")
    public ResponseEntity<ByteArrayResource> download(@PathVariable String name) {
        StoredImage image = service.load(name);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType() != null
                        ? image.contentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(new ByteArrayResource(image.bytes()));
    }
}
