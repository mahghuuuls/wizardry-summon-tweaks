package com.mahghuuuls.wizardrysummontweaks.summon;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;

import java.util.UUID;

/** ForgeData for the verified launch of an individual projectile. */
public final class ProjectileData {
    static final String KEY = SummonData.KEY + ":launch";

    private ProjectileData() {}

    public static boolean hasLaunchRecord(Entity projectile) {
        NBTTagCompound data = SummonData.peek(projectile);
        return data != null && data.hasKey(KEY);
    }

    /** A record binds to the projectile UUID and original dimension, including after an orderly reload. */
    public static Launch read(Entity projectile) {
        return read(SummonData.peek(projectile), projectile.getUniqueID(), projectile.dimension);
    }

    public static void remember(Entity projectile, Entity actor, SummonFacts summon) {
        NBTTagCompound data = projectile.getEntityData();
        if (!data.hasKey(KEY)) {
            data.setTag(KEY, write(projectile.getUniqueID(), projectile.dimension, new Launch(actor.getUniqueID(), summon)));
        }
    }

    static NBTTagCompound write(UUID projectile, int dimension, Launch launch) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("schema", 1);
        tag.setInteger("dimension", dimension);
        tag.setString("projectile", projectile.toString());
        tag.setString("actor", launch.actor.toString());
        if (launch.summon == null) tag.setString("kind", "player");
        else {
            tag.setString("kind", "summon");
            tag.setTag("summon", SummonData.writeFacts(launch.summon));
        }
        return tag;
    }

    /** Reconcile only a record we understand. Unknown schemas remain untouched. */
    public static void updateOnSave(Entity projectile, Entity actor, SummonFacts summon) {
        NBTTagCompound data = SummonData.peek(projectile);
        Launch previous = read(data, projectile.getUniqueID(), projectile.dimension);
        if (previous == null) return;
        if (actor == null) data.removeTag(KEY);
        else {
            NBTTagCompound next = write(projectile.getUniqueID(), projectile.dimension, new Launch(actor.getUniqueID(), summon));
            if (!next.equals(data.getCompoundTag(KEY))) data.setTag(KEY, next);
        }
    }

    static Launch read(NBTTagCompound data, UUID projectile, int dimension) {
        if (data == null || !data.hasKey(KEY, 10)) return null;
        NBTTagCompound tag = data.getCompoundTag(KEY);
        try {
            if (!tag.hasKey("schema", 3) || tag.getInteger("schema") != 1
                    || !tag.hasKey("dimension", 3) || tag.getInteger("dimension") != dimension
                    || !projectile.equals(SummonData.requiredUUID(tag, "projectile"))) return null;
            UUID actor = SummonData.requiredUUID(tag, "actor");
            if (actor.equals(projectile) || (actor.getMostSignificantBits() == 0 && actor.getLeastSignificantBits() == 0)) return null;
            String kind = tag.getString("kind");
            if ("player".equals(kind) && !tag.hasKey("summon")) return new Launch(actor, null);
            if ("summon".equals(kind) && tag.hasKey("summon", 10)) {
                SummonFacts facts = SummonData.readFacts(tag.getCompoundTag("summon"));
                if (facts.status() == SummonFacts.Status.AFFECTED) return new Launch(actor, facts);
            }
        } catch (IllegalArgumentException invalid) {
            // An unknown or malformed record cannot establish an actor.
        }
        return null;
    }

    public static final class Launch {
        public final UUID actor;
        public final SummonFacts summon;

        Launch(UUID actor, SummonFacts summon) {
            this.actor = actor;
            this.summon = summon;
        }
    }
}
