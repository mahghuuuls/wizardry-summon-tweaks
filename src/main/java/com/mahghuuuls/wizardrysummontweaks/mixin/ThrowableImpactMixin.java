package com.mahghuuuls.wizardrysummontweaks.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mahghuuuls.wizardrysummontweaks.WizardrySummonTweaksMod;
import com.mahghuuuls.wizardrysummontweaks.combat.AttackProtection;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.RayTraceResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityThrowable.class)
abstract class ThrowableImpactMixin {
    // Vanilla assigns its ignored entity before checking this candidate's intersection.
    @ModifyExpressionValue(method = "onUpdate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/math/AxisAlignedBB;calculateIntercept(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/RayTraceResult;"),
            require = 1, allow = 1)
    private RayTraceResult wizardrysummontweaks$skipProtectedIntersection(RayTraceResult hit, @Local(index = 10) Entity candidate) {
        AttackProtection protection = WizardrySummonTweaksMod.attackProtection();
        return hit != null && protection != null && protection.blocksProjectileVictim((EntityThrowable) (Object) this, candidate)
                ? null : hit;
    }

    @Inject(method = "writeEntityToNBT", at = @At("HEAD"), require = 1)
    private void wizardrysummontweaks$saveThrownLauncher(NBTTagCompound tag, CallbackInfo callback) {
        AttackProtection protection = WizardrySummonTweaksMod.attackProtection();
        if (protection != null) protection.saveProjectile((EntityThrowable) (Object) this);
    }
}
