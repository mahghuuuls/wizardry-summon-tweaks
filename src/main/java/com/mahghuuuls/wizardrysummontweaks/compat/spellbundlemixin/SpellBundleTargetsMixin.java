package com.mahghuuuls.wizardrysummontweaks.compat.spellbundlemixin;

import com.mahghuuuls.wizardrysummontweaks.WizardrySummonTweaksMod;
import com.mahghuuuls.wizardrysummontweaks.combat.TargetProtection;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.windanesz.spellbundle.capability.SummonedCreatureData", remap = false)
abstract class SpellBundleTargetsMixin {
    @Shadow @Final private EntityLivingBase minion;

    @Inject(method = "isValidTarget(Lnet/minecraft/entity/Entity;)Z", at = @At("HEAD"),
            cancellable = true, require = 1, allow = 1)
    private void wizardrysummontweaks$guardTargetSelection(Entity candidate, CallbackInfoReturnable<Boolean> callback) {
        TargetProtection protection = WizardrySummonTweaksMod.targetProtection();
        if (protection != null && minion instanceof EntityLiving && protection.rejects((EntityLiving) minion, candidate)) {
            callback.setReturnValue(false);
        }
    }
}
