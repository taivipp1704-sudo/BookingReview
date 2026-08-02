package com.claritycam.platform.customer;

import com.claritycam.platform.model.booking.Booking;
import com.claritycam.platform.model.customer.CustomerAccount;
import com.claritycam.platform.repository.booking.BookingRepository;
import com.claritycam.platform.repository.customer.CustomerAccountRepository;
import com.claritycam.platform.service.customer.CustomerAccountService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class CustomerAccountServiceTest {
  @Mock private CustomerAccountRepository accounts;
  @Mock private BookingRepository bookings;
  @Mock private PasswordEncoder passwordEncoder;
  @InjectMocks private CustomerAccountService service;

  @Test
  void loginMarksLegacyCustomerWithBookingAsOnboarded() {
    CustomerAccount account = new CustomerAccount("CUS-LEGACY", "0901234567", "Khách cũ");
    account.setPasswordHash("encoded-password");
    when(accounts.findByPhoneNormalized("0901234567")).thenReturn(Optional.of(account));
    when(bookings.existsByPhoneNormalized("0901234567")).thenReturn(true);
    when(accounts.save(any(CustomerAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(passwordEncoder.matches("password-123", "encoded-password")).thenReturn(true);

    CustomerAccount loggedIn = service.login("0901234567", "password-123");

    assertEquals(1, loggedIn.getOnboardingVersion());
  }

  @Test
  void loginKeepsCustomerWithoutBookingOnFirstOnboarding() {
    CustomerAccount account = new CustomerAccount("CUS-NEW", "0907654321", "Khách mới");
    account.setPasswordHash("encoded-password");
    when(accounts.findByPhoneNormalized("0907654321")).thenReturn(Optional.of(account));
    when(bookings.existsByPhoneNormalized("0907654321")).thenReturn(false);
    when(accounts.save(any(CustomerAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(passwordEncoder.matches("password-456", "encoded-password")).thenReturn(true);

    CustomerAccount loggedIn = service.login("0907654321", "password-456");

    assertEquals(0, loggedIn.getOnboardingVersion());
  }

  @Test
  void registrationStoresOnlyTheEncodedPassword() {
    when(accounts.findByPhoneNormalized("0908888888")).thenReturn(Optional.empty());
    when(passwordEncoder.encode("strong-password")).thenReturn("bcrypt-password-hash");
    when(accounts.save(any(CustomerAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

    CustomerAccount registered = service.register("0908888888", "Khách đăng ký", "strong-password");

    assertEquals("bcrypt-password-hash", registered.getPasswordHash());
    assertNotEquals("strong-password", registered.getPasswordHash());
    assertTrue(registered.getId().startsWith("CUS-"));
  }
}
