package com.mahghuuuls.wizardrysummontweaks.summon;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static com.mahghuuuls.wizardrysummontweaks.summon.SummonFacts.OwnerKind.*;

class SummonDataTest {
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
