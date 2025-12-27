package com.chs.productservice.controller;

import com.chs.productservice.dto.ImageResponse;
import com.chs.productservice.service.ImageService;
import com.timgroup.statsd.StatsDClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/product/{productId}/image")
@RequiredArgsConstructor
@Slf4j
public class ImageController {

    private final ImageService imageService;
    private final StatsDClient statsDClient;

    @PostMapping
    public ResponseEntity<ImageResponse> uploadImage(
            @PathVariable UUID productId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        long startTime = System.currentTimeMillis();

        try {
            statsDClient.incrementCounter("api.image.post.count");
            log.info("POST /v1/product/{}/image - Uploading image", productId);

            String authenticatedEmail = authentication.getName();
            ImageResponse response = imageService.uploadImage(productId, file, authenticatedEmail);

            long duration = System.currentTimeMillis() - startTime;
            statsDClient.recordExecutionTime("api.image.post.time", duration);
            log.info("POST /v1/product/{}/image - Image uploaded - {}ms", productId, duration);

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            statsDClient.incrementCounter("api.image.post.error");
            long duration = System.currentTimeMillis() - startTime;
            log.error("POST /v1/product/{}/image - Error: {} - {}ms", productId, e.getMessage(), duration, e);
            throw e;
        }
    }

    @GetMapping
    public ResponseEntity<List<ImageResponse>> getProductImages(@PathVariable UUID productId) {
        long startTime = System.currentTimeMillis();

        try {
            statsDClient.incrementCounter("api.image.getall.count");
            log.info("GET /v1/product/{}/image - Getting images", productId);

            List<ImageResponse> images = imageService.getProductImages(productId);

            long duration = System.currentTimeMillis() - startTime;
            statsDClient.recordExecutionTime("api.image.getall.time", duration);
            log.info("GET /v1/product/{}/image - Retrieved {} images - {}ms", productId, images.size(), duration);

            return ResponseEntity.ok(images);

        } catch (Exception e) {
            statsDClient.incrementCounter("api.image.getall.error");
            long duration = System.currentTimeMillis() - startTime;
            log.error("GET /v1/product/{}/image - Error: {} - {}ms", productId, e.getMessage(), duration, e);
            throw e;
        }
    }

    @GetMapping("/{imageId}")
    public ResponseEntity<ImageResponse> getImageById(
            @PathVariable UUID productId,
            @PathVariable UUID imageId) {
        long startTime = System.currentTimeMillis();

        try {
            statsDClient.incrementCounter("api.image.get.count");
            log.info("GET /v1/product/{}/image/{} - Getting image", productId, imageId);

            ImageResponse response = imageService.getImageById(productId, imageId);

            long duration = System.currentTimeMillis() - startTime;
            statsDClient.recordExecutionTime("api.image.get.time", duration);
            log.info("GET /v1/product/{}/image/{} - Image retrieved - {}ms", productId, imageId, duration);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            statsDClient.incrementCounter("api.image.get.error");
            long duration = System.currentTimeMillis() - startTime;
            log.error("GET /v1/product/{}/image/{} - Error: {} - {}ms", productId, imageId, e.getMessage(), duration, e);
            throw e;
        }
    }

    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> deleteImage(
            @PathVariable UUID productId,
            @PathVariable UUID imageId,
            Authentication authentication) {
        long startTime = System.currentTimeMillis();

        try {
            statsDClient.incrementCounter("api.image.delete.count");
            log.info("DELETE /v1/product/{}/image/{} - Deleting image", productId, imageId);

            String authenticatedEmail = authentication.getName();
            imageService.deleteImage(productId, imageId, authenticatedEmail);

            long duration = System.currentTimeMillis() - startTime;
            statsDClient.recordExecutionTime("api.image.delete.time", duration);
            log.info("DELETE /v1/product/{}/image/{} - Image deleted - {}ms", productId, imageId, duration);

            return ResponseEntity.noContent().build();

        } catch (Exception e) {
            statsDClient.incrementCounter("api.image.delete.error");
            long duration = System.currentTimeMillis() - startTime;
            log.error("DELETE /v1/product/{}/image/{} - Error: {} - {}ms", productId, imageId, e.getMessage(), duration, e);
            throw e;
        }
    }
}
