package com.claritycam.platform.repository.catalog;

import com.claritycam.platform.model.catalog.Product;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, String> {
  List<Product> findByActiveTrueOrderByLevelCodeAscNameAsc();
}
