package com.chs.productservice.repository;

import com.chs.productservice.entity.Image;
import com.chs.productservice.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ImageRepository extends JpaRepository<Image, UUID> {
    List<Image> findByProduct(Product product);
    void deleteByProduct(Product product);
}
