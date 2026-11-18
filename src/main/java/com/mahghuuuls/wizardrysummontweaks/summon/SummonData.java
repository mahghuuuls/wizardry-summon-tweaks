package com.mahghuuuls.wizardrysummontweaks.summon;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/** Owns the private entity compound. Unrecognized bytes are never replaced. */
public final class SummonData {
    public static final String KEY = "wizardrysummontweaks";
    private static final SummonFacts.Result MISSING = SummonFacts.reconcile(null, false, null);
    private static final SummonFacts.Result INVALID = SummonFacts.reconcile(null, true, null);
    private static final Field CUSTOM_DATA = ReflectionHelper.findField(Entity.class, "customEntityData");

    private SummonData() {}

    public static SummonFacts.Result evaluate(NBTTagCompound entityData, SummonFacts observation) {
        return evaluate(entityData, observation, uuid -> false);
    }

    public static SummonFacts.Result evaluate(NBTTagCompound entityData, SummonFacts observation, Predicate<UUID> knownPlayer) {
        return reconcile(read(entityData), observation, knownPlayer);
    }

    static SummonFacts.Result reconcile(SummonFacts.Result stored, SummonFacts observation, Predicate<UUID> knownPlayer) {
        SummonFacts.Result result = observation == null ? stored
                : SummonFacts.reconcile(stored.facts, !stored.mayPersist, observation);
        SummonFacts facts = result.facts;
        if (result.mayPersist && facts != null && facts.ownerKind == SummonFacts.OwnerKind.UNRESOLVED
                && facts.ownerUUID != null && knownPlayer.test(facts.ownerUUID)) {
            SummonFacts confirmed = new SummonFacts(facts.originProvider, facts.ownerUUID, SummonFacts.OwnerKind.PLAYER, "server_player_record");
            return new SummonFacts.Result(confirmed, confirmed.status(), confirmed.ownershipEvidence, true);
        }
        return result;
    }

    static SummonFacts.Result read(NBTTagCompound entityData) {
        if (entityData == null || !entityData.hasKey(KEY)) return MISSING;
        if (!entityData.hasKey(KEY, 10)) return INVALID;
        try {
            return SummonFacts.reconcile(readFacts(entityData.getCompoundTag(KEY)), false, null);
        } catch (IllegalArgumentException failure) {
            return INVALID;
        }
    }

    static SummonFacts readFacts(NBTTagCompound tag) {
        if (!tag.hasKey("schema", 3) || tag.getInteger("schema") != 1
                || !tag.hasKey("originProvider", 8) || !tag.hasKey("ownerKind", 8)
                || !tag.hasKey("ownershipEvidence", 8)) {
            throw new IllegalArgumentException("Invalid summon facts");
        }
        UUID owner = tag.hasKey("ownerUUID") ? requiredUUID(tag, "ownerUUID") : null;
        return new SummonFacts(tag.getString("originProvider"), owner,
                SummonFacts.OwnerKind.valueOf(tag.getString("ownerKind")), tag.getString("ownershipEvidence"));
    }

    static UUID requiredUUID(NBTTagCompound tag, String key) {
        if (!tag.hasKey(key, 8)) throw new IllegalArgumentException("Invalid UUID tag type");
        String text = tag.getString(key);
        UUID uuid = UUID.fromString(text);
        if (!uuid.toString().equalsIgnoreCase(text)) throw new IllegalArgumentException("Noncanonical UUID");
        return uuid;
    }

    public static void remember(NBTTagCompound entityData, SummonFacts.Result result) {
        if (!result.mayPersist || result.facts == null) return;
        NBTTagCompound tag = writeFacts(result.facts);
        if (!entityData.hasKey(KEY, 10) || !tag.equals(entityData.getCompoundTag(KEY))) {
            entityData.setTag(KEY, tag);
        }
    }

    static NBTTagCompound writeFacts(SummonFacts facts) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("schema", 1);
        tag.setString("originProvider", facts.originProvider);
        if (facts.ownerUUID != null) tag.setString("ownerUUID", facts.ownerUUID.toString());
        tag.setString("ownerKind", facts.ownerKind.name());
        tag.setString("ownershipEvidence", facts.ownershipEvidence);
        return tag;
    }

    /** The join event must read the facts during the call. A rejected spawn retains no new record. */
    static boolean captureSpawn(NBTTagCompound entityData, SummonFacts facts, BooleanSupplier spawn) {
        SummonFacts.Result result = evaluate(entityData, facts);
        if (!result.mayPersist) return spawn.getAsBoolean();
        NBTBase previous = entityData.getTag(KEY);
        if (previous != null) previous = previous.copy();
        remember(entityData, result);
        boolean added = false;
        try {
            added = spawn.getAsBoolean();
            return added;
        } finally {
            if (!added) {
                if (previous == null) entityData.removeTag(KEY);
                else entityData.setTag(KEY, previous);
            }
        }
    }

    /** Reads existing Forge custom data without allocating a new entity compound. */
    static NBTTagCompound peek(Entity entity) {
        try { return (NBTTagCompound) CUSTOM_DATA.get(entity); }
        catch (IllegalAccessException failure) { throw new IllegalStateException("Cannot read Forge custom entity data", failure); }
    }
}
