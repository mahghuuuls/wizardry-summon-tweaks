package com.mahghuuuls.wizardrysummontweaks.combat;

import com.mahghuuuls.wizardrysummontweaks.summon.ProjectileData;
import com.mahghuuuls.wizardrysummontweaks.summon.SummonTracker;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSourceIndirect;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.apache.logging.log4j.LogManager;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProjectileAttributionTest {
    @BeforeAll
    static void initializeMinecraftData() {
        Bootstrap.register();
    }

    @Test
    void onlyNativeArrowDamageQualifiesEvenWithAnArrowInTheSource() {
        EntityTippedArrow arrow = new EntityTippedArrow(null);
        assertTrue(ProjectileAttribution.qualifies(DamageSource.causeArrowDamage(arrow, arrow)));
        assertFalse(ProjectileAttribution.qualifies(new EntityDamageSourceIndirect("indirectMagic", arrow, arrow).setProjectile()));
        assertFalse(ProjectileAttribution.qualifies(new EntityDamageSourceIndirect("arrow", arrow, arrow)));
        assertFalse(ProjectileAttribution.qualifies(DamageSource.causeArrowDamage(arrow, arrow).setExplosion()));
        assertFalse(ProjectileAttribution.qualifies(DamageSource.IN_FIRE));
    }

    @Test
    void unresolvedExplicitNativeOwnerCannotInheritPreviousActorRole() {
        UUID actor = UUID.randomUUID();
        assertTrue(ProjectileAttribution.ownerAgrees(null, actor));
        assertTrue(ProjectileAttribution.ownerAgrees(actor.toString(), actor));
        assertFalse(ProjectileAttribution.ownerAgrees(UUID.randomUUID().toString(), actor));
        assertFalse(ProjectileAttribution.ownerAgrees("PreviousPlayerName", actor));
    }

    @Test
    void currentActorOrVerifiedWrapperSupersedesStoredActorKind() {
        SummonTracker tracker = new SummonTracker(LogManager.getLogger(ProjectileAttributionTest.class));
        ProjectileAttribution attribution = new ProjectileAttribution(tracker);
        EntityTippedArrow arrow = new EntityTippedArrow(null);
        Entity saved = new OtherEntity();
        Entity competing = new OtherEntity();
        ProjectileData.remember(arrow, saved, null);
        ProjectileAttribution.Result restored = attribution.resolve(arrow, null);
        assertEquals(CombatPolicy.Participant.PLAYER, restored.participant);
        assertEquals(saved.getUniqueID(), restored.actor);
        assertSame(arrow, restored.projectile);
        ProjectileAttribution.Result wrapped = attribution.resolve(arrow, competing);
        assertEquals(CombatPolicy.Participant.OTHER, wrapped.participant);
        assertEquals(competing.getUniqueID(), wrapped.actor);
        arrow.shootingEntity = competing;
        assertEquals(CombatPolicy.Participant.OTHER, attribution.resolve(arrow, null).participant);
        arrow.shootingEntity = null;
        assertEquals(CombatPolicy.Participant.PLAYER, attribution.resolve(arrow, null).participant);
    }

    private static final class OtherEntity extends Entity {
        OtherEntity() { super(null); }
        @Override protected void entityInit() {}
        @Override protected void readEntityFromNBT(NBTTagCompound tag) {}
        @Override protected void writeEntityToNBT(NBTTagCompound tag) {}
    }
}
