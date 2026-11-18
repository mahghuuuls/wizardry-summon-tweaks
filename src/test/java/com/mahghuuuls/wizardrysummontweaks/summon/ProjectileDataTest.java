package com.mahghuuuls.wizardrysummontweaks.summon;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProjectileDataTest {
    private final UUID projectile = UUID.randomUUID();
    private final UUID launcher = UUID.randomUUID();
    private final UUID creditedPlayer = UUID.randomUUID();

    @Test
    void recordsLauncherSeparatelyFromCreditedPlayerAndSurvivesNbtCopy() {
        SummonFacts facts = new SummonFacts("wizardry_interface", creditedPlayer, SummonFacts.OwnerKind.PLAYER, "live_player");
        NBTTagCompound data = record(new ProjectileData.Launch(launcher, facts));
        ProjectileData.Launch restored = ProjectileData.read(data.copy(), projectile, 2);
        assertNotNull(restored);
        assertEquals(launcher, restored.actor);
        assertEquals(creditedPlayer, restored.summon.ownerUUID);
        assertEquals("wizardry_interface", restored.summon.originProvider);
        assertEquals(SummonFacts.Status.AFFECTED, restored.summon.status());

        NBTTagCompound player = record(new ProjectileData.Launch(creditedPlayer, null));
        assertEquals(creditedPlayer, ProjectileData.read(player, projectile, 2).actor);
        assertNull(ProjectileData.read(player, projectile, 2).summon);
    }

    @Test
    void rejectedRecordsAreNeverEvidenceForAnotherShotWorldOrUnresolvedActor() {
        NBTTagCompound data = record(new ProjectileData.Launch(launcher, null));
        assertNull(ProjectileData.read(data, UUID.randomUUID(), 2));
        assertNull(ProjectileData.read(data, projectile, -1));
        NBTTagCompound tag = data.getCompoundTag(ProjectileData.KEY);
        tag.setInteger("schema", 2);
        assertNull(ProjectileData.read(data, projectile, 2));
        assertEquals(2, tag.getInteger("schema"));
        tag.setInteger("schema", 1);
        tag.setString("actor", "1-2-3-4-5");
        assertNull(ProjectileData.read(data, projectile, 2));
        tag.setString("actor", projectile.toString());
        assertNull(ProjectileData.read(data, projectile, 2));

        SummonFacts unresolved = new SummonFacts("wizardry_interface", creditedPlayer, SummonFacts.OwnerKind.UNRESOLVED, "missing_owner");
        assertNull(ProjectileData.read(record(new ProjectileData.Launch(launcher, unresolved)), projectile, 2));
    }

    private NBTTagCompound record(ProjectileData.Launch launch) {
        NBTTagCompound data = new NBTTagCompound();
        data.setTag(ProjectileData.KEY, ProjectileData.write(projectile, 2, launch));
        return data;
    }
}
