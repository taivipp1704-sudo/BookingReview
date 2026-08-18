package com.claritycam.platform.repository.booking;

import com.claritycam.platform.model.booking.CheckoutHoldReservation;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface CheckoutHoldReservationRepository extends JpaRepository<CheckoutHoldReservation, String> {
  List<CheckoutHoldReservation> findByOwnerPhoneAndExpiresAtAfterOrderByExpiresAtAsc(
      String ownerPhone, Instant now);

  Optional<CheckoutHoldReservation> findFirstByOwnerPhoneAndExpiresAtAfterOrderByExpiresAtAsc(
      String ownerPhone, Instant now);

  @Query("select distinct hold from CheckoutHoldReservation hold left join fetch hold.items "
      + "where hold.expiresAt > :now and hold.pickupTime < :to and hold.returnTime > :from")
  List<CheckoutHoldReservation> findActiveOverlapping(
      @Param("now") Instant now, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

  @Transactional
  @Modifying
  long deleteByExpiresAtLessThanEqual(Instant now);
}
