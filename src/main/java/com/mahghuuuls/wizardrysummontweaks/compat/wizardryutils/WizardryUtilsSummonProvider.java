package com.mahghuuuls.wizardrysummontweaks.compat.wizardryutils;

import com.mahghuuuls.wizardrysummontweaks.summon.SummonFacts;
import com.mahghuuuls.wizardrysummontweaks.summon.SummonProvider;
import com.windanesz.wizardryutils.capability.SummonedCreatureData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

public final class WizardryUtilsSummonProvider implements SummonProvider {
    @Override
    public boolean matches(Entity entity) {
        return SummonedCreatureData.isSummonedEntity(entity);
    }

    @Override
    public SummonFacts observe(Entity entity, SummonFacts saved) {
        SummonedCreatureData data = SummonedCreatureData.get((EntityLivingBase) entity);
        return SummonProvider.observeCurrentOwner("wizardryutils:summoned_creature", entity, data.getOwnerId());
    }
}
