package com.mahghuuuls.wizardrysummontweaks.combat;

import com.mahghuuuls.wizardrysummontweaks.mixin.ThrowableOwnerAccessor;
import com.mahghuuuls.wizardrysummontweaks.summon.ProjectileData;
import com.mahghuuuls.wizardrysummontweaks.summon.SummonFacts;
import com.mahghuuuls.wizardrysummontweaks.summon.SummonTracker;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntitySnowball;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSourceIndirect;
import net.minecraft.world.WorldServer;

import java.util.UUID;

/** Resolves only qualified projectile formats. Kill credit is never evidence about the launcher. */
final class ProjectileAttribution {
    private final SummonTracker tracker;

    ProjectileAttribution(SummonTracker tracker) {
        this.tracker = tracker;
    }

    static boolean qualifies(Entity projectile) {
        return projectile != null && (projectile.getClass() == EntityTippedArrow.class || projectile.getClass() == EntitySnowball.class);
    }

    static boolean qualifies(DamageSource source) {
        if (!(source instanceof EntityDamageSourceIndirect) || !source.isProjectile() || source.isExplosion()) return false;
        Entity projectile = source.getImmediateSource();
        return qualifies(projectile) && (projectile instanceof EntityTippedArrow
                ? "arrow".equals(source.damageType) : "thrown".equals(source.damageType));
    }

    void rememberLaunch(Entity projectile) {
        if (!qualifies(projectile) || ProjectileData.hasLaunchRecord(projectile)) return;
        Entity live = liveActor(projectile, pendingOwner(projectile));
        if (!validActor(projectile, live)) return;
        if (live instanceof EntityPlayer) {
            ProjectileData.remember(projectile, live, null);
        } else {
            SummonFacts.Result result = tracker.inspect(live);
            if (result.affected()) ProjectileData.remember(projectile, live, result.facts);
        }
    }

    void saveLauncher(Entity projectile) {
        if (!qualifies(projectile) || !ProjectileData.hasLaunchRecord(projectile)) return;
        Entity live = liveActor(projectile, pendingOwner(projectile));
        if (live == null) return;
        if (!validActor(projectile, live)) {
            ProjectileData.updateOnSave(projectile, null, null);
        } else if (live instanceof EntityPlayer) {
            ProjectileData.updateOnSave(projectile, live, null);
        } else {
            SummonFacts.Result result = tracker.inspect(live);
            ProjectileData.updateOnSave(projectile, result.affected() ? live : null, result.affected() ? result.facts : null);
        }
    }

    Result resolve(Entity projectile, Entity wrappedMinion) {
        String pendingOwner = pendingOwner(projectile);
        Entity live = liveActor(projectile, pendingOwner);
        if (live != null) return classify(projectile, live, "native_launcher");
        if (wrappedMinion != null) {
            return ownerAgrees(pendingOwner, wrappedMinion.getUniqueID())
                    ? classify(projectile, wrappedMinion, "wizardry_minion")
                    : unresolved(projectile, "conflicting_native_launcher");
        }
        ProjectileData.Launch launch = ProjectileData.read(projectile);
        if (launch == null) {
            return unresolved(projectile, ProjectileData.hasLaunchRecord(projectile)
                    ? "invalid_or_foreign_launch_record" : "launcher_unverified");
        }
        if (!ownerAgrees(pendingOwner, launch.actor)) return unresolved(projectile, "conflicting_native_launcher");
        Entity current = loadedEntity(projectile, launch.actor);
        if (current != null) return classify(projectile, current, "live_saved_launcher");
        CombatPolicy.Participant participant = launch.summon == null
                ? CombatPolicy.Participant.PLAYER : CombatPolicy.Participant.AFFECTED_SUMMON;
        return new Result(projectile, launch.actor, participant, "saved_launch", null);
    }

    static boolean ownerAgrees(String pendingOwner, UUID actor) {
        return pendingOwner == null || pendingOwner.isEmpty() || actor.toString().equalsIgnoreCase(pendingOwner);
    }

    private Result classify(Entity projectile, Entity actor, String evidence) {
        if (!validActor(projectile, actor)) return unresolved(projectile, "invalid_live_launcher");
        if (actor instanceof EntityPlayer) {
            return new Result(projectile, actor.getUniqueID(), CombatPolicy.Participant.PLAYER, evidence, null);
        }
        SummonFacts.Result facts = tracker.inspect(actor);
        CombatPolicy.Participant participant = facts.affected() ? CombatPolicy.Participant.AFFECTED_SUMMON : CombatPolicy.Participant.OTHER;
        return new Result(projectile, actor.getUniqueID(), participant, evidence, facts.reason);
    }

    private static boolean validActor(Entity projectile, Entity actor) {
        return actor != null && actor != projectile && actor.world == projectile.world;
    }

    private static String pendingOwner(Entity projectile) {
        return projectile instanceof EntitySnowball
                ? ((ThrowableOwnerAccessor) projectile).wizardrysummontweaks$pendingOwner() : null;
    }

    private static Entity liveActor(Entity projectile, String pendingOwner) {
        if (projectile instanceof EntityTippedArrow) return ((EntityTippedArrow) projectile).shootingEntity;
        Entity cached = ((ThrowableOwnerAccessor) projectile).wizardrysummontweaks$cachedThrower();
        if (cached != null || pendingOwner == null || pendingOwner.isEmpty()) return cached;
        // Match vanilla's loaded-entity lookup without updating its cached thrower.
        Entity named = projectile.world.getPlayerEntityByName(pendingOwner);
        if (named != null) return named;
        try {
            Entity entity = loadedEntity(projectile, UUID.fromString(pendingOwner));
            return entity instanceof EntityLivingBase ? entity : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Entity loadedEntity(Entity projectile, UUID actor) {
        return projectile.world instanceof WorldServer ? ((WorldServer) projectile.world).getEntityFromUuid(actor) : null;
    }

    private static Result unresolved(Entity projectile, String reason) {
        return new Result(projectile, null, CombatPolicy.Participant.OTHER, "unresolved", reason);
    }

    static final class Result {
        final Entity projectile;
        final UUID actor;
        final CombatPolicy.Participant participant;
        final String evidence;
        final String reason;

        Result(Entity projectile, UUID actor, CombatPolicy.Participant participant, String evidence, String reason) {
            this.projectile = projectile;
            this.actor = actor;
            this.participant = participant;
            this.evidence = evidence;
            this.reason = reason;
        }
    }
}
