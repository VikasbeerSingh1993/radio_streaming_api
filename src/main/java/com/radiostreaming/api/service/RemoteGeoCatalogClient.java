package com.radiostreaming.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class RemoteGeoCatalogClient implements GeoCatalogClient {

    private static final Logger log = LoggerFactory.getLogger(RemoteGeoCatalogClient.class);

    private final CredentialService credentialService;
    private final RestClient restCountries = RestClient.builder()
            .baseUrl("https://restcountries.com")
            .defaultHeader("User-Agent", "radio-streaming-api/1.0 (country-catalog)")
            .defaultHeader("Accept", "application/json")
            .build();
    private final RestClient countryStateCity = RestClient.builder()
            .baseUrl("https://api.countrystatecity.in")
            .defaultHeader("User-Agent", "radio-streaming-api/1.0 (country-catalog)")
            .defaultHeader("Accept", "application/json")
            .build();
    private final RestClient countriesNow = RestClient.builder()
            .baseUrl("https://countriesnow.space")
            .defaultHeader("User-Agent", "radio-streaming-api/1.0 (country-catalog)")
            .defaultHeader("Accept", "application/json")
            .build();

    public RemoteGeoCatalogClient(CredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @Override
    public List<Map<String, String>> fetchCountries() {
        String apiKey = credentialService.geoApiKey();
        if (!apiKey.isBlank()) {
            List<Map<String, String>> fromKey = fetchCscCountries(apiKey);
            if (!fromKey.isEmpty()) {
                return fromKey;
            }
        }
        return fetchRestCountries();
    }

    @Override
    public List<Map<String, String>> fetchStates(String countryCode, String countryName) {
        String apiKey = credentialService.geoApiKey();
        if (!apiKey.isBlank()) {
            List<Map<String, String>> fromKey = fetchCscStates(countryCode, apiKey);
            if (!fromKey.isEmpty()) {
                return fromKey;
            }
        }
        return fetchCountriesNowStates(countryName);
    }

    @Override
    public List<Map<String, String>> fetchCities(
            String countryCode,
            String countryName,
            String stateName,
            String stateCode) {
        String apiKey = credentialService.geoApiKey();
        if (!apiKey.isBlank() && stateCode != null && !stateCode.isBlank()) {
            List<Map<String, String>> fromKey = fetchCscCities(countryCode, stateCode, stateName, apiKey);
            if (!fromKey.isEmpty()) {
                return fromKey;
            }
        }
        return fetchCountriesNowCities(countryName, stateName);
    }

    private List<Map<String, String>> fetchCscCountries(String apiKey) {
        try {
            List<Map<String, Object>> rows = countryStateCity.get()
                    .uri("/v1/countries")
                    .header("X-CSCAPI-KEY", apiKey)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (rows == null) {
                return List.of();
            }
            List<Map<String, String>> countries = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                String code = stringOf(row.get("iso2")).toUpperCase(Locale.ROOT);
                String name = stringOf(row.get("name"));
                if (code.length() != 2 || name.isBlank()) {
                    continue;
                }
                countries.add(entry(code, name));
            }
            return countries;
        } catch (Exception ex) {
            log.warn("CountryStateCity countries lookup failed; using fallback geo service", ex);
            return List.of();
        }
    }

    private List<Map<String, String>> fetchCscStates(String countryCode, String apiKey) {
        try {
            List<Map<String, Object>> rows = countryStateCity.get()
                    .uri("/v1/countries/{code}/states", countryCode)
                    .header("X-CSCAPI-KEY", apiKey)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (rows == null) {
                return List.of();
            }
            List<Map<String, String>> states = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                String name = stringOf(row.get("name"));
                if (name.isBlank()) {
                    continue;
                }
                Map<String, String> state = new LinkedHashMap<>();
                state.put("code", stringOf(row.get("iso2")).toUpperCase(Locale.ROOT));
                state.put("name", name);
                state.put("countryCode", countryCode.toUpperCase(Locale.ROOT));
                states.add(state);
            }
            return states;
        } catch (Exception ex) {
            log.warn("CountryStateCity states lookup failed for {}", countryCode, ex);
            return List.of();
        }
    }

    private List<Map<String, String>> fetchCscCities(
            String countryCode,
            String stateCode,
            String stateName,
            String apiKey) {
        try {
            List<Map<String, Object>> rows = countryStateCity.get()
                    .uri("/v1/countries/{code}/states/{state}/cities", countryCode, stateCode)
                    .header("X-CSCAPI-KEY", apiKey)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (rows == null) {
                return List.of();
            }
            List<Map<String, String>> cities = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                String name = stringOf(row.get("name"));
                if (name.isBlank()) {
                    continue;
                }
                Map<String, String> city = new LinkedHashMap<>();
                city.put("name", name);
                city.put("state", stateName);
                city.put("countryCode", countryCode.toUpperCase(Locale.ROOT));
                cities.add(city);
            }
            return cities;
        } catch (Exception ex) {
            log.warn("CountryStateCity cities lookup failed for {} / {}", countryCode, stateCode, ex);
            return List.of();
        }
    }

    private List<Map<String, String>> fetchRestCountries() {
        try {
            List<Map<String, Object>> rows = restCountries.get()
                    .uri(uri -> uri.path("/v3.1/all").queryParam("fields", "cca2,name").build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (rows == null || rows.isEmpty()) {
                return List.of();
            }
            List<Map<String, String>> countries = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                String code = stringOf(row.get("cca2")).toUpperCase(Locale.ROOT);
                String name = commonName(row.get("name"));
                if (code.length() != 2 || name.isBlank()) {
                    continue;
                }
                countries.add(entry(code, name));
            }
            return countries;
        } catch (Exception ex) {
            log.warn("REST Countries lookup failed", ex);
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> fetchCountriesNowStates(String countryName) {
        if (countryName == null || countryName.isBlank()) {
            return List.of();
        }
        try {
            Map<String, Object> body = countriesNow.post()
                    .uri("/api/v0.1/countries/states")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("country", countryName))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            Object data = body == null ? null : body.get("data");
            Map<String, Object> payload = data instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
            Object statesObj = payload.get("states");
            if (!(statesObj instanceof List<?> rows)) {
                return List.of();
            }
            List<Map<String, String>> states = new ArrayList<>();
            for (Object rowObj : rows) {
                if (!(rowObj instanceof Map<?, ?> row)) {
                    continue;
                }
                String name = stringOf(row.get("name"));
                if (name.isBlank()) {
                    continue;
                }
                Map<String, String> state = new LinkedHashMap<>();
                state.put("code", stringOf(row.get("state_code")).toUpperCase(Locale.ROOT));
                state.put("name", name);
                states.add(state);
            }
            return states;
        } catch (Exception ex) {
            log.warn("CountriesNow states lookup failed for {}", countryName, ex);
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> fetchCountriesNowCities(String countryName, String stateName) {
        if (countryName == null || countryName.isBlank() || stateName == null || stateName.isBlank()) {
            return List.of();
        }
        try {
            Map<String, Object> body = countriesNow.post()
                    .uri("/api/v0.1/countries/state/cities")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("country", countryName, "state", stateName))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            Object data = body == null ? null : body.get("data");
            if (!(data instanceof List<?> rows)) {
                return List.of();
            }
            List<Map<String, String>> cities = new ArrayList<>();
            for (Object row : rows) {
                String name = stringOf(row);
                if (name.isBlank()) {
                    continue;
                }
                Map<String, String> city = new LinkedHashMap<>();
                city.put("name", name);
                city.put("state", stateName);
                cities.add(city);
            }
            return cities;
        } catch (Exception ex) {
            log.warn("CountriesNow cities lookup failed for {} / {}", countryName, stateName, ex);
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static String commonName(Object name) {
        if (name instanceof Map<?, ?> map) {
            return stringOf(((Map<String, Object>) map).get("common"));
        }
        return stringOf(name);
    }

    private static Map<String, String> entry(String code, String name) {
        Map<String, String> country = new LinkedHashMap<>();
        country.put("code", code);
        country.put("name", name);
        return country;
    }

    private static String stringOf(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
