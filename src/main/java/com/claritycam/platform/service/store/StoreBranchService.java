package com.claritycam.platform.service.store;

import com.claritycam.platform.controller.store.StoreBranchController;
import com.claritycam.platform.model.store.StoreBranch;
import com.claritycam.platform.repository.store.StoreBranchRepository;
import com.claritycam.platform.exception.ApiException;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreBranchService {
  private final StoreBranchRepository branches;

  public StoreBranchService(StoreBranchRepository branches) {
    this.branches = branches;
  }

  @PostConstruct
  @Transactional
  void ensureDefaultBranch() {
    if (branches.count() > 0) return;
    branches.save(new StoreBranch(
        newId(),
        "STORE-001",
        "Chi nhánh trung tâm",
        "Địa chỉ đang cập nhật",
        "",
        "Admin cập nhật địa chỉ và thông tin liên hệ trước khi vận hành chính thức.",
        true,
        10));
  }

  public List<StoreBranch> publicBranches() {
    return branches.findByActiveTrueOrderBySortOrderAscNameAsc();
  }

  public List<StoreBranch> adminBranches() {
    return branches.findAllByOrderBySortOrderAscNameAsc();
  }

  public StoreBranch requireForBooking(String branchId) {
    if (branchId == null || branchId.isBlank()) {
      return publicBranches().stream().findFirst()
          .orElseThrow(() -> ApiException.badRequest("Hiện chưa có chi nhánh nhận máy đang hoạt động."));
    }
    return branches.findById(branchId)
        .filter(StoreBranch::isActive)
        .orElseThrow(() -> ApiException.badRequest("Chi nhánh đã chọn không còn hoạt động."));
  }

  @Transactional
  public StoreBranch create(StoreBranchController.Payload payload) {
    String id = newId();
    String code = nextCode();
    return branches.save(new StoreBranch(id, code, payload.name(), payload.address(), payload.phone(),
        payload.note(), payload.active(), payload.sortOrder()));
  }

  @Transactional
  public StoreBranch update(String id, StoreBranchController.Payload payload) {
    StoreBranch branch = branches.findById(id)
        .orElseThrow(() -> ApiException.notFound("Không tìm thấy chi nhánh."));
    branch.apply(payload.name(), payload.address(), payload.phone(), payload.note(),
        payload.active(), payload.sortOrder());
    return branches.save(branch);
  }

  @Transactional
  public StoreBranch archive(String id) {
    StoreBranch branch = branches.findById(id)
        .orElseThrow(() -> ApiException.notFound("Không tìm thấy chi nhánh."));
    branch.deactivate();
    return branches.save(branch);
  }

  private String nextCode() {
    int sequence = Math.max(1, branches.findAll().stream()
        .map(StoreBranch::getCode)
        .filter(code -> code != null && code.startsWith("STORE-"))
        .map(code -> code.substring("STORE-".length()))
        .mapToInt(value -> {
          try { return Integer.parseInt(value); }
          catch (NumberFormatException ignored) { return 0; }
        })
        .max()
        .orElse(0) + 1);
    return "STORE-%03d".formatted(sequence);
  }

  private static String newId() {
    return "BRANCH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
  }
}
