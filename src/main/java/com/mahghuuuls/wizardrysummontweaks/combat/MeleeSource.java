package com.mahghuuuls.wizardrysummontweaks.combat;

import net.minecraft.entity.Entity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.EntityDamageSourceIndirect;

import java.util.function.Function;

/** Resolves direct melee. An indirect source needs a qualified provider lookup, even if it credits a player. */
final class MeleeSource {
    private MeleeSource() {}

    static Entity actor(DamageSource source, Function<DamageSource, Entity> wrappedActor) {
        if (!(source instanceof EntityDamageSource) || source.isProjectile() || source.isExplosion()) return null;
        // Wizardry also wraps Thorns and retains the original damage type.
        if (!"player".equals(source.damageType) && !"mob".equals(source.damageType)) return null;
        if (source instanceof EntityDamageSourceIndirect) return wrappedActor.apply(source);
        return source.getTrueSource();
    }
}
