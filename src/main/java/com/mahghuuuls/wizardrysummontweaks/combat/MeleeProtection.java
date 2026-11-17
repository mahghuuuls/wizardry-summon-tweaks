package com.mahghuuuls.wizardrysummontweaks.combat;

import com.mahghuuuls.wizardrysummontweaks.config.ServerRules;
import com.mahghuuuls.wizardrysummontweaks.summon.SummonTracker;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.function.Function;

import static com.mahghuuuls.wizardrysummontweaks.combat.MeleeRules.Participant.*;

/** One decision for direct melee, its early effect boundaries, and provider rewrites. */
public final class MeleeProtection {
    private final ServerRules rules;
    private final SummonTracker tracker;
    private final Function<DamageSource, Entity> wrappedActor;

    public MeleeProtection(ServerRules rules, SummonTracker tracker, Function<DamageSource, Entity> wrappedActor) {
        this.rules = rules;
        this.tracker = tracker;
        this.wrappedActor = wrappedActor;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingAttack(LivingAttackEvent event) {
        blocksAttack(event);
    }

    /** Also called before the installed Wizardry handler can rewrite or apply hit effects. */
    public boolean blocksAttack(LivingAttackEvent event) {
        EntityLivingBase victim = event.getEntityLiving();
        if (victim.world.isRemote || (!rules.protectPlayers && !rules.protectSummons)) return false;
        Entity actor = MeleeSource.actor(event.getSource(), wrappedActor);
        if (!blocksPair(actor, victim)) return false;
        event.setCanceled(true);
        return true;
    }

    public boolean blocksPair(Entity actor, Entity victim) {
        if (actor == null || actor.world != victim.world || victim.world.isRemote) return false;
        boolean actorPlayer = actor instanceof EntityPlayer;
        boolean victimPlayer = victim instanceof EntityPlayer;
        if (actorPlayer == victimPlayer) return false;
        if (actorPlayer) {
            if (!rules.protectSummons) return false;
            return MeleeRules.blocks(rules, PLAYER, participant(victim));
        }
        if (!rules.protectPlayers) return false;
        return MeleeRules.blocks(rules, participant(actor), PLAYER);
    }

    private MeleeRules.Participant participant(Entity entity) {
        return tracker.inspect(entity).affected() ? AFFECTED_SUMMON : OTHER;
    }
}
