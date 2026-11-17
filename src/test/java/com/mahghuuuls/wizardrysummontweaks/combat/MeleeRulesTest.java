package com.mahghuuuls.wizardrysummontweaks.combat;

import com.mahghuuuls.wizardrysummontweaks.config.ServerRules;
import org.junit.jupiter.api.Test;

import static com.mahghuuuls.wizardrysummontweaks.combat.MeleeRules.Participant.*;
import static org.junit.jupiter.api.Assertions.*;

class MeleeRulesTest {
    @Test
    void onlyTheMatchingDirectionCanBlockAcrossAllEightSettings() {
        for (boolean drops : new boolean[]{false, true}) {
            for (boolean players : new boolean[]{false, true}) {
                for (boolean summons : new boolean[]{false, true}) {
                    ServerRules rules = new ServerRules(drops, players, summons);
                    assertEquals(players, MeleeRules.blocks(rules, AFFECTED_SUMMON, PLAYER));
                    assertEquals(summons, MeleeRules.blocks(rules, PLAYER, AFFECTED_SUMMON));
                    assertFalse(MeleeRules.blocks(rules, AFFECTED_SUMMON, AFFECTED_SUMMON));
                    assertFalse(MeleeRules.blocks(rules, PLAYER, PLAYER));
                    for (MeleeRules.Participant participant : MeleeRules.Participant.values()) {
                        assertFalse(MeleeRules.blocks(rules, OTHER, participant));
                        assertFalse(MeleeRules.blocks(rules, participant, OTHER));
                    }
                }
            }
        }
    }
}
