package com.radiostreaming.api.service;

import com.radiostreaming.api.saas.dto.SaasRegisterRequest;
import com.radiostreaming.api.saas.model.SaasPendingRegistrationDocument;
import com.radiostreaming.api.saas.repository.SaasPendingRegistrationRepository;
import com.radiostreaming.api.saas.repository.SaasUsageEventRepository;
import com.radiostreaming.api.saas.repository.SaasUserRepository;
import com.radiostreaming.api.saas.service.CreditMeteringService;
import com.radiostreaming.api.saas.service.SaasAuthService;
import com.radiostreaming.api.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SaasAuthServiceTest {

    @Mock
    private SaasUserRepository userRepository;
    @Mock
    private SaasPendingRegistrationRepository pendingRepository;
    @Mock
    private SaasUsageEventRepository usageEventRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private MailDeliveryService mailDeliveryService;
    @Mock
    private CreditMeteringService creditMeteringService;

    private SaasAuthService service;

    @BeforeEach
    void setUp() {
        service = new SaasAuthService(
                userRepository,
                pendingRepository,
                usageEventRepository,
                new BCryptPasswordEncoder(),
                jwtService,
                mailDeliveryService,
                creditMeteringService);
    }

    @Test
    void beginRegistrationSendsEmailBeforeSavingPendingUser() {
        when(userRepository.existsByEmailIgnoreCase("user@example.com")).thenReturn(false);
        when(pendingRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());
        when(pendingRepository.save(any(SaasPendingRegistrationDocument.class))).thenAnswer(invocation -> {
            SaasPendingRegistrationDocument pending = invocation.getArgument(0);
            pending.setId("pending-1");
            return pending;
        });

        SaasRegisterRequest request = new SaasRegisterRequest();
        request.setEmail("user@example.com");
        request.setPassword("Password123!");
        request.setFirstName("Test");
        request.setLastName("User");

        Map<String, Object> body = service.beginRegistration(request);

        assertEquals("otp_sent", body.get("status"));
        verify(mailDeliveryService).sendPlainText(anyString(), anyString(), anyString());
        verify(pendingRepository).save(any(SaasPendingRegistrationDocument.class));
    }

    @Test
    void beginRegistrationFailsWhenEmailCannotBeSent() {
        when(userRepository.existsByEmailIgnoreCase("user@example.com")).thenReturn(false);
        when(pendingRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());
        doThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not send the verification email. Check the address and try again."))
                .when(mailDeliveryService).sendPlainText(anyString(), anyString(), anyString());

        SaasRegisterRequest request = new SaasRegisterRequest();
        request.setEmail("user@example.com");
        request.setPassword("Password123!");
        request.setFirstName("Test");
        request.setLastName("User");

        assertThrows(ResponseStatusException.class, () -> service.beginRegistration(request));
        verify(pendingRepository, never()).save(any());
    }
}
