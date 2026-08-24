package com.radiostreaming.api.model;

public class ModulePermission {

    private boolean read;
    private boolean create;
    private boolean update;
    private boolean delete;
    private boolean approve;

    public static ModulePermission none() {
        return new ModulePermission();
    }

    public static ModulePermission full(boolean includeApprove) {
        ModulePermission permission = new ModulePermission();
        permission.read = true;
        permission.create = true;
        permission.update = true;
        permission.delete = true;
        permission.approve = includeApprove;
        return permission;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public boolean isCreate() {
        return create;
    }

    public void setCreate(boolean create) {
        this.create = create;
    }

    public boolean isUpdate() {
        return update;
    }

    public void setUpdate(boolean update) {
        this.update = update;
    }

    public boolean isDelete() {
        return delete;
    }

    public void setDelete(boolean delete) {
        this.delete = delete;
    }

    public boolean isApprove() {
        return approve;
    }

    public void setApprove(boolean approve) {
        this.approve = approve;
    }
}
