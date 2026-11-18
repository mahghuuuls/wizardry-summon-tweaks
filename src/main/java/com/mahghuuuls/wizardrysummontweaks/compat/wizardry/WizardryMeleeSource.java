package com.mahghuuuls.wizardrysummontweaks.compat.wizardry;

import com.mahghuuuls.wizardrysummontweaks.compat.wizardrymixin.IndirectMinionAccessor;
import electroblob.wizardry.util.IndirectMinionDamage;
import electroblob.wizardry.util.MinionDamage;
import net.minecraft.entity.Entity;
import net.minecraft.util.DamageSource;

public final class WizardryMeleeSource {
    private WizardryMeleeSource() {}

    public static Entity actor(DamageSource source) {
        if (source instanceof IndirectMinionDamage) {
            return ((IndirectMinionAccessor) source).wizardrysummontweaks$minion();
        }
        // Wizardry puts the minion in the immediate slot and its credited caster in the true slot.
        return source instanceof MinionDamage ? source.getImmediateSource() : null;
    }
}
