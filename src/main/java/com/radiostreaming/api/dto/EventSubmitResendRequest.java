package com.radiostreaming.api.dto;

import jakarta.validation.constraints.NotBlank;

public class EventSubmitResendRequest {

    @NotBlank
    private String submissionId;

    public String getSubmissionId() {
        return submissionId;
    }

    public void setSubmissionId(String submissionId) {
        this.submissionId = submissionId;
    }
}
