package com.claritycam.platform.controller.finance;

import com.claritycam.platform.model.finance.Payment;
import com.claritycam.platform.exception.ApiException;
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
    throw ApiException.badRequest("KhÃƒÆ’Ã‚Â´ng Ãƒâ€žÃ¢â‚¬ËœÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Â£c nhÃƒÂ¡Ã‚ÂºÃ‚Â­p trÃƒÂ¡Ã‚Â»Ã‚Â±c tiÃƒÂ¡Ã‚ÂºÃ‚Â¿p sÃƒÂ¡Ã‚Â»Ã¢â‚¬Ëœ dÃƒâ€ Ã‚Â°. HÃƒÆ’Ã‚Â£y dÃƒÆ’Ã‚Â¹ng luÃƒÂ¡Ã‚Â»Ã¢â‚¬Å“ng payment, expense, refund hoÃƒÂ¡Ã‚ÂºÃ‚Â·c reversal cÃƒÆ’Ã‚Â³ chÃƒÂ¡Ã‚Â»Ã‚Â©ng tÃƒÂ¡Ã‚Â»Ã‚Â«.");
  }

  public record FinanceEntryRequest(
      @Size(max = 96) String bookingId,
      @NotBlank @Size(max = 40) String type,
      @NotNull @DecimalMin(value = "1") BigDecimal amount,
      @NotBlank @Size(max = 40) String method,
      @NotBlank @Size(max = 500) String note) {}
}
