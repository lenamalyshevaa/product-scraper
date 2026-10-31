package com.example.priceparser.repository;

import java.util.List;

import com.example.priceparser.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findAllByOrderByParsedAtDesc();

    // все обходы одной ссылки, от старых к новым - это история цены
    List<Product> findByUrlOrderByParsedAtAsc(String url);

    @Query("select distinct p.url from Product p")
    List<String> findAllUrls();
}
