package com.mahghuuuls.wizardrysummontweaks.combat;

import com.mahghuuuls.wizardrysummontweaks.config.ServerRules;
import org.junit.jupiter.api.Test;

import static com.mahghuuuls.wizardrysummontweaks.combat.CombatPolicy.Participant.*;
import static org.junit.jupiter.api.Assertions.*;

class CombatPolicyTest {
    @Test
    void onlyTheMatchingDirectionCanBlockAcrossAllEightSettings() {
        for (boolean drops : new boolean[]{false, true}) {
            for (boolean players : new boolean[]{false, true}) {
                for (boolean summons : new boolean[]{false, true}) {
                    ServerRules rules = new ServerRules(drops, players, summons);
                    assertEquals(players, CombatPolicy.blocks(rules, AFFECTED_SUMMON, PLAYER));
                    assertEquals(summons, CombatPolicy.blocks(rules, PLAYER, AFFECTED_SUMMON));
                    assertFalse(CombatPolicy.blocks(rules, AFFECTED_SUMMON, AFFECTED_SUMMON));
                    assertFalse(CombatPolicy.blocks(rules, PLAYER, PLAYER));
                    for (CombatPolicy.Participant participant : CombatPolicy.Participant.values()) {
                        assertFalse(CombatPolicy.blocks(rules, OTHER, participant));
                        assertFalse(CombatPolicy.blocks(rules, participant, OTHER));
                    }
                }
            }
        }
    }
}
