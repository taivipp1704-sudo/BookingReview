package com.claritycam.platform.controller.store;

import com.claritycam.platform.model.store.StoreBranch;
import com.claritycam.platform.service.store.StoreBranchService;
import com.claritycam.platform.service.audit.AuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StoreBranchController {
  private final StoreBranchService service;
  private final AuditService audit;

  public StoreBranchController(StoreBranchService service, AuditService audit) {
    this.service = service;
    this.audit = audit;
  }

  @GetMapping("/api/stores")
  List<PublicBranch> publicBranches() {
    return service.publicBranches().stream()
        .map(PublicBranch::from)
        .toList();
  }

  @GetMapping("/api/admin/stores")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
  List<StoreBranch> adminBranches() {
    return service.adminBranches();
  }

  @PostMapping("/api/admin/stores")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
  StoreBranch create(@Valid @RequestBody Payload payload, Authentication authentication) {
    StoreBranch saved = service.create(payload);
    audit.record(authentication.getName(), "STORE_BRANCH_CREATED", "STORE_BRANCH", saved.getId(), saved.getCode());
    return saved;
  }

  @PatchMapping("/api/admin/stores/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
  StoreBranch update(@PathVariable String id, @Valid @RequestBody Payload payload,
      Authentication authentication) {
    StoreBranch saved = service.update(id, payload);
    audit.record(authentication.getName(), "STORE_BRANCH_UPDATED", "STORE_BRANCH", saved.getId(), saved.getCode());
    return saved;
  }

  @DeleteMapping("/api/admin/stores/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
  StoreBranch archive(@PathVariable String id, Authentication authentication) {
    StoreBranch saved = service.archive(id);
    audit.record(authentication.getName(), "STORE_BRANCH_ARCHIVED", "STORE_BRANCH", saved.getId(), saved.getCode());
    return saved;
  }

  public record Payload(
      @NotBlank @Size(max = 180) String name,
      @NotBlank @Size(max = 500) String address,
      @Size(max = 30) String phone,
      @Size(max = 500) String note,
      boolean active,
      @Min(0) @Max(9999) int sortOrder) {}

  public record PublicBranch(String id, String code, String name, String address, String phone) {
    static PublicBranch from(StoreBranch branch) {
      return new PublicBranch(branch.getId(), branch.getCode(), branch.getName(), branch.getAddress(),
          branch.getPhone());
    }
  }
}
