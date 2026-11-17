package com.mahghuuuls.wizardrysummontweaks.combat;

import com.mahghuuuls.wizardrysummontweaks.config.ServerRules;

final class MeleeRules {
    enum Participant { PLAYER, AFFECTED_SUMMON, OTHER }

    private MeleeRules() {}

    static boolean blocks(ServerRules rules, Participant actor, Participant victim) {
        return (rules.protectPlayers && actor == Participant.AFFECTED_SUMMON && victim == Participant.PLAYER)
                || (rules.protectSummons && actor == Participant.PLAYER && victim == Participant.AFFECTED_SUMMON);
    }
}
