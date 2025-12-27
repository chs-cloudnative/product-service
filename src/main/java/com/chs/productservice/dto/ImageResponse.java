package com.chs.productservice.dto;

import com.chs.productservice.entity.Image;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageResponse {
    private UUID imageId;
    private UUID productId;
    private String fileName;
    private LocalDateTime dateCreated;
    private String s3BucketPath;

    // ImageResponse 的 from(Image image) 靜態方法是必要的，因為 Image 實體是它唯一且明確的資料來源。它是一個中間轉換層，用於將單個實體轉換成單個 DTO
    public static ImageResponse from(Image image) {
        return ImageResponse.builder()
                .imageId(image.getImageId())
                .productId(image.getProduct().getId())
                .fileName(image.getFileName())
                .dateCreated(image.getDateCreated())
                .s3BucketPath(image.getS3BucketPath())
                .build();
    }
}
