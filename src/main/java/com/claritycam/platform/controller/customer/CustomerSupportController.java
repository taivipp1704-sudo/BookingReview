package com.claritycam.platform.controller.customer;

import com.claritycam.platform.model.customer.SupportRequest;
import com.claritycam.platform.repository.booking.BookingRepository;
import com.claritycam.platform.repository.customer.SupportRequestRepository;
import com.claritycam.platform.service.customer.CustomerAccountService;
import com.claritycam.platform.exception.ApiException;
import com.claritycam.platform.model.booking.Booking;
import com.claritycam.platform.service.common.ClientAddressResolver;
import com.claritycam.platform.service.common.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CustomerSupportController {
  private final SupportRequestRepository requests;
  private final CustomerAccountService accounts;
  private final BookingRepository bookings;
  private final RateLimitService rateLimit;
  private final ClientAddressResolver clientAddressResolver;
  public CustomerSupportController(
      SupportRequestRepository requests,
      CustomerAccountService accounts,
      BookingRepository bookings,
      RateLimitService rateLimit,
      ClientAddressResolver clientAddressResolver) {
    this.requests=requests;
    this.accounts=accounts;
    this.bookings=bookings;
    this.rateLimit=rateLimit;
    this.clientAddressResolver=clientAddressResolver;
  }

  @GetMapping("/api/customer/support")
  List<SupportRequest> mine(HttpServletRequest request){return requests.findByPhoneNormalizedOrderByCreatedAtDesc(phone(request));}

  @PostMapping("/api/customer/support") @ResponseStatus(HttpStatus.CREATED)
  SupportRequest create(@Valid @RequestBody CreateRequest input,HttpServletRequest request){
    String phone=phone(request);
    rateLimit.check("support:phone:" + phone, 8, Duration.ofHours(1));
    rateLimit.check("support:ip:" + clientAddressResolver.resolve(request), 20, Duration.ofHours(1));
    Booking booking=bookings.findById(input.bookingId().trim()).orElseThrow(()->ApiException.notFound("KhÃƒÆ’Ã‚Â´ng tÃƒÆ’Ã‚Â¬m thÃƒÂ¡Ã‚ÂºÃ‚Â¥y Ãƒâ€žÃ¢â‚¬ËœÃƒâ€ Ã‚Â¡n thuÃƒÆ’Ã‚Âª."));
    if(!phone.equals(booking.getPhoneNormalized())) throw ApiException.forbidden("BÃƒÂ¡Ã‚ÂºÃ‚Â¡n khÃƒÆ’Ã‚Â´ng cÃƒÆ’Ã‚Â³ quyÃƒÂ¡Ã‚Â»Ã‚Ân gÃƒÂ¡Ã‚Â»Ã‚Â­i yÃƒÆ’Ã‚Âªu cÃƒÂ¡Ã‚ÂºÃ‚Â§u cho Ãƒâ€žÃ¢â‚¬ËœÃƒâ€ Ã‚Â¡n nÃƒÆ’Ã‚Â y.");
    return requests.save(new SupportRequest("REQ-"+UUID.randomUUID().toString().replace("-","").substring(0,10).toUpperCase(),phone,input.bookingId().trim(),input.type(),input.message().trim()));
  }

  @GetMapping("/api/admin/support-requests") List<SupportRequest> all(){return requests.findAllByOrderByCreatedAtDesc();}
  @PatchMapping("/api/admin/support-requests/{id}") SupportRequest review(@PathVariable String id,@Valid @RequestBody ReviewRequest input){
    SupportRequest support=requests.findById(id).orElseThrow(()->ApiException.notFound("KhÃƒÆ’Ã‚Â´ng tÃƒÆ’Ã‚Â¬m thÃƒÂ¡Ã‚ÂºÃ‚Â¥y yÃƒÆ’Ã‚Âªu cÃƒÂ¡Ã‚ÂºÃ‚Â§u hÃƒÂ¡Ã‚Â»Ã¢â‚¬â€ trÃƒÂ¡Ã‚Â»Ã‚Â£.")); support.review(input.status(),input.note()); return requests.save(support);
  }

  private String phone(HttpServletRequest request){String value=request.getSession(false)==null?null:(String)request.getSession(false).getAttribute(CustomerAccountService.SESSION_PHONE);return accounts.require(value).getPhoneNormalized();}
  public record CreateRequest(@NotBlank @Size(max=64) String bookingId,@NotBlank @Pattern(regexp="CHANGE_REQUEST|EQUIPMENT_ISSUE|EARLY_RETURN|OTHER") String type,@NotBlank @Size(max=1000) String message){}
  public record ReviewRequest(@NotBlank @Pattern(regexp="OPEN|IN_REVIEW|RESOLVED|REJECTED") String status,@Size(max=500) String note){}
}
