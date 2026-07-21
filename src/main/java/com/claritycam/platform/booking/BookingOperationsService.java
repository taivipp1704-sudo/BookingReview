package com.claritycam.platform.booking;

import com.claritycam.platform.catalog.Product;
import com.claritycam.platform.catalog.ProductRepository;
import com.claritycam.platform.common.ApiException;
import com.claritycam.platform.inventory.InventoryAsset;
import com.claritycam.platform.inventory.InventoryAssetRepository;
import com.claritycam.platform.inventory.InventoryLedgerService;
import com.claritycam.platform.inventory.StockItem;
import com.claritycam.platform.inventory.StockItemRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingOperationsService {
  private static final List<AllocationState> ACTIVE_ALLOCATIONS =
      List.of(AllocationState.ALLOCATED, AllocationState.IN_USE);

  private final BookingReservationRepository reservations;
  private final BookingAllocationRepository allocations;
  private final ProductRepository products;
  private final InventoryAssetRepository assets;
  private final StockItemRepository stock;
  private final InventoryLedgerService ledger;

  public BookingOperationsService(BookingReservationRepository reservations,
      BookingAllocationRepository allocations, ProductRepository products,
      InventoryAssetRepository assets, StockItemRepository stock, InventoryLedgerService ledger) {
    this.reservations = reservations;
    this.allocations = allocations;
    this.products = products;
    this.assets = assets;
    this.stock = stock;
    this.ledger = ledger;
  }

  @Transactional
  public List<BookingReservation> replaceReservations(Booking booking, ReservationType type, String actor) {
    releaseReservations(booking.getId(), ReservationState.RELEASED);
    LocalDateTime expiresAt = type == ReservationType.SOFT ? booking.getHoldExpiresAt() : null;
    List<BookingReservation> created = booking.getItems().stream()
        .map(item -> new BookingReservation(booking.getId(), item.getProductId(), item.getQuantity(), type,
            booking.getPickupTime(), booking.getReturnTime(), expiresAt, actor))
        .toList();
    return reservations.saveAll(created);
  }

  @Transactional
  public void releaseReservations(String bookingId, ReservationState state) {
    List<BookingReservation> active = reservations.findByBookingIdAndState(bookingId, ReservationState.ACTIVE);
    active.forEach(item -> item.release(state));
    reservations.saveAll(active);
  }

  @Transactional
  public int activeReservedQuantity(String productId, LocalDateTime from, LocalDateTime to,
      String excludedBookingId) {
    expireSoftReservations();
    return reservations.findByProductIdAndState(productId, ReservationState.ACTIVE).stream()
        .filter(item -> excludedBookingId == null || !excludedBookingId.equals(item.getBookingId()))
        .filter(item -> item.overlaps(from, to))
        .mapToInt(BookingReservation::getQuantity)
        .sum();
  }

  public boolean hasActiveReservation(String bookingId) {
    return reservations.existsByBookingIdAndState(bookingId, ReservationState.ACTIVE);
  }

  public OperationsSnapshot snapshot(String bookingId) {
    return new OperationsSnapshot(
        reservations.findByBookingIdOrderByCreatedAtAsc(bookingId),
        allocations.findByBookingIdOrderByCreatedAtAsc(bookingId));
  }

  @Transactional
  public void ensureMigrationBaseline(Booking booking) {
    if (!List.of(BookingState.COMPLETED, BookingState.REJECTED).contains(booking.getState())
        && !hasActiveReservation(booking.getId())) {
      ReservationType type = List.of(BookingState.CONFIRMED, BookingState.READY_FOR_PICKUP,
          BookingState.IN_USE, BookingState.INCIDENT).contains(booking.getState())
          ? ReservationType.HARD : ReservationType.SOFT;
      replaceReservations(booking, type, "SYSTEM_MIGRATION");
    }
    if (booking.getState() == BookingState.IN_USE
        && allocations.findByBookingIdOrderByCreatedAtAsc(booking.getId()).isEmpty()) {
      List<BookingAllocation> baseline = booking.getItems().stream().map(line -> {
        BookingAllocation allocation = new BookingAllocation(booking.getId(), line.getProductId(),
            line.getSerialId(), line.getQuantity(), AllocationRole.PRIMARY, "SYSTEM_MIGRATION");
        allocation.changeState(AllocationState.IN_USE);
        return allocation;
      }).toList();
      allocations.saveAll(baseline);
    }
  }

  @Transactional
  public List<BookingAllocation> autoAllocate(Booking booking, String actor) {
    List<BookingAllocation> existing = allocations.findByBookingIdAndStateIn(booking.getId(), ACTIVE_ALLOCATIONS);
    if (!existing.isEmpty()) return existing;

    Map<String, Integer> requested = new LinkedHashMap<>();
    booking.getItems().forEach(item -> requested.merge(item.getProductId(), item.getQuantity(), Integer::sum));
    List<BookingAllocation> created = new ArrayList<>();
    for (Map.Entry<String, Integer> request : requested.entrySet()) {
      Product product = products.findById(request.getKey())
          .orElseThrow(() -> ApiException.notFound("Không tìm thấy thiết bị cần phân bổ."));
      if ("SERIALIZED".equals(product.getTrackingMode())) {
        List<InventoryAsset> candidates = assets.findByProductIdAndStatusForUpdate(product.getId(), "AVAILABLE")
            .stream()
            .filter(asset -> !allocations.existsBySerialIdAndStateIn(asset.getSerialId(), ACTIVE_ALLOCATIONS))
            .limit(request.getValue())
            .toList();
        if (candidates.size() < request.getValue()) {
          throw ApiException.badRequest("Không đủ serial sẵn sàng để phân bổ cho " + product.getName() + ".");
        }
        candidates.forEach(asset -> created.add(new BookingAllocation(booking.getId(), product.getId(),
            asset.getSerialId(), 1, AllocationRole.PRIMARY, actor)));
      } else {
        StockItem item = stock.findByIdForUpdate(product.getId())
            .orElseThrow(() -> ApiException.badRequest("Chưa có số dư kho cho " + product.getName() + "."));
        if (item.getAvailableQty() < request.getValue()) {
          throw ApiException.badRequest("Không đủ tồn khả dụng để phân bổ cho " + product.getName() + ".");
        }
        created.add(new BookingAllocation(booking.getId(), product.getId(), null, request.getValue(),
            AllocationRole.PRIMARY, actor));
      }
    }
    return allocations.saveAll(created);
  }

  @Transactional
  public List<BookingAllocation> startUse(Booking booking, String actor) {
    List<BookingAllocation> active = autoAllocate(booking, actor);
    String documentId = "BOOKING-" + booking.getId() + "-CHECKOUT";
    for (BookingAllocation allocation : active) {
      if (allocation.getState() == AllocationState.IN_USE) continue;
      if (allocation.getSerialId() != null) {
        InventoryAsset asset = assets.findById(allocation.getSerialId())
            .orElseThrow(() -> ApiException.notFound("Serial phân bổ không còn tồn tại."));
        asset.updateStatus("IN_USE");
        assets.save(asset);
        ledger.append(documentId, allocation.getProductId(), allocation.getSerialId(), "CHECKOUT", -1, null,
            "Bàn giao theo booking " + booking.getId(), actor);
      } else {
        StockItem item = stock.findByIdForUpdate(allocation.getProductId())
            .orElseThrow(() -> ApiException.notFound("Không tìm thấy số dư kho."));
        item.adjustInUse(allocation.getQuantity());
        stock.save(item);
        ledger.append(documentId, allocation.getProductId(), null, "CHECKOUT", -allocation.getQuantity(),
            item.getAvailableQty(), "Bàn giao theo booking " + booking.getId(), actor);
      }
      allocation.changeState(AllocationState.IN_USE);
    }
    return allocations.saveAll(active);
  }

  @Transactional
  public void completeUse(Booking booking, String actor) {
    List<BookingAllocation> active = allocations.findByBookingIdAndStateIn(booking.getId(), ACTIVE_ALLOCATIONS);
    String documentId = "BOOKING-" + booking.getId() + "-RETURN";
    for (BookingAllocation allocation : active) {
      if (allocation.getState() != AllocationState.IN_USE) {
        allocation.changeState(AllocationState.RELEASED);
        continue;
      }
      if (allocation.getSerialId() != null) {
        assets.findById(allocation.getSerialId()).ifPresent(asset -> {
          if ("IN_USE".equals(asset.getStatus())) {
            asset.updateStatus("AVAILABLE");
            assets.save(asset);
          }
        });
        ledger.append(documentId, allocation.getProductId(), allocation.getSerialId(), "RETURN", 1, null,
            "Hoàn trả theo booking " + booking.getId(), actor);
      } else if (allocation.getState() == AllocationState.IN_USE) {
        stock.findByIdForUpdate(allocation.getProductId()).ifPresent(item -> {
          item.adjustInUse(-allocation.getQuantity());
          stock.save(item);
          ledger.append(documentId, allocation.getProductId(), null, "RETURN", allocation.getQuantity(),
              item.getAvailableQty(), "Hoàn trả theo booking " + booking.getId(), actor);
        });
      }
      allocation.changeState(AllocationState.RELEASED);
    }
    allocations.saveAll(active);
    releaseReservations(booking.getId(), ReservationState.RELEASED);
  }

  @Transactional
  public void cancel(Booking booking) {
    releaseReservations(booking.getId(), ReservationState.RELEASED);
    List<BookingAllocation> active = allocations.findByBookingIdAndStateIn(booking.getId(), ACTIVE_ALLOCATIONS);
    active.stream().filter(item -> item.getState() == AllocationState.ALLOCATED)
        .forEach(item -> item.changeState(AllocationState.RELEASED));
    allocations.saveAll(active);
  }

  private void expireSoftReservations() {
    List<BookingReservation> expired = reservations.findByStateAndExpiresAtBefore(
        ReservationState.ACTIVE, LocalDateTime.now()).stream()
        .filter(item -> item.getExpiresAt() != null)
        .toList();
    expired.forEach(item -> item.release(ReservationState.EXPIRED));
    reservations.saveAll(expired);
  }

  public record OperationsSnapshot(List<BookingReservation> reservations,
                                   List<BookingAllocation> allocations) {}
}
