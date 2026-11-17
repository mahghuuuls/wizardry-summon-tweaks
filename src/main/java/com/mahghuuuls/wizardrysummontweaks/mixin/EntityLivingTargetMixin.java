package com.mahghuuuls.wizardrysummontweaks.mixin;

import com.mahghuuuls.wizardrysummontweaks.WizardrySummonTweaksMod;
import com.mahghuuuls.wizardrysummontweaks.combat.TargetProtection;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityLiving.class)
abstract class EntityLivingTargetMixin {
    @Inject(method = "setAttackTarget", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void wizardrysummontweaks$beforeTargetAssignment(EntityLivingBase candidate, CallbackInfo callback) {
        TargetProtection protection = WizardrySummonTweaksMod.targetProtection();
        if (protection != null && protection.rejects((EntityLiving) (Object) this, candidate)) callback.cancel();
    }

    @Inject(method = "updateEntityActionState", at = @At("HEAD"), require = 1, allow = 1)
    private void wizardrysummontweaks$clearBeforeAi(CallbackInfo callback) {
        TargetProtection protection = WizardrySummonTweaksMod.targetProtection();
        if (protection != null) protection.clearForbidden((EntityLiving) (Object) this);
    }
}
