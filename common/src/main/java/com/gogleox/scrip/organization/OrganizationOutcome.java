package com.gogleox.scrip.organization;

import java.util.Optional;
import java.util.UUID;

/** Typed organization-operation result; failed authorization never changes state. */
public record OrganizationOutcome(Status status, UUID operationId, Optional<OrganizationSnapshot> organization) {
    public enum Status {
        SUCCESS, UNKNOWN_ORGANIZATION, UNKNOWN_PLAYER, NAME_CONFLICT, ALREADY_MEMBER, INVITATION_MISSING,
        NOT_MEMBER, UNAUTHORIZED, INVALID_ROLE_CHANGE, OWNER_PROTECTED, POLICY_UNRESOLVED,
        OPERATION_CONFLICT, STORAGE_FAILURE
    }
    public OrganizationOutcome { organization = organization == null ? Optional.empty() : organization; }
    public static OrganizationOutcome rejected(Status status, UUID operationId) { return new OrganizationOutcome(status, operationId, Optional.empty()); }
    public static OrganizationOutcome success(UUID operationId, OrganizationSnapshot organization) { return new OrganizationOutcome(Status.SUCCESS, operationId, Optional.of(organization)); }
}
