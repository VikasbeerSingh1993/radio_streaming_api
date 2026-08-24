package com.radiostreaming.api.service;

import java.util.List;
import java.util.Map;

public interface GeoCatalogClient {

    List<Map<String, String>> fetchCountries();

    List<Map<String, String>> fetchStates(String countryCode, String countryName);

    List<Map<String, String>> fetchCities(
            String countryCode,
            String countryName,
            String stateName,
            String stateCode);
}
