package com.mahghuuuls.wizardrysummontweaks.compat.wizardrymixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mahghuuuls.wizardrysummontweaks.compat.wizardry.WizardrySpellSource;
import electroblob.wizardry.entity.construct.EntityStormcloud;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

@Mixin(targets = "electroblob.wizardry.entity.construct.EntityStormcloud", remap = false)
abstract class WizardryStormcloudMixin {
    @WrapOperation(method = {"onUpdate()V", "func_70071_h_()V"}, at = @At(value = "INVOKE",
            target = "Lelectroblob/wizardry/util/EntityUtils;attackEntityWithoutKnockback("
                    + "Lnet/minecraft/entity/Entity;Lnet/minecraft/util/DamageSource;F)Z"),
            remap = false, require = 1, allow = 1)
    private boolean wizardrysummontweaks$guardStrike(Entity victim, DamageSource source, float damage, Operation<Boolean> original) {
        return !wizardrysummontweaks$blocks(victim) && original.call(victim, source, damage);
    }

    // Keep native per-strike duration bookkeeping, while omitting the protected hit's two sounds.
    @WrapOperation(method = {"onUpdate()V", "func_70071_h_()V"}, at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityLivingBase;playSound(Lnet/minecraft/util/SoundEvent;FF)V"),
            @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityLivingBase;func_184185_a(Lnet/minecraft/util/SoundEvent;FF)V")
    }, remap = false, require = 2, allow = 2)
    private void wizardrysummontweaks$guardHitSound(EntityLivingBase victim, SoundEvent sound, float volume,
                                                   float pitch, Operation<Void> original) {
        if (!wizardrysummontweaks$blocks(victim)) original.call(victim, sound, volume, pitch);
    }

    // Only the ring's separate pull selector uses this list. Let it choose the closest allowed entity.
    @ModifyExpressionValue(method = {"onUpdate()V", "func_70071_h_()V"}, at = @At(value = "INVOKE",
            target = "Lelectroblob/wizardry/util/EntityUtils;getLivingWithinRadius("
                    + "DDDDLnet/minecraft/world/World;)Ljava/util/List;"),
            remap = false, require = 1, allow = 1)
    private List<EntityLivingBase> wizardrysummontweaks$guardPull(List<EntityLivingBase> candidates) {
        Entity caster = WizardrySpellSource.stormCaster((EntityStormcloud) (Object) this);
        if (caster == null || candidates.isEmpty()) return candidates;
        List<EntityLivingBase> allowed = null;
        for (int i = 0; i < candidates.size(); i++) {
            EntityLivingBase candidate = candidates.get(i);
            if (WizardrySpellSource.blocks(caster, candidate)) {
                if (allowed == null) allowed = new ArrayList<>(candidates.subList(0, i));
            } else if (allowed != null) {
                allowed.add(candidate);
            }
        }
        return allowed == null ? candidates : allowed;
    }

    @Unique
    private boolean wizardrysummontweaks$blocks(Entity victim) {
        return WizardrySpellSource.blocks(WizardrySpellSource.stormCaster((EntityStormcloud) (Object) this), victim);
    }
}
