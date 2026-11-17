package com.mahghuuuls.wizardrysummontweaks.summon;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static com.mahghuuuls.wizardrysummontweaks.summon.SummonFacts.OwnerKind.*;

class SummonDataTest {
    @Test void exactSpawnExposesEachOriginDuringOneOperationAndPersistsSuccessfulSpawns() {
        for (String spell : new String[]{"summon_snow_golem", "summon_spirit_wolf", "summon_spirit_horse"}) {
            NBTTagCompound data = new NBTTagCompound();
            SummonFacts facts = new SummonFacts("ebwizardry:" + spell, UUID.randomUUID(), PLAYER, "exact_spell_caster");
            AtomicInteger attempts = new AtomicInteger();
            assertTrue(SummonData.captureSpawn(data, facts, () -> {
                attempts.incrementAndGet();
                SummonFacts.Result visible = SummonData.evaluate(data, null);
                assertEquals(facts.originProvider, visible.facts.originProvider);
                assertEquals(facts.ownerUUID, visible.facts.ownerUUID);
                return true;
            }));
            assertEquals(1, attempts.get());
            SummonFacts.Result result = SummonData.evaluate(data.copy(), null);
            assertTrue(result.affected());
            assertEquals("exact_spell_caster", result.reason);
            assertEquals(1, data.getCompoundTag(SummonData.KEY).getInteger("schema"));
        }
    }

    @Test void rejectedOrThrownSpawnRestoresPriorEvidenceAndLeavesOtherDataAlone() {
        NBTTagCompound data = new NBTTagCompound();
        SummonFacts facts = new SummonFacts("ebwizardry:summon_snow_golem", UUID.randomUUID(), PLAYER, "exact_spell_caster");
        AtomicInteger attempts = new AtomicInteger();
        assertFalse(SummonData.captureSpawn(data, facts, () -> {
            attempts.incrementAndGet();
            assertTrue(SummonData.evaluate(data, null).affected());
            data.setInteger("other_mod", 7);
            return false;
        }));
        assertEquals(1, attempts.get());
        assertFalse(data.hasKey(SummonData.KEY));
        assertEquals(7, data.getInteger("other_mod"));

        SummonFacts earlier = new SummonFacts(facts.originProvider, UUID.randomUUID(), PLAYER, "previous");
        SummonData.remember(data, SummonData.evaluate(data, earlier));
        NBTTagCompound before = data.copy();
        IllegalStateException providerFailure = new IllegalStateException("native spawn failed");
        assertSame(providerFailure, assertThrows(IllegalStateException.class, () ->
                SummonData.captureSpawn(data, facts, () -> {
                    assertEquals(facts.ownerUUID, SummonData.evaluate(data, null).facts.ownerUUID);
                    throw providerFailure;
                })));
        assertEquals(before, data);
    }

    @Test void authoritativePlayerRecordCanResolveSavedUnknownOwnerWithoutChangingAnNpcOrBadSchema() {
        UUID owner = UUID.randomUUID();
        NBTTagCompound data = new NBTTagCompound();
        SummonData.remember(data, SummonData.evaluate(data,
                new SummonFacts("provider:spell", owner, UNRESOLVED, "owner_kind_unresolved")));
        NBTTagCompound before = data.copy();
        SummonFacts.Result resolved = SummonData.evaluate(data, null, owner::equals);
        assertTrue(resolved.affected());
        assertEquals(owner, resolved.facts.ownerUUID);
        assertEquals(before, data);
        assertEquals(SummonFacts.Status.EXCLUDED, SummonData.evaluate(data,
                new SummonFacts("provider:spell", owner, NON_PLAYER, "live_npc"), owner::equals).status);
        data.getCompoundTag(SummonData.KEY).setInteger("schema", 99);
        assertFalse(SummonData.evaluate(data, null, uuid -> true).affected());
    }

    @Test void absentCasterSaveRestoreUsesProductionCodecAndRetainsOwnership() throws Exception {
        UUID owner = UUID.randomUUID();
        SummonFacts initial = new SummonFacts("ebwizardry:summoned_creature", owner, PLAYER, "live_player_caster");
        NBTTagCompound data = new NBTTagCompound();
        data.setString("other_mod", "preserved");
        SummonData.remember(data, SummonData.evaluate(data, initial));
        SummonFacts unavailable = new SummonFacts(initial.originProvider, null, UNRESOLVED, "caster_unavailable");
        SummonData.remember(data, SummonData.evaluate(data, unavailable));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompressedStreamTools.writeCompressed(data, bytes);
        NBTTagCompound restored = CompressedStreamTools.readCompressed(new ByteArrayInputStream(bytes.toByteArray()));
        SummonFacts.Result result = SummonData.evaluate(restored, unavailable);
        assertTrue(result.affected());
        assertEquals(owner, result.facts.ownerUUID);
        assertEquals("preserved", restored.getString("other_mod"));
    }

    @Test void evaluationNeverWritesEvenWhenEvidenceCouldEstablishFacts() {
        NBTTagCompound data = new NBTTagCompound();
        NBTTagCompound before = data.copy();
        SummonData.evaluate(data, new SummonFacts("provider:spell", UUID.randomUUID(), PLAYER, "live"));
        assertEquals(before, data);
    }

    @Test void futureSchemaAndMalformedTagRemainUntouched() {
        for (boolean future : new boolean[]{true, false}) {
            NBTTagCompound data = new NBTTagCompound();
            if (future) {
                NBTTagCompound unknown = new NBTTagCompound();
                unknown.setInteger("schema", 99);
                unknown.setString("future_payload", "keep");
                data.setTag(SummonData.KEY, unknown);
            } else data.setString(SummonData.KEY, "malformed");
            NBTTagCompound before = data.copy();
            SummonFacts.Result result = SummonData.evaluate(data,
                    new SummonFacts("provider:spell", UUID.randomUUID(), PLAYER, "live"));
            SummonData.remember(data, result);
            assertEquals(SummonFacts.Status.UNRESOLVED, result.status);
            assertEquals(before, data);
        }
    }

    @Test void invalidOwnerFieldsCannotDefaultToAPlayer() {
        NBTTagCompound data = new NBTTagCompound();
        SummonData.remember(data, SummonData.evaluate(data,
                new SummonFacts("provider:spell", UUID.randomUUID(), PLAYER, "live")));
        data.getCompoundTag(SummonData.KEY).setString("ownerUUID", "not-a-uuid");
        assertFalse(SummonData.evaluate(data, null).affected());
        data.getCompoundTag(SummonData.KEY).removeTag("ownerUUID");
        assertFalse(SummonData.evaluate(data, null).affected());
    }
}
