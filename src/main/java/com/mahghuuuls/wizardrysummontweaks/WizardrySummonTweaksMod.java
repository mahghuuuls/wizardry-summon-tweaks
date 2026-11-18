package com.mahghuuuls.wizardrysummontweaks;

import com.mahghuuuls.wizardrysummontweaks.command.InspectCommand;
import com.mahghuuuls.wizardrysummontweaks.combat.CombatPolicy;
import com.mahghuuuls.wizardrysummontweaks.combat.DeathRewards;
import com.mahghuuuls.wizardrysummontweaks.combat.AttackProtection;
import com.mahghuuuls.wizardrysummontweaks.combat.TargetProtection;
import com.mahghuuuls.wizardrysummontweaks.compat.wizardry.WizardryMeleeSource;
import com.mahghuuuls.wizardrysummontweaks.compat.wizardry.WizardrySpellSource;
import com.mahghuuuls.wizardrysummontweaks.config.ServerConfiguration;
import com.mahghuuuls.wizardrysummontweaks.config.ServerRules;
import com.mahghuuuls.wizardrysummontweaks.summon.SummonFacts;
import com.mahghuuuls.wizardrysummontweaks.summon.SummonProvider;
import com.mahghuuuls.wizardrysummontweaks.summon.SummonTracker;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

@Mod(
        modid = Tags.MOD_ID,
        name = Tags.MOD_NAME,
        version = Tags.VERSION,
        acceptedMinecraftVersions = "[1.12.2]",
        acceptableRemoteVersions = "*",
        dependencies = "required-after:forge@[14.23.5.2847,);required-after:mixinbooter@[12.1,);after:ebwizardry@[4.3.19,)"
                + ";after:wizardryutils@[1.3.0,);after:spellbundle@[1.12.2-1.7.4,);after:mod_lavacow@[1.6.2,)"
)
public final class WizardrySummonTweaksMod {
    @Mod.Instance(Tags.MOD_ID)
    private static WizardrySummonTweaksMod instance;

    private ServerConfiguration configuration;
    private ServerRules rules = ServerRules.DEFAULTS;
    private SummonTracker tracker;
    private Logger logger;
    private CombatPolicy combat;
    private AttackProtection attacks;
    private DeathRewards rewards;
    private TargetProtection targets;

    public static CombatPolicy combatPolicy() {
        return instance == null ? null : instance.combat;
    }

    public static AttackProtection attackProtection() {
        return instance == null ? null : instance.attacks;
    }

    public static TargetProtection targetProtection() {
        return instance == null ? null : instance.targets;
    }

    public static boolean captureSummonSpawn(Entity entity, SummonFacts facts, BooleanSupplier spawn) {
        SummonTracker tracker = instance == null ? null : instance.tracker;
        return tracker == null ? spawn.getAsBoolean() : tracker.captureSpawn(entity, facts, spawn);
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        configuration = new ServerConfiguration(new File(event.getModConfigurationDirectory(), Tags.MOD_ID + ".cfg").toPath());
        tracker = new SummonTracker(logger);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Mod.EventHandler
    public void aboutToStart(FMLServerAboutToStartEvent event) {
        tracker.stop();
        rules = configuration.activate(message -> logger.error(message));
    }

    @Mod.EventHandler
    public void starting(FMLServerStartingEvent event) {
        Map<String, SummonProvider> providers = new LinkedHashMap<>();
        addProvider(providers, "ebwizardry", "wizardry.WizardrySummonProvider");
        addProvider(providers, "wizardryutils", "wizardryutils.WizardryUtilsSummonProvider");
        addProvider(providers, "spellbundle", "spellbundle.SpellBundleSummonProvider");
        addProvider(providers, "mod_lavacow", "fish.FishSummonProvider");
        tracker.start(event.getServer(), providers);
        boolean wizardry = providers.containsKey("ebwizardry");
        combat = new CombatPolicy(rules, tracker);
        targets = new TargetProtection(combat);
        attacks = new AttackProtection(combat, tracker, wizardry ? WizardryMeleeSource::actor : source -> null,
                wizardry ? WizardrySpellSource::caster : source -> null);
        rewards = new DeathRewards(rules, tracker);
        MinecraftForge.EVENT_BUS.register(attacks);
        MinecraftForge.EVENT_BUS.register(rewards);
        event.registerServerCommand(new InspectCommand(tracker, () -> rules, attacks));
    }

    private static void addProvider(Map<String, SummonProvider> providers, String modId, String className) {
        if (!Loader.isModLoaded(modId)) return;
        try {
            // Defer verification/loading of optional API descriptors until the provider is present.
            SummonProvider provider = (SummonProvider) Class.forName(WizardrySummonTweaksMod.class.getPackage().getName()
                            + ".compat." + className)
                    .getDeclaredConstructor().newInstance();
            providers.put(modId, provider);
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw new IllegalStateException("Cannot initialize the installed summon provider " + modId, failure);
        }
    }

    @SubscribeEvent
    public void joined(EntityJoinWorldEvent event) {
        if (!event.getWorld().isRemote) refreshAndClearTarget(event.getEntity());
    }

    @SubscribeEvent
    public void livingUpdate(LivingEvent.LivingUpdateEvent event) {
        refreshAndClearTarget(event.getEntityLiving());
    }

    private void refreshAndClearTarget(Entity entity) {
        if (tracker.refresh(entity) && targets != null && entity instanceof EntityLiving) {
            targets.clearForbidden((EntityLiving) entity);
        }
    }

    @SubscribeEvent
    public void playerJoined(PlayerEvent.PlayerLoggedInEvent event) {
        if (!event.player.world.isRemote) tracker.playerJoined(event.player.getUniqueID());
    }

    @Mod.EventHandler
    public void stopped(FMLServerStoppedEvent event) {
        if (attacks != null) {
            MinecraftForge.EVENT_BUS.unregister(attacks);
            attacks = null;
        }
        if (rewards != null) {
            MinecraftForge.EVENT_BUS.unregister(rewards);
            rewards = null;
        }
        targets = null;
        combat = null;
        tracker.stop();
        rules = ServerRules.DEFAULTS;
    }
}
