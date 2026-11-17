package com.mahghuuuls.wizardrysummontweaks.combat;

import com.mahghuuuls.wizardrysummontweaks.config.ServerRules;
import com.mahghuuuls.wizardrysummontweaks.summon.SummonFacts;
import com.mahghuuuls.wizardrysummontweaks.summon.SummonTracker;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.function.Supplier;

/** Restricts the dying victim's captured rewards after ordinary loot producers run. */
public final class DeathRewards {
    private final ServerRules rules;
    private final SummonTracker tracker;

    public DeathRewards(ServerRules rules, SummonTracker tracker) {
        this.rules = rules;
        this.tracker = tracker;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onDrops(LivingDropsEvent event) {
        if (!shouldSuppress(event.getEntityLiving())) return;
        event.getDrops().clear();
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onExperience(LivingExperienceDropEvent event) {
        if (!shouldSuppress(event.getEntityLiving())) return;
        event.setDroppedExperience(0);
        event.setCanceled(true);
    }

    private boolean shouldSuppress(EntityLivingBase victim) {
        return !victim.world.isRemote && suppresses(rules, () -> tracker.inspect(victim));
    }

    static boolean suppresses(ServerRules rules, Supplier<SummonFacts.Result> victim) {
        return rules.suppressSummonDrops && victim.get().affected();
    }
}
