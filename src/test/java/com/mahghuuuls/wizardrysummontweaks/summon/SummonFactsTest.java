package com.mahghuuuls.wizardrysummontweaks.summon;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static com.mahghuuuls.wizardrysummontweaks.summon.SummonFacts.OwnerKind.*;

class SummonFactsTest {
    private final UUID owner = UUID.randomUUID();
    private SummonFacts facts(UUID id, SummonFacts.OwnerKind kind) {
        return new SummonFacts("provider:spell", id, kind, "observed_owner");
    }

    @Test void unavailableOwnerRetainsEstablishedFacts() {
        SummonFacts saved = facts(owner, PLAYER);
        assertSame(saved, SummonFacts.reconcile(saved, false, facts(null, UNRESOLVED)).facts);
        assertSame(saved, SummonFacts.reconcile(saved, false, facts(owner, UNRESOLVED)).facts);
        assertSame(saved, SummonFacts.reconcile(saved, false, null).facts);
    }

    @Test void changedUnknownOwnerNeverInheritsPriorPlayerKind() {
        UUID other = UUID.randomUUID();
        SummonFacts.Result result = SummonFacts.reconcile(facts(owner, PLAYER), false, facts(other, UNRESOLVED));
        assertEquals(SummonFacts.Status.UNRESOLVED, result.status);
        assertEquals(other, result.facts.ownerUUID);
    }

    @Test void verifiedTransfersAndRemovalReplaceOldOwnership() {
        SummonFacts saved = facts(owner, PLAYER);
        UUID second = UUID.randomUUID();
        assertEquals(second, SummonFacts.reconcile(saved, false, facts(second, PLAYER)).facts.ownerUUID);
        assertEquals(SummonFacts.Status.EXCLUDED, SummonFacts.reconcile(saved, false, facts(second, NON_PLAYER)).status);
        assertEquals(UNOWNED, SummonFacts.reconcile(saved, false, facts(null, UNOWNED)).facts.ownerKind);
    }

    @Test void uncertaintyAndNoOriginAreDifferent() {
        assertEquals(SummonFacts.Status.EXCLUDED, SummonFacts.reconcile(null, false, null).status);
        assertEquals(SummonFacts.Status.UNRESOLVED, SummonFacts.reconcile(null, false, facts(null, UNRESOLVED)).status);
    }

    @Test void invalidSavedBytesCannotBeOverriddenByLiveEvidence() {
        SummonFacts.Result result = SummonFacts.reconcile(null, true, facts(owner, PLAYER));
        assertFalse(result.affected());
        assertFalse(result.mayPersist);
    }

    @Test void contradictoryOriginsDoNotOverwriteSavedEvidence() {
        SummonFacts saved = facts(owner, PLAYER);
        SummonFacts.Result result = SummonFacts.reconcile(saved, false,
                new SummonFacts("different:origin", owner, PLAYER, "live"));
        assertEquals(SummonFacts.Status.UNRESOLVED, result.status);
        assertFalse(result.mayPersist);
        assertSame(saved, result.facts);
    }

    @Test void knownOwnerRequiresIdentityAndUnownedCannotHaveOne() {
        assertThrows(IllegalArgumentException.class, () -> facts(null, PLAYER));
        assertThrows(IllegalArgumentException.class, () -> facts(owner, UNOWNED));
    }
}
