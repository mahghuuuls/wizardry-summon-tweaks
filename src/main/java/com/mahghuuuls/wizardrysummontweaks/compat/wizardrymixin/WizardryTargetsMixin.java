package com.mahghuuuls.wizardrysummontweaks.compat.wizardrymixin;

import com.google.common.base.Predicate;
import com.mahghuuuls.wizardrysummontweaks.WizardrySummonTweaksMod;
import com.mahghuuuls.wizardrysummontweaks.combat.TargetProtection;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "electroblob.wizardry.entity.living.ISummonedCreature", remap = false)
public interface WizardryTargetsMixin {
    @Inject(method = "getTargetSelector()Lcom/google/common/base/Predicate;", at = @At("RETURN"),
            cancellable = true, require = 1, allow = 1)
    default void wizardrysummontweaks$filterTargetSelector(CallbackInfoReturnable<Predicate<Entity>> callback) {
        if (!((Object) this instanceof EntityLiving)) return;
        EntityLiving summon = (EntityLiving) (Object) this;
        Predicate<Entity> original = callback.getReturnValue();
        callback.setReturnValue(candidate -> {
            TargetProtection protection = WizardrySummonTweaksMod.targetProtection();
            return (protection == null || !protection.rejects(summon, candidate)) && original.apply(candidate);
        });
    }

    @Inject(method = "shouldRevengeTarget(Lnet/minecraft/entity/EntityLivingBase;)Z", at = @At("HEAD"),
            cancellable = true, require = 1, allow = 1)
    default void wizardrysummontweaks$guardRevenge(EntityLivingBase candidate, CallbackInfoReturnable<Boolean> callback) {
        TargetProtection protection = WizardrySummonTweaksMod.targetProtection();
        if (protection != null && (Object) this instanceof EntityLiving && protection.rejects((EntityLiving) (Object) this, candidate)) {
            callback.setReturnValue(false);
        }
    }
}
