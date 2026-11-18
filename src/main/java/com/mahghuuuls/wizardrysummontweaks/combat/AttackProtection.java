package com.mahghuuuls.wizardrysummontweaks.combat;

import com.mahghuuuls.wizardrysummontweaks.summon.SummonTracker;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Enforces the same directed rule before native hit effects and provider rewrites. */
public final class AttackProtection {
    private final CombatPolicy policy;
    private final Function<DamageSource, Entity> wrappedActor;
    private final Function<DamageSource, Entity> spellCaster;
    private final ProjectileAttribution projectiles;

    public AttackProtection(CombatPolicy policy, SummonTracker tracker, Function<DamageSource, Entity> wrappedActor,
                            Function<DamageSource, Entity> spellCaster) {
        this.policy = policy;
        this.wrappedActor = wrappedActor;
        this.spellCaster = spellCaster;
        projectiles = new ProjectileAttribution(tracker);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingAttack(LivingAttackEvent event) {
        blocksAttack(event);
    }

    /** Also called before the installed Wizardry handler can rewrite or apply hit effects. */
    public boolean blocksAttack(LivingAttackEvent event) {
        EntityLivingBase victim = event.getEntityLiving();
        if (victim.world.isRemote || !policy.hasProtection()) return false;
        DamageSource source = event.getSource();
        boolean blocked;
        if (ProjectileAttribution.qualifies(source)) {
            Entity projectile = source.getImmediateSource();
            blocked = projectile.world == victim.world
                    && blocksProjectileVictim(projectiles.resolve(projectile, wrappedActor.apply(source)), victim);
        } else {
            Entity actor = MeleeSource.actor(source, wrappedActor);
            blocked = policy.blocksPair(actor == null ? spellCaster.apply(source) : actor, victim);
        }
        if (!blocked) return false;
        event.setCanceled(true);
        return true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onProjectileJoin(EntityJoinWorldEvent event) {
        Entity projectile = event.getEntity();
        if (!event.getWorld().isRemote && projectile.world == event.getWorld()) projectiles.rememberLaunch(projectile);
    }

    public void saveProjectile(Entity projectile) {
        if (!projectile.world.isRemote) projectiles.saveLauncher(projectile);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onProjectileImpact(ProjectileImpactEvent event) {
        Entity projectile = event.getEntity();
        RayTraceResult hit = event.getRayTraceResult();
        if (hit != null && hit.typeOfHit == RayTraceResult.Type.ENTITY
                && blocksProjectileVictim(projectile, hit.entityHit)) event.setCanceled(true);
    }

    public boolean blocksProjectileVictim(Entity projectile, Entity victim) {
        return victim != null && victim.world == projectile.world && qualifiesImpact(projectile)
                && blocksProjectileVictim(projectiles.resolve(projectile, null), victim);
    }

    private boolean blocksProjectileVictim(ProjectileAttribution.Result attack, Entity victim) {
        return attack.projectile.world == victim.world && policy.blocksPair(attack.participant, victim);
    }

    public String describeProjectile(Entity entity) {
        if (!ProjectileAttribution.qualifies(entity)) return null;
        ProjectileAttribution.Result attack = projectiles.resolve(entity, null);
        return "Projectile actor=" + (attack.actor == null ? "unknown" : attack.actor) + " role=" + attack.participant
                + " evidence=" + attack.evidence + (attack.reason == null ? "" : " reason=" + attack.reason);
    }

    /** Before nearest-hit selection, keep every eligible candidate in its native order. */
    public List<Entity> projectileCandidates(Entity projectile, List<Entity> candidates) {
        if (candidates.isEmpty() || !qualifiesImpact(projectile)) return candidates;
        ProjectileAttribution.Result attack = projectiles.resolve(projectile, null);
        if (attack.participant == CombatPolicy.Participant.OTHER) return candidates;
        List<Entity> allowed = null;
        for (int i = 0; i < candidates.size(); i++) {
            Entity candidate = candidates.get(i);
            if (blocksProjectileVictim(attack, candidate)) {
                if (allowed == null) allowed = new ArrayList<>(candidates.subList(0, i));
            } else if (allowed != null) {
                allowed.add(candidate);
            }
        }
        return allowed == null ? candidates : allowed;
    }

    private boolean qualifiesImpact(Entity projectile) {
        return !projectile.world.isRemote && policy.hasProtection() && ProjectileAttribution.qualifies(projectile);
    }
}
