package com.chs.productservice.dto;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductUpdateRequest {

    private String name;
    private String description;
    private String sku;
    private String manufacturer;

    @Min(value = 0, message = "Quantity cannot be less than 0")
    private Integer quantity;
}
