package com.mahghuuuls.wizardrysummontweaks.compat.wizardrymixin;

import com.mahghuuuls.wizardrysummontweaks.WizardrySummonTweaksMod;
import com.mahghuuuls.wizardrysummontweaks.combat.AttackProtection;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "electroblob.wizardry.entity.living.ISummonedCreature", remap = false)
public interface WizardryMeleeMixin {
    @Inject(method = "onLivingAttackEvent(Lnet/minecraftforge/event/entity/living/LivingAttackEvent;)V",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    static void wizardrysummontweaks$guardWizardryRewrite(LivingAttackEvent event, CallbackInfo callback) {
        AttackProtection protection = WizardrySummonTweaksMod.attackProtection();
        if (protection != null && protection.blocksAttack(event)) callback.cancel();
    }
}
