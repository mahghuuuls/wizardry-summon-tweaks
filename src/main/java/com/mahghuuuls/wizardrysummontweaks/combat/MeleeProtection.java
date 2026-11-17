package com.mahghuuuls.wizardrysummontweaks.combat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.function.Function;

/** One decision for direct melee, its early effect boundaries, and provider rewrites. */
public final class MeleeProtection {
    private final CombatPolicy policy;
    private final Function<DamageSource, Entity> wrappedActor;

    public MeleeProtection(CombatPolicy policy, Function<DamageSource, Entity> wrappedActor) {
        this.policy = policy;
        this.wrappedActor = wrappedActor;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingAttack(LivingAttackEvent event) {
        blocksAttack(event);
    }

    /** Also called before the installed Wizardry handler can rewrite or apply hit effects. */
    public boolean blocksAttack(LivingAttackEvent event) {
        EntityLivingBase victim = event.getEntityLiving();
        if (victim.world.isRemote || !policy.hasProtection()) return false;
        Entity actor = MeleeSource.actor(event.getSource(), wrappedActor);
        if (!policy.blocksPair(actor, victim)) return false;
        event.setCanceled(true);
        return true;
    }
}
