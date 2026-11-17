package com.mahghuuuls.wizardrysummontweaks.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mahghuuuls.wizardrysummontweaks.WizardrySummonTweaksMod;
import com.mahghuuuls.wizardrysummontweaks.combat.CombatPolicy;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(EntityPlayer.class)
abstract class PlayerMeleeMixin {
    @Inject(method = "attackTargetEntityWithCurrentItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/Entity;canBeAttackedWithItem()Z"),
            cancellable = true, require = 1, allow = 1)
    private void wizardrysummontweaks$beforePrimaryMelee(Entity victim, CallbackInfo callback) {
        CombatPolicy protection = WizardrySummonTweaksMod.combatPolicy();
        if (protection != null && protection.blocksPair((EntityPlayer) (Object) this, victim)) callback.cancel();
    }

    // Vanilla knocks sweep victims back before their individual damage events.
    @ModifyExpressionValue(method = "attackTargetEntityWithCurrentItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/World;getEntitiesWithinAABB(Ljava/lang/Class;Lnet/minecraft/util/math/AxisAlignedBB;)Ljava/util/List;"),
            require = 1, allow = 1)
    private List<EntityLivingBase> wizardrysummontweaks$filterSweepVictims(List<EntityLivingBase> candidates) {
        CombatPolicy protection = WizardrySummonTweaksMod.combatPolicy();
        if (protection == null) return candidates;
        EntityPlayer attacker = (EntityPlayer) (Object) this;
        List<EntityLivingBase> allowed = null;
        for (int i = 0; i < candidates.size(); i++) {
            EntityLivingBase candidate = candidates.get(i);
            if (protection.blocksPair(attacker, candidate)) {
                if (allowed == null) allowed = new ArrayList<>(candidates.subList(0, i));
            } else if (allowed != null) {
                allowed.add(candidate);
            }
        }
        return allowed == null ? candidates : allowed;
    }
}
