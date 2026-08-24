package com.radiostreaming.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Document(collection = "events")
@JsonInclude(JsonInclude.Include.NON_NULL)
@CompoundIndexes({
        @CompoundIndex(name = "events_org_date_idx", def = "{'organization': 1, 'date': 1}"),
        @CompoundIndex(name = "events_category_status_idx", def = "{'category': 1, 'approvalStatus': 1}"),
        @CompoundIndex(name = "events_created_by_idx", def = "{'createdBy': 1, 'date': 1}")
})
public class EventDocument {

    @Id
    @JsonProperty("_id")
    private String id;

    @TextIndexed
    private String title;
    private Instant date;

    @Field("end_date")
    @JsonProperty("end_date")
    private Instant endDate;

    @Indexed
    @TextIndexed
    private String city;
    @TextIndexed
    private String description;
    private String address;
    private Double latitude;
    private Double longitude;
    private String status;

    @Indexed
    @TextIndexed
    @Field("organizedBy")
    @JsonProperty("organizedBy")
    private String organizedBy;

    @Indexed
    @TextIndexed
    private String organization;

    @Indexed
    private String category;

    @Indexed
    private String createdBy;

    /** pending | approved | rejected. Missing/blank is treated as approved (legacy rows). */
    @Field("approvalStatus")
    @JsonProperty("approvalStatus")
    private String approvalStatus;

    private String submitterName;
    private String submitterUsername;
    private String submitterEmail;
    private String submitterPhone;
    private boolean emailVerified;
    private Instant submittedAt;
    private Instant reviewedAt;
    private String reviewNote;
    private Double distanceKm;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Instant getDate() {
        return date;
    }

    public void setDate(Instant date) {
        this.date = date;
    }

    public Instant getEndDate() {
        return endDate;
    }

    public void setEndDate(Instant endDate) {
        this.endDate = endDate;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOrganizedBy() {
        return organizedBy;
    }

    public void setOrganizedBy(String organizedBy) {
        this.organizedBy = organizedBy;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public String getSubmitterName() {
        return submitterName;
    }

    public void setSubmitterName(String submitterName) {
        this.submitterName = submitterName;
    }

    public String getSubmitterUsername() {
        return submitterUsername;
    }

    public void setSubmitterUsername(String submitterUsername) {
        this.submitterUsername = submitterUsername;
    }

    public String getSubmitterEmail() {
        return submitterEmail;
    }

    public void setSubmitterEmail(String submitterEmail) {
        this.submitterEmail = submitterEmail;
    }

    public String getSubmitterPhone() {
        return submitterPhone;
    }

    public void setSubmitterPhone(String submitterPhone) {
        this.submitterPhone = submitterPhone;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public void setReviewNote(String reviewNote) {
        this.reviewNote = reviewNote;
    }

    public Double getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(Double distanceKm) {
        this.distanceKm = distanceKm;
    }

    @JsonIgnore
    public boolean isListedPublicly() {
        return approvalStatus == null
                || approvalStatus.isBlank()
                || "approved".equalsIgnoreCase(approvalStatus);
    }
}
