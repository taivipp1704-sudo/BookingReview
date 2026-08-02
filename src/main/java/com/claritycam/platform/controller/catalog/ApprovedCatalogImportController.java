package com.claritycam.platform.controller.catalog;

import com.claritycam.platform.service.catalog.ApprovedCatalogImportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/imports/amy-catalog")
@PreAuthorize("hasRole('ADMIN')")
public class ApprovedCatalogImportController {
  private final ApprovedCatalogImportService imports;

  public ApprovedCatalogImportController(ApprovedCatalogImportService imports) {
    this.imports = imports;
  }

  @GetMapping("/preview")
  ApprovedCatalogImportService.ImportPreview preview() {
    return imports.preview();
  }

  @PostMapping
  ApprovedCatalogImportService.ImportResult apply(@Valid @RequestBody ImportRequest request) {
    return imports.apply(request.confirmation());
  }

  public record ImportRequest(@NotBlank String confirmation) {}
}
