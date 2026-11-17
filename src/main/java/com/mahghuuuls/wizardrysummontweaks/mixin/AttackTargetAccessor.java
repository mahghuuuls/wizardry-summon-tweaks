package com.mahghuuuls.wizardrysummontweaks.mixin;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityLiving.class)
public interface AttackTargetAccessor {
    @Accessor("attackTarget")
    EntityLivingBase wizardrysummontweaks$getAttackTarget();

    @Accessor("attackTarget")
    void wizardrysummontweaks$setAttackTarget(EntityLivingBase target);
}
