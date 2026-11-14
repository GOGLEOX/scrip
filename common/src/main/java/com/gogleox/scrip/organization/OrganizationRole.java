package com.gogleox.scrip.organization;

/** Fixed baseline organization roles; custom permission editing is intentionally absent. */
public enum OrganizationRole {
    OWNER,
    MANAGER,
    MEMBER;

    public boolean canManageMembers() { return this == OWNER || this == MANAGER; }
    public boolean canSpendFunds() { return this == OWNER || this == MANAGER; }
}
