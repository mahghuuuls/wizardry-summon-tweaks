package com.mahghuuuls.wizardrysummontweaks.compat.wizardrymixin;

import com.mahghuuuls.wizardrysummontweaks.compat.wizardry.WizardrySpellSource;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "electroblob.wizardry.spell.Paralysis", remap = false)
abstract class WizardryParalysisMixin {
    @Inject(method = "onEntityHit(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;"
            + "Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/entity/EntityLivingBase;"
            + "Lnet/minecraft/util/math/Vec3d;ILelectroblob/wizardry/util/SpellModifiers;)Z",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void wizardrysummontweaks$guardTargetEffects(World world, Entity victim, Vec3d hit, EntityLivingBase caster,
                                                        Vec3d origin, int tick, SpellModifiers modifiers,
                                                        CallbackInfoReturnable<Boolean> callback) {
        // The spell can add its status effect even when Forge rejects the preceding damage.
        if (WizardrySpellSource.blocks(caster, victim)) callback.setReturnValue(false);
    }
}
