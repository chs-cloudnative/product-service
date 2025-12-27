package com.chs.productservice.dto;

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
public class ProductResponse {

    private UUID id;
    private String name;
    private String description;
    private String sku;
    private String manufacturer;
    private Integer quantity;
    private LocalDateTime dateAdded;
    private LocalDateTime dateLastUpdated;
    private UUID ownerUserId;
}
