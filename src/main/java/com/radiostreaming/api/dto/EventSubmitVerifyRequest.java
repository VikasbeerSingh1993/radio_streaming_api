package com.radiostreaming.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class EventSubmitVerifyRequest {

    @NotBlank
    private String submissionId;

    @NotBlank
    @Size(min = 4, max = 8)
    private String otp;

    public String getSubmissionId() {
        return submissionId;
    }

    public void setSubmissionId(String submissionId) {
        this.submissionId = submissionId;
    }

    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }
}
