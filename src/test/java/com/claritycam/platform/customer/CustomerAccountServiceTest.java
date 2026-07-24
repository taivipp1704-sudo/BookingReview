package com.claritycam.platform.customer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.claritycam.platform.booking.BookingRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerAccountServiceTest {
  @Mock private CustomerAccountRepository accounts;
  @Mock private BookingRepository bookings;
  @InjectMocks private CustomerAccountService service;

  @Test
  void loginMarksLegacyCustomerWithBookingAsOnboarded() {
    CustomerAccount account = new CustomerAccount("CUS-LEGACY", "0901234567", "Khách cũ");
    when(accounts.findByPhoneNormalized("0901234567")).thenReturn(Optional.of(account));
    when(bookings.existsByPhoneNormalized("0901234567")).thenReturn(true);
    when(accounts.save(any(CustomerAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

    CustomerAccount loggedIn = service.login("0901234567", "Khách cũ");

    assertEquals(1, loggedIn.getOnboardingVersion());
  }

  @Test
  void loginKeepsCustomerWithoutBookingOnFirstOnboarding() {
    CustomerAccount account = new CustomerAccount("CUS-NEW", "0907654321", "Khách mới");
    when(accounts.findByPhoneNormalized("0907654321")).thenReturn(Optional.of(account));
    when(bookings.existsByPhoneNormalized("0907654321")).thenReturn(false);
    when(accounts.save(any(CustomerAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

    CustomerAccount loggedIn = service.login("0907654321", "Khách mới");

    assertEquals(0, loggedIn.getOnboardingVersion());
  }
}
