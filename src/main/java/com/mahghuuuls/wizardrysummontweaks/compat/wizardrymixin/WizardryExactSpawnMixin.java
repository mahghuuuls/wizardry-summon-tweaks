package com.mahghuuuls.wizardrysummontweaks.compat.wizardrymixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mahghuuuls.wizardrysummontweaks.WizardrySummonTweaksMod;
import com.mahghuuuls.wizardrysummontweaks.compat.wizardry.WizardrySummonProvider;
import com.mahghuuuls.wizardrysummontweaks.summon.SummonFacts;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = {
        "electroblob.wizardry.spell.SummonSnowGolem",
        "electroblob.wizardry.spell.SummonSpiritWolf",
        "electroblob.wizardry.spell.SummonSpiritHorse"
}, remap = false)
abstract class WizardryExactSpawnMixin {
    @WrapOperation(method = "cast(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;"
            + "Lnet/minecraft/util/EnumHand;ILelectroblob/wizardry/util/SpellModifiers;)Z",
            at = {
                    @At(value = "INVOKE", target = "Lnet/minecraft/world/World;spawnEntity(Lnet/minecraft/entity/Entity;)Z"),
                    @At(value = "INVOKE", target = "Lnet/minecraft/world/World;func_72838_d(Lnet/minecraft/entity/Entity;)Z")
            }, remap = false, require = 1, allow = 1)
    private boolean wizardrysummontweaks$recordExactSpawn(World world, Entity entity, Operation<Boolean> original,
                                                          @Local(argsOnly = true) EntityPlayer caster) {
        String origin = WizardrySummonProvider.exactOrigin(entity);
        if (origin == null || world.isRemote || world != entity.world || world != caster.world) {
            return original.call(world, entity);
        }
        SummonFacts facts = new SummonFacts(origin, caster.getUniqueID(), SummonFacts.OwnerKind.PLAYER, "exact_spell_caster");
        return WizardrySummonTweaksMod.captureSummonSpawn(entity, facts, () -> original.call(world, entity));
    }
}
