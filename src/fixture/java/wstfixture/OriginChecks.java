package wstfixture;

import electroblob.wizardry.data.WizardData;
import electroblob.wizardry.entity.living.EntitySpiritHorse;
import electroblob.wizardry.entity.living.EntitySpiritWolf;
import electroblob.wizardry.entity.living.ISummonedCreature;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.spell.SummonSpiritHorse;
import electroblob.wizardry.spell.SummonSpiritWolf;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntitySnowman;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.storage.ThreadedFileIOBase;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static wstfixture.IdentityFixture.inspect;
import static wstfixture.IdentityFixture.loadArena;
import static wstfixture.IdentityFixture.named;
import static wstfixture.IdentityFixture.output;
import static wstfixture.IdentityFixture.require;

public final class OriginChecks {
    private static final String DATA = "wizardrysummontweaks";
    private static final String SNOW = "wst_origin_snow";
    private static final String WOLF = "wst_origin_wolf";
    private static final String HORSE = "wst_origin_horse";
    private static final String LEGACY_SNOW = "wst_origin_old_snow";
    private static final String BUILT_SNOW = "wst_origin_built_snow";
    private static final String EGG_WOLF = "wst_origin_egg_wolf";
    private static final String EGG_HORSE = "wst_origin_egg_horse";
    private static final String ENEMY = "wst_origin_enemy";
    private static final String[] KEEP = {SNOW, WOLF, HORSE, LEGACY_SNOW, BUILT_SNOW, EGG_WOLF, EGG_HORSE};
    private static final int SAVE_CHUNK = 64;

    public static void run(MinecraftServer server, ICommandSender sender, String[] args) throws Exception {
        WorldServer world = server.worlds[0];
        switch (args[1]) {
            case "expiry-start":
                startExpiry(world, CommandBase.getPlayer(server, sender, args[2]));
                break;
            case "expiry-before":
            case "expiry-after":
                observeExpiry(world, args[1]);
                break;
            case "cast":
                checkCasts(server, sender, world, CommandBase.getPlayer(server, sender, args[2]));
                break;
            case "fight":
                checkFight(server, world);
                break;
            case "clear-owners":
                clearOwners(server, sender, CommandBase.getPlayer(server, sender, args[2]));
                break;
            case "restore-owners":
                restoreOwners(server, sender, CommandBase.getPlayer(server, sender, args[2]));
                break;
            case "move":
                moveForSave(server, world);
                break;
            case "unload":
                unload(server, world);
                break;
            case "reload":
            case "restart":
                inspectSaved(server, sender, world, args[1]);
                break;
            default:
                throw new IllegalArgumentException("Unknown origin scenario " + args[1]);
        }
    }

    private static void startExpiry(WorldServer world, EntityPlayerMP owner) throws Exception {
        loadArena(world);
        owner.connection.setPlayerLocation(8.5, 4, 8.5, 0, 0);
        EntitySpiritHorse horse = checkSpawn(world, owner, "summon_spirit_horse", EntitySpiritHorse.class, false);
        horse.setNoAI(true);
        Files.write(expiryPath(world), horse.getUniqueID().toString().getBytes(StandardCharsets.UTF_8));
        output("ORIGIN_EXPIRY_START uuid=" + horse.getUniqueID() + " tick=" + world.getTotalWorldTime());
    }

    private static void observeExpiry(WorldServer world, String phase) throws Exception {
        UUID uuid = UUID.fromString(new String(Files.readAllBytes(expiryPath(world)), StandardCharsets.UTF_8));
        Entity actual = world.getEntityFromUuid(uuid);
        if (phase.equals("expiry-before")) {
            require(actual instanceof EntitySpiritHorse && !actual.isDead && actual.ticksExisted >= 180
                    && !actual.isBeingRidden(), "the initial exact horse did not reach the native idle timer");
            output("ORIGIN_EXPIRY_BEFORE uuid=" + uuid + " age=" + actual.ticksExisted
                    + " tick=" + world.getTotalWorldTime());
        } else {
            require(actual == null, "the unmounted native horse stayed after its normal expiry");
            output("ORIGIN_EXPIRY_AFTER uuid=" + uuid + " live=false tick=" + world.getTotalWorldTime());
        }
    }

    private static Path expiryPath(WorldServer world) {
        return world.getSaveHandler().getWorldDirectory().toPath().resolve("wst-origin-expiry-uuid.txt");
    }

    private static void checkCasts(MinecraftServer server, ICommandSender sender, WorldServer world, EntityPlayerMP player)
            throws Exception {
        loadArena(world);
        player.setGameType(GameType.SURVIVAL);
        player.connection.setPlayerLocation(8.5, 4, 8.5, 0, 0);
        for (BlockPos floor : BlockPos.getAllInBox(new BlockPos(2, 3, 2), new BlockPos(18, 3, 18))) {
            world.setBlockState(floor, Blocks.STONE.getDefaultState());
            for (int height = 1; height <= 3; height++) world.setBlockToAir(floor.up(height));
        }

        checkSpawn(world, player, "summon_snow_golem", EntitySnowman.class, true);
        EntitySnowman oldSnow = reloadWithoutPrivateFacts(world,
                checkSpawn(world, player, "summon_snow_golem", EntitySnowman.class, false), EntitySnowman.class, entity -> {});
        rememberName(oldSnow, LEGACY_SNOW);
        inspect(server, sender, oldSnow, "UNRESOLVED");
        EntitySnowman snow = checkSpawn(world, player, "summon_snow_golem", EntitySnowman.class, false);
        rememberName(snow, SNOW);

        EntitySpiritWolf linkedWolf = reloadWithoutPrivateFacts(world,
                checkSpawn(world, player, "summon_spirit_wolf", EntitySpiritWolf.class, false), EntitySpiritWolf.class,
                candidate -> candidate.setOwnerId(null));
        checkMismatchedLink(server, sender, linkedWolf, WizardData.get(player).getVariable(SummonSpiritWolf.UUID_KEY), "wolf");
        linkedWolf = reloadWithoutPrivateFacts(world, linkedWolf, EntitySpiritWolf.class, candidate -> {
            candidate.setTamed(true);
            candidate.setOwnerId(player.getUniqueID());
        });
        inspect(server, sender, linkedWolf, "AFFECTED");
        require(mark(linkedWolf).getString("ownershipEvidence").equals("legacy_wizardry_spell_link"), "Wolf legacy link was not used");
        EntitySpiritWolf wolf = checkSpawn(world, player, "summon_spirit_wolf", EntitySpiritWolf.class, false);
        require(linkedWolf.isDead, "native spirit dismissal by recast failed");
        output("ORIGIN_DISMISS spell=wolf original=" + linkedWolf.getUniqueID() + " replacement=" + wolf.getUniqueID());
        rememberName(wolf, WOLF);

        EntitySpiritHorse linkedHorse = reloadWithoutPrivateFacts(world,
                checkSpawn(world, player, "summon_spirit_horse", EntitySpiritHorse.class, false), EntitySpiritHorse.class,
                candidate -> candidate.setOwnerUniqueId(null));
        checkMismatchedLink(server, sender, linkedHorse, WizardData.get(player).getVariable(SummonSpiritHorse.UUID_KEY), "horse");
        linkedHorse = reloadWithoutPrivateFacts(world, linkedHorse, EntitySpiritHorse.class, candidate -> candidate.setTamedBy(player));
        inspect(server, sender, linkedHorse, "AFFECTED");
        require(mark(linkedHorse).getString("ownershipEvidence").equals("legacy_wizardry_spell_link"), "Horse legacy link was not used");
        EntitySpiritHorse horse = checkSpawn(world, player, "summon_spirit_horse", EntitySpiritHorse.class, false);
        require(linkedHorse.isDead, "native horse dismissal by recast failed");
        output("ORIGIN_DISMISS spell=horse original=" + linkedHorse.getUniqueID() + " replacement=" + horse.getUniqueID());
        rememberName(horse, HORSE);

        EntitySnowman built = buildSnowGolem(world);
        rememberName(built, BUILT_SNOW);
        inspect(server, sender, built, "UNRESOLVED");
        EntitySpiritWolf eggWolf = EntitySpiritWolf.class.cast(spawnEgg(world, "ebwizardry:spirit_wolf", 14.5));
        rememberName(eggWolf, EGG_WOLF);
        eggWolf.setOwnerId(player.getUniqueID());
        eggWolf.setTamed(true);
        inspect(server, sender, eggWolf, "UNRESOLVED");
        EntitySpiritHorse eggHorse = EntitySpiritHorse.class.cast(spawnEgg(world, "ebwizardry:spirit_horse", 16.5));
        rememberName(eggHorse, EGG_HORSE);
        eggHorse.setTamedBy(player);
        inspect(server, sender, eggHorse, "UNRESOLVED");

        EntityWolf pet = new EntityWolf(world);
        pet.setPosition(5.5, 4, 5.5);
        pet.setOwnerId(player.getUniqueID());
        pet.setTamed(true);
        require(world.spawnEntity(pet), "vanilla pet did not spawn");
        inspect(server, sender, pet, "EXCLUDED");
        verifyMelee(snow, player, true);
        verifyMelee(wolf, player, true);
        verifyMelee(horse, player, true);
        verifyMelee(oldSnow, player, false);
        verifyMelee(built, player, false);
        verifyMelee(eggWolf, player, false);
        verifyMelee(eggHorse, player, false);
        verifyMelee(pet, player, false);
        checkNpcCast(server, sender, world, player);

        EntityPlayerMP other = NativeFixture.secondPlayer(server, world);
        other.setHealth(20);
        wolf.setAttackTarget(other);
        require(wolf.getAttackTarget() == null, "exact spirit accepted a protected player target");
        float before = other.getHealth();
        require(!wolf.attackEntityAsMob(other) && other.getHealth() == before, "exact spirit damaged the player");
        output("ORIGIN_OUTBOUND_VETO wolf=" + wolf.getUniqueID() + " player=" + other.getUniqueID() + " health=" + before);
        EntityZombie enemy = new EntityZombie(world);
        enemy.setPosition(13.5, 4, 8.5);
        enemy.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(40);
        enemy.setHealth(40);
        rememberName(enemy, ENEMY);
        require(world.spawnEntity(enemy), "enemy control did not spawn");
        wolf.setPosition(10.5, 4, 8.5);
        wolf.getAISit().setSitting(false);
        wolf.setSitting(false);
        wolf.setNoAI(false);
        wolf.setAttackTarget(enemy);
        require(wolf.getAttackTarget() == enemy, "exact spirit rejected an eligible mob");
        output("ORIGIN_FIGHT_READY wolf=" + wolf.getUniqueID() + " enemy=" + enemy.getUniqueID()
                + " distance=" + wolf.getDistance(enemy) + " hp=" + enemy.getHealth());
    }

    private static void checkFight(MinecraftServer server, WorldServer world) {
        EntitySpiritWolf wolf = (EntitySpiritWolf) named(server, WOLF);
        EntityZombie enemy = (EntityZombie) named(server, ENEMY);
        output("ORIGIN_FIGHT_RESULT wolf=" + wolf.getUniqueID() + " target="
                + (wolf.getAttackTarget() == null ? null : wolf.getAttackTarget().getUniqueID())
                + " enemy=" + enemy.getUniqueID() + " distance=" + wolf.getDistance(enemy) + " hp=" + enemy.getHealth());
        require(enemy.getHealth() < enemy.getMaxHealth(), "the original Wolf melee goal did not damage the enemy");
        world.removeEntityDangerously(enemy);
    }

    private static void clearOwners(MinecraftServer server, ICommandSender sender, EntityPlayerMP player) {
        EntitySpiritWolf wolf = (EntitySpiritWolf) named(server, WOLF);
        EntitySpiritHorse horse = (EntitySpiritHorse) named(server, HORSE);
        require(player.getUniqueID().equals(wolf.getOwnerId()) && wolf.isTamed()
                && player.getUniqueID().equals(horse.getOwnerUniqueId()) && horse.isTame(), "expected both native owners");
        wolf.setOwnerId(null);
        horse.setOwnerUniqueId(null);
        require(wolf.isTamed() && horse.isTame(), "the partial native state needs both tame flags intact");
        inspect(server, sender, wolf, "AFFECTED");
        inspect(server, sender, horse, "AFFECTED");
        output("ORIGIN_OWNERSHIP_UNAVAILABLE wolf=" + wolf.getUniqueID() + " horse=" + horse.getUniqueID());
        wolf.setTamed(false);
        horse.setHorseTamed(false);
        for (EntityLivingBase spirit : new EntityLivingBase[]{wolf, horse}) {
            inspect(server, sender, spirit, "EXCLUDED");
            verifyMelee(spirit, player, false);
        }
        output("ORIGIN_OWNERS_CLEARED wolf=" + wolf.getUniqueID() + " horse=" + horse.getUniqueID());
    }

    private static void restoreOwners(MinecraftServer server, ICommandSender sender, EntityPlayerMP player) {
        EntitySpiritWolf wolf = (EntitySpiritWolf) named(server, WOLF);
        EntitySpiritHorse horse = (EntitySpiritHorse) named(server, HORSE);
        for (EntityLivingBase spirit : new EntityLivingBase[]{wolf, horse}) {
            NBTTagCompound current = mark(spirit);
            require(current.getString("ownerKind").equals("UNOWNED") && !current.hasKey("ownerUUID"),
                    "cleared ownership was not persisted");
            output("ORIGIN_OWNER_REMOVAL_SAVED uuid=" + spirit.getUniqueID() + " facts=" + current);
        }
        wolf.setOwnerId(player.getUniqueID());
        wolf.setTamed(true);
        horse.setTamedBy(player);
        inspect(server, sender, wolf, "AFFECTED");
        inspect(server, sender, horse, "AFFECTED");
    }

    private static void verifyMelee(EntityLivingBase target, EntityPlayerMP player, boolean blocked) {
        float before = target.getHealth();
        boolean applied = target.attackEntityFrom(DamageSource.causePlayerDamage(player), 2);
        output("ORIGIN_INBOUND uuid=" + target.getUniqueID() + " type=" + EntityList.getKey(target)
                + " applied=" + applied + " before=" + before + " after=" + target.getHealth());
        require(blocked ? !applied && target.getHealth() == before : applied && target.getHealth() < before,
                "wrong player melee outcome for " + EntityList.getKey(target));
    }

    static <T extends EntityLiving> T checkSpawn(WorldServer world, EntityPlayerMP caster, String spell,
                                                  Class<T> type, boolean cancel) {
        SpawnTrace trace = new SpawnTrace(world, type, cancel);
        MinecraftForge.EVENT_BUS.register(trace);
        boolean cast;
        try {
            cast = Spell.get("ebwizardry:" + spell).cast(world, caster, EnumHand.MAIN_HAND, 0, new SpellModifiers());
        } finally {
            MinecraftForge.EVENT_BUS.unregister(trace);
        }
        require(trace.attempts.size() == 1, "wrong number of provider spawn attempts for " + spell);
        T entity = type.cast(trace.attempts.get(0));
        boolean usable = world.getEntityFromUuid(entity.getUniqueID()) == entity && entity.isAddedToWorld();
        output("ORIGIN_SPAWN spell=" + spell + " caster=" + caster.getUniqueID() + " uuid=" + entity.getUniqueID()
                + " attempts=" + trace.attempts.size() + " requestedCancel=" + cancel + " cast=" + cast + " live=" + usable
                + " duringJoin=" + trace.seenMark + " after=" + mark(entity));
        require(cast, "native cast bookkeeping changed for " + spell);
        require(usable != cancel, "spawn Boolean did not match the canceled or allowed native result");
        require(trace.seenMark.getString("originProvider").equals("ebwizardry:" + spell)
                && trace.seenMark.getString("ownerUUID").equals(caster.getUniqueID().toString()),
                "exact facts were not visible at the native join operation");
        require(cancel ? !entity.getEntityData().hasKey(DATA) : mark(entity).equals(trace.seenMark),
                "spawn transaction left inconsistent evidence");
        return entity;
    }

    private static void checkMismatchedLink(MinecraftServer server, ICommandSender sender, EntityLiving spirit, UUID link,
                                            String name) {
        require(spirit.getUniqueID().equals(link), "lost native spell link in mismatch control");
        inspect(server, sender, spirit, "UNRESOLVED");
        require(!spirit.getEntityData().hasKey(DATA), "inconsistent owner acquired a private mark");
        output("ORIGIN_LEGACY_MISMATCH spell=" + name + " uuid=" + spirit.getUniqueID() + " providerLink=" + link);
    }

    private static <T extends EntityLiving> T reloadWithoutPrivateFacts(WorldServer world, T original, Class<T> type,
                                                                       Consumer<T> beforeJoin) {
        NBTTagCompound legacy = original.serializeNBT().copy();
        legacy.getCompoundTag("ForgeData").removeTag(DATA);
        UUID uuid = original.getUniqueID();
        world.removeEntityDangerously(original);
        T loaded = type.cast(EntityList.createEntityFromNBT(legacy, world));
        require(loaded != null, "native legacy serialization did not reconstruct the entity");
        beforeJoin.accept(loaded);
        require(!loaded.getEntityData().hasKey(DATA) && world.spawnEntity(loaded)
                && loaded.getUniqueID().equals(uuid), "native legacy load did not retain UUID");
        return loaded;
    }

    private static Entity spawnEgg(WorldServer world, String id, double x) {
        ResourceLocation key = new ResourceLocation(id);
        require(EntityList.ENTITY_EGGS.containsKey(key), "registered egg is missing: " + id);
        Entity actual = ItemMonsterPlacer.spawnCreature(world, key, x, 4, 13.5);
        require(actual != null && actual.isAddedToWorld(), "native egg spawn failed: " + id);
        return actual;
    }

    private static EntitySnowman buildSnowGolem(WorldServer world) {
        SpawnTrace trace = new SpawnTrace(world, EntitySnowman.class, false);
        MinecraftForge.EVENT_BUS.register(trace);
        try {
            BlockPos feet = new BlockPos(5, 4, 11);
            world.setBlockState(feet, Blocks.SNOW.getDefaultState());
            world.setBlockState(feet.up(), Blocks.SNOW.getDefaultState());
            world.setBlockState(feet.up(2), Blocks.PUMPKIN.getDefaultState());
        } finally {
            MinecraftForge.EVENT_BUS.unregister(trace);
        }
        require(trace.attempts.size() == 1 && trace.seenMark.isEmpty(), "vanilla snow recipe did not create an ordinary golem");
        return (EntitySnowman) trace.attempts.get(0);
    }

    private static void checkNpcCast(MinecraftServer server, ICommandSender sender, WorldServer world, EntityPlayerMP player) {
        EntityZombie npc = new EntityZombie(world);
        npc.setPosition(4.5, 4, 8.5);
        npc.setNoAI(true);
        require(world.spawnEntity(npc), "NPC caster did not spawn");
        SpawnTrace trace = new SpawnTrace(world, EntityLiving.class, false);
        MinecraftForge.EVENT_BUS.register(trace);
        try {
            require(Spell.get("ebwizardry:summon_zombie").cast(world, npc, EnumHand.MAIN_HAND, 0, player, new SpellModifiers()),
                    "native NPC cast did not complete");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(trace);
        }
        require(trace.attempts.size() == 1 && trace.attempts.get(0) instanceof ISummonedCreature,
                "native NPC did not create one minion");
        Entity summoned = trace.attempts.get(0);
        require(npc.getUniqueID().equals(((ISummonedCreature) summoned).getOwnerId()), "NPC caster UUID mismatch");
        inspect(server, sender, summoned, "EXCLUDED");
        world.removeEntityDangerously(summoned);
        world.removeEntityDangerously(npc);
    }

    private static void moveForSave(MinecraftServer server, WorldServer world) throws Exception {
        List<String> entities = new ArrayList<>();
        for (int x = SAVE_CHUNK - 2; x <= SAVE_CHUNK + 2; x++) {
            for (int z = -2; z <= 2; z++) world.getChunk(x, z);
        }
        for (int index = 0; index < KEEP.length; index++) {
            EntityLiving entity = (EntityLiving) named(server, KEEP[index]);
            entity.setNoAI(true);
            entity.motionX = entity.motionY = entity.motionZ = 0;
            entity.setPosition(SAVE_CHUNK * 16 + 2.5 + index * 1.6, 4, 2.5);
            entities.add(KEEP[index] + "=" + entity.getUniqueID());
            output("ORIGIN_SAVE_READY name=" + KEEP[index] + " uuid=" + entity.getUniqueID() + " private=" + mark(entity));
        }
        Files.write(indexPath(world), entities, StandardCharsets.UTF_8);
    }

    private static void unload(MinecraftServer server, WorldServer world) throws Exception {
        require(server.getPlayerList().getPlayers().isEmpty(), "owner still present before unload");
        Chunk chunk = world.getChunkProvider().getLoadedChunk(SAVE_CHUNK, 0);
        require(chunk != null, "save chunk never loaded");
        for (String name : KEEP) require(named(server, name).chunkCoordX == SAVE_CHUNK, "entity did not migrate before unload: " + name);
        world.getChunkProvider().queueUnload(chunk);
        world.getChunkProvider().tick();
        ThreadedFileIOBase.getThreadedIOInstance().waitForFinish();
        require(world.getChunkProvider().getLoadedChunk(SAVE_CHUNK, 0) == null, "save chunk still loaded");
        output("ORIGIN_CHUNK_UNLOADED chunk=" + SAVE_CHUNK + ",0 players=0");
    }

    private static void inspectSaved(MinecraftServer server, ICommandSender sender, WorldServer world, String phase) throws Exception {
        require(server.getPlayerList().getPlayers().isEmpty(), "owner present for " + phase);
        require(world.getChunkProvider().getLoadedChunk(SAVE_CHUNK, 0) == null, "expected an unloaded chunk before " + phase);
        world.getChunk(SAVE_CHUNK, 0);
        Map<String, EntityLivingBase> restored = new HashMap<>();
        for (String line : Files.readAllLines(indexPath(world), StandardCharsets.UTF_8)) {
            String[] fields = line.split("=", 2);
            UUID uuid = UUID.fromString(fields[1]);
            Entity actual = world.getEntityFromUuid(uuid);
            require(actual instanceof EntityLivingBase && actual.getUniqueID().equals(uuid), "same UUID did not reload: " + fields[0]);
            restored.put(fields[0], (EntityLivingBase) actual);
            boolean affected = fields[0].equals(SNOW) || fields[0].equals(WOLF) || fields[0].equals(HORSE);
            inspect(server, sender, actual, affected ? "AFFECTED" : "UNRESOLVED");
            require(actual.getEntityData().hasKey(DATA) == affected, "wrong saved evidence boundary: " + fields[0]);
            output("ORIGIN_RESTORED phase=" + phase + " name=" + fields[0] + " uuid=" + uuid + " private=" + mark(actual));
        }
        if (phase.equals("restart")) {
            EntityPlayerMP other = NativeFixture.secondPlayer(server, world);
            verifyMelee(restored.get(SNOW), other, true);
            verifyMelee(restored.get(EGG_WOLF), other, false);
            EntitySpiritWolf wolf = (EntitySpiritWolf) restored.get(WOLF);
            EntityZombie enemy = new EntityZombie(world);
            enemy.setPosition(wolf.posX, wolf.posY, wolf.posZ + 1);
            require(world.spawnEntity(enemy), "restarted enemy control failed to spawn");
            float before = enemy.getHealth();
            require(wolf.attackEntityAsMob(enemy) && enemy.getHealth() < before, "restarted Wolf failed its allowed native hit");
            output("ORIGIN_AFTER_RESTART_COMBAT wolf=" + wolf.getUniqueID() + " enemy=" + enemy.getUniqueID()
                    + " healthBefore=" + before + " healthAfter=" + enemy.getHealth());
            world.removeEntityDangerously(enemy);
        }
        output("ORIGIN_PERSIST_PASS phase=" + phase + " players=0");
    }

    private static Path indexPath(WorldServer world) {
        return world.getSaveHandler().getWorldDirectory().toPath().resolve("wst-origin-identities.txt");
    }

    private static NBTTagCompound mark(Entity entity) {
        return entity.getEntityData().getCompoundTag(DATA).copy();
    }

    private static void rememberName(EntityLiving entity, String name) {
        entity.setCustomNameTag(name);
        entity.setNoAI(true);
    }

    public static final class SpawnTrace {
        private final WorldServer world;
        private final Class<?> type;
        private final boolean cancel;
        private final List<Entity> attempts = new ArrayList<>();
        private NBTTagCompound seenMark = new NBTTagCompound();

        SpawnTrace(WorldServer world, Class<?> type, boolean cancel) {
            this.world = world;
            this.type = type;
            this.cancel = cancel;
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
        public void join(EntityJoinWorldEvent event) {
            Entity entity = event.getEntity();
            if (event.getWorld() != world || !type.isInstance(entity)) return;
            attempts.add(entity);
            seenMark = mark(entity);
            if (cancel) event.setCanceled(true);
        }
    }
}
