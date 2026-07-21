package com.claritycam.platform.finance;

import com.claritycam.platform.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/finance")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class AdminFinanceController {
  @PostMapping("/entries")
  void createEntry(@Valid @RequestBody FinanceEntryRequest request, Authentication authentication) {
    throw ApiException.badRequest("Không được nhập trực tiếp số dư. Hãy dùng luồng payment, expense, refund hoặc reversal có chứng từ.");
  }

  public record FinanceEntryRequest(
      @Size(max = 96) String bookingId,
      @NotBlank @Size(max = 40) String type,
      @NotNull @DecimalMin(value = "1") BigDecimal amount,
      @NotBlank @Size(max = 40) String method,
      @NotBlank @Size(max = 500) String note) {}
}
