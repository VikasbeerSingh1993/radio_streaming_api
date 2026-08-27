package com.radiostreaming.api.service;

import com.radiostreaming.api.dto.EventSubmitRequest;
import com.radiostreaming.api.model.EventDocument;
import com.radiostreaming.api.model.EventSubmissionDocument;
import com.radiostreaming.api.repository.EventSubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventSubmissionServiceTest {

    @Mock
    private EventSubmissionRepository repository;
    @Mock
    private AdminCatalogService catalogService;
    @Mock
    private MailDeliveryService mailDeliveryService;

    private EventSubmissionService service;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        service = new EventSubmissionService(
                repository, catalogService, encoder, mailDeliveryService);
    }

    private void stubRepositorySave() {
        when(repository.save(any(EventSubmissionDocument.class))).thenAnswer(invocation -> {
            EventSubmissionDocument doc = invocation.getArgument(0);
            if (doc.getId() == null) {
                doc.setId("sub-1");
            }
            return doc;
        });
    }

    @Test
    void startStoresHashedOtpAndMasksEmail() {
        stubRepositorySave();
        EventSubmitRequest request = baseRequest();
        var body = service.start(request);

        assertEquals("sub-1", body.get("submissionId"));
        assertEquals("a***n@example.com", body.get("email"));
        ArgumentCaptor<EventSubmissionDocument> captor = ArgumentCaptor.forClass(EventSubmissionDocument.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        EventSubmissionDocument saved = captor.getValue();
        assertTrue(saved.getOtpHash() != null && saved.getOtpHash().startsWith("$2"));
        verify(catalogService, never()).submitVerifiedEvent(any());
    }

    @Test
    void verifyCreatesPendingEventWhenOtpMatches() {
        EventSubmissionDocument submission = openSubmission("654321");
        when(repository.findById("sub-1")).thenReturn(Optional.of(submission));
        EventDocument created = new EventDocument();
        created.setId("evt-1");
        created.setApprovalStatus("pending");
        when(catalogService.submitVerifiedEvent(any())).thenReturn(created);

        EventDocument saved = service.verify("sub-1", "654321");

        assertEquals("evt-1", saved.getId());
        verify(catalogService).submitVerifiedEvent(any());
        verify(repository).deleteById("sub-1");
    }

    @Test
    void verifyRejectsWrongOtp() {
        EventSubmissionDocument submission = openSubmission("654321");
        when(repository.findById("sub-1")).thenReturn(Optional.of(submission));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.verify("sub-1", "000000"));
        assertTrue(ex.getReason().contains("Incorrect code"));
        verify(catalogService, never()).submitVerifiedEvent(any());
    }

    @Test
    void maskEmailHidesLocalPart() {
        assertEquals("a***n@example.com", EventSubmissionService.maskEmail("aman@example.com"));
        assertEquals("j***@x.com", EventSubmissionService.maskEmail("jo@x.com"));
    }

    private EventSubmitRequest baseRequest() {
        EventSubmitRequest request = new EventSubmitRequest();
        request.setTitle("Sangat");
        request.setDate(Instant.parse("2026-09-01T10:00:00Z"));
        request.setCity("Amritsar");
        request.setSubmitterUsername("aman");
        request.setSubmitterEmail("aman@example.com");
        return request;
    }

    private EventSubmissionDocument openSubmission(String otp) {
        EventSubmissionDocument submission = new EventSubmissionDocument();
        submission.setId("sub-1");
        submission.setTitle("Sangat");
        submission.setDate(Instant.parse("2026-09-01T10:00:00Z"));
        submission.setCity("Amritsar");
        submission.setSubmitterUsername("aman");
        submission.setSubmitterName("aman");
        submission.setSubmitterEmail("aman@example.com");
        submission.setOtpHash(encoder.encode(otp));
        submission.setOtpExpiresAt(Instant.now().plusSeconds(300));
        submission.setExpiresAt(Instant.now().plusSeconds(1800));
        submission.setSendCount(1);
        return submission;
    }
}
