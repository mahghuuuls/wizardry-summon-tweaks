package com.mahghuuuls.wizardrysummontweaks.compat.wizardry;

import com.mahghuuuls.wizardrysummontweaks.summon.SummonData;
import com.mahghuuuls.wizardrysummontweaks.summon.SummonFacts;
import electroblob.wizardry.entity.living.ISummonedCreature;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class WizardryOwnerDecodingTest {
    @Test void actualWizardryNbtReaderMissingOwnerRetainsEstablishedPlayerEvidence() {
        UUID player = UUID.randomUUID();
        NBTTagCompound privateData = new NBTTagCompound();
        SummonData.remember(privateData, SummonData.evaluate(privateData,
                new SummonFacts("ebwizardry:summoned_creature", player, SummonFacts.OwnerKind.PLAYER, "live_player_caster")));
        SourceOwner source = new SourceOwner();
        NBTTagCompound providerSave = new NBTTagCompound();
        providerSave.setInteger("lifetime", 600);
        source.readNBTDelegate(providerSave);
        assertEquals(new UUID(0, 0), source.getOwnerId(), "Exercise the real source reader's absent-field sentinel");
        UUID observation = WizardrySummonProvider.availableOwnerId(source, null);
        SummonFacts.Result result = SummonData.evaluate(privateData,
                new SummonFacts("ebwizardry:summoned_creature", observation, SummonFacts.OwnerKind.UNRESOLVED, "caster_unavailable"));
        assertTrue(result.affected());
        assertEquals(player, result.facts.ownerUUID);
    }

    @Test void realChangedOwnerUuidIsNotTreatedAsAbsence() {
        SourceOwner source = new SourceOwner();
        UUID other = UUID.randomUUID();
        NBTTagCompound providerSave = new NBTTagCompound();
        providerSave.setUniqueId("casterUUID", other);
        source.readNBTDelegate(providerSave);
        assertEquals(other, WizardrySummonProvider.availableOwnerId(source, null));
    }

    @Test void matchingLiveZeroUuidCasterIsAffirmativeEvidence() {
        SourceOwner source = new SourceOwner();
        UUID zero = new UUID(0, 0);
        source.setOwnerId(zero);
        assertEquals(zero, WizardrySummonProvider.availableOwnerId(source, zero));
    }

    /** Backing storage only. The real default NBT reader and production bridge are exercised. */
    private static final class SourceOwner implements ISummonedCreature {
        private UUID owner;
        private int lifetime;
        @Override public void setLifetime(int value) { lifetime = value; }
        @Override public int getLifetime() { return lifetime; }
        @Override public void setOwnerId(UUID value) { owner = value; }
        @Override public UUID getOwnerId() { return owner; }
        @Override public void onSpawn() {}
        @Override public void onDespawn() {}
        @Override public boolean hasParticleEffect() { return false; }
    }
}
