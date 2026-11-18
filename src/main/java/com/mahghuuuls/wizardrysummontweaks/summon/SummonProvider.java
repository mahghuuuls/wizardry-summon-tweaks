package com.mahghuuuls.wizardrysummontweaks.summon;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.WorldServer;

import java.util.UUID;

/** Optional source knowledge; no policy or persistence belongs in a provider. */
public interface SummonProvider {
    boolean matches(Entity entity);
    /** For a matching entity, return verified origin and ownership evidence or null if origin remains unproven. */
    SummonFacts observe(Entity entity, SummonFacts saved);

    static EntityLivingBase loadedOwner(Entity entity, UUID owner) {
        if (owner == null || !(entity.world instanceof WorldServer)) return null;
        // Consult the loaded-world UUID index without resolving or loading entities elsewhere.
        Entity found = ((WorldServer) entity.world).getEntityFromUuid(owner);
        return found instanceof EntityLivingBase && found.world == entity.world ? (EntityLivingBase) found : null;
    }

    static SummonFacts ownerFacts(String origin, UUID uuid, EntityLivingBase owner) {
        if (owner != null && owner.getUniqueID().equals(uuid)) {
            boolean player = owner instanceof EntityPlayer;
            return new SummonFacts(origin, uuid, player ? SummonFacts.OwnerKind.PLAYER : SummonFacts.OwnerKind.NON_PLAYER,
                    player ? "live_player_caster" : "live_non_player_caster");
        }
        return new SummonFacts(origin, uuid, SummonFacts.OwnerKind.UNRESOLVED,
                uuid == null ? "caster_unavailable" : "owner_kind_unresolved");
    }

    /** Use only when the stored owner UUID survives absence and a null field authoritatively means unowned. */
    static SummonFacts observeCurrentOwner(String origin, Entity summon, UUID owner) {
        if (owner == null) return new SummonFacts(origin, null, SummonFacts.OwnerKind.UNOWNED, "provider_owner_unset");
        return ownerFacts(origin, owner, loadedOwner(summon, owner));
    }
}
