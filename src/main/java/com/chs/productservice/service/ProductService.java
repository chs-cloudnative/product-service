package com.chs.productservice.service;

import com.chs.productservice.dto.ProductCreateRequest;
import com.chs.productservice.dto.ProductResponse;
import com.chs.productservice.dto.ProductUpdateRequest;
import com.chs.productservice.entity.Product;
import com.chs.productservice.entity.User;
import com.chs.productservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final UserService userService;
    private final ImageService imageService;

    @Transactional
    public ProductResponse createProduct(ProductCreateRequest request, String authenticatedEmail) {
        log.info("Creating product with SKU: {} for user: {}", request.getSku(), authenticatedEmail);

        if (productRepository.existsBySku(request.getSku())) {
            throw new IllegalArgumentException("Product with SKU " + request.getSku() + " already exists");
        }

        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .sku(request.getSku())
                .manufacturer(request.getManufacturer())
                .quantity(request.getQuantity())
                .owner(userService.findByEmail(authenticatedEmail))
                .build();

        Product savedProduct = productRepository.saveAndFlush(product);
        Product refreshedProduct = productRepository.findById(savedProduct.getId())
                .orElse(savedProduct);

        return mapToResponse(refreshedProduct);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductById(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with ID: " + productId));

        return mapToResponse(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getProductsByUser(String authenticatedEmail) {
        User owner = userService.findByEmail(authenticatedEmail);
        return productRepository.findByOwner(owner).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public ProductResponse updateProduct(UUID productId, ProductUpdateRequest request, String authenticatedEmail) {
        log.info("Updating product with ID: {} for user: {}", productId, authenticatedEmail);

        User authenticatedUser = userService.findByEmail(authenticatedEmail);
        Product product = productRepository.findByIdAndOwner(productId, authenticatedUser)
                .orElseThrow(() -> new IllegalArgumentException("Product not found or access denied"));

        boolean updated = false;

        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            product.setName(request.getName().trim());
            updated = true;
        }

        if (request.getDescription() != null) {
            product.setDescription(request.getDescription().trim());
            updated = true;
        }

        if (request.getSku() != null && !request.getSku().trim().isEmpty()) {
            if(!request.getSku().equals(product.getSku()) && productRepository.existsBySku(request.getSku())) {
                throw new IllegalArgumentException("Product with SKU " + request.getSku() + " already exists");
            }
            product.setSku(request.getSku().trim());
            updated = true;
        }

        if (request.getManufacturer() != null && !request.getManufacturer().trim().isEmpty()) {
            product.setManufacturer(request.getManufacturer().trim());
            updated = true;
        }

        if (request.getQuantity() != null) {
            product.setQuantity(request.getQuantity());
            updated = true;
        }

        if (!updated) {
            throw new IllegalArgumentException("No valid fields provided for update");
        }

        Product savedProduct = productRepository.saveAndFlush(product);
        Product refreshedProduct = productRepository.findById(savedProduct.getId()).orElse(savedProduct);

        log.info("Product updated successfully with ID: {}", refreshedProduct.getId());
        return mapToResponse(refreshedProduct);
    }

    @Transactional
    public void deleteProduct(UUID id, String authenticatedEmail) {
        log.info("Deleting product with ID: {}", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with id: " + id));

        User authenticatedUser = userService.findByEmail(authenticatedEmail);

        if (!product.getOwner().getId().equals(authenticatedUser.getId())) {
            throw new IllegalArgumentException("Access denied: You can only delete your own products");
        }

        // 先刪除所有相關的圖片
        imageService.deleteProductImages(id);

        // 再刪除產品
        productRepository.delete(product);

        log.info("Product deleted successfully: {}", id);
    }

    private ProductResponse mapToResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .sku(product.getSku())
                .manufacturer(product.getManufacturer())
                .quantity(product.getQuantity())
                .dateAdded(product.getDateAdded())
                .dateLastUpdated(product.getDateLastUpdated())
                .ownerUserId(product.getOwner().getId())
                .build();
    }
}
