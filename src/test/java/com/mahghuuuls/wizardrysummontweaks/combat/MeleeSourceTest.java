package com.mahghuuuls.wizardrysummontweaks.combat;

import com.mahghuuuls.wizardrysummontweaks.compat.wizardry.WizardryMeleeSource;
import electroblob.wizardry.util.MagicDamage.DamageType;
import electroblob.wizardry.util.MinionDamage;
import net.minecraft.entity.Entity;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.EntityDamageSourceIndirect;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MeleeSourceTest {
    @BeforeAll
    static void initializeMinecraftData() {
        Bootstrap.register();
    }

    @Test
    void wizardryMeleeUsesItsImmediateMinionAndKeepsTheCreditedOwner() {
        Entity minion = new StubEntity();
        Entity owner = new StubEntity();
        DamageSource wrapped = new MinionDamage("mob", minion, owner, DamageType.MAGIC, false);
        assertSame(minion, MeleeSource.actor(wrapped, WizardryMeleeSource::actor));
        assertSame(owner, wrapped.getTrueSource());
        assertSame(minion, wrapped.getImmediateSource());
    }

    @Test
    void unqualifiedCreditAndEnvironmentalSourcesDoNotBecomeMeleeActors() {
        Entity owner = new StubEntity();
        Entity projectile = new StubEntity();
        assertNull(MeleeSource.actor(new EntityDamageSourceIndirect("mob", projectile, owner), WizardryMeleeSource::actor));
        assertNull(MeleeSource.actor(new EntityDamageSource("thorns", owner), WizardryMeleeSource::actor));
        assertNull(MeleeSource.actor(new MinionDamage("thorns", projectile, owner, DamageType.MAGIC, true), WizardryMeleeSource::actor));
        assertNull(MeleeSource.actor(new EntityDamageSource("mob", owner).setProjectile(), WizardryMeleeSource::actor));
        assertNull(MeleeSource.actor(DamageSource.IN_FIRE, WizardryMeleeSource::actor));
        assertNull(MeleeSource.actor(null, WizardryMeleeSource::actor));
        assertSame(owner, MeleeSource.actor(new EntityDamageSource("player", owner), WizardryMeleeSource::actor));
    }

    private static final class StubEntity extends Entity {
        StubEntity() { super(null); }
        @Override protected void entityInit() {}
        @Override protected void readEntityFromNBT(NBTTagCompound tag) {}
        @Override protected void writeEntityToNBT(NBTTagCompound tag) {}
    }
}
