package com.claritycam.platform.promotion;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromotionRepository extends JpaRepository<Promotion, String> {
  Optional<Promotion> findByCodeIgnoreCase(String code);
  boolean existsByCodeIgnoreCase(String code);
}
