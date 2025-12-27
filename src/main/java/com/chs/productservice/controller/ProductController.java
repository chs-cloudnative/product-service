package com.chs.productservice.controller;

import com.chs.productservice.dto.ProductCreateRequest;
import com.chs.productservice.dto.ProductResponse;
import com.chs.productservice.dto.ProductUpdateRequest;
import com.chs.productservice.service.ProductService;
import com.timgroup.statsd.StatsDClient;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/product")
@RequiredArgsConstructor
@Slf4j
public class ProductController {

    private final ProductService productService;
    private final StatsDClient statsDClient;

    @PostMapping
    public ResponseEntity<?> createProduct(@Valid @RequestBody ProductCreateRequest request, Authentication authentication) {
        long startTime = System.currentTimeMillis();

        try {
            statsDClient.incrementCounter("api.product.post.count");
            log.info("POST /v1/product - Creating product with SKU: {}", request.getSku());

            String authenticatedEmail = authentication.getName();
            ProductResponse productResponse = productService.createProduct(request, authenticatedEmail);

            long duration = System.currentTimeMillis() - startTime;
            statsDClient.recordExecutionTime("api.product.post.time", duration);
            log.info("POST /v1/product - Product created - {}ms", duration);

            return ResponseEntity.status(HttpStatus.CREATED).body(productResponse);

        } catch (Exception e) {
            statsDClient.incrementCounter("api.product.post.error");
            long duration = System.currentTimeMillis() - startTime;
            log.error("POST /v1/product - Error: {} - {}ms", e.getMessage(), duration, e);
            throw e;
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getProductById(@PathVariable UUID id) {
        long startTime = System.currentTimeMillis();

        try {
            statsDClient.incrementCounter("api.product.get.count");
            log.info("GET /v1/product/{} - Getting product", id);

            ProductResponse productResponse = productService.getProductById(id);

            long duration = System.currentTimeMillis() - startTime;
            statsDClient.recordExecutionTime("api.product.get.time", duration);
            log.info("GET /v1/product/{} - Product retrieved - {}ms", id, duration);

            return ResponseEntity.ok(productResponse);

        } catch (Exception e) {
            statsDClient.incrementCounter("api.product.get.error");
            long duration = System.currentTimeMillis() - startTime;
            log.error("GET /v1/product/{} - Error: {} - {}ms", id, e.getMessage(), duration, e);
            throw e;
        }
    }

    @GetMapping
    public ResponseEntity<?> getAllProducts() {
        long startTime = System.currentTimeMillis();

        try {
            statsDClient.incrementCounter("api.product.getall.count");
            log.info("GET /v1/product - Getting all products");

            List<ProductResponse> products = productService.getAllProducts();

            long duration = System.currentTimeMillis() - startTime;
            statsDClient.recordExecutionTime("api.product.getall.time", duration);
            log.info("GET /v1/product - Retrieved {} products - {}ms", products.size(), duration);

            return ResponseEntity.ok(products);

        } catch (Exception e) {
            statsDClient.incrementCounter("api.product.getall.error");
            long duration = System.currentTimeMillis() - startTime;
            log.error("GET /v1/product - Error: {} - {}ms", e.getMessage(), duration, e);
            throw e;
        }
    }

    @GetMapping("/user")
    public ResponseEntity<?> getUserProducts(Authentication authentication) {
        long startTime = System.currentTimeMillis();

        try {
            statsDClient.incrementCounter("api.product.user.count");
            log.info("GET /v1/product/user - Getting user products");

            String authenticatedEmail = authentication.getName();
            List<ProductResponse> products = productService.getProductsByUser(authenticatedEmail);

            long duration = System.currentTimeMillis() - startTime;
            statsDClient.recordExecutionTime("api.product.user.time", duration);
            log.info("GET /v1/product/user - Retrieved {} products - {}ms", products.size(), duration);

            return ResponseEntity.ok(products);

        } catch (Exception e) {
            statsDClient.incrementCounter("api.product.user.error");
            long duration = System.currentTimeMillis() - startTime;
            log.error("GET /v1/product/user - Error: {} - {}ms", e.getMessage(), duration, e);
            throw e;
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateProduct(@PathVariable UUID id, @Valid @RequestBody ProductUpdateRequest request, Authentication authentication) {
        long startTime = System.currentTimeMillis();

        try {
            statsDClient.incrementCounter("api.product.put.count");
            log.info("PUT /v1/product/{} - Updating product", id);

            String authenticatedEmail = authentication.getName();
            ProductResponse productResponse = productService.updateProduct(id, request, authenticatedEmail);

            long duration = System.currentTimeMillis() - startTime;
            statsDClient.recordExecutionTime("api.product.put.time", duration);
            log.info("PUT /v1/product/{} - Product updated - {}ms", id, duration);

            return ResponseEntity.ok(productResponse);

        } catch (Exception e) {
            statsDClient.incrementCounter("api.product.put.error");
            long duration = System.currentTimeMillis() - startTime;
            log.error("PUT /v1/product/{} - Error: {} - {}ms", id, e.getMessage(), duration, e);
            throw e;
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProduct(@PathVariable UUID id, Authentication authentication) {
        long startTime = System.currentTimeMillis();

        try {
            statsDClient.incrementCounter("api.product.delete.count");
            log.info("DELETE /v1/product/{} - Deleting product", id);

            String authenticatedEmail = authentication.getName();
            productService.deleteProduct(id, authenticatedEmail);

            long duration = System.currentTimeMillis() - startTime;
            statsDClient.recordExecutionTime("api.product.delete.time", duration);
            log.info("DELETE /v1/product/{} - Product deleted - {}ms", id, duration);

            return ResponseEntity.noContent().build();

        } catch (Exception e) {
            statsDClient.incrementCounter("api.product.delete.error");
            long duration = System.currentTimeMillis() - startTime;
            log.error("DELETE /v1/product/{} - Error: {} - {}ms", id, e.getMessage(), duration, e);
            throw e;
        }
    }
}
