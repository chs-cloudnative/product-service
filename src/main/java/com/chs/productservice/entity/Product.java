package com.chs.productservice.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "products")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotBlank(message = "Product name is required")
    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @NotBlank(message = "SKU is required")
    @Column(unique = true, nullable = false)
    private String sku;

    @NotBlank(message = "Manufacturer is required")
    @Column(nullable = false)
    private String manufacturer;

    @NotNull(message = "Quantity is required")
    @Min(value = 0, message = "Quantity cannot be less than 0")
    @Column(nullable = false)
    private Integer quantity;

    @CreationTimestamp
    @Column(name = "date_added", nullable = false, updatable = false)
    private LocalDateTime dateAdded;

    @UpdateTimestamp
    @Column(name = "date_last_updated", nullable = false)
    private LocalDateTime dateLastUpdated;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User owner;
}
