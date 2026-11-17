package com.mahghuuuls.wizardrysummontweaks.combat;

import com.mahghuuuls.wizardrysummontweaks.mixin.AttackTargetAccessor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;

/** Enforces the player target rule at selection, assignment and pre-AI boundaries. */
public final class TargetProtection {
    private final CombatPolicy policy;

    public TargetProtection(CombatPolicy policy) {
        this.policy = policy;
    }

    public boolean rejects(EntityLiving actor, Entity candidate) {
        return policy.blocksPlayerTarget(actor, candidate);
    }

    public void clearForbidden(EntityLiving actor) {
        if (actor.world.isRemote) return;
        AttackTargetAccessor field = (AttackTargetAccessor) actor;
        EntityLivingBase previous = field.wizardrysummontweaks$getAttackTarget();
        if (!rejects(actor, previous)) return;
        actor.setAttackTarget(null);
        // A competing target authority can veto null. Only clear the same forbidden target.
        if (field.wizardrysummontweaks$getAttackTarget() == previous) {
            field.wizardrysummontweaks$setAttackTarget(null);
        }
    }
}
