package wstfixture;

import com.mojang.authlib.GameProfile;
import electroblob.wizardry.entity.living.ISummonedCreature;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumHand;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import static wstfixture.IdentityFixture.*;

public final class WizardryChecks {
    public static void run(MinecraftServer server, ICommandSender sender, String[] args) throws Exception {
        WorldServer world = server.worlds[0];
        if (args[0].equals("native")) {
            EntityPlayerMP player = CommandBase.getPlayer(server, sender, args[1]);
            loadArena(world);
            player.setPositionAndUpdate(0.5, 4, 0.5);
            world.getWorldInfo().setWorldTime(18000);
            world.getGameRules().setOrCreateGameRule("doDaylightCycle", "false");
            world.getGameRules().setOrCreateGameRule("doMobSpawning", "false");
            Spell spell = Spell.get("ebwizardry:summon_zombie");
            require(spell != null, "live spell not registered");
            Set<Entity> before = new HashSet<>(world.loadedEntityList);
            require(spell.cast(world, player, EnumHand.MAIN_HAND, 0, new SpellModifiers()), "native player cast returned false");
            Entity creature = newlySummoned(world, before);
            creature.setCustomNameTag("wst_keep");
            java.nio.file.Files.write(world.getSaveHandler().getWorldDirectory().toPath().resolve("wst-kept-uuid.txt"),
                    creature.getUniqueID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            ((net.minecraft.entity.EntityLiving) creature).setNoAI(true);
            require(player.getUniqueID().equals(((ISummonedCreature) creature).getOwnerId()), "native cast owner mismatch");
            inspect(server, sender, creature, "AFFECTED");
            EntityZombie npc = new EntityZombie(world);
            npc.setPosition(10, 4, 0);
            npc.setNoAI(true);
            npc.setCustomNameTag("wst_npc_owner");
            require(world.spawnEntity(npc), "NPC control spawn rejected");
            before = new HashSet<>(world.loadedEntityList);
            require(spell.cast(world, npc, EnumHand.MAIN_HAND, 0, player, new SpellModifiers()), "native NPC cast returned false");
            Entity npcSummon = newlySummoned(world, before);
            npcSummon.setCustomNameTag("wst_npc_summon");
            ((net.minecraft.entity.EntityLiving) npcSummon).setNoAI(true);
            inspect(server, sender, npcSummon, "EXCLUDED");
            // Forge's writer exposes the live ForgeData compound; a load fixture needs an independent snapshot.
            NBTTagCompound legacy = creature.serializeNBT().copy();
            require(legacy.getCompoundTag("ForgeData") != creature.getEntityData(), "legacy snapshot aliases live data");
            legacy.getCompoundTag("ForgeData").removeTag("wizardrysummontweaks");
            legacy.setUniqueId("UUID", UUID.randomUUID());
            legacy.setString("CustomName", "wst_legacy");
            require(!legacy.getCompoundTag("ForgeData").hasKey("wizardrysummontweaks"), "legacy input already has private facts");
            Entity restored = net.minecraft.entity.EntityList.createEntityFromNBT(legacy, world);
            require(restored != null && world.spawnEntity(restored), "legacy native NBT load failed");
            require(restored.getEntityData() != creature.getEntityData(), "legacy load shares original entity data");
            inspect(server, sender, restored, "AFFECTED");
            output("LEGACY_PASS nativeProviderOwner=" + ((ISummonedCreature) restored).getOwnerId());
            world.getSaveHandler().getPlayerNBTManager().writePlayerData(FakePlayerFactory.get(world,
                    new GameProfile(SECOND_PLAYER, "WstSecondPlayer")));
            output("NATIVE_PASS spell=" + spell.getRegistryName() + " player=" + player.getUniqueID()
                    + " kept=" + creature.getUniqueID() + " lifetime=" + ((ISummonedCreature) creature).getLifetime());
        } else if (args[0].equals("chunk-out")) {
            world.getChunk(64, 0);
            Entity creature = named(server, "wst_keep");
            creature.setPosition(1024.5, 4, 0.5);
            output("CHUNK_OUT uuid=" + creature.getUniqueID());
        } else if (args[0].equals("chunk-unload")) {
            net.minecraft.world.chunk.Chunk chunk = world.getChunkProvider().getLoadedChunk(64, 0);
            require(chunk != null, "test chunk not loaded before unload");
            require(named(server, "wst_keep").chunkCoordX == 64, "keeper did not enter the test chunk");
            world.getChunkProvider().queueUnload(chunk);
            world.getChunkProvider().tick();
            output("CHUNK_UNLOAD_REQUESTED");
        } else if (args[0].equals("chunk-check")) {
            require(world.getChunkProvider().getLoadedChunk(64, 0) == null, "test chunk stayed loaded");
            for (Entity entity : world.loadedEntityList) require(!keeperId(server).equals(entity.getUniqueID()), "fixture stayed loaded");
            output("CHUNK_UNLOADED_PASS");
        } else if (args[0].equals("chunk-load")) {
            world.getChunk(64, 0);
            output("CHUNK_RELOADED");
        } else if (args[0].equals("dimension")) {
            EntityPlayerMP player = CommandBase.getPlayer(server, sender, args[1]);
            int target = Integer.parseInt(args[2]);
            server.getWorld(target).getChunk(0, 0);
            player.changeDimension(target, (destination, moved, yaw) -> moved.setLocationAndAngles(0.5, 80, 0.5, yaw, 0));
            output("DIMENSION_CHANGED player=" + player.getUniqueID() + " dimension=" + player.dimension);
        } else if (args[0].equals("transfer")) {
            Entity creature = named(server, "wst_keep");
            ISummonedCreature summon = (ISummonedCreature) creature;
            if (args[1].equals("npc")) summon.setCaster((EntityLivingBase) named(server, "wst_npc_owner"));
            else if (args[1].equals("unknown")) summon.setOwnerId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
            else if (args[1].equals("missing")) summon.setOwnerId(null);
            else if (args[1].equals("second")) summon.setOwnerId(SECOND_PLAYER);
            else summon.setCaster(CommandBase.getPlayer(server, sender, args[1]));
            output("TRANSFER_SET mode=" + args[1] + " rawOwner=" + summon.getOwnerId() + " ticksExisted=" + creature.ticksExisted);
        } else if (args[0].equals("prepare-save")) {
            Entity creature = named(server, "wst_keep");
            require(((ISummonedCreature) creature).getCaster() == null, "live caster must actually be absent");
            inspect(server, sender, creature, "AFFECTED");
            NBTTagCompound raw = creature.serializeNBT();
            require(raw.getCompoundTag("ForgeData").getCompoundTag("wizardrysummontweaks").getString("ownerKind").equals("PLAYER"), "saved player evidence missing");
            java.nio.file.Files.write(world.getSaveHandler().getWorldDirectory().toPath().resolve("wst-kept-uuid.txt"),
                    creature.getUniqueID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output("PREPARE_SAVE_PASS sameUUID=" + creature.getUniqueID() + " liveCaster=null raw=" + raw);
        } else if (args[0].equals("restored")) {
            String previous = new String(java.nio.file.Files.readAllBytes(world.getSaveHandler().getWorldDirectory().toPath().resolve("wst-kept-uuid.txt")), java.nio.charset.StandardCharsets.UTF_8);
            Entity creature = named(server, "wst_keep");
            require(creature.getUniqueID().toString().equals(previous), "restored a different entity");
            inspect(server, sender, creature, "AFFECTED");
            output("RESTORED_PASS sameUUID=" + previous + " rawProviderOwner=" + ((ISummonedCreature) creature).getOwnerId());
        }
    }

    private static Entity newlySummoned(WorldServer world, Set<Entity> before) {
        for (Entity entity : world.loadedEntityList) {
            if (!before.contains(entity) && entity instanceof ISummonedCreature) return entity;
        }
        throw new IllegalStateException("Native cast did not create a live summoned entity");
    }
}
