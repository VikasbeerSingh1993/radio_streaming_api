package com.radiostreaming.api.service;

import com.radiostreaming.api.dto.EventApprovalRequest;
import com.radiostreaming.api.dto.EventSubmitRequest;
import com.radiostreaming.api.dto.PageResponse;
import com.radiostreaming.api.model.AdminUser;
import com.radiostreaming.api.model.AudioLinkDocument;
import com.radiostreaming.api.model.CategoryDocument;
import com.radiostreaming.api.model.EventDocument;
import com.radiostreaming.api.model.StationDocument;
import com.radiostreaming.api.repository.AdminUserRepository;
import com.radiostreaming.api.repository.AudioLinkRepository;
import com.radiostreaming.api.repository.CategoryRepository;
import com.radiostreaming.api.repository.EventRepository;
import com.radiostreaming.api.repository.StationRepository;
import com.radiostreaming.api.security.AdminAction;
import com.radiostreaming.api.security.AdminModule;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminCatalogService {

    private final EventRepository eventRepository;
    private final StationRepository stationRepository;
    private final CategoryRepository categoryRepository;
    private final AudioLinkRepository audioLinkRepository;
    private final RadioCacheService radioCacheService;
    private final AdminAccessService accessService;
    private final AdminDirectory adminDirectory;
    private final AdminUserRepository adminUserRepository;

    public AdminCatalogService(
            EventRepository eventRepository,
            StationRepository stationRepository,
            CategoryRepository categoryRepository,
            AudioLinkRepository audioLinkRepository,
            RadioCacheService radioCacheService,
            AdminAccessService accessService,
            AdminDirectory adminDirectory,
            AdminUserRepository adminUserRepository) {
        this.eventRepository = eventRepository;
        this.stationRepository = stationRepository;
        this.categoryRepository = categoryRepository;
        this.audioLinkRepository = audioLinkRepository;
        this.radioCacheService = radioCacheService;
        this.accessService = accessService;
        this.adminDirectory = adminDirectory;
        this.adminUserRepository = adminUserRepository;
    }

    public Map<String, Object> stats(AdminUser user) {
        List<EventDocument> events = listEvents(user, null);
        long pending = events.stream().filter(e -> "pending".equalsIgnoreCase(e.getApprovalStatus())).count();
        long approved = events.stream().filter(EventDocument::isListedPublicly).count();
        long rejected = events.stream().filter(e -> "rejected".equalsIgnoreCase(e.getApprovalStatus())).count();
        Map<String, Object> cache = radioCacheService.status();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("stations", listStations(user, null).size());
        body.put("categories", listCategories(user, null).size());
        body.put("audioLinks", listAudioLinks(user, null).size());
        body.put("events", events.size());
        body.put("pendingEvents", pending);
        body.put("approvedEvents", approved);
        body.put("rejectedEvents", rejected);
        body.put("cacheSource", cache.get("source"));
        body.put("cacheLoadedAt", cache.get("loadedAt"));
        return body;
    }

    public EventDocument submitVerifiedEvent(EventSubmitRequest request) {
        return submitEvent(request);
    }

    public EventDocument submitEvent(EventSubmitRequest request) {
        EventDocument event = new EventDocument();
        event.setTitle(request.getTitle().trim());
        event.setDate(request.getDate());
        event.setEndDate(request.getEndDate() != null ? request.getEndDate() : request.getDate());
        event.setCity(trimToEmpty(request.getCity()));
        event.setCountry(trimToEmpty(request.getCountry()));
        event.setCountryCode(trimToEmpty(request.getCountryCode()));
        event.setState(trimToEmpty(request.getState()));
        event.setDescription(trimToEmpty(request.getDescription()));
        event.setAddress(trimToEmpty(request.getAddress()));
        event.setLatitude(request.getLatitude());
        event.setLongitude(request.getLongitude());
        event.setOrganizedBy(trimToEmpty(request.getOrganizedBy()));
        event.setOrganization(trimToEmpty(request.getOrganizedBy()));
        event.setStatus("scheduled");
        event.setApprovalStatus("pending");
        String username = trimToEmpty(request.getSubmitterUsername());
        String name = trimToEmpty(request.getSubmitterName());
        event.setSubmitterUsername(username);
        event.setSubmitterName(name.isBlank() ? username : name);
        event.setSubmitterEmail(trimToEmpty(request.getSubmitterEmail()));
        event.setSubmitterPhone(trimToEmpty(request.getSubmitterPhone()));
        event.setEmailVerified(true);
        event.setSubmittedAt(Instant.now());
        event.setCreatedBy("public");
        EventDocument saved = eventRepository.save(event);
        radioCacheService.upsertEvent(saved);
        return saved;
    }

    public List<EventDocument> listEvents(AdminUser user, String query) {
        return listEvents(user, query, "all");
    }

    public List<EventDocument> listEvents(AdminUser user, String query, String status) {
        accessService.assertCan(user, AdminModule.EVENTS, AdminAction.READ);
        List<EventDocument> events = accessService.visibleEvents(user, radioCacheService.getAllEventsForAdmin(), query);
        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            String wanted = status.toLowerCase();
            events = events.stream().filter(event -> wanted.equals(eventStatus(event))).toList();
        }
        return events;
    }

    public PageResponse<EventDocument> pageEvents(AdminUser user, String query, String status, int page, int size) {
        return PageResponse.of(listEvents(user, query, status), page, size);
    }

    public EventDocument getEvent(AdminUser user, String id) {
        EventDocument event = findEvent(id);
        accessService.assertCan(user, AdminModule.EVENTS, AdminAction.READ);
        accessService.assertEventInScope(user, event);
        return event;
    }

    public EventDocument saveEvent(AdminUser user, EventDocument event) {
        accessService.assertCan(user, AdminModule.EVENTS, AdminAction.CREATE);
        event.setId(null);
        stampNewEvent(user, event);
        accessService.assertEventInScope(user, event);
        if (event.getCategory() != null && !event.getCategory().isBlank()) {
            accessService.assertStationCategoryAllowed(user, event.getCategory());
        }
        return persistEvent(event);
    }

    public EventDocument updateEvent(AdminUser user, String id, EventDocument incoming) {
        EventDocument existing = findEvent(id);
        accessService.assertCan(user, AdminModule.EVENTS, AdminAction.UPDATE);
        accessService.assertEventInScope(user, existing);
        incoming.setId(existing.getId());
        if (incoming.getSubmittedAt() == null) {
            incoming.setSubmittedAt(existing.getSubmittedAt());
        }
        if (incoming.getSubmitterName() == null) {
            incoming.setSubmitterName(existing.getSubmitterName());
        }
        if (incoming.getSubmitterEmail() == null) {
            incoming.setSubmitterEmail(existing.getSubmitterEmail());
        }
        if (incoming.getSubmitterPhone() == null) {
            incoming.setSubmitterPhone(existing.getSubmitterPhone());
        }
        if (incoming.getCreatedBy() == null) {
            incoming.setCreatedBy(existing.getCreatedBy());
        }
        applyEventScope(user, incoming);
        accessService.assertEventInScope(user, incoming);
        return persistEvent(incoming);
    }

    public void deleteEvent(AdminUser user, String id) {
        EventDocument existing = findEvent(id);
        accessService.assertCan(user, AdminModule.EVENTS, AdminAction.DELETE);
        accessService.assertEventInScope(user, existing);
        eventRepository.deleteById(id);
        radioCacheService.removeEvent(id);
    }

    public EventDocument approveEvent(AdminUser user, String id, EventApprovalRequest request) {
        return reviewEvent(user, id, "approved", request);
    }

    public EventDocument rejectEvent(AdminUser user, String id, EventApprovalRequest request) {
        return reviewEvent(user, id, "rejected", request);
    }

    public List<StationDocument> listStations(AdminUser user, String query) {
        accessService.assertCan(user, AdminModule.STATIONS, AdminAction.READ);
        return accessService.visibleStations(user, radioCacheService.getStationsForAdmin(), query);
    }

    public PageResponse<StationDocument> pageStations(AdminUser user, String query, int page, int size) {
        return PageResponse.of(listStations(user, query), page, size);
    }

    public StationDocument saveStation(AdminUser user, StationDocument station) {
        accessService.assertCan(user, AdminModule.STATIONS, AdminAction.CREATE);
        accessService.assertStationCategoryAllowed(user, station.getCategory());
        station.setId(null);
        station.setCreatedBy(user.getUsername());
        if (station.getCreatedAt() == null) {
            station.setCreatedAt(Instant.now());
        }
        StationDocument saved = stationRepository.save(station);
        radioCacheService.upsertStation(saved);
        return saved;
    }

    public StationDocument updateStation(AdminUser user, String id, StationDocument incoming) {
        StationDocument existing = stationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Station not found"));
        accessService.assertCan(user, AdminModule.STATIONS, AdminAction.UPDATE);
        if (!accessService.canViewStation(user, existing)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This station is outside your assigned access");
        }
        accessService.assertStationCategoryAllowed(user, incoming.getCategory());
        incoming.setId(existing.getId());
        if (incoming.getCreatedAt() == null) {
            incoming.setCreatedAt(existing.getCreatedAt());
        }
        if (incoming.getCreatedBy() == null) {
            incoming.setCreatedBy(existing.getCreatedBy());
        }
        StationDocument saved = stationRepository.save(incoming);
        radioCacheService.upsertStation(saved);
        return saved;
    }

    public void deleteStation(AdminUser user, String id) {
        StationDocument existing = stationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Station not found"));
        accessService.assertCan(user, AdminModule.STATIONS, AdminAction.DELETE);
        if (!accessService.canViewStation(user, existing)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This station is outside your assigned access");
        }
        audioLinkRepository.deleteByStationId(id);
        stationRepository.deleteById(id);
        radioCacheService.removeStation(id);
    }

    public List<CategoryDocument> listCategories(AdminUser user, String query) {
        accessService.assertCan(user, AdminModule.CATEGORIES, AdminAction.READ);
        return accessService.visibleCategories(user, radioCacheService.getCategoriesForAdmin(), query);
    }

    public PageResponse<CategoryDocument> pageCategories(AdminUser user, String query, int page, int size) {
        return PageResponse.of(listCategories(user, query), page, size);
    }

    public CategoryDocument saveCategory(AdminUser user, CategoryDocument category) {
        accessService.assertCan(user, AdminModule.CATEGORIES, AdminAction.CREATE);
        category.setId(null);
        category.setCreatedBy(user.getUsername());
        if (category.getCreatedAt() == null) {
            category.setCreatedAt(Instant.now());
        }
        CategoryDocument saved = categoryRepository.save(category);
        grantCategoryToSubAdmin(user, saved.getCategory());
        radioCacheService.upsertCategory(saved);
        return saved;
    }

    public CategoryDocument updateCategory(AdminUser user, String id, CategoryDocument incoming) {
        CategoryDocument existing = categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        accessService.assertCan(user, AdminModule.CATEGORIES, AdminAction.UPDATE);
        if (!accessService.canViewCategory(user, existing)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This category is outside your assigned access");
        }
        incoming.setId(existing.getId());
        if (incoming.getCreatedAt() == null) {
            incoming.setCreatedAt(existing.getCreatedAt());
        }
        if (incoming.getCreatedBy() == null) {
            incoming.setCreatedBy(existing.getCreatedBy());
        }
        CategoryDocument saved = categoryRepository.save(incoming);
        radioCacheService.upsertCategory(saved);
        return saved;
    }

    public void deleteCategory(AdminUser user, String id) {
        CategoryDocument existing = categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        accessService.assertCan(user, AdminModule.CATEGORIES, AdminAction.DELETE);
        if (!accessService.canViewCategory(user, existing)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This category is outside your assigned access");
        }
        categoryRepository.deleteById(id);
        radioCacheService.removeCategory(id);
    }

    public List<AudioLinkDocument> listAudioLinks(AdminUser user, String query) {
        return listAudioLinks(user, query, null);
    }

    public List<AudioLinkDocument> listAudioLinks(AdminUser user, String query, String stationId) {
        accessService.assertCan(user, AdminModule.AUDIO_LINKS, AdminAction.READ);
        List<AudioLinkDocument> links = accessService.visibleLinks(
                user,
                radioCacheService.getAllAudioLinksForAdmin(),
                radioCacheService.stationsByIdForAdmin(),
                query);
        if (stationId != null && !stationId.isBlank()) {
            String wanted = RadioDataService.cleanId(stationId);
            links = links.stream()
                    .filter(link -> wanted.equals(RadioDataService.cleanId(link.getStationId())))
                    .toList();
        }
        return links;
    }

    public PageResponse<AudioLinkDocument> pageAudioLinks(AdminUser user, String query, String stationId, int page, int size) {
        return PageResponse.of(listAudioLinks(user, query, stationId), page, size);
    }

    public List<AudioLinkDocument> listAudioLinksByStation(AdminUser user, String stationId) {
        accessService.assertCan(user, AdminModule.AUDIO_LINKS, AdminAction.READ);
        StationDocument station = radioCacheService.stationsByIdForAdmin().get(RadioDataService.cleanId(stationId));
        return listAudioLinks(user, null).stream()
                .filter(link -> RadioDataService.cleanId(stationId).equals(RadioDataService.cleanId(link.getStationId())))
                .filter(link -> accessService.canViewLink(user, link, station))
                .toList();
    }

    public AudioLinkDocument saveAudioLink(AdminUser user, AudioLinkDocument link) {
        accessService.assertCan(user, AdminModule.AUDIO_LINKS, AdminAction.CREATE);
        prepareLink(link);
        StationDocument station = radioCacheService.stationsByIdForAdmin().get(link.getStationId());
        if (station != null) {
            accessService.assertStationCategoryAllowed(user, station.getCategory());
        }
        link.setId(null);
        link.setCreatedBy(user.getUsername());
        AudioLinkDocument saved = audioLinkRepository.save(link);
        radioCacheService.upsertAudioLink(saved);
        return saved;
    }

    public AudioLinkDocument updateAudioLink(AdminUser user, String id, AudioLinkDocument incoming) {
        AudioLinkDocument existing = audioLinkRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Audio link not found"));
        accessService.assertCan(user, AdminModule.AUDIO_LINKS, AdminAction.UPDATE);
        StationDocument existingStation = radioCacheService.stationsByIdForAdmin()
                .get(RadioDataService.cleanId(existing.getStationId()));
        if (!accessService.canViewLink(user, existing, existingStation)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This audio link is outside your assigned access");
        }
        incoming.setId(existing.getId());
        if (incoming.getCreatedAt() == null) {
            incoming.setCreatedAt(existing.getCreatedAt());
        }
        if (incoming.getCreatedBy() == null) {
            incoming.setCreatedBy(existing.getCreatedBy());
        }
        prepareLink(incoming);
        StationDocument station = radioCacheService.stationsByIdForAdmin().get(incoming.getStationId());
        if (station != null) {
            accessService.assertStationCategoryAllowed(user, station.getCategory());
        }
        AudioLinkDocument saved = audioLinkRepository.save(incoming);
        radioCacheService.upsertAudioLink(saved);
        return saved;
    }

    public void deleteAudioLink(AdminUser user, String id) {
        AudioLinkDocument existing = audioLinkRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Audio link not found"));
        accessService.assertCan(user, AdminModule.AUDIO_LINKS, AdminAction.DELETE);
        StationDocument station = radioCacheService.stationsByIdForAdmin()
                .get(RadioDataService.cleanId(existing.getStationId()));
        if (!accessService.canViewLink(user, existing, station)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This audio link is outside your assigned access");
        }
        audioLinkRepository.deleteById(id);
        radioCacheService.removeAudioLink(id);
    }

    public Map<String, Object> reloadCache() {
        Map<String, Object> status = radioCacheService.reloadFromDatabase();
        adminDirectory.reloadFromDatabase();
        status.put("admins", adminDirectory.list().size());
        return status;
    }

    private EventDocument reviewEvent(AdminUser user, String id, String status, EventApprovalRequest request) {
        EventDocument event = findEvent(id);
        accessService.assertCan(user, AdminModule.EVENTS, AdminAction.APPROVE);
        accessService.assertEventInScope(user, event);
        event.setApprovalStatus(status);
        event.setReviewedAt(Instant.now());
        if (request != null) {
            event.setReviewNote(trimToEmpty(request.getReviewNote()));
        }
        return persistEvent(event);
    }

    private EventDocument persistEvent(EventDocument event) {
        if (event.getDate() != null && event.getEndDate() == null) {
            event.setEndDate(event.getDate());
        }
        EventDocument saved = eventRepository.save(event);
        radioCacheService.upsertEvent(saved);
        return saved;
    }

    private EventDocument findEvent(String id) {
        return radioCacheService.getAllEventsForAdmin().stream()
                .filter(event -> id.equals(event.getId()))
                .findFirst()
                .orElseGet(() -> eventRepository.findById(id)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found")));
    }

    private void stampNewEvent(AdminUser user, EventDocument event) {
        event.setCreatedBy(user.getUsername());
        applyEventScope(user, event);
        if (event.getApprovalStatus() == null || event.getApprovalStatus().isBlank()) {
            event.setApprovalStatus("approved");
        }
    }

    private void applyEventScope(AdminUser user, EventDocument event) {
        if (user.isSuperAdmin()) {
            if ((event.getOrganization() == null || event.getOrganization().isBlank())
                    && event.getOrganizedBy() != null) {
                event.setOrganization(event.getOrganizedBy());
            }
            return;
        }
        if (user.getOrganization() != null && !user.getOrganization().isBlank()) {
            event.setOrganization(user.getOrganization());
        } else if (user.getAllowedOrganizations().size() == 1) {
            event.setOrganization(user.getAllowedOrganizations().getFirst());
        }
        if (!user.getAllowedCategoryKeys().isEmpty() && user.getAllowedCategoryKeys().size() == 1
                && (event.getCategory() == null || event.getCategory().isBlank())) {
            event.setCategory(user.getAllowedCategoryKeys().getFirst());
        }
    }

    private void grantCategoryToSubAdmin(AdminUser user, String categoryKey) {
        if (user.isSuperAdmin() || categoryKey == null || categoryKey.isBlank()) {
            return;
        }
        List<String> allowed = new ArrayList<>(user.getAllowedCategoryKeys());
        boolean exists = allowed.stream().anyMatch(key -> key.equalsIgnoreCase(categoryKey));
        if (!exists) {
            allowed.add(categoryKey);
            user.setAllowedCategoryKeys(allowed);
            adminDirectory.put(adminUserRepository.save(user));
        }
    }

    private void prepareLink(AudioLinkDocument link) {
        if (link.getStationId() != null) {
            link.setStationId(RadioDataService.cleanId(link.getStationId()));
        }
        if (link.getCreatedAt() == null) {
            link.setCreatedAt(Instant.now());
        }
        if (link.getPlayed() == null) {
            link.setPlayed(false);
        }
        if (link.getStatus() == null || link.getStatus().isBlank()) {
            link.setStatus(Boolean.TRUE.equals(link.getPlayed()) ? "Y" : "N");
        }
    }

    private static String eventStatus(EventDocument event) {
        if (event.getApprovalStatus() == null || event.getApprovalStatus().isBlank()) {
            return "approved";
        }
        return event.getApprovalStatus().toLowerCase();
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
