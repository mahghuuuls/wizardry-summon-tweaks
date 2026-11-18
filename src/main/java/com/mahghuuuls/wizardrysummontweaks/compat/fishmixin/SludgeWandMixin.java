package com.mahghuuuls.wizardrysummontweaks.compat.fishmixin;

import com.Fishmod.mod_LavaCow.entities.tameable.EntityLilSludge;
import com.Fishmod.mod_LavaCow.init.FishItems;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mahghuuuls.wizardrysummontweaks.WizardrySummonTweaksMod;
import com.mahghuuuls.wizardrysummontweaks.compat.fish.FishSummonProvider;
import com.mahghuuuls.wizardrysummontweaks.summon.SummonFacts;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(targets = "com.Fishmod.mod_LavaCow.item.ItemFishCustomWeapon", remap = false)
abstract class SludgeWandMixin {
    @WrapOperation(method = {
            "onItemRightClick(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/EnumHand;)Lnet/minecraft/util/ActionResult;",
            "func_77659_a(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/EnumHand;)Lnet/minecraft/util/ActionResult;"
    }, slice = @Slice(from = @At(value = "FIELD", opcode = Opcodes.GETSTATIC,
            target = "Lcom/Fishmod/mod_LavaCow/init/FishItems;SLUDGE_WAND:Lnet/minecraft/item/Item;"),
            to = @At(value = "FIELD", opcode = Opcodes.GETSTATIC,
                    target = "Lcom/Fishmod/mod_LavaCow/init/FishItems;SCARAB_WAND:Lnet/minecraft/item/Item;")),
            at = {
                    @At(value = "INVOKE", target = "Lnet/minecraft/world/World;spawnEntity(Lnet/minecraft/entity/Entity;)Z"),
                    @At(value = "INVOKE", target = "Lnet/minecraft/world/World;func_72838_d(Lnet/minecraft/entity/Entity;)Z")
            }, remap = false, require = 1, allow = 1)
    private boolean wizardrysummontweaks$recordStaffSpawn(World world, Entity entity, Operation<Boolean> original,
                                                           @Local(argsOnly = true) EntityPlayer player,
                                                           @Local(argsOnly = true) EnumHand hand) {
        if (world.isRemote || entity.world != world || player.world != world || !(entity instanceof EntityLilSludge)
                || player.getHeldItem(hand).getItem() != FishItems.SLUDGE_WAND) {
            return original.call(world, entity);
        }
        EntityLilSludge sludge = (EntityLilSludge) entity;
        if (!sludge.isTamed() || !player.getUniqueID().equals(sludge.getOwnerId())) {
            return original.call(world, entity);
        }
        SummonFacts facts = new SummonFacts(FishSummonProvider.SLUDGE_WAND, player.getUniqueID(),
                SummonFacts.OwnerKind.PLAYER, "exact_staff_caster");
        return WizardrySummonTweaksMod.captureSummonSpawn(entity, facts, () -> original.call(world, entity));
    }
}
