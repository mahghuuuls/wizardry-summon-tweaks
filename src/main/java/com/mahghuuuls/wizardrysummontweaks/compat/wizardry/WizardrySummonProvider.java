package com.mahghuuuls.wizardrysummontweaks.compat.wizardry;

import com.mahghuuuls.wizardrysummontweaks.summon.SummonFacts;
import com.mahghuuuls.wizardrysummontweaks.summon.SummonProvider;
import electroblob.wizardry.entity.living.ISummonedCreature;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.WorldServer;

import java.util.UUID;

public final class WizardrySummonProvider implements SummonProvider {
    private static final UUID MISSING_SERIALIZED_OWNER = new UUID(0, 0);
    @Override
    public boolean matches(Entity entity) { return entity instanceof ISummonedCreature; }

    @Override
    public SummonFacts observe(Entity entity) {
        ISummonedCreature creature = (ISummonedCreature) entity;
        UUID rawOwner = creature.getOwnerId();
        // Wizardry's getCaster scans every loaded entity. Server UUID lookup has the same loaded-world boundary.
        Entity found = rawOwner == null ? null : ((WorldServer) entity.world).getEntityFromUuid(rawOwner);
        EntityLivingBase caster = found instanceof EntityLivingBase ? (EntityLivingBase) found : null;
        UUID uuid = availableOwnerId(creature, caster == null ? null : caster.getUniqueID());
        SummonFacts.OwnerKind kind;
        String evidence;
        if (caster != null && caster.getUniqueID().equals(uuid)) {
            kind = caster instanceof EntityPlayer ? SummonFacts.OwnerKind.PLAYER : SummonFacts.OwnerKind.NON_PLAYER;
            evidence = caster instanceof EntityPlayer ? "live_player_caster" : "live_non_player_caster";
        } else {
            kind = SummonFacts.OwnerKind.UNRESOLVED;
            evidence = uuid == null ? "caster_unavailable" : "owner_kind_unresolved";
        }
        return new SummonFacts("ebwizardry:summoned_creature", uuid, kind, evidence);
    }

    static UUID availableOwnerId(ISummonedCreature creature, UUID resolvedCaster) {
        UUID owner = creature.getOwnerId();
        // Wizardry reads an omitted casterUUID with NBT.getUniqueId, yielding zero. That is not a transfer.
        // Preserve a zero UUID only when an actual matching live caster independently confirms it.
        return MISSING_SERIALIZED_OWNER.equals(owner) && !MISSING_SERIALIZED_OWNER.equals(resolvedCaster) ? null : owner;
    }
}
