package com.ssok.server.domain.space.entity;

public enum SpaceRole {
    OWNER,
    EDITOR,
    VIEWER;

    public boolean canWrite() {
        return this == OWNER || this == EDITOR;
    }

    public boolean canSearch() {
        return canWrite();
    }
}
