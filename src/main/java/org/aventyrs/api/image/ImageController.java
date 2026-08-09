package org.aventyrs.api.image;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.aventyrs.api.image.dto.ImageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
}
