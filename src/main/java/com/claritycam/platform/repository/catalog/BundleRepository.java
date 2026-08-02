package com.claritycam.platform.repository.catalog;

import com.claritycam.platform.model.catalog.RentalBundle;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface BundleRepository extends JpaRepository<RentalBundle, String> {
  @Query("select distinct bundle from RentalBundle bundle left join fetch bundle.items where bundle.active = true order by bundle.name asc")
  List<RentalBundle> findByActiveTrueOrderByNameAsc();
  @Query("select distinct bundle from RentalBundle bundle left join fetch bundle.items order by bundle.name asc")
  List<RentalBundle> findAllWithItems();
  @Query("select distinct bundle from RentalBundle bundle left join fetch bundle.items where bundle.id = :id")
  Optional<RentalBundle> findByIdWithItems(@Param("id") String id);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select distinct bundle from RentalBundle bundle left join fetch bundle.items where bundle.id = :id")
  Optional<RentalBundle> findByIdWithItemsForUpdate(@Param("id") String id);
}
