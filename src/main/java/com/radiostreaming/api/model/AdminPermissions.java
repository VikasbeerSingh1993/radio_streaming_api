package com.radiostreaming.api.model;

public class AdminPermissions {

    private ModulePermission events = ModulePermission.none();
    private ModulePermission stations = ModulePermission.none();
    private ModulePermission categories = ModulePermission.none();
    private ModulePermission audioLinks = ModulePermission.none();
    private ModulePermission users = ModulePermission.none();

    public static AdminPermissions fullAccess() {
        AdminPermissions permissions = new AdminPermissions();
        permissions.events = ModulePermission.full(true);
        permissions.stations = ModulePermission.full(false);
        permissions.categories = ModulePermission.full(false);
        permissions.audioLinks = ModulePermission.full(false);
        permissions.users = ModulePermission.full(false);
        return permissions;
    }

    public static AdminPermissions none() {
        return new AdminPermissions();
    }

    public ModulePermission getEvents() {
        return events == null ? ModulePermission.none() : events;
    }

    public void setEvents(ModulePermission events) {
        this.events = events;
    }

    public ModulePermission getStations() {
        return stations == null ? ModulePermission.none() : stations;
    }

    public void setStations(ModulePermission stations) {
        this.stations = stations;
    }

    public ModulePermission getCategories() {
        return categories == null ? ModulePermission.none() : categories;
    }

    public void setCategories(ModulePermission categories) {
        this.categories = categories;
    }

    public ModulePermission getAudioLinks() {
        return audioLinks == null ? ModulePermission.none() : audioLinks;
    }

    public void setAudioLinks(ModulePermission audioLinks) {
        this.audioLinks = audioLinks;
    }

    public ModulePermission getUsers() {
        return users == null ? ModulePermission.none() : users;
    }

    public void setUsers(ModulePermission users) {
        this.users = users;
    }
}
