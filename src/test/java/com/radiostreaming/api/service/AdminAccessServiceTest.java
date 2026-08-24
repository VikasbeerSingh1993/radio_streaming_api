package com.radiostreaming.api.service;

import com.radiostreaming.api.model.AdminPermissions;
import com.radiostreaming.api.model.AdminUser;
import com.radiostreaming.api.model.EventDocument;
import com.radiostreaming.api.model.ModulePermission;
import com.radiostreaming.api.model.StationDocument;
import com.radiostreaming.api.security.AdminAction;
import com.radiostreaming.api.security.AdminModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminAccessServiceTest {

    private AdminAccessService access;

    @BeforeEach
    void setUp() {
        access = new AdminAccessService();
    }

    @Test
    void superAdminCanDoEverything() {
        AdminUser admin = superAdmin();
        assertTrue(access.can(admin, AdminModule.EVENTS, AdminAction.DELETE));
        assertTrue(access.can(admin, AdminModule.USERS, AdminAction.CREATE));
    }

    @Test
    void subAdminHonorsIndependentCrudFlags() {
        AdminUser sub = subAdmin();
        sub.getPermissions().setEvents(permission(true, false, true, false, false));

        assertTrue(access.can(sub, AdminModule.EVENTS, AdminAction.READ));
        assertTrue(access.can(sub, AdminModule.EVENTS, AdminAction.UPDATE));
        assertFalse(access.can(sub, AdminModule.EVENTS, AdminAction.CREATE));
        assertFalse(access.can(sub, AdminModule.EVENTS, AdminAction.DELETE));
        assertFalse(access.can(sub, AdminModule.STATIONS, AdminAction.READ));
    }

    @Test
    void organizationScopeHidesOtherEvents() {
        AdminUser sub = subAdmin();
        sub.getPermissions().setEvents(permission(true, true, true, true, true));
        sub.setAllowedOrganizations(List.of("dodra"));

        EventDocument dodra = new EventDocument();
        dodra.setOrganization("dodra");
        dodra.setTitle("Dodra Sangat");
        EventDocument other = new EventDocument();
        other.setOrganization("amritsar");
        other.setTitle("Amritsar");

        List<EventDocument> visible = access.visibleEvents(sub, List.of(dodra, other), null);
        assertEquals(1, visible.size());
        assertEquals("Dodra Sangat", visible.getFirst().getTitle());
    }

    @Test
    void categoryScopeLimitsStations() {
        AdminUser sub = subAdmin();
        sub.getPermissions().setStations(permission(true, true, true, false, false));
        sub.setAllowedCategoryKeys(List.of("live_kirtan"));

        StationDocument allowed = new StationDocument();
        allowed.setCategory("live_kirtan");
        StationDocument blocked = new StationDocument();
        blocked.setCategory("katha");

        assertEquals(1, access.visibleStations(sub, List.of(allowed, blocked), null).size());
        assertFalse(access.can(sub, AdminModule.STATIONS, AdminAction.DELETE));
    }

    @Test
    void ownRecordsOnlyHidesOthersEvents() {
        AdminUser sub = subAdmin();
        sub.setUsername("sevadar");
        sub.setOwnRecordsOnly(true);
        sub.getPermissions().setEvents(permission(true, true, true, true, false));

        EventDocument mine = new EventDocument();
        mine.setCreatedBy("sevadar");
        mine.setTitle("Mine");
        EventDocument theirs = new EventDocument();
        theirs.setCreatedBy("admin");
        theirs.setTitle("Theirs");

        List<EventDocument> visible = access.visibleEvents(sub, List.of(mine, theirs), "mine");
        assertEquals(1, visible.size());
        assertEquals("Mine", visible.getFirst().getTitle());
    }

    private static AdminUser superAdmin() {
        AdminUser user = new AdminUser();
        user.setUsername("admin");
        user.setRole("SUPER_ADMIN");
        user.setEnabled(true);
        user.setPermissions(AdminPermissions.fullAccess());
        return user;
    }

    private static AdminUser subAdmin() {
        AdminUser user = new AdminUser();
        user.setUsername("helper");
        user.setRole("SUB_ADMIN");
        user.setEnabled(true);
        user.setPermissions(AdminPermissions.none());
        return user;
    }

    private static ModulePermission permission(boolean read, boolean create, boolean update, boolean delete, boolean approve) {
        ModulePermission permission = new ModulePermission();
        permission.setRead(read);
        permission.setCreate(create);
        permission.setUpdate(update);
        permission.setDelete(delete);
        permission.setApprove(approve);
        return permission;
    }
}
