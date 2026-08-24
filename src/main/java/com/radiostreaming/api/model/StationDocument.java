package com.radiostreaming.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.Map;

@Document(collection = "stations")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StationDocument {

    @Id
    @JsonProperty("_id")
    private String id;

    private String thumbnail;
    private String category;
    private Boolean live;
    private String language;

    @Field("play_mode")
    @JsonProperty("play_mode")
    private String playMode;

    private String type;
    private Map<String, Map<String, String>> translations;

    @Field("created_at")
    @JsonProperty("created_at")
    private Instant createdAt;

    @Field("has_played_all_links")
    @JsonProperty("has_played_all_links")
    private Boolean hasPlayedAllLinks;

    @Field("is_favorite")
    @JsonProperty("is_favorite")
    private Boolean isFavorite;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Boolean getLive() {
        return live;
    }

    public void setLive(Boolean live) {
        this.live = live;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getPlayMode() {
        return playMode;
    }

    public void setPlayMode(String playMode) {
        this.playMode = playMode;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, Map<String, String>> getTranslations() {
        return translations;
    }

    public void setTranslations(Map<String, Map<String, String>> translations) {
        this.translations = translations;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Boolean getHasPlayedAllLinks() {
        return hasPlayedAllLinks;
    }

    public void setHasPlayedAllLinks(Boolean hasPlayedAllLinks) {
        this.hasPlayedAllLinks = hasPlayedAllLinks;
    }

    public Boolean getIsFavorite() {
        return isFavorite;
    }

    public void setIsFavorite(Boolean isFavorite) {
        this.isFavorite = isFavorite;
    }
}
