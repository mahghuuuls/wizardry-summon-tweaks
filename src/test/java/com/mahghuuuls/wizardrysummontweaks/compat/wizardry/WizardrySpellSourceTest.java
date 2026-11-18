package com.mahghuuuls.wizardrysummontweaks.compat.wizardry;

import electroblob.wizardry.util.MagicDamage;
import electroblob.wizardry.util.MagicDamage.DamageType;
import electroblob.wizardry.util.MinionDamage;
import net.minecraft.entity.Entity;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WizardrySpellSourceTest {
    @BeforeAll static void initializeMinecraftData() {
        Bootstrap.register();
    }

    @Test
    void directMagicAndItsRewriteKeepTheActualCaster() {
        Entity caster = new StubEntity();
        Entity owner = new StubEntity();
        DamageSource direct = MagicDamage.causeDirectMagicDamage(caster, DamageType.SHOCK);
        DamageSource wrapped = new MinionDamage(direct.damageType, caster, owner, DamageType.SHOCK, false);
        assertSame(caster, WizardrySpellSource.caster(direct));
        assertSame(caster, WizardrySpellSource.caster(wrapped));
        assertSame(owner, wrapped.getTrueSource());
    }

    @Test
    void unrelatedIndirectCreditAndRetaliationDoNotEstablishACaster() {
        Entity immediate = new StubEntity();
        Entity credited = new StubEntity();
        assertNull(WizardrySpellSource.caster(MagicDamage.causeIndirectMagicDamage(immediate, credited, DamageType.SHOCK)));
        assertNull(WizardrySpellSource.caster(MagicDamage.causeDirectMagicDamage(credited, DamageType.SHOCK, true)));
        assertNull(WizardrySpellSource.caster(MagicDamage.causeDirectMagicDamage(credited, DamageType.SHOCK).setExplosion()));
        assertNull(WizardrySpellSource.caster(DamageSource.ON_FIRE));
    }

    private static final class StubEntity extends Entity {
        StubEntity() { super(null); }
        @Override protected void entityInit() {}
        @Override protected void readEntityFromNBT(NBTTagCompound tag) {}
        @Override protected void writeEntityToNBT(NBTTagCompound tag) {}
    }
}
