package com.claritycam.platform.customer;

import com.claritycam.platform.common.ApiException;
import com.claritycam.platform.booking.Booking;
import com.claritycam.platform.booking.BookingRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
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
  public CustomerSupportController(SupportRequestRepository requests, CustomerAccountService accounts, BookingRepository bookings){this.requests=requests;this.accounts=accounts;this.bookings=bookings;}

  @GetMapping("/api/customer/support")
  List<SupportRequest> mine(HttpServletRequest request){return requests.findByPhoneNormalizedOrderByCreatedAtDesc(phone(request));}

  @PostMapping("/api/customer/support") @ResponseStatus(HttpStatus.CREATED)
  SupportRequest create(@Valid @RequestBody CreateRequest input,HttpServletRequest request){
    String phone=phone(request);
    Booking booking=bookings.findById(input.bookingId().trim()).orElseThrow(()->ApiException.notFound("Không tìm thấy đơn thuê."));
    if(!phone.equals(booking.getPhoneNormalized())) throw ApiException.forbidden("Bạn không có quyền gửi yêu cầu cho đơn này.");
    return requests.save(new SupportRequest("REQ-"+UUID.randomUUID().toString().replace("-","").substring(0,10).toUpperCase(),phone,input.bookingId().trim(),input.type(),input.message().trim()));
  }

  @GetMapping("/api/admin/support-requests") List<SupportRequest> all(){return requests.findAllByOrderByCreatedAtDesc();}
  @PatchMapping("/api/admin/support-requests/{id}") SupportRequest review(@PathVariable String id,@Valid @RequestBody ReviewRequest input){
    SupportRequest support=requests.findById(id).orElseThrow(()->ApiException.notFound("Không tìm thấy yêu cầu hỗ trợ.")); support.review(input.status(),input.note()); return requests.save(support);
  }

  private String phone(HttpServletRequest request){String value=request.getSession(false)==null?null:(String)request.getSession(false).getAttribute(CustomerAccountService.SESSION_PHONE);return accounts.require(value).getPhoneNormalized();}
  public record CreateRequest(@NotBlank @Size(max=64) String bookingId,@NotBlank @Pattern(regexp="CHANGE_REQUEST|EQUIPMENT_ISSUE|EARLY_RETURN|OTHER") String type,@NotBlank @Size(max=1000) String message){}
  public record ReviewRequest(@NotBlank @Pattern(regexp="OPEN|IN_REVIEW|RESOLVED|REJECTED") String status,@Size(max=500) String note){}
}
