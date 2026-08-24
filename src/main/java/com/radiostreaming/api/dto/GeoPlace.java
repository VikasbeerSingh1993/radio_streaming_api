package com.radiostreaming.api.dto;

public class GeoPlace {

    private String name;
    private String state;
    private String country;
    private String countryCode;
    private String address;
    private Double latitude;
    private Double longitude;

    public GeoPlace() {
    }

    public GeoPlace(String name, String state, String country, String countryCode) {
        this.name = name;
        this.state = state;
        this.country = country;
        this.countryCode = countryCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
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

    public String getLabel() {
        StringBuilder label = new StringBuilder();
        if (address != null && !address.isBlank()) {
            return address;
        }
        if (name != null && !name.isBlank()) {
            label.append(name);
        }
        if (state != null && !state.isBlank()) {
            if (!label.isEmpty()) {
                label.append(", ");
            }
            label.append(state);
        }
        return label.toString();
    }
}
