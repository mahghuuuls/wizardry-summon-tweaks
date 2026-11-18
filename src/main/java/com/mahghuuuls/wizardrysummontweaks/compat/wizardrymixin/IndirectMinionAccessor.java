package com.mahghuuuls.wizardrysummontweaks.compat.wizardrymixin;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "electroblob.wizardry.util.IndirectMinionDamage", remap = false)
public interface IndirectMinionAccessor {
    @Accessor(value = "minion", remap = false)
    Entity wizardrysummontweaks$minion();
}
