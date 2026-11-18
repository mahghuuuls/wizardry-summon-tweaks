package com.mahghuuuls.wizardrysummontweaks.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mahghuuuls.wizardrysummontweaks.WizardrySummonTweaksMod;
import com.mahghuuuls.wizardrysummontweaks.combat.AttackProtection;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(EntityArrow.class)
abstract class ArrowImpactMixin {
    @ModifyExpressionValue(method = "findEntityOnPath", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/World;getEntitiesInAABBexcluding(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/AxisAlignedBB;Lcom/google/common/base/Predicate;)Ljava/util/List;"),
            require = 1, allow = 1)
    private List<Entity> wizardrysummontweaks$selectEligibleArrowHits(List<Entity> candidates) {
        AttackProtection protection = WizardrySummonTweaksMod.attackProtection();
        return protection == null ? candidates : protection.projectileCandidates((EntityArrow) (Object) this, candidates);
    }

    @Inject(method = "writeEntityToNBT", at = @At("HEAD"), require = 1)
    private void wizardrysummontweaks$saveArrowLauncher(NBTTagCompound tag, CallbackInfo callback) {
        AttackProtection protection = WizardrySummonTweaksMod.attackProtection();
        if (protection != null) protection.saveProjectile((EntityArrow) (Object) this);
    }
}
