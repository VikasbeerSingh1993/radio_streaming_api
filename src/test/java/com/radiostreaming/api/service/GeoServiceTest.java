package com.radiostreaming.api.service;

import com.radiostreaming.api.model.CityDocument;
import com.radiostreaming.api.model.CountryDocument;
import com.radiostreaming.api.model.StateDocument;
import com.radiostreaming.api.repository.CityRepository;
import com.radiostreaming.api.repository.CountryRepository;
import com.radiostreaming.api.repository.StateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeoServiceTest {

    @Mock
    private CountryRepository countryRepository;
    @Mock
    private StateRepository stateRepository;
    @Mock
    private CityRepository cityRepository;
    @Mock
    private GeoCatalogClient geoCatalogClient;

    private GeoService geoService;

    @BeforeEach
    void setUp() {
        geoService = new GeoService(countryRepository, stateRepository, cityRepository, geoCatalogClient);
    }

    @Test
    void countriesComeFromDatabaseWhenPresent() {
        when(countryRepository.findAll()).thenReturn(List.of(
                country("CA", "Canada"),
                country("IN", "India")
        ));

        var countries = geoService.countries();

        assertEquals("IN", countries.getFirst().get("code"));
        assertTrue(countries.stream().anyMatch(row -> "CA".equals(row.get("code"))));
        verify(geoCatalogClient, never()).fetchCountries();
        verify(countryRepository, never()).saveAll(anyList());
    }

    @Test
    void countriesComeFromGeoServiceWhenDatabaseEmptyThenStored() {
        when(countryRepository.findAll()).thenReturn(List.of());
        when(geoCatalogClient.fetchCountries()).thenReturn(List.of(
                Map.of("code", "FR", "name", "France"),
                Map.of("code", "IN", "name", "India")
        ));

        var countries = geoService.countries();

        assertEquals("IN", countries.getFirst().get("code"));
        assertEquals("France", countries.get(1).get("name"));
        verify(countryRepository).saveAll(anyList());
    }

    @Test
    void countriesComeFromGeoServiceWhenDatabaseUnavailable() {
        when(countryRepository.findAll()).thenThrow(new RuntimeException("mongo down"));
        when(geoCatalogClient.fetchCountries()).thenReturn(List.of(
                Map.of("code", "IN", "name", "India"),
                Map.of("code", "GB", "name", "United Kingdom")
        ));

        var countries = geoService.countries();

        assertEquals("IN", countries.getFirst().get("code"));
        assertTrue(countries.stream().anyMatch(row -> "GB".equals(row.get("code"))));
    }

    @Test
    void statesComeFromDatabaseWhenPresent() {
        when(stateRepository.findByCountryCodeIgnoreCaseOrderByNameAsc("IN")).thenReturn(List.of(
                state("IN", "PB", "Punjab"),
                state("IN", "HR", "Haryana")
        ));

        var states = geoService.states("IN");

        assertEquals("Haryana", states.getFirst().get("name"));
        assertTrue(states.stream().anyMatch(row -> "Punjab".equals(row.get("name"))));
        verify(geoCatalogClient, never()).fetchStates(anyString(), anyString());
    }

    @Test
    void statesComeFromGeoServiceWhenDatabaseEmptyThenStored() {
        when(stateRepository.findByCountryCodeIgnoreCaseOrderByNameAsc("IN")).thenReturn(List.of());
        when(countryRepository.findAll()).thenReturn(List.of(country("IN", "India")));
        when(geoCatalogClient.fetchStates("IN", "India")).thenReturn(List.of(
                Map.of("code", "PB", "name", "Punjab"),
                Map.of("code", "HR", "name", "Haryana")
        ));

        var states = geoService.states("IN");

        assertTrue(states.stream().anyMatch(row -> "Punjab".equals(row.get("name"))));
        verify(stateRepository).saveAll(anyList());
    }

    @Test
    void citiesComeFromDatabaseForCountryAndState() {
        when(cityRepository.findByCountryCodeIgnoreCaseAndStateIgnoreCaseOrderByNameAsc("IN", "Punjab"))
                .thenReturn(List.of(city("IN", "Punjab", "Amritsar"), city("IN", "Punjab", "Ludhiana")));
        when(countryRepository.findAll()).thenReturn(List.of(country("IN", "India")));

        var cities = geoService.cities("IN", "Punjab", "ludh");

        assertEquals(1, cities.size());
        assertEquals("Ludhiana", cities.getFirst().getName());
        verify(geoCatalogClient, never()).fetchCities(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void citiesComeFromGeoServiceWhenDatabaseEmptyThenStored() {
        when(cityRepository.findByCountryCodeIgnoreCaseAndStateIgnoreCaseOrderByNameAsc("IN", "Punjab"))
                .thenReturn(List.of());
        when(stateRepository.findByCountryCodeIgnoreCaseOrderByNameAsc("IN")).thenReturn(List.of(
                state("IN", "PB", "Punjab")
        ));
        when(countryRepository.findAll()).thenReturn(List.of(country("IN", "India")));
        when(geoCatalogClient.fetchCities("IN", "India", "Punjab", "PB")).thenReturn(List.of(
                Map.of("name", "Amritsar", "state", "Punjab")
        ));

        var cities = geoService.cities("IN", "Punjab", "");

        assertEquals("Amritsar", cities.getFirst().getName());
        assertEquals("Punjab", cities.getFirst().getState());
        verify(cityRepository).saveAll(anyList());
    }

    @Test
    void citiesUseBuiltInListWhenDatabaseAndGeoServiceUnavailable() {
        when(cityRepository.findByCountryCodeIgnoreCaseAndStateIgnoreCaseOrderByNameAsc("IN", "Punjab"))
                .thenThrow(new RuntimeException("mongo down"));
        when(stateRepository.findByCountryCodeIgnoreCaseOrderByNameAsc("IN"))
                .thenThrow(new RuntimeException("mongo down"));
        when(countryRepository.findAll()).thenThrow(new RuntimeException("mongo down"));
        when(geoCatalogClient.fetchCities(anyString(), anyString(), anyString(), anyString())).thenReturn(List.of());

        var cities = geoService.cities("IN", "Punjab", "amrit");

        assertEquals(1, cities.size());
        assertEquals("Amritsar", cities.getFirst().getName());
        assertEquals("Punjab", cities.getFirst().getState());
    }

    private static CountryDocument country(String code, String name) {
        CountryDocument document = new CountryDocument();
        document.setCode(code);
        document.setName(name);
        return document;
    }

    private static StateDocument state(String countryCode, String iso, String name) {
        StateDocument document = new StateDocument();
        document.setCountryCode(countryCode);
        document.setIsoCode(iso);
        document.setName(name);
        return document;
    }

    private static CityDocument city(String countryCode, String state, String name) {
        CityDocument document = new CityDocument();
        document.setCountryCode(countryCode);
        document.setState(state);
        document.setName(name);
        return document;
    }
}
