package com.example.priceparser.repository;

import java.util.List;

import com.example.priceparser.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findAllByOrderByParsedAtDesc();

    @Query("select distinct p.url from Product p")
    List<String> findAllUrls();
}
