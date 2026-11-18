package com.mahghuuuls.wizardrysummontweaks.compat.fish;

import com.Fishmod.mod_LavaCow.entities.tameable.EntityLilSludge;
import com.mahghuuuls.wizardrysummontweaks.summon.SummonFacts;
import com.mahghuuuls.wizardrysummontweaks.summon.SummonProvider;
import net.minecraft.entity.Entity;

import java.util.UUID;

public final class FishSummonProvider implements SummonProvider {
    public static final String SLUDGE_WAND = "mod_lavacow:sludge_wand";

    @Override
    public boolean matches(Entity entity) {
        return entity instanceof EntityLilSludge;
    }

    @Override
    public SummonFacts observe(Entity entity, SummonFacts saved) {
        // Staff casts and NPC spells create the same class. Only the tracked staff spawn establishes origin.
        if (saved == null || !SLUDGE_WAND.equals(saved.originProvider)) return null;
        EntityLilSludge sludge = (EntityLilSludge) entity;
        UUID nativeOwner = sludge.getOwnerId();
        if (!sludge.isTamed() && nativeOwner == null) {
            return new SummonFacts(SLUDGE_WAND, null, SummonFacts.OwnerKind.UNOWNED, "sludge_ownership_removed");
        }
        UUID owner = sludge.isTamed() ? nativeOwner : null;
        SummonFacts current = SummonProvider.ownerFacts(SLUDGE_WAND, owner, SummonProvider.loadedOwner(sludge, owner));
        return current.ownerKind == saved.ownerKind && owner != null && owner.equals(saved.ownerUUID) ? saved : current;
    }
}
