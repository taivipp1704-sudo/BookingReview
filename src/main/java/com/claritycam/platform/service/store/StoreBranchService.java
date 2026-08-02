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
        "Chi nhÃƒÆ’Ã‚Â¡nh trung tÃƒÆ’Ã‚Â¢m",
        "Ãƒâ€žÃ‚ÂÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¹a chÃƒÂ¡Ã‚Â»Ã¢â‚¬Â° Ãƒâ€žÃ¢â‚¬Ëœang cÃƒÂ¡Ã‚ÂºÃ‚Â­p nhÃƒÂ¡Ã‚ÂºÃ‚Â­t",
        "",
        "Admin cÃƒÂ¡Ã‚ÂºÃ‚Â­p nhÃƒÂ¡Ã‚ÂºÃ‚Â­t Ãƒâ€žÃ¢â‚¬ËœÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¹a chÃƒÂ¡Ã‚Â»Ã¢â‚¬Â° vÃƒÆ’Ã‚Â  thÃƒÆ’Ã‚Â´ng tin liÃƒÆ’Ã‚Âªn hÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡ trÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºc khi vÃƒÂ¡Ã‚ÂºÃ‚Â­n hÃƒÆ’Ã‚Â nh chÃƒÆ’Ã‚Â­nh thÃƒÂ¡Ã‚Â»Ã‚Â©c.",
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
          .orElseThrow(() -> ApiException.badRequest("HiÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡n chÃƒâ€ Ã‚Â°a cÃƒÆ’Ã‚Â³ chi nhÃƒÆ’Ã‚Â¡nh nhÃƒÂ¡Ã‚ÂºÃ‚Â­n mÃƒÆ’Ã‚Â¡y Ãƒâ€žÃ¢â‚¬Ëœang hoÃƒÂ¡Ã‚ÂºÃ‚Â¡t Ãƒâ€žÃ¢â‚¬ËœÃƒÂ¡Ã‚Â»Ã¢â€žÂ¢ng."));
    }
    return branches.findById(branchId)
        .filter(StoreBranch::isActive)
        .orElseThrow(() -> ApiException.badRequest("Chi nhÃƒÆ’Ã‚Â¡nh Ãƒâ€žÃ¢â‚¬ËœÃƒÆ’Ã‚Â£ chÃƒÂ¡Ã‚Â»Ã‚Ân khÃƒÆ’Ã‚Â´ng cÃƒÆ’Ã‚Â²n hoÃƒÂ¡Ã‚ÂºÃ‚Â¡t Ãƒâ€žÃ¢â‚¬ËœÃƒÂ¡Ã‚Â»Ã¢â€žÂ¢ng."));
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
        .orElseThrow(() -> ApiException.notFound("KhÃƒÆ’Ã‚Â´ng tÃƒÆ’Ã‚Â¬m thÃƒÂ¡Ã‚ÂºÃ‚Â¥y chi nhÃƒÆ’Ã‚Â¡nh."));
    branch.apply(payload.name(), payload.address(), payload.phone(), payload.note(),
        payload.active(), payload.sortOrder());
    return branches.save(branch);
  }

  @Transactional
  public StoreBranch archive(String id) {
    StoreBranch branch = branches.findById(id)
        .orElseThrow(() -> ApiException.notFound("KhÃƒÆ’Ã‚Â´ng tÃƒÆ’Ã‚Â¬m thÃƒÂ¡Ã‚ÂºÃ‚Â¥y chi nhÃƒÆ’Ã‚Â¡nh."));
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
