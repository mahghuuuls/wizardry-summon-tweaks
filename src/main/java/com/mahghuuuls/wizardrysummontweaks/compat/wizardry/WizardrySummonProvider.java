package com.mahghuuuls.wizardrysummontweaks.compat.wizardry;

import com.mahghuuuls.wizardrysummontweaks.summon.SummonFacts;
import com.mahghuuuls.wizardrysummontweaks.summon.SummonProvider;
import electroblob.wizardry.data.IStoredVariable;
import electroblob.wizardry.data.WizardData;
import electroblob.wizardry.entity.living.EntitySpiritHorse;
import electroblob.wizardry.entity.living.EntitySpiritWolf;
import electroblob.wizardry.entity.living.ISummonedCreature;
import electroblob.wizardry.spell.SummonSpiritHorse;
import electroblob.wizardry.spell.SummonSpiritWolf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntitySnowman;
import net.minecraft.entity.player.EntityPlayer;

import java.util.UUID;

import static com.mahghuuuls.wizardrysummontweaks.summon.SummonProvider.loadedOwner;
import static com.mahghuuuls.wizardrysummontweaks.summon.SummonProvider.ownerFacts;

public final class WizardrySummonProvider implements SummonProvider {
    private static final String SNOW_GOLEM = "ebwizardry:summon_snow_golem";
    private static final String SPIRIT_HORSE = "ebwizardry:summon_spirit_horse";
    private static final String SPIRIT_WOLF = "ebwizardry:summon_spirit_wolf";
    private static final UUID MISSING_SERIALIZED_OWNER = new UUID(0, 0);

    /** Used at the exact spell spawn call and to match stored origin to its expected runtime type. */
    public static String exactOrigin(Entity entity) {
        if (entity instanceof EntitySpiritWolf) return SPIRIT_WOLF;
        if (entity instanceof EntitySpiritHorse) return SPIRIT_HORSE;
        if (entity.getClass() == EntitySnowman.class) return SNOW_GOLEM;
        return null;
    }

    @Override
    public boolean matches(Entity entity) {
        return entity instanceof ISummonedCreature || exactOrigin(entity) != null;
    }

    @Override
    public SummonFacts observe(Entity entity, SummonFacts saved) {
        if (entity instanceof ISummonedCreature) {
            ISummonedCreature creature = (ISummonedCreature) entity;
            EntityLivingBase caster = loadedOwner(entity, creature.getOwnerId());
            UUID uuid = availableOwnerId(creature, caster == null ? null : caster.getUniqueID());
            return ownerFacts("ebwizardry:summoned_creature", uuid, caster);
        }
        if (entity instanceof EntitySpiritWolf) {
            EntitySpiritWolf wolf = (EntitySpiritWolf) entity;
            return observeSpirit(wolf, SPIRIT_WOLF, wolf.getOwnerId(), wolf.isTamed(), SummonSpiritWolf.UUID_KEY, saved);
        }
        if (entity instanceof EntitySpiritHorse) {
            EntitySpiritHorse horse = (EntitySpiritHorse) entity;
            return observeSpirit(horse, SPIRIT_HORSE, horse.getOwnerUniqueId(), horse.isTame(), SummonSpiritHorse.UUID_KEY, saved);
        }
        return null;
    }

    private static SummonFacts observeSpirit(Entity entity, String origin, UUID owner, boolean tamed,
                                            IStoredVariable<UUID> link, SummonFacts saved) {
        EntityLivingBase caster = tamed ? loadedOwner(entity, owner) : null;
        if (saved != null && origin.equals(saved.originProvider)) {
            // Native tame and ownership fields agree when a spirit becomes unowned.
            // The native owner UUID is saved independently of whether the player is online.
            if (!tamed && owner == null) {
                return new SummonFacts(origin, null, SummonFacts.OwnerKind.UNOWNED, "spirit_ownership_removed");
            }
            if (caster instanceof EntityPlayer && owner.equals(saved.ownerUUID) && saved.ownerKind == SummonFacts.OwnerKind.PLAYER) {
                return saved;
            }
            return ownerFacts(origin, tamed ? owner : null, caster);
        }
        if (!(caster instanceof EntityPlayer)) return null;
        WizardData data = WizardData.get((EntityPlayer) caster);
        if (data == null || !entity.getUniqueID().equals(data.getVariable(link))) return null;
        return new SummonFacts(origin, owner, SummonFacts.OwnerKind.PLAYER, "legacy_wizardry_spell_link");
    }

    static UUID availableOwnerId(ISummonedCreature creature, UUID resolvedCaster) {
        UUID owner = creature.getOwnerId();
        // Wizardry reads an omitted casterUUID with NBT.getUniqueId, yielding zero. That is not a transfer.
        // Preserve a zero UUID only when an actual matching live caster independently confirms it.
        return MISSING_SERIALIZED_OWNER.equals(owner) && !MISSING_SERIALIZED_OWNER.equals(resolvedCaster) ? null : owner;
    }
}
