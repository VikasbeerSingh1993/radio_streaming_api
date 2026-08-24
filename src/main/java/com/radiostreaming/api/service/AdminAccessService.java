package com.radiostreaming.api.service;

import com.radiostreaming.api.model.AdminPermissions;
import com.radiostreaming.api.model.AdminUser;
import com.radiostreaming.api.model.AudioLinkDocument;
import com.radiostreaming.api.model.CategoryDocument;
import com.radiostreaming.api.model.EventDocument;
import com.radiostreaming.api.model.ModulePermission;
import com.radiostreaming.api.model.StationDocument;
import com.radiostreaming.api.security.AdminAction;
import com.radiostreaming.api.security.AdminModule;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class AdminAccessService {

    public boolean isSuper(AdminUser user) {
        return user != null && user.isSuperAdmin();
    }

    public boolean can(AdminUser user, AdminModule module, AdminAction action) {
        if (user == null || !user.isEnabledAccount()) {
            return false;
        }
        if (user.isSuperAdmin()) {
            return true;
        }
        ModulePermission permission = modulePermission(user, module);
        return switch (action) {
            case READ -> permission.isRead() || permission.isCreate() || permission.isUpdate() || permission.isDelete();
            case CREATE -> permission.isCreate();
            case UPDATE -> permission.isUpdate();
            case DELETE -> permission.isDelete();
            case APPROVE -> permission.isApprove();
        };
    }

    public void assertCan(AdminUser user, AdminModule module, AdminAction action) {
        if (!can(user, module, action)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission for this action");
        }
    }

    public boolean canViewEvent(AdminUser user, EventDocument event) {
        if (!can(user, AdminModule.EVENTS, AdminAction.READ)) {
            return false;
        }
        if (isSuper(user)) {
            return true;
        }
        if (user.isOwnRecordsOnly() && !ownedBy(event.getCreatedBy(), user.getUsername())) {
            return false;
        }
        if (!matchesOrganization(user, event)) {
            return false;
        }
        return matchesEventCategory(user, event);
    }

    public boolean canViewCategory(AdminUser user, CategoryDocument category) {
        if (!can(user, AdminModule.CATEGORIES, AdminAction.READ)) {
            return false;
        }
        if (isSuper(user)) {
            return true;
        }
        if (user.isOwnRecordsOnly() && !ownedBy(category.getCreatedBy(), user.getUsername())) {
            return false;
        }
        return matchesAllowedCategory(user, category.getCategory());
    }

    public boolean canViewStation(AdminUser user, StationDocument station) {
        if (!can(user, AdminModule.STATIONS, AdminAction.READ)) {
            return false;
        }
        if (isSuper(user)) {
            return true;
        }
        if (user.isOwnRecordsOnly() && !ownedBy(station.getCreatedBy(), user.getUsername())) {
            return false;
        }
        return matchesAllowedCategory(user, station.getCategory());
    }

    public boolean canViewLink(AdminUser user, AudioLinkDocument link, StationDocument station) {
        if (!can(user, AdminModule.AUDIO_LINKS, AdminAction.READ)) {
            return false;
        }
        if (isSuper(user)) {
            return true;
        }
        if (user.isOwnRecordsOnly() && !ownedBy(link.getCreatedBy(), user.getUsername())) {
            return false;
        }
        String category = station == null ? null : station.getCategory();
        return matchesAllowedCategory(user, category);
    }

    public boolean canMutateEvent(AdminUser user, EventDocument event, AdminAction action) {
        return can(user, AdminModule.EVENTS, action) && canViewEvent(user, event);
    }

    public List<EventDocument> visibleEvents(AdminUser user, Collection<EventDocument> events, String query) {
        Stream<EventDocument> stream = events.stream().filter(event -> canViewEvent(user, event));
        if (hasQuery(query)) {
            String q = query.toLowerCase(Locale.ROOT);
            stream = stream.filter(event -> matches(q,
                    event.getTitle(),
                    event.getCity(),
                    event.getAddress(),
                    event.getOrganizedBy(),
                    event.getOrganization(),
                    event.getCategory(),
                    event.getSubmitterName(),
                    event.getSubmitterEmail(),
                    event.getCreatedBy(),
                    event.getDescription()));
        }
        return stream.toList();
    }

    public List<StationDocument> visibleStations(AdminUser user, Collection<StationDocument> stations, String query) {
        Stream<StationDocument> stream = stations.stream().filter(station -> canViewStation(user, station));
        if (hasQuery(query)) {
            String q = query.toLowerCase(Locale.ROOT);
            stream = stream.filter(station -> matches(q,
                    station.getId(),
                    station.getCategory(),
                    station.getLanguage(),
                    station.getType(),
                    station.getCreatedBy(),
                    translationName(station.getTranslations())));
        }
        return stream.toList();
    }

    public List<CategoryDocument> visibleCategories(AdminUser user, Collection<CategoryDocument> categories, String query) {
        Stream<CategoryDocument> stream = categories.stream().filter(category -> canViewCategory(user, category));
        if (hasQuery(query)) {
            String q = query.toLowerCase(Locale.ROOT);
            stream = stream.filter(category -> matches(q,
                    category.getId(),
                    category.getCategory(),
                    category.getIcon(),
                    category.getCreatedBy(),
                    translationName(category.getTranslations())));
        }
        return stream.toList();
    }

    public List<AudioLinkDocument> visibleLinks(
            AdminUser user,
            Collection<AudioLinkDocument> links,
            Map<String, StationDocument> stationsById,
            String query) {
        Stream<AudioLinkDocument> stream = links.stream().filter(link ->
                canViewLink(user, link, stationsById.get(RadioDataService.cleanId(link.getStationId()))));
        if (hasQuery(query)) {
            String q = query.toLowerCase(Locale.ROOT);
            stream = stream.filter(link -> matches(q,
                    link.getId(),
                    link.getUrl(),
                    link.getStationId(),
                    link.getCreatedBy(),
                    translationName(link.getTranslations())));
        }
        return stream.toList();
    }

    public List<AdminUser> visibleUsers(AdminUser actor, Collection<AdminUser> users, String query) {
        if (!can(actor, AdminModule.USERS, AdminAction.READ) && !isSuper(actor)) {
            return List.of();
        }
        Stream<AdminUser> stream = users.stream();
        if (hasQuery(query)) {
            String q = query.toLowerCase(Locale.ROOT);
            stream = stream.filter(user -> matches(q,
                    user.getUsername(),
                    user.getDisplayName(),
                    user.getOrganization(),
                    user.getRole()));
        }
        return stream.toList();
    }

    public void assertStationCategoryAllowed(AdminUser user, String categoryKey) {
        if (isSuper(user)) {
            return;
        }
        if (!matchesAllowedCategory(user, categoryKey)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This category is outside your assigned access");
        }
    }

    public void assertEventInScope(AdminUser user, EventDocument event) {
        if (!canViewEvent(user, event)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This event is outside your assigned access");
        }
    }

    public Set<String> allowedCategorySet(AdminUser user) {
        return user.getAllowedCategoryKeys().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private boolean matchesAllowedCategory(AdminUser user, String categoryKey) {
        Set<String> allowed = allowedCategorySet(user);
        if (allowed.isEmpty()) {
            return true;
        }
        if (categoryKey == null || categoryKey.isBlank()) {
            return false;
        }
        return allowed.contains(categoryKey.trim().toLowerCase(Locale.ROOT));
    }

    private boolean matchesEventCategory(AdminUser user, EventDocument event) {
        Set<String> allowed = allowedCategorySet(user);
        if (allowed.isEmpty()) {
            return true;
        }
        if (event.getCategory() == null || event.getCategory().isBlank()) {
            return true;
        }
        return allowed.contains(event.getCategory().trim().toLowerCase(Locale.ROOT));
    }

    private boolean matchesOrganization(AdminUser user, EventDocument event) {
        List<String> allowed = user.getAllowedOrganizations();
        if (allowed == null || allowed.isEmpty()) {
            return true;
        }
        Set<String> keys = allowed.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (keys.isEmpty()) {
            return true;
        }
        return containsNormalized(keys, event.getOrganization())
                || containsNormalized(keys, event.getOrganizedBy());
    }

    private static boolean containsNormalized(Set<String> keys, String value) {
        return value != null && keys.contains(value.trim().toLowerCase(Locale.ROOT));
    }

    private static boolean ownedBy(String createdBy, String username) {
        return createdBy != null && username != null && createdBy.equalsIgnoreCase(username);
    }

    private ModulePermission modulePermission(AdminUser user, AdminModule module) {
        AdminPermissions permissions = user.getPermissions() == null
                ? AdminPermissions.none()
                : user.getPermissions();
        return switch (module) {
            case EVENTS -> permissions.getEvents();
            case STATIONS -> permissions.getStations();
            case CATEGORIES -> permissions.getCategories();
            case AUDIO_LINKS -> permissions.getAudioLinks();
            case USERS -> permissions.getUsers();
        };
    }

    private static boolean hasQuery(String query) {
        return query != null && !query.isBlank();
    }

    private static boolean matches(String query, String... values) {
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).contains(query)) {
                return true;
            }
        }
        return false;
    }

    private static String translationName(Map<String, Map<String, String>> translations) {
        if (translations == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        translations.values().forEach(map -> {
            if (map != null) {
                map.values().forEach(value -> {
                    if (value != null) {
                        builder.append(' ').append(value);
                    }
                });
            }
        });
        return builder.toString();
    }
}
