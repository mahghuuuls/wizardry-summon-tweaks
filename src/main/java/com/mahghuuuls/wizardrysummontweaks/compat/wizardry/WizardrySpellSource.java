package com.mahghuuuls.wizardrysummontweaks.compat.wizardry;

import com.mahghuuuls.wizardrysummontweaks.WizardrySummonTweaksMod;
import com.mahghuuuls.wizardrysummontweaks.combat.CombatPolicy;
import electroblob.wizardry.entity.construct.EntityStormcloud;
import electroblob.wizardry.util.IElementalDamage;
import electroblob.wizardry.util.IndirectMagicDamage;
import electroblob.wizardry.util.IndirectMinionDamage;
import electroblob.wizardry.util.MagicDamage;
import electroblob.wizardry.util.MinionDamage;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import net.minecraft.world.WorldServer;

import java.util.UUID;

public final class WizardrySpellSource {
    private WizardrySpellSource() {}

    public static Entity caster(DamageSource source) {
        if (!(source instanceof IElementalDamage) || source.isExplosion()) return null;
        IElementalDamage magic = (IElementalDamage) source;
        if (magic.isRetaliatory() || magic.getType() == null) return null;

        Entity immediate = source.getImmediateSource();
        if (MagicDamage.getDirectDamageNameForType(magic.getType()).equals(source.damageType)) {
            if (source.getClass() == MagicDamage.class) return immediate;
            if (source instanceof MinionDamage) return WizardryMeleeSource.actor(source);
        }
        if ((source.getClass() == IndirectMagicDamage.class || source instanceof IndirectMinionDamage)
                && immediate != null && immediate.getClass() == EntityStormcloud.class
                && MagicDamage.getIndirectDamageNameForType(magic.getType()).equals(source.damageType)) {
            Entity actor = stormCaster((EntityStormcloud) immediate);
            if (actor != null) return actor;
            if (source instanceof IndirectMinionDamage) return WizardryMeleeSource.actor(source);
            // The credited entity of an unqualified indirect source is not the caster.
        }
        return null;
    }

    public static EntityLivingBase stormCaster(EntityStormcloud cloud) {
        if (!(cloud.world instanceof WorldServer)) return null;
        UUID uuid = cloud.getOwnerId();
        if (uuid == null) return null;
        Entity owner = ((WorldServer) cloud.world).getEntityFromUuid(uuid);
        return owner instanceof EntityLivingBase && owner.world == cloud.world ? (EntityLivingBase) owner : null;
    }

    public static boolean blocks(Entity caster, Entity victim) {
        CombatPolicy policy = WizardrySummonTweaksMod.combatPolicy();
        return policy != null && policy.blocksPair(caster, victim);
    }
}
