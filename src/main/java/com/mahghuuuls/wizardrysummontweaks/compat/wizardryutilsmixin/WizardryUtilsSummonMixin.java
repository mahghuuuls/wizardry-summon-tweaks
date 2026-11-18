package com.mahghuuuls.wizardrysummontweaks.compat.wizardryutilsmixin;

import com.mahghuuuls.wizardrysummontweaks.WizardrySummonTweaksMod;
import com.mahghuuuls.wizardrysummontweaks.combat.AttackProtection;
import com.mahghuuuls.wizardrysummontweaks.combat.TargetProtection;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.windanesz.wizardryutils.capability.SummonedCreatureData", remap = false)
abstract class WizardryUtilsSummonMixin {
    @Shadow @Final private EntityLivingBase minion;

    @Inject(method = "isValidTarget(Lnet/minecraft/entity/Entity;)Z", at = @At("HEAD"),
            cancellable = true, require = 1, allow = 1)
    private void wizardrysummontweaks$guardTargetSelection(Entity candidate, CallbackInfoReturnable<Boolean> callback) {
        TargetProtection protection = WizardrySummonTweaksMod.targetProtection();
        if (protection != null && minion instanceof EntityLiving && protection.rejects((EntityLiving) minion, candidate)) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "onLivingAttackEvent(Lnet/minecraftforge/event/entity/living/LivingAttackEvent;)V",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void wizardrysummontweaks$guardBeforeOwnerRewrite(LivingAttackEvent event, CallbackInfo callback) {
        // The provider can replace the incoming attack at the same Forge priority as the common handler.
        AttackProtection protection = WizardrySummonTweaksMod.attackProtection();
        if (protection != null && protection.blocksAttack(event)) callback.cancel();
    }
}
