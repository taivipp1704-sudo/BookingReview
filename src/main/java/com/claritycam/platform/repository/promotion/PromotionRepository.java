package com.claritycam.platform.repository.promotion;

import com.claritycam.platform.model.promotion.Promotion;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromotionRepository extends JpaRepository<Promotion, String> {
  Optional<Promotion> findByCodeIgnoreCase(String code);
  boolean existsByCodeIgnoreCase(String code);
}
