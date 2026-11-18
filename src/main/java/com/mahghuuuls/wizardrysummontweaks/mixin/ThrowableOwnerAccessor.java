package com.mahghuuuls.wizardrysummontweaks.mixin;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityThrowable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityThrowable.class)
public interface ThrowableOwnerAccessor {
    @Accessor("thrower")
    EntityLivingBase wizardrysummontweaks$cachedThrower();

    @Accessor("throwerName")
    String wizardrysummontweaks$pendingOwner();
}
