package com.chs.productservice.service;

import com.chs.productservice.dto.ImageResponse;
import com.chs.productservice.entity.Image;
import com.chs.productservice.entity.Product;
import com.chs.productservice.entity.User;
import com.chs.productservice.repository.ImageRepository;
import com.chs.productservice.repository.ProductRepository;
import com.chs.productservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageService {

    private final ImageRepository imageRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final S3Service s3Service;

    // 支援的圖片格式
    private static final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList(
            "image/jpeg",
            "image/jpg",
            "image/png"
    );

    /**
     * 上傳圖片到產品
     */
    @Transactional
    public ImageResponse uploadImage(UUID productId, MultipartFile file, String userEmail) {
        log.info("Uploading image for product: {}, by user: {}", productId, userEmail);

        // 1. 驗證檔案類型
        validateImageFile(file);

        // 2. 驗證產品存在
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with id: " + productId));

        // 3. 驗證使用者是產品擁有者
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));

        if (!product.getOwner().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Access denied: You can only upload images to your own products");
        }

        // 4. 上傳到 S3
        String s3Key = s3Service.uploadFile(file, user.getId(), productId);

        // 5. 儲存 metadata 到資料庫
        Image image = Image.builder()
                .product(product)
                .fileName(file.getOriginalFilename())
                .s3BucketPath(s3Key)
                .build();

        image = imageRepository.save(image);

        log.info("Image uploaded successfully: imageId={}, s3Key={}", image.getImageId(), s3Key);

        return ImageResponse.from(image);
    }

    /**
     * 取得產品的所有圖片
     */
    @Transactional(readOnly = true)
    public List<ImageResponse> getProductImages(UUID productId) {
        log.info("Getting images for product: {}", productId);

        // 驗證產品存在
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with id: " + productId));

        List<Image> images = imageRepository.findByProduct(product);

        return images.stream()
                .map(ImageResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 取得單一圖片資訊
     */
    @Transactional(readOnly = true)
    public ImageResponse getImageById(UUID productId, UUID imageId) {
        log.info("Getting image: imageId={}, productId={}", imageId, productId);

        Image image = imageRepository.findById(imageId)
                .orElseThrow(() -> new IllegalArgumentException("Image not found with id: " + imageId));

        // 驗證圖片屬於指定的產品
        if (!image.getProduct().getId().equals(productId)) {
            throw new IllegalArgumentException("Image does not belong to the specified product");
        }

        return ImageResponse.from(image);
    }

    /**
     * 刪除圖片
     */
    @Transactional
    public void deleteImage(UUID productId, UUID imageId, String userEmail) {
        log.info("Deleting image: imageId={}, productId={}, user={}", imageId, productId, userEmail);

        // 1. 查找圖片
        Image image = imageRepository.findById(imageId)
                .orElseThrow(() -> new IllegalArgumentException("Image not found with id: " + imageId));

        // 2. 驗證圖片屬於指定的產品
        if (!image.getProduct().getId().equals(productId)) {
            throw new IllegalArgumentException("Image does not belong to the specified product");
        }

        // 3. 驗證使用者是產品擁有者
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));

        if (!image.getProduct().getOwner().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Access denied: You can only delete images from your own products");
        }

        // 4. 從 S3 刪除檔案
        try {
            s3Service.deleteFile(image.getS3BucketPath());
        } catch (Exception e) {
            log.error("Failed to delete file from S3: {}", image.getS3BucketPath(), e);
            // 即使 S3 刪除失敗，仍然刪除資料庫記錄
        }

        // 5. 從資料庫刪除記錄
        imageRepository.delete(image);

        log.info("Image deleted successfully: imageId={}", imageId);
    }

    /**
     * 刪除產品的所有圖片（當產品被刪除時呼叫）
     */
    @Transactional
    public void deleteProductImages(UUID productId) {
        log.info("Deleting all images for product: {}", productId);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with id: " + productId));

        List<Image> images = imageRepository.findByProduct(product);

        // 刪除所有 S3 檔案
        for (Image image : images) {
            try {
                s3Service.deleteFile(image.getS3BucketPath());
            } catch (Exception e) {
                log.error("Failed to delete file from S3: {}", image.getS3BucketPath(), e);
            }
        }

        // 刪除所有資料庫記錄
        imageRepository.deleteByProduct(product);

        log.info("Deleted {} images for product: {}", images.size(), productId);
    }

    /**
     * 驗證圖片檔案
     */
    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }

        // 驗證檔案類型
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException(
                    "Invalid file type. Only JPEG, JPG, and PNG files are allowed. Received: " + contentType
            );
        }

        // 驗證檔案大小（Spring 已經在 application.properties 中設定了 5MB 限制）
        log.debug("File validation passed: name={}, type={}, size={}",
                    file.getOriginalFilename(), contentType, file.getSize());
    }
}
