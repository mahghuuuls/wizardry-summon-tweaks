package com.mahghuuuls.wizardrysummontweaks.compat.wizardrymixin;

import com.mahghuuuls.wizardrysummontweaks.compat.wizardry.WizardrySpellSource;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "electroblob.wizardry.spell.Firestorm", remap = false)
abstract class WizardryFirestormMixin {
    @Inject(method = "affectEntity(Lnet/minecraft/world/World;Lnet/minecraft/util/math/Vec3d;"
            + "Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/entity/EntityLivingBase;II"
            + "Lelectroblob/wizardry/util/SpellModifiers;)Z",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void wizardrysummontweaks$guardIgnition(World world, Vec3d origin, EntityLivingBase caster,
                                                    EntityLivingBase victim, int elapsed, int duration,
                                                    SpellModifiers modifiers, CallbackInfoReturnable<Boolean> callback) {
        // Preserve the per-target completion result so only this victim's ignition is skipped.
        if (WizardrySpellSource.blocks(caster, victim)) callback.setReturnValue(true);
    }
}
