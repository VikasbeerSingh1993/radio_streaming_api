package com.radiostreaming.api.service;

import com.radiostreaming.api.dto.GeoPlace;
import com.radiostreaming.api.model.CityDocument;
import com.radiostreaming.api.model.CountryDocument;
import com.radiostreaming.api.model.StateDocument;
import com.radiostreaming.api.repository.CityRepository;
import com.radiostreaming.api.repository.CountryRepository;
import com.radiostreaming.api.repository.StateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class GeoService {

    private static final Logger log = LoggerFactory.getLogger(GeoService.class);
    private static final List<Map<String, String>> FALLBACK_COUNTRIES = buildCountries();
    private static final List<GeoPlace> FALLBACK_CITIES = buildCities();
    private static final String CONTACT_EMAIL = "vikasbeersingh@gmail.com";
    private static final String USER_AGENT = "RadioStreamingApp/1.0 (" + CONTACT_EMAIL + ")";

    private final CountryRepository countryRepository;
    private final StateRepository stateRepository;
    private final CityRepository cityRepository;
    private final GeoCatalogClient geoCatalogClient;
    private final CredentialService credentialService;
    private final RestClient nominatim = geoHttp("https://nominatim.openstreetmap.org");
    private final RestClient photon = geoHttp("https://photon.komoot.io");
    private final RestClient locationIq = geoHttp("https://api.locationiq.com");
    private final RestClient geoapify = geoHttp("https://api.geoapify.com");

    public GeoService(
            CountryRepository countryRepository,
            StateRepository stateRepository,
            CityRepository cityRepository,
            GeoCatalogClient geoCatalogClient,
            CredentialService credentialService) {
        this.countryRepository = countryRepository;
        this.stateRepository = stateRepository;
        this.cityRepository = cityRepository;
        this.geoCatalogClient = geoCatalogClient;
        this.credentialService = credentialService;
    }

    private static RestClient geoHttp(String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(4));
        factory.setReadTimeout(Duration.ofSeconds(6));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("User-Agent", USER_AGENT)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Accept-Language", "en")
                .build();
    }

    public List<Map<String, String>> countries() {
        List<Map<String, String>> stored = loadCountriesFromDatabase();
        if (!stored.isEmpty()) {
            return stored;
        }
        List<Map<String, String>> fromGeo = orderedCountries(geoCatalogClient.fetchCountries());
        if (!fromGeo.isEmpty()) {
            persistCountries(fromGeo);
            return fromGeo;
        }
        log.warn("Using built-in country list because database and geo service were unavailable");
        return FALLBACK_COUNTRIES;
    }

    public List<Map<String, String>> states(String countryCode) {
        String code = normalizeCode(countryCode);
        if (code.isBlank()) {
            return List.of();
        }
        List<Map<String, String>> stored = loadStatesFromDatabase(code);
        if (!stored.isEmpty()) {
            return stored;
        }
        List<Map<String, String>> fromGeo = orderedByName(geoCatalogClient.fetchStates(code, countryName(code)));
        if (!fromGeo.isEmpty()) {
            persistStates(code, fromGeo);
            return fromGeo;
        }
        return fallbackStates(code);
    }

    public List<GeoPlace> cities(String countryCode, String state, String query) {
        String code = normalizeCode(countryCode);
        String stateName = trim(state);
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (code.isBlank() || stateName.isBlank()) {
            return List.of();
        }
        List<GeoPlace> stored = loadCitiesFromDatabase(code, stateName);
        if (!stored.isEmpty()) {
            return filterCities(stored, needle);
        }
        String stateCode = stateIso(code, stateName);
        List<Map<String, String>> fromGeo = geoCatalogClient.fetchCities(
                code, countryName(code), stateName, stateCode);
        if (!fromGeo.isEmpty()) {
            persistCities(code, stateName, fromGeo);
            return filterCities(toCityPlaces(code, stateName, fromGeo), needle);
        }
        return filterCities(fallbackCities(code, stateName), needle);
    }

    private List<Map<String, String>> loadCountriesFromDatabase() {
        try {
            List<CountryDocument> rows = countryRepository.findAll();
            if (rows == null || rows.isEmpty()) {
                return List.of();
            }
            List<Map<String, String>> countries = new ArrayList<>();
            for (CountryDocument row : rows) {
                if (row.getCode() == null || row.getCode().isBlank() || row.getName() == null || row.getName().isBlank()) {
                    continue;
                }
                Map<String, String> country = new LinkedHashMap<>();
                country.put("code", row.getCode().trim().toUpperCase(Locale.ROOT));
                country.put("name", row.getName().trim());
                countries.add(country);
            }
            return orderedCountries(countries);
        } catch (Exception ex) {
            log.warn("Country database unavailable; falling back to geo service", ex);
            return List.of();
        }
    }

    private List<Map<String, String>> loadStatesFromDatabase(String countryCode) {
        try {
            List<StateDocument> rows = stateRepository.findByCountryCodeIgnoreCaseOrderByNameAsc(countryCode);
            if (rows == null || rows.isEmpty()) {
                return List.of();
            }
            List<Map<String, String>> states = new ArrayList<>();
            for (StateDocument row : rows) {
                if (row.getName() == null || row.getName().isBlank()) {
                    continue;
                }
                Map<String, String> state = new LinkedHashMap<>();
                state.put("code", trim(row.getIsoCode()).toUpperCase(Locale.ROOT));
                state.put("name", row.getName().trim());
                state.put("countryCode", countryCode);
                states.add(state);
            }
            return orderedByName(states);
        } catch (Exception ex) {
            log.warn("State database unavailable for {}; falling back to geo service", countryCode, ex);
            return List.of();
        }
    }

    private List<GeoPlace> loadCitiesFromDatabase(String countryCode, String state) {
        try {
            List<CityDocument> rows = cityRepository
                    .findByCountryCodeIgnoreCaseAndStateIgnoreCaseOrderByNameAsc(countryCode, state);
            if (rows == null || rows.isEmpty()) {
                return List.of();
            }
            List<GeoPlace> cities = new ArrayList<>();
            String country = countryName(countryCode);
            for (CityDocument row : rows) {
                if (row.getName() == null || row.getName().isBlank()) {
                    continue;
                }
                cities.add(new GeoPlace(row.getName().trim(), state, country, countryCode));
            }
            return cities;
        } catch (Exception ex) {
            log.warn("City database unavailable for {} / {}; falling back to geo service", countryCode, state, ex);
            return List.of();
        }
    }

    private void persistCountries(List<Map<String, String>> countries) {
        try {
            Instant now = Instant.now();
            List<CountryDocument> docs = new ArrayList<>();
            for (Map<String, String> country : countries) {
                CountryDocument doc = new CountryDocument();
                doc.setCode(country.get("code"));
                doc.setName(country.get("name"));
                doc.setUpdatedAt(now);
                docs.add(doc);
            }
            countryRepository.saveAll(docs);
            log.info("Stored {} countries in geo_countries", docs.size());
        } catch (Exception ex) {
            log.warn("Could not store countries in database", ex);
        }
    }

    private void persistStates(String countryCode, List<Map<String, String>> states) {
        try {
            Instant now = Instant.now();
            List<StateDocument> docs = new ArrayList<>();
            for (Map<String, String> state : states) {
                String name = trim(state.get("name"));
                if (name.isBlank()) {
                    continue;
                }
                String iso = trim(state.get("code")).toUpperCase(Locale.ROOT);
                StateDocument doc = new StateDocument();
                doc.setId(countryCode + ":" + (iso.isBlank() ? name : iso));
                doc.setCountryCode(countryCode);
                doc.setName(name);
                doc.setIsoCode(iso);
                doc.setUpdatedAt(now);
                docs.add(doc);
            }
            if (!docs.isEmpty()) {
                stateRepository.saveAll(docs);
                log.info("Stored {} states in geo_states for {}", docs.size(), countryCode);
            }
        } catch (Exception ex) {
            log.warn("Could not store states in database for {}", countryCode, ex);
        }
    }

    private void persistCities(String countryCode, String state, List<Map<String, String>> cities) {
        try {
            Instant now = Instant.now();
            List<CityDocument> docs = new ArrayList<>();
            for (Map<String, String> city : cities) {
                String name = trim(city.get("name"));
                if (name.isBlank()) {
                    continue;
                }
                CityDocument doc = new CityDocument();
                doc.setId(countryCode + ":" + state + ":" + name);
                doc.setCountryCode(countryCode);
                doc.setState(state);
                doc.setName(name);
                doc.setUpdatedAt(now);
                docs.add(doc);
            }
            if (!docs.isEmpty()) {
                cityRepository.saveAll(docs);
                log.info("Stored {} cities in geo_cities for {} / {}", docs.size(), countryCode, state);
            }
        } catch (Exception ex) {
            log.warn("Could not store cities in database for {} / {}", countryCode, state, ex);
        }
    }

    private String countryName(String countryCode) {
        return countries().stream()
                .filter(country -> countryCode.equalsIgnoreCase(country.get("code")))
                .map(country -> country.get("name"))
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .orElse(countryCode);
    }

    private String stateIso(String countryCode, String stateName) {
        List<Map<String, String>> states = states(countryCode);
        return states.stream()
                .filter(state -> stateName.equalsIgnoreCase(state.get("name"))
                        || stateName.equalsIgnoreCase(state.get("code")))
                .map(state -> trim(state.get("code")))
                .filter(code -> !code.isBlank())
                .findFirst()
                .orElse("");
    }

    private List<GeoPlace> toCityPlaces(String countryCode, String state, List<Map<String, String>> cities) {
        String country = countryName(countryCode);
        List<GeoPlace> places = new ArrayList<>();
        for (Map<String, String> city : cities) {
            String name = trim(city.get("name"));
            if (!name.isBlank()) {
                places.add(new GeoPlace(name, state, country, countryCode));
            }
        }
        places.sort(Comparator.comparing(GeoPlace::getName, String.CASE_INSENSITIVE_ORDER));
        return places;
    }

    private static List<GeoPlace> filterCities(List<GeoPlace> cities, String needle) {
        if (needle == null || needle.isBlank()) {
            return cities;
        }
        return cities.stream()
                .filter(city -> city.getName().toLowerCase(Locale.ROOT).contains(needle))
                .toList();
    }

    private List<Map<String, String>> fallbackStates(String countryCode) {
        List<Map<String, String>> states = new ArrayList<>();
        FALLBACK_CITIES.stream()
                .filter(city -> countryCode.equalsIgnoreCase(city.getCountryCode()))
                .map(GeoPlace::getState)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(name -> {
                    Map<String, String> state = new LinkedHashMap<>();
                    state.put("code", "");
                    state.put("name", name);
                    state.put("countryCode", countryCode);
                    states.add(state);
                });
        return states;
    }

    private List<GeoPlace> fallbackCities(String countryCode, String state) {
        return FALLBACK_CITIES.stream()
                .filter(city -> countryCode.equalsIgnoreCase(city.getCountryCode()))
                .filter(city -> state.equalsIgnoreCase(city.getState()))
                .sorted(Comparator.comparing(GeoPlace::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static List<Map<String, String>> orderedCountries(List<Map<String, String>> countries) {
        if (countries == null || countries.isEmpty()) {
            return List.of();
        }
        return countries.stream()
                .sorted(Comparator
                        .comparing((Map<String, String> country) -> !"IN".equalsIgnoreCase(country.get("code")))
                        .thenComparing(country -> country.getOrDefault("name", ""), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static List<Map<String, String>> orderedByName(List<Map<String, String>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .sorted(Comparator.comparing(row -> row.getOrDefault("name", ""), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    public List<GeoPlace> suggest(String countryCode, String city, String query) {
        String needle = query == null ? "" : query.trim();
        if (needle.length() < 3) {
            return List.of();
        }
        String q = needle;
        if (city != null && !city.isBlank() && !needle.toLowerCase(Locale.ROOT).contains(city.toLowerCase(Locale.ROOT))) {
            q = needle + ", " + city;
        }
        String provider = credentialService.geoProvider();
        String apiKey = credentialService.geoApiKey();
        if (!apiKey.isBlank()) {
            if ("locationiq".equals(provider)) {
                List<GeoPlace> keyed = searchLocationIq(countryCode, q, apiKey);
                if (!keyed.isEmpty()) {
                    return keyed;
                }
            } else if ("geoapify".equals(provider)) {
                List<GeoPlace> keyed = searchGeoapify(countryCode, q, apiKey);
                if (!keyed.isEmpty()) {
                    return keyed;
                }
            }
        }
        List<GeoPlace> photonPlaces = searchPhoton(countryCode, q);
        if (!photonPlaces.isEmpty()) {
            return photonPlaces;
        }
        log.info("Photon returned no address matches for {}; trying Nominatim", q);
        return searchNominatim(countryCode, q, false);
    }

    private List<GeoPlace> searchPhoton(String countryCode, String query) {
        try {
            Map<String, Object> body = photon.get()
                    .uri(uri -> uri.path("/api/")
                            .queryParam("q", query)
                            .queryParam("limit", "8")
                            .queryParam("lang", "en")
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return fromPhotonBody(body, countryCode);
        } catch (Exception ex) {
            log.warn("Photon lookup failed for {}", query, ex);
            return List.of();
        }
    }

    private List<GeoPlace> searchLocationIq(String countryCode, String query, String apiKey) {
        try {
            List<Map<String, Object>> rows = locationIq.get()
                    .uri(uri -> uri.path("/v1/autocomplete")
                            .queryParam("key", apiKey)
                            .queryParam("q", query)
                            .queryParam("limit", "8")
                            .queryParam("format", "json")
                            .queryParam("addressdetails", "1")
                            .queryParam("countrycodes", countryCode == null ? "" : countryCode.toLowerCase(Locale.ROOT))
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return fromNominatimRows(rows, countryCode, false);
        } catch (Exception ex) {
            log.warn("LocationIQ lookup failed for {}", query, ex);
            return List.of();
        }
    }

    private List<GeoPlace> searchGeoapify(String countryCode, String query, String apiKey) {
        try {
            String code = countryCode == null ? "" : countryCode.toLowerCase(Locale.ROOT);
            Map<String, Object> body = geoapify.get()
                    .uri(uri -> {
                        var builder = uri.path("/v1/geocode/autocomplete")
                                .queryParam("text", query)
                                .queryParam("limit", "8")
                                .queryParam("format", "geojson")
                                .queryParam("apiKey", apiKey);
                        if (!code.isBlank()) {
                            builder.queryParam("filter", "countrycode:" + code);
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return fromPhotonBody(body, countryCode);
        } catch (Exception ex) {
            log.warn("Geoapify lookup failed for {}", query, ex);
            return List.of();
        }
    }

    private List<GeoPlace> searchNominatim(String countryCode, String query, boolean citiesOnly) {
        try {
            List<Map<String, Object>> rows = nominatim.get()
                    .uri(uri -> uri.path("/search")
                            .queryParam("format", "json")
                            .queryParam("addressdetails", "1")
                            .queryParam("limit", citiesOnly ? "12" : "8")
                            .queryParam("email", CONTACT_EMAIL)
                            .queryParam("countrycodes", countryCode == null ? "" : countryCode.toLowerCase(Locale.ROOT))
                            .queryParam("q", query)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return fromNominatimRows(rows, countryCode, citiesOnly);
        } catch (Exception ex) {
            log.warn("Nominatim lookup failed for {}", query, ex);
            return List.of();
        }
    }

    private static List<GeoPlace> fromNominatimRows(
            List<Map<String, Object>> rows,
            String countryCode,
            boolean citiesOnly) {
        if (rows == null) {
            return List.of();
        }
        List<GeoPlace> places = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            GeoPlace place = fromNominatim(row, countryCode);
            if (place.getName() == null || place.getName().isBlank()) {
                continue;
            }
            if (citiesOnly && place.getLatitude() == null) {
                continue;
            }
            places.add(place);
        }
        return places;
    }

    @SuppressWarnings("unchecked")
    static List<GeoPlace> fromPhotonBody(Map<String, Object> body, String countryCode) {
        if (body == null) {
            return List.of();
        }
        Object featuresObj = body.get("features");
        if (!(featuresObj instanceof List<?> features)) {
            return List.of();
        }
        String wanted = normalizeCode(countryCode);
        List<GeoPlace> places = new ArrayList<>();
        for (Object featureObj : features) {
            if (!(featureObj instanceof Map<?, ?> feature)) {
                continue;
            }
            GeoPlace place = fromPhotonFeature((Map<String, Object>) feature, wanted);
            if (place != null) {
                places.add(place);
            }
        }
        return places;
    }

    @SuppressWarnings("unchecked")
    private static GeoPlace fromPhotonFeature(Map<String, Object> feature, String countryCode) {
        Map<String, Object> properties = feature.get("properties") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
        Map<String, Object> geometry = feature.get("geometry") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
        String code = stringOf(properties.get("countrycode"));
        if (code.isBlank()) {
            code = stringOf(properties.get("country_code"));
        }
        code = code.toUpperCase(Locale.ROOT);
        if (!countryCode.isBlank() && !code.isBlank() && !countryCode.equals(code)) {
            return null;
        }
        GeoPlace place = new GeoPlace();
        place.setName(first(properties, "name", "street", "city", "formatted"));
        place.setState(first(properties, "state", "region"));
        place.setCountry(stringOf(properties.get("country")));
        place.setCountryCode(code.isBlank() ? countryCode : code);
        String formatted = stringOf(properties.get("formatted"));
        place.setAddress(formatted.isBlank() ? photonAddress(properties) : formatted);
        if (properties.get("lat") != null && properties.get("lon") != null) {
            place.setLatitude(toDouble(properties.get("lat")));
            place.setLongitude(toDouble(properties.get("lon")));
        } else if (geometry.get("coordinates") instanceof List<?> coords && coords.size() >= 2) {
            place.setLongitude(toDouble(coords.get(0)));
            place.setLatitude(toDouble(coords.get(1)));
        }
        if (place.getName() == null || place.getName().isBlank()
                || place.getLatitude() == null || place.getLongitude() == null) {
            return null;
        }
        if (place.getAddress() == null || place.getAddress().isBlank()) {
            place.setAddress(place.getName());
        }
        return place;
    }

    private static String photonAddress(Map<String, Object> properties) {
        List<String> parts = new ArrayList<>();
        String name = stringOf(properties.get("name"));
        String house = stringOf(properties.get("housenumber"));
        String street = stringOf(properties.get("street"));
        String streetLine = (house + " " + street).trim();
        if (!name.isBlank()) {
            parts.add(name);
        }
        if (!streetLine.isBlank() && !streetLine.equalsIgnoreCase(name)) {
            parts.add(streetLine);
        }
        for (String key : List.of("district", "city", "state", "postcode", "country")) {
            String value = stringOf(properties.get(key));
            if (!value.isBlank() && parts.stream().noneMatch(part -> part.equalsIgnoreCase(value))) {
                parts.add(value);
            }
        }
        return String.join(", ", parts);
    }

    @SuppressWarnings("unchecked")
    private static GeoPlace fromNominatim(Map<String, Object> row, String countryCode) {
        Map<String, Object> address = row.get("address") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
        GeoPlace place = new GeoPlace();
        place.setName(first(address, "amenity", "building", "tourism", "shop", "leisure",
                "historic", "name", "road", "city", "town", "village", "suburb", "hamlet", "county"));
        place.setState(first(address, "state", "region", "state_district"));
        place.setCountry(stringOf(address.get("country")));
        String code = stringOf(address.get("country_code"));
        place.setCountryCode(code.isBlank() ? normalizeCode(countryCode) : code.toUpperCase(Locale.ROOT));
        place.setAddress(firstNonBlank(stringOf(row.get("display_name")), stringOf(row.get("display_place"))));
        place.setLatitude(toDouble(row.get("lat")));
        place.setLongitude(toDouble(row.get("lon")));
        if (place.getName() == null || place.getName().isBlank()) {
            String display = place.getAddress();
            place.setName(display.contains(",") ? display.substring(0, display.indexOf(',')) : display);
        }
        return place;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String first(Map<String, Object> address, String... keys) {
        for (String key : keys) {
            String value = stringOf(address.get(key));
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String stringOf(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String normalizeCode(String countryCode) {
        return countryCode == null ? "" : countryCode.trim().toUpperCase(Locale.ROOT);
    }

    private static List<Map<String, String>> buildCountries() {
        String[][] rows = {
                {"IN", "India"}, {"AU", "Australia"}, {"CA", "Canada"}, {"GB", "United Kingdom"},
                {"US", "United States"}, {"AE", "United Arab Emirates"}, {"SG", "Singapore"},
                {"MY", "Malaysia"}, {"NZ", "New Zealand"}, {"PK", "Pakistan"}, {"BD", "Bangladesh"},
                {"NP", "Nepal"}, {"LK", "Sri Lanka"}, {"AF", "Afghanistan"}, {"DE", "Germany"},
                {"IT", "Italy"}, {"FR", "France"}, {"ES", "Spain"}, {"NL", "Netherlands"},
                {"IE", "Ireland"}, {"SE", "Sweden"}, {"NO", "Norway"}, {"DK", "Denmark"},
                {"FI", "Finland"}, {"CH", "Switzerland"}, {"AT", "Austria"}, {"BE", "Belgium"},
                {"PT", "Portugal"}, {"GR", "Greece"}, {"PL", "Poland"}, {"CZ", "Czechia"},
                {"HU", "Hungary"}, {"RO", "Romania"}, {"UA", "Ukraine"}, {"TR", "Turkey"},
                {"SA", "Saudi Arabia"}, {"QA", "Qatar"}, {"KW", "Kuwait"}, {"BH", "Bahrain"},
                {"OM", "Oman"}, {"EG", "Egypt"}, {"ZA", "South Africa"}, {"KE", "Kenya"},
                {"NG", "Nigeria"}, {"GH", "Ghana"}, {"TZ", "Tanzania"}, {"UG", "Uganda"},
                {"ET", "Ethiopia"}, {"BR", "Brazil"}, {"MX", "Mexico"}, {"AR", "Argentina"},
                {"CL", "Chile"}, {"CO", "Colombia"}, {"PE", "Peru"}, {"JP", "Japan"},
                {"KR", "South Korea"}, {"CN", "China"}, {"TH", "Thailand"}, {"ID", "Indonesia"},
                {"PH", "Philippines"}, {"VN", "Vietnam"}, {"HK", "Hong Kong"}, {"TW", "Taiwan"},
                {"IL", "Israel"}, {"RU", "Russia"}, {"KZ", "Kazakhstan"}, {"FJ", "Fiji"}
        };
        List<Map<String, String>> list = new ArrayList<>();
        for (String[] row : rows) {
            Map<String, String> country = new LinkedHashMap<>();
            country.put("code", row[0]);
            country.put("name", row[1]);
            list.add(country);
        }
        return List.copyOf(list);
    }

    private static List<GeoPlace> buildCities() {
        List<GeoPlace> list = new ArrayList<>();
        add(list, "IN", "India",
                "Amritsar|Punjab", "Ludhiana|Punjab", "Jalandhar|Punjab", "Patiala|Punjab",
                "Bathinda|Punjab", "Mohali|Punjab", "Anandpur Sahib|Punjab", "Fatehgarh Sahib|Punjab",
                "Tarn Taran|Punjab", "Gurdaspur|Punjab", "Hoshiarpur|Punjab", "Moga|Punjab",
                "Firozpur|Punjab", "Kapurthala|Punjab", "Sangrur|Punjab", "Barnala|Punjab",
                "Faridkot|Punjab", "Muktsar|Punjab", "Pathankot|Punjab", "Nangal|Punjab",
                "Chandigarh|Chandigarh", "Delhi|Delhi", "New Delhi|Delhi", "Ambala|Haryana",
                "Kurukshetra|Haryana", "Karnal|Haryana", "Panipat|Haryana", "Hisar|Haryana",
                "Sirsa|Haryana", "Yamunanagar|Haryana", "Panchkula|Haryana", "Faridabad|Haryana",
                "Gurugram|Haryana", "Jaipur|Rajasthan", "Ajmer|Rajasthan", "Jodhpur|Rajasthan",
                "Haridwar|Uttarakhand", "Rishikesh|Uttarakhand", "Dehradun|Uttarakhand",
                "Jammu|Jammu and Kashmir", "Srinagar|Jammu and Kashmir", "Nanded|Maharashtra",
                "Mumbai|Maharashtra", "Pune|Maharashtra", "Nagpur|Maharashtra", "Patna|Bihar",
                "Lucknow|Uttar Pradesh", "Kanpur|Uttar Pradesh", "Varanasi|Uttar Pradesh",
                "Ahmedabad|Gujarat", "Surat|Gujarat", "Kolkata|West Bengal", "Bengaluru|Karnataka",
                "Hyderabad|Telangana", "Chennai|Tamil Nadu", "Indore|Madhya Pradesh",
                "Bhopal|Madhya Pradesh", "Raipur|Chhattisgarh", "Ranchi|Jharkhand",
                "Guwahati|Assam", "Bhubaneswar|Odisha");
        add(list, "CA", "Canada",
                "Surrey|British Columbia", "Vancouver|British Columbia", "Abbotsford|British Columbia",
                "Calgary|Alberta", "Edmonton|Alberta", "Brampton|Ontario", "Toronto|Ontario",
                "Mississauga|Ontario", "Ottawa|Ontario", "Winnipeg|Manitoba", "Montreal|Quebec");
        add(list, "GB", "United Kingdom",
                "London|England", "Southall|England", "Birmingham|England", "Leicester|England",
                "Slough|England", "Manchester|England", "Leeds|England", "Glasgow|Scotland",
                "Edinburgh|Scotland", "Cardiff|Wales");
        add(list, "US", "United States",
                "New York|New York", "Fremont|California", "Yuba City|California", "Fresno|California",
                "Hayward|California", "Seattle|Washington", "Houston|Texas", "Dallas|Texas",
                "Chicago|Illinois", "Newark|New Jersey", "Iselin|New Jersey");
        add(list, "AU", "Australia",
                "Melbourne|Victoria", "Sydney|New South Wales", "Brisbane|Queensland",
                "Adelaide|South Australia", "Perth|Western Australia", "Canberra|Australian Capital Territory");
        add(list, "AE", "United Arab Emirates", "Dubai|Dubai", "Abu Dhabi|Abu Dhabi", "Sharjah|Sharjah");
        add(list, "NZ", "New Zealand", "Auckland|Auckland", "Wellington|Wellington", "Christchurch|Canterbury");
        add(list, "SG", "Singapore", "Singapore|Singapore");
        add(list, "MY", "Malaysia", "Kuala Lumpur|Federal Territory", "Penang|Penang", "Johor Bahru|Johor");
        return List.copyOf(list);
    }

    private static void add(List<GeoPlace> list, String code, String country, String... cityState) {
        for (String row : cityState) {
            String[] parts = row.split("\\|", 2);
            list.add(new GeoPlace(parts[0], parts.length > 1 ? parts[1] : "", country, code));
        }
    }
}
