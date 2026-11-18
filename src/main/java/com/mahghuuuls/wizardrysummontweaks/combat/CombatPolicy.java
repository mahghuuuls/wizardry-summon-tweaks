package com.mahghuuuls.wizardrysummontweaks.combat;

import com.mahghuuuls.wizardrysummontweaks.config.ServerRules;
import com.mahghuuuls.wizardrysummontweaks.summon.SummonTracker;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;

/** Applies the server's directed protection rules to classified participants. */
public final class CombatPolicy {
    enum Participant { PLAYER, AFFECTED_SUMMON, OTHER }

    private final ServerRules rules;
    private final SummonTracker tracker;

    public CombatPolicy(ServerRules rules, SummonTracker tracker) {
        this.rules = rules;
        this.tracker = tracker;
    }

    boolean hasProtection() {
        return rules.protectPlayers || rules.protectSummons;
    }

    public boolean blocksPair(Entity actor, Entity victim) {
        if (actor == null || actor.world != victim.world || victim.world.isRemote) return false;
        if (actor instanceof EntityPlayer) return blocksPair(Participant.PLAYER, victim);
        return victim instanceof EntityPlayer && protectsPlayerFrom(actor);
    }

    boolean blocksPair(Participant actor, Entity victim) {
        if (actor == Participant.PLAYER && !(victim instanceof EntityPlayer)) {
            return blocks(rules, Participant.PLAYER, Participant.AFFECTED_SUMMON) && tracker.inspect(victim).affected();
        }
        return victim instanceof EntityPlayer && blocks(rules, actor, Participant.PLAYER);
    }

    public boolean blocksPlayerTarget(EntityLiving actor, Entity candidate) {
        return candidate instanceof EntityPlayer && !actor.world.isRemote && protectsPlayerFrom(actor);
    }

    private boolean protectsPlayerFrom(Entity actor) {
        return blocks(rules, Participant.AFFECTED_SUMMON, Participant.PLAYER) && tracker.inspect(actor).affected();
    }

    static boolean blocks(ServerRules rules, Participant actor, Participant victim) {
        return (rules.protectPlayers && actor == Participant.AFFECTED_SUMMON && victim == Participant.PLAYER)
                || (rules.protectSummons && actor == Participant.PLAYER && victim == Participant.AFFECTED_SUMMON);
    }
}
