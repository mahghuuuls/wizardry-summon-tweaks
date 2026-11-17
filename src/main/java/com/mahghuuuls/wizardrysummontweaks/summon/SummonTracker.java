package com.mahghuuuls.wizardrysummontweaks.summon;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.Map;
import java.util.WeakHashMap;

/** Single authority for live observations, stored evidence and read-only classification. */
public final class SummonTracker {
    // This Forge-added field has no obfuscated vanilla alias. Read it without getEntityData's allocation side effect.
    private static final Field CUSTOM_DATA = ReflectionHelper.findField(Entity.class, "customEntityData");
    private final PlayerIdentities players = new PlayerIdentities();
    private final Logger logger;
    // A failed loaded instance is retried only after reload; weak keys retain neither entities nor worlds.
    private final Map<Entity, Boolean> failedEntities = new WeakHashMap<>();
    private boolean failureReported;
    private SummonProvider provider;
    private String providerState = "not_started";
    private boolean active;

    public SummonTracker(Logger logger) { this.logger = logger; }

    public void start(MinecraftServer server, SummonProvider provider) {
        players.start(server);
        failedEntities.clear();
        failureReported = false;
        this.provider = provider;
        providerState = provider == null ? "absent" : "active";
        active = true;
    }

    public void stop() {
        active = false;
        provider = null;
        providerState = "not_started";
        players.clear();
        failedEntities.clear();
        failureReported = false;
    }

    public void playerJoined(UUID uuid) { if (active) players.joined(uuid); }
    public String providerState() { return providerState + (failedEntities.isEmpty() ? "" : "_with_entity_failures"); }

    public void refresh(Entity entity) {
        if (!active || entity.world.isRemote) return;
        NBTTagCompound data = peekData(entity);
        boolean candidate = provider != null && provider.matches(entity);
        if (!candidate && (data == null || !data.hasKey(SummonData.KEY))) return;
        SummonFacts.Result result = classify(entity, true);
        if (result.mayPersist && result.facts != null) SummonData.remember(entity.getEntityData(), result);
    }

    public SummonFacts.Result inspect(Entity entity) {
        return classify(entity, false);
    }

    private SummonFacts.Result classify(Entity entity, boolean recordFailure) {
        NBTTagCompound data = peekData(entity);
        if (!active) return new SummonFacts.Result(null, SummonFacts.Status.UNRESOLVED, "server_not_active", false);
        if (failedEntities.containsKey(entity)) return failedObservation(data);
        SummonFacts observation = null;
        if (provider != null && provider.matches(entity)) {
            try {
                observation = provider.observe(entity);
            } catch (RuntimeException | LinkageError failure) {
                if (recordFailure) {
                    failedEntities.put(entity, Boolean.TRUE);
                    if (!failureReported) {
                        logger.error("Summon observation failed for " + entity.getClass().getName() + " uuid=" + entity.getUniqueID()
                                + "; this loaded instance is unresolved until reload. Other entities remain independent.", failure);
                        failureReported = true;
                    }
                }
                return failedObservation(data);
            }
        }
        return SummonData.evaluate(data == null ? new NBTTagCompound() : data, observation, players::isPlayer);
    }

    private SummonFacts.Result failedObservation(NBTTagCompound data) {
        SummonFacts.Result saved = SummonData.evaluate(data == null ? new NBTTagCompound() : data, null, players::isPlayer);
        return new SummonFacts.Result(saved.facts, SummonFacts.Status.UNRESOLVED, "provider_observation_failed", false);
    }

    private static NBTTagCompound peekData(Entity entity) {
        try { return (NBTTagCompound) CUSTOM_DATA.get(entity); }
        catch (IllegalAccessException failure) { throw new IllegalStateException("Cannot read Forge custom entity data", failure); }
    }
}
