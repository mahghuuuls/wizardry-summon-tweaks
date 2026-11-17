package com.mahghuuuls.wizardrysummontweaks.summon;

import java.util.Objects;
import java.util.UUID;

/** Established spell origin and current ownership, independent of a live caster. */
public final class SummonFacts {
    public enum OwnerKind { PLAYER, NON_PLAYER, UNOWNED, UNRESOLVED }
    public enum Status { AFFECTED, EXCLUDED, UNRESOLVED }

    public final String originProvider;
    public final UUID ownerUUID;
    public final OwnerKind ownerKind;
    public final String ownershipEvidence;

    public SummonFacts(String originProvider, UUID ownerUUID, OwnerKind ownerKind, String ownershipEvidence) {
        if (originProvider == null || originProvider.isEmpty() || originProvider.length() > 128
                || ownershipEvidence == null || ownershipEvidence.isEmpty() || ownershipEvidence.length() > 128) {
            throw new IllegalArgumentException("Origin and evidence must be bounded, nonempty identifiers");
        }
        if ((ownerKind == OwnerKind.PLAYER || ownerKind == OwnerKind.NON_PLAYER) && ownerUUID == null) {
            throw new IllegalArgumentException("Confirmed ownership requires a UUID");
        }
        if (ownerKind == OwnerKind.UNOWNED && ownerUUID != null) {
            throw new IllegalArgumentException("Explicitly unowned facts cannot retain an owner UUID");
        }
        this.originProvider = originProvider;
        this.ownerUUID = ownerUUID;
        this.ownerKind = Objects.requireNonNull(ownerKind, "ownerKind");
        this.ownershipEvidence = ownershipEvidence;
    }

    public Status status() {
        if (ownerKind == OwnerKind.PLAYER) return Status.AFFECTED;
        if (ownerKind == OwnerKind.UNRESOLVED) return Status.UNRESOLVED;
        return Status.EXCLUDED;
    }

    /** A null observation is no claim; unresolved null ownership is unavailable, never removal. */
    public static Result reconcile(SummonFacts saved, boolean invalidSavedData, SummonFacts observation) {
        if (invalidSavedData) return new Result(null, Status.UNRESOLVED, "invalid_or_unknown_saved_data", false);
        if (saved != null && observation != null && !saved.originProvider.equals(observation.originProvider)) {
            return new Result(saved, Status.UNRESOLVED, "conflicting_origin_evidence", false);
        }
        SummonFacts result = observation;
        if (observation == null || (observation.ownerKind == OwnerKind.UNRESOLVED
                && (observation.ownerUUID == null || (saved != null && observation.ownerUUID.equals(saved.ownerUUID))))) {
            result = saved != null ? saved : observation;
        }
        if (result == null) return new Result(null, Status.EXCLUDED, "no_verified_spell_origin", true);
        return new Result(result, result.status(), result.ownershipEvidence, true);
    }

    public static final class Result {
        public final SummonFacts facts;
        public final Status status;
        public final String reason;
        public final boolean mayPersist;

        public Result(SummonFacts facts, Status status, String reason, boolean mayPersist) {
            this.facts = facts;
            this.status = status;
            this.reason = reason;
            this.mayPersist = mayPersist;
        }

        public boolean affected() { return status == Status.AFFECTED; }
    }
}
