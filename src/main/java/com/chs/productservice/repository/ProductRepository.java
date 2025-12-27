package com.chs.productservice.repository;

import com.chs.productservice.entity.Product;
import com.chs.productservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    List<Product> findByOwner(User owner);
    Optional<Product> findByIdAndOwner(UUID id, User owner);
    boolean existsBySku(String sku);
}
