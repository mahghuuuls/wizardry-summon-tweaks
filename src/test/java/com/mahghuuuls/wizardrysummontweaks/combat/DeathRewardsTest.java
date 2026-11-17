package com.mahghuuuls.wizardrysummontweaks.combat;

import com.mahghuuuls.wizardrysummontweaks.config.ServerRules;
import com.mahghuuuls.wizardrysummontweaks.summon.SummonFacts;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

class DeathRewardsTest {
    @Test
    void onlyVerifiedPlayerVictimsDependOnTheDeathRewardRule() {
        UUID confirmed = UUID.randomUUID();
        for (boolean drops : new boolean[]{false, true}) {
            for (boolean players : new boolean[]{false, true}) {
                for (boolean summons : new boolean[]{false, true}) {
                    ServerRules rules = new ServerRules(drops, players, summons);
                    for (SummonFacts.OwnerKind kind : SummonFacts.OwnerKind.values()) {
                        UUID owner = kind == SummonFacts.OwnerKind.UNOWNED ? null : confirmed;
                        SummonFacts facts = new SummonFacts("provider:spell", owner, kind, "test_evidence");
                        assertEquals(drops && kind == SummonFacts.OwnerKind.PLAYER,
                                DeathRewards.suppresses(rules, () -> SummonFacts.reconcile(null, false, facts)));
                    }
                    assertFalse(DeathRewards.suppresses(rules, () -> SummonFacts.reconcile(null, false, null)));
                    assertFalse(DeathRewards.suppresses(rules, () -> SummonFacts.reconcile(null, true, null)));
                }
            }
        }
    }

    @Test
    void disabledRewardsDoNotReadVictimEvidence() {
        assertFalse(DeathRewards.suppresses(new ServerRules(false, true, true),
                () -> fail("Disabled reward rule requested victim evidence")));
    }
}
