package com.radiostreaming.api.controller;

import com.radiostreaming.api.dto.AdminProfile;
import com.radiostreaming.api.dto.AdminUserRequest;
import com.radiostreaming.api.dto.EventApprovalRequest;
import com.radiostreaming.api.model.AudioLinkDocument;
import com.radiostreaming.api.model.CategoryDocument;
import com.radiostreaming.api.model.EventDocument;
import com.radiostreaming.api.model.StationDocument;
import com.radiostreaming.api.service.AdminCatalogService;
import com.radiostreaming.api.service.AdminUserService;
import com.radiostreaming.api.service.CurrentAdmin;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminApiController {

    private final AdminCatalogService adminCatalogService;
    private final AdminUserService adminUserService;
    private final CurrentAdmin currentAdmin;

    public AdminApiController(
            AdminCatalogService adminCatalogService,
            AdminUserService adminUserService,
            CurrentAdmin currentAdmin) {
        this.adminCatalogService = adminCatalogService;
        this.adminUserService = adminUserService;
        this.currentAdmin = currentAdmin;
    }

    @GetMapping("/me")
    public AdminProfile me() {
        return AdminProfile.from(currentAdmin.require());
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return adminCatalogService.stats(currentAdmin.require());
    }

    @PostMapping("/cache/reload")
    public Map<String, Object> reloadCache() {
        return adminCatalogService.reloadCache();
    }

    @GetMapping("/events")
    public List<EventDocument> listEvents(@RequestParam(required = false) String q) {
        return adminCatalogService.listEvents(currentAdmin.require(), q);
    }

    @GetMapping("/events/{id}")
    public EventDocument getEvent(@PathVariable String id) {
        return adminCatalogService.getEvent(currentAdmin.require(), id);
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    public EventDocument createEvent(@RequestBody EventDocument event) {
        return adminCatalogService.saveEvent(currentAdmin.require(), event);
    }

    @PutMapping("/events/{id}")
    public EventDocument updateEvent(@PathVariable String id, @RequestBody EventDocument event) {
        return adminCatalogService.updateEvent(currentAdmin.require(), id, event);
    }

    @DeleteMapping("/events/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEvent(@PathVariable String id) {
        adminCatalogService.deleteEvent(currentAdmin.require(), id);
    }

    @PostMapping("/events/{id}/approve")
    public EventDocument approveEvent(
            @PathVariable String id,
            @RequestBody(required = false) EventApprovalRequest request) {
        return adminCatalogService.approveEvent(currentAdmin.require(), id, request);
    }

    @PostMapping("/events/{id}/reject")
    public EventDocument rejectEvent(
            @PathVariable String id,
            @RequestBody(required = false) EventApprovalRequest request) {
        return adminCatalogService.rejectEvent(currentAdmin.require(), id, request);
    }

    @GetMapping("/stations")
    public List<StationDocument> listStations(@RequestParam(required = false) String q) {
        return adminCatalogService.listStations(currentAdmin.require(), q);
    }

    @PostMapping("/stations")
    @ResponseStatus(HttpStatus.CREATED)
    public StationDocument createStation(@RequestBody StationDocument station) {
        return adminCatalogService.saveStation(currentAdmin.require(), station);
    }

    @PutMapping("/stations/{id}")
    public StationDocument updateStation(@PathVariable String id, @RequestBody StationDocument station) {
        return adminCatalogService.updateStation(currentAdmin.require(), id, station);
    }

    @DeleteMapping("/stations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStation(@PathVariable String id) {
        adminCatalogService.deleteStation(currentAdmin.require(), id);
    }

    @GetMapping("/categories")
    public List<CategoryDocument> listCategories(@RequestParam(required = false) String q) {
        return adminCatalogService.listCategories(currentAdmin.require(), q);
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryDocument createCategory(@RequestBody CategoryDocument category) {
        return adminCatalogService.saveCategory(currentAdmin.require(), category);
    }

    @PutMapping("/categories/{id}")
    public CategoryDocument updateCategory(@PathVariable String id, @RequestBody CategoryDocument category) {
        return adminCatalogService.updateCategory(currentAdmin.require(), id, category);
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable String id) {
        adminCatalogService.deleteCategory(currentAdmin.require(), id);
    }

    @GetMapping("/audio-links")
    public List<AudioLinkDocument> listAudioLinks(@RequestParam(required = false) String q) {
        return adminCatalogService.listAudioLinks(currentAdmin.require(), q);
    }

    @GetMapping("/audio-links/station/{stationId}")
    public List<AudioLinkDocument> listAudioLinksByStation(@PathVariable String stationId) {
        return adminCatalogService.listAudioLinksByStation(currentAdmin.require(), stationId);
    }

    @PostMapping("/audio-links")
    @ResponseStatus(HttpStatus.CREATED)
    public AudioLinkDocument createAudioLink(@RequestBody AudioLinkDocument link) {
        return adminCatalogService.saveAudioLink(currentAdmin.require(), link);
    }

    @PutMapping("/audio-links/{id}")
    public AudioLinkDocument updateAudioLink(@PathVariable String id, @RequestBody AudioLinkDocument link) {
        return adminCatalogService.updateAudioLink(currentAdmin.require(), id, link);
    }

    @DeleteMapping("/audio-links/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAudioLink(@PathVariable String id) {
        adminCatalogService.deleteAudioLink(currentAdmin.require(), id);
    }

    @GetMapping("/users")
    public List<AdminProfile> listUsers(@RequestParam(required = false) String q) {
        return adminUserService.list(currentAdmin.require(), q);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminProfile createUser(@Valid @RequestBody AdminUserRequest request) {
        return adminUserService.create(currentAdmin.require(), request);
    }

    @PutMapping("/users/{id}")
    public AdminProfile updateUser(@PathVariable String id, @Valid @RequestBody AdminUserRequest request) {
        return adminUserService.update(currentAdmin.require(), id, request);
    }

    @DeleteMapping("/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable String id) {
        adminUserService.delete(currentAdmin.require(), id);
    }
}
