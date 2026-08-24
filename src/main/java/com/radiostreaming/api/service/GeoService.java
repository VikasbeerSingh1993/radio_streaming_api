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
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

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

    private final CountryRepository countryRepository;
    private final StateRepository stateRepository;
    private final CityRepository cityRepository;
    private final GeoCatalogClient geoCatalogClient;
    private final RestClient nominatim = RestClient.builder()
            .baseUrl("https://nominatim.openstreetmap.org")
            .defaultHeader("User-Agent", "radio-streaming-api/1.0 (event-location)")
            .defaultHeader("Accept", "application/json")
            .build();

    public GeoService(
            CountryRepository countryRepository,
            StateRepository stateRepository,
            CityRepository cityRepository,
            GeoCatalogClient geoCatalogClient) {
        this.countryRepository = countryRepository;
        this.stateRepository = stateRepository;
        this.cityRepository = cityRepository;
        this.geoCatalogClient = geoCatalogClient;
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
        return searchNominatim(countryCode, q, false);
    }

    private List<GeoPlace> searchNominatim(String countryCode, String query, boolean citiesOnly) {
        try {
            List<Map<String, Object>> rows = nominatim.get()
                    .uri(uri -> uri.path("/search")
                            .queryParam("format", "json")
                            .queryParam("addressdetails", "1")
                            .queryParam("limit", citiesOnly ? "12" : "8")
                            .queryParam("countrycodes", countryCode == null ? "" : countryCode.toLowerCase(Locale.ROOT))
                            .queryParam("q", query)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
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
        } catch (Exception ex) {
            log.warn("Location lookup failed for {}", query, ex);
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static GeoPlace fromNominatim(Map<String, Object> row, String countryCode) {
        Map<String, Object> address = row.get("address") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
        GeoPlace place = new GeoPlace();
        place.setName(first(address, "city", "town", "village", "suburb", "hamlet", "county"));
        place.setState(first(address, "state", "region", "state_district"));
        place.setCountry(stringOf(address.get("country")));
        String code = stringOf(address.get("country_code"));
        place.setCountryCode(code.isBlank() ? normalizeCode(countryCode) : code.toUpperCase(Locale.ROOT));
        place.setAddress(stringOf(row.get("display_name")));
        place.setLatitude(toDouble(row.get("lat")));
        place.setLongitude(toDouble(row.get("lon")));
        if (place.getName() == null || place.getName().isBlank()) {
            String display = place.getAddress();
            place.setName(display.contains(",") ? display.substring(0, display.indexOf(',')) : display);
        }
        return place;
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
