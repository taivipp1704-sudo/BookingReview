package com.claritycam.platform.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.claritycam.platform.exception.ApiException;
import com.claritycam.platform.model.customer.CustomerAccount;
import com.claritycam.platform.model.customer.CustomerWaitlistEntry;
import com.claritycam.platform.repository.customer.CustomerAccountRepository;
import com.claritycam.platform.repository.customer.CustomerWaitlistRepository;
import com.claritycam.platform.service.common.ReleaseFeatureService;
import com.claritycam.platform.service.customer.CustomerWaitlistService;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CustomerWaitlistServiceTest {
  private final CustomerAccountRepository accounts = mock(CustomerAccountRepository.class);
  private final CustomerWaitlistRepository waitlist = mock(CustomerWaitlistRepository.class);
  private final CustomerWaitlistService service = new CustomerWaitlistService(accounts, waitlist);

  @Test
  void registersWithoutOtpAndStoresConsent() {
    when(waitlist.findByPhoneNormalized("0901234567")).thenReturn(Optional.empty());
    when(accounts.findByPhoneNormalized("0901234567")).thenReturn(Optional.empty());
    when(accounts.save(any(CustomerAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(waitlist.save(any(CustomerWaitlistEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

    var result = service.register("0901234567", "Nguyễn Văn A", true);

    assertThat(result.newlyCreated()).isTrue();
    assertThat(result.entry().getConsentVersion()).isEqualTo("early-access-v1");
    assertThat(result.entry().getPhoneNormalized()).isEqualTo("0901234567");
    verify(accounts).save(any(CustomerAccount.class));
    verify(waitlist).save(any(CustomerWaitlistEntry.class));
  }

  @Test
  void requiresConsent() {
    assertThatThrownBy(() -> service.register("0901234567", "Nguyễn Văn A", false))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("đồng ý");
  }

  @Test
  void bookingFlagBlocksProductionCalls() {
    assertThatThrownBy(() -> new ReleaseFeatureService(false, true).requireBookingEnabled())
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("chưa mở");
  }

  @Test
  void earlyAccessRegistrationCanBeStoppedImmediately() {
    assertThatThrownBy(() -> new ReleaseFeatureService(false, false)
        .requireEarlyAccessRegistrationEnabled())
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("tạm dừng");
  }
}
