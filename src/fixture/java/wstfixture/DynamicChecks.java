package wstfixture;

import com.google.common.base.Predicate;
import com.mahghuuuls.wizardrysummontweaks.compat.spellbundle.SpellBundleSummonProvider;
import com.mahghuuuls.wizardrysummontweaks.compat.wizardryutils.WizardryUtilsSummonProvider;
import com.mahghuuuls.wizardrysummontweaks.summon.SummonFacts;
import com.mahghuuuls.wizardrysummontweaks.summon.SummonProvider;
import com.mahghuuuls.wizardrysummontweaks.summon.SummonTracker;
import com.windanesz.wizardryutils.capability.SummonedCreatureData;
import electroblob.wizardry.item.ItemArtefact;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.block.Block;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.apache.logging.log4j.LogManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static wstfixture.IdentityFixture.*;

public final class DynamicChecks {
    private static final ResourceLocation FOXHOUND = new ResourceLocation("quark:foxhound");
    private static final String FOX_SPELL = "spellbundle:conjure_foxhound";
    private static final String UTILS_ORIGIN = "wizardryutils:summoned_creature";
    private static final String BUNDLE_ORIGIN = "spellbundle:summoned_creature";
    private static final String MARK = "wizardrysummontweaks";
    private static final double LANE = 6.5;
    private static final Field ATTACK_TARGET = ReflectionHelper.findField(EntityLiving.class, "attackTarget", "field_70696_bz");
    private static Expiry expiry;
    private static AiTrial ai;

    public static void run(MinecraftServer server, ICommandSender sender, String[] args) throws Exception {
        WorldServer world = server.worlds[0];
        String action = args[1];
        if ("restore".equals(action)) {
            restore(world);
            return;
        }
        if (action.startsWith("ai-")) {
            runAi(world, action, args, sender);
            return;
        }
        if ("collect".equals(action)) {
            Expiry trial = expiry;
            require(trial != null, "No active dynamic expiry trial");
            try {
                output("DY_EXPIRY_RESULT uuid=" + id(trial.summon) + " start=" + trial.start + " removal=" + trial.removal
                        + " nativeLifetime=" + utils(trial.summon).getLifetime());
                require(trial.summon.isDead && trial.removal - trial.start == 21, "Native Foxhound expiry disagreed with its age");
            } finally {
                MinecraftForge.EVENT_BUS.unregister(trial);
                trial.summon.setDead();
                expiry = null;
            }
            return;
        }
        EntityPlayerMP player = getPlayer(server, sender, args[3]);
        boolean enabled = Boolean.parseBoolean(args[2]);
        ProjectileChecks.equipPlayer(player, 8.5);
        List<EntityLiving> cleanup = new ArrayList<>();
        try {
            switch (action) {
                case "setup":
                    setup(world);
                    break;
                case "stores":
                    stores(world, player, enabled, cleanup);
                    break;
                case "live":
                    live(world, player, enabled, cleanup);
                    break;
                case "controls":
                    controls(world, player, enabled, cleanup);
                    break;
                case "rewards":
                    rewards(world, player, enabled, cleanup);
                    break;
                case "transport":
                    transport(world, player, cleanup);
                    break;
                case "expiry":
                    startExpiry(world, player);
                    break;
                case "save":
                    save(world, player);
                    break;
                case "restored-hit":
                    checkRestoredCombat(world, player, enabled);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown dynamic case " + action);
            }
        } finally {
            for (EntityLiving entity : cleanup) entity.setDead();
        }
        output("DY_CASE_PASS case=" + action + " enabled=" + enabled);
    }

    private static void setup(WorldServer world) {
        loadArena(world);
        ProjectileChecks.floor(world, 2, 40);
        Class<? extends Entity> actor = EntityList.getClass(FOXHOUND);
        require(actor != null && "vazkii.quark.world.entity.EntityFoxhound".equals(actor.getName()), "Native Foxhound registry absent");
        Spell fox = spell(FOX_SPELL);
        require("com.windanesz.wizardryutils.spell.SpellDynamicMinion".equals(fox.getClass().getSuperclass().getName()),
                "Foxhound does not use the actual Wizardry Utils cast");
        output("DY_REGISTRY spell=" + fox.getRegistryName() + " enabled=" + fox.isEnabled() + " type=" + actor.getName()
                + " entity=" + FOXHOUND + " castClass=" + fox.getClass().getName());
    }

    private static void stores(WorldServer world, EntityPlayerMP player, boolean enabled, List<EntityLiving> cleanup) throws Exception {
        EntityWolf absentMarker = new EntityWolf(world);
        SummonedCreatureData utils = utils(absentMarker);
        com.windanesz.spellbundle.capability.SummonedCreatureData bundle = bundle(absentMarker);
        require(utils != null && bundle != null, "Both actual capability stores must be attached");
        utils.setOwnerId(player.getUniqueID());
        bundle.setOwnerId(player.getUniqueID());
        spawn(world, absentMarker, cleanup, 11.5);
        inspect(world.getMinecraftServer(), world.getMinecraftServer(), absentMarker, "EXCLUDED");
        require(!SummonedCreatureData.isSummonedEntity(absentMarker) && !bundleMarked(absentMarker)
                && !absentMarker.getEntityData().hasKey(MARK), "Stored UUID without a summon mark became origin");
        output("DY_STORES_FALSE uuid=" + id(absentMarker) + " utils=" + utils.serializeNBT() + " bundle=" + bundle.serializeNBT()
                + " owner=" + player.getUniqueID());
        markerSensitivity(world.getMinecraftServer(), absentMarker, "wizardryutils", new WizardryUtilsSummonProvider());
        markerSensitivity(world.getMinecraftServer(), absentMarker, "spellbundle", new SpellBundleSummonProvider());

        EntityWolf local = new EntityWolf(world);
        bundle(local).setCaster(player);
        bundle(local).setLifetime(1200);
        spawn(world, local, cleanup, 13.5);
        inspect(world.getMinecraftServer(), world.getMinecraftServer(), local, "AFFECTED");
        require(bundleMarked(local) && !SummonedCreatureData.isSummonedEntity(local)
                && player.getUniqueID().equals(bundle(local).getOwnerId()), "Local capability source evidence is inconsistent");
        requireOrigin(local, BUNDLE_ORIGIN, player.getUniqueID(), "PLAYER");
        output("DY_STORES_LOCAL uuid=" + id(local) + " native=" + bundle(local).serializeNBT() + " mark=" + mark(local));
        assertBundleSelector(world, local, enabled);
        // Exercise actual simultaneous observations. Two stores claiming origin must not pick whichever ran first.
        utils(local).setCaster(player);
        utils(local).setLifetime(1200);
        NBTTagCompound kept = mark(local).copy();
        require(SummonedCreatureData.isSummonedEntity(local) && bundleMarked(local), "Dual store control was not armed");
        local.onUpdate();
        inspect(world.getMinecraftServer(), world.getMinecraftServer(), local, "UNRESOLVED");
        require(kept.equals(mark(local)), "Origin conflict changed the saved mark during a native update");
        playerHit(player, local, false, "ambiguous-stores");
        output("DY_STORES_CONFLICT uuid=" + id(local) + " nativeUtils=" + utils(local).serializeNBT()
                + " nativeBundle=" + bundle(local).serializeNBT() + " kept=" + kept);
    }

    @SuppressWarnings("unchecked")
    private static void assertBundleSelector(WorldServer world, EntityWolf local, boolean protect) throws Exception {
        Object data = bundle(local);
        Method accessor = data.getClass().getDeclaredMethod("getTargetSelector");
        accessor.setAccessible(true);
        Predicate<Entity> selector = (Predicate<Entity>) accessor.invoke(data);
        EntityPlayerMP player = MeleeChecks.secondPlayer(world.getMinecraftServer(), world);
        EntityZombie enemy = new EntityZombie(world);
        try {
            boolean allowsPlayer = selector.apply(player);
            boolean allowsEnemy = selector.apply(enemy);
            output("DY_LOCAL_SELECTOR uuid=" + id(local) + " player=" + id(player) + " allowsPlayer=" + allowsPlayer
                    + " enemy=" + id(enemy) + " allowsEnemy=" + allowsEnemy);
            require(allowsPlayer != protect && allowsEnemy, "Spell Bundle's native selector disagreed with eligibility");
        } finally {
            player.setDead();
            enemy.setDead();
        }
    }

    private static void markerSensitivity(MinecraftServer server, EntityWolf carrier, String key, SummonProvider original) {
        SummonProvider attachmentOnly = new SummonProvider() {
            @Override public boolean matches(Entity entity) { return entity == carrier; }
            @Override public SummonFacts observe(Entity entity, SummonFacts saved) { return original.observe(entity, saved); }
        };
        SummonTracker mutated = new SummonTracker(LogManager.getLogger("WSTDynamicFixture"));
        mutated.start(server, Collections.singletonMap(key, attachmentOnly));
        try {
            SummonFacts.Result result = mutated.inspect(carrier);
            output("DY_MARKER_SENSITIVITY store=" + key + " carrier=" + id(carrier) + " attachmentOnly=" + result.status);
            require(result.affected(), "An attachment-only predicate did not expose the false-marker regression");
        } finally {
            mutated.stop();
        }
    }

    private static void live(WorldServer world, EntityPlayerMP player, boolean enabled, List<EntityLiving> cleanup) throws Exception {
        EntityWolf fox = fox(world, player, player, cleanup, 11.5);
        inspect(world.getMinecraftServer(), world.getMinecraftServer(), fox, "AFFECTED");
        requireOrigin(fox, UTILS_ORIGIN, player.getUniqueID(), "PLAYER");
        EntityPlayerMP other = MeleeChecks.secondPlayer(world.getMinecraftServer(), world);
        ProjectileChecks.equipPlayer(other, 13.5);
        EntityZombie enemy = new EntityZombie(world);
        spawn(world, enemy, cleanup, 14.5);
        EntityWolf peer = fox(world, player, player, cleanup, 15.5);
        requireAlternateOwner(world);
        utils(peer).setOwnerId(SECOND_PLAYER);
        peer.onUpdate();
        requireOrigin(peer, UTILS_ORIGIN, SECOND_PLAYER, "PLAYER");
        CombatProbe probe = new CombatProbe(fox);
        MinecraftForge.EVENT_BUS.register(probe);
        try {
            fox.setAttackTarget(other);
            EntityLivingBase targetedPlayer = fox.getAttackTarget();
            output("DY_TARGET uuid=" + id(fox) + " attempted=" + id(other) + " stored=" + id(targetedPlayer) + " events=" + probe.playerTargets);
            require((targetedPlayer == other) != enabled && (probe.playerTargets > 0) != enabled,
                    "Native setter did not distinguish protected player target");
            fox.setAttackTarget(enemy);
            require(fox.getAttackTarget() == enemy, "Allowed enemy target did not reach native state");
            attack(fox, other, enabled, "player", probe);
            attack(fox, enemy, false, "enemy", probe);
            attack(fox, peer, false, "affected-peer", probe);
            require(probe.sources.stream().anyMatch(source -> source.getImmediateSource() == fox && source.getTrueSource() == player),
                    "Allowed Foxhound hit did not retain original caster credit");
            playerHit(player, fox, enabled, "owner");
            playerHit(other, fox, enabled, "nonowner");
            output("DY_LIVE uuid=" + id(fox) + " spell=" + FOX_SPELL + " native=" + utils(fox).serializeNBT() + " mark=" + mark(fox));
        } finally {
            MinecraftForge.EVENT_BUS.unregister(probe);
            other.setDead();
        }
    }

    private static void runAi(WorldServer world, String action, String[] args, ICommandSender sender) throws Exception {
        if ("ai-start".equals(action)) {
            require(ai == null, "A Foxhound AI trial is already running");
            MinecraftServer server = world.getMinecraftServer();
            EntityPlayerMP owner = getPlayer(server, sender, args[3]);
            boolean protect = Boolean.parseBoolean(args[2]);
            ProjectileChecks.equipPlayer(owner, 8.5);
            EntityWolf fox = fox(world, owner, owner, new ArrayList<>(), 11.5);
            EntityPlayerMP other = MeleeChecks.secondPlayer(server, world);
            ProjectileChecks.equipPlayer(other, 12.5);
            MeleeChecks.preparePlayerAttack(other, Enchantments.SHARPNESS);
            float before = owner.getHealth();
            other.attackTargetEntityWithCurrentItem(owner);
            require(owner.getHealth() < before && owner.getRevengeTarget() == other,
                    "Original other-player hit did not arm native owner retaliation");
            ai = new AiTrial(world, owner, fox, other, protect);
            MinecraftForge.EVENT_BUS.register(ai);
            fox.setNoAI(false);
            output("DY_AI_START actor=" + id(fox) + " owner=" + id(owner) + " other=" + id(other)
                    + " hpBefore=" + before + " hpAfter=" + owner.getHealth() + " nativeRevenge=" + id(owner.getRevengeTarget())
                    + " ownerTask=" + ai.hasOwnerTask + " nearestTask=" + ai.hasNearestTask);
            require(ai.hasOwnerTask && ai.hasNearestTask, "Dynamic provider did not install its native owner and nearest-target tasks");
            return;
        }
        AiTrial trial = ai;
        require(trial != null, "No active Foxhound AI trial");
        try {
            switch (action) {
                case "ai-stale":
                    trial.observeRetaliationAndSeedStale();
                    break;
                case "ai-enemy":
                    trial.observeStaleAndStartCompetition();
                    break;
                case "ai-collect":
                    trial.observeCompetition();
                    trial.stop();
                    ai = null;
                    break;
                default:
                    throw new IllegalArgumentException(action);
            }
        } catch (Exception | Error failure) {
            trial.stop();
            ai = null;
            throw failure;
        }
    }

    private static void controls(WorldServer world, EntityPlayerMP player, boolean enabled, List<EntityLiving> cleanup) throws Exception {
        EntityWolf ordinary = newFox(world);
        ordinary.setTamed(true);
        ordinary.setOwnerId(player.getUniqueID());
        spawn(world, ordinary, cleanup, 11.5);
        inspect(world.getMinecraftServer(), world.getMinecraftServer(), ordinary, "EXCLUDED");
        require(!SummonedCreatureData.isSummonedEntity(ordinary) && !bundleMarked(ordinary), "Tamed same-class animal became a summon");
        playerHit(player, ordinary, false, "ordinary");
        EntityVillager npc = new EntityVillager(world);
        spawn(world, npc, cleanup, 7.5);
        EntityWolf npcFox = fox(world, npc, player, cleanup, 11.5);
        inspect(world.getMinecraftServer(), world.getMinecraftServer(), npcFox, "EXCLUDED");
        requireOrigin(npcFox, UTILS_ORIGIN, npc.getUniqueID(), "NON_PLAYER");
        attack(npcFox, player, false, "npc-owned", null);

        EntityWolf transferred = fox(world, player, player, cleanup, 14.5);
        requireAlternateOwner(world);
        utils(transferred).setOwnerId(SECOND_PLAYER);
        transferred.onUpdate();
        inspect(world.getMinecraftServer(), world.getMinecraftServer(), transferred, "AFFECTED");
        requireOrigin(transferred, UTILS_ORIGIN, SECOND_PLAYER, "PLAYER");
        output("DY_TRANSFER case=offline-player uuid=" + id(transferred) + " native=" + utils(transferred).serializeNBT()
                + " mark=" + mark(transferred));
        playerHit(player, transferred, enabled, "transferred-player");
        utils(transferred).setCaster(npc);
        transferred.onUpdate();
        inspect(world.getMinecraftServer(), world.getMinecraftServer(), transferred, "EXCLUDED");
        requireOrigin(transferred, UTILS_ORIGIN, npc.getUniqueID(), "NON_PLAYER");
        output("DY_TRANSFER case=live-npc uuid=" + id(transferred) + " native=" + utils(transferred).serializeNBT()
                + " mark=" + mark(transferred));
        playerHit(player, transferred, false, "transferred-npc");
        utils(transferred).setOwnerId(null);
        transferred.onUpdate();
        inspect(world.getMinecraftServer(), world.getMinecraftServer(), transferred, "EXCLUDED");
        requireOrigin(transferred, UTILS_ORIGIN, null, "UNOWNED");
        output("DY_TRANSFER case=removed-owner uuid=" + id(transferred) + " native=" + utils(transferred).serializeNBT()
                + " mark=" + mark(transferred));
        playerHit(player, transferred, false, "removed-owner");
        require(transferred.isTamed() && player.getUniqueID().equals(transferred.getOwnerId()) && utils(transferred).getOwnerId() == null,
                "Ownership control must distinguish the actual capability from unrelated vanilla taming");
        output("DY_CONTROL ordinary=" + id(ordinary) + " npc=" + id(npcFox) + " transferred=" + id(transferred)
                + " currentNative=" + utils(transferred).serializeNBT() + " vanillaOwner=" + transferred.getOwnerId());
    }

    private static void rewards(WorldServer world, EntityPlayerMP player, boolean enabled, List<EntityLiving> cleanup) {
        EntityWolf fox = fox(world, player, player, cleanup, 11.5);
        EntityWolf ordinary = newFox(world);
        spawn(world, ordinary, cleanup, 13.5);
        EntityZombie killer = new EntityZombie(world);
        spawn(world, killer, cleanup, 10.5);
        killer.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(100);
        killWithGear(world, killer, fox, enabled, true);
        killWithGear(world, killer, ordinary, false, false);
    }

    private static void killWithGear(WorldServer world, EntityZombie killer, EntityWolf victim, boolean suppress, boolean summon) {
        victim.setItemStackToSlot(EntityEquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        victim.setDropChance(EntityEquipmentSlot.CHEST, 2);
        DropProbe probe = new DropProbe(world, victim);
        MinecraftForge.EVENT_BUS.register(probe);
        try {
            killer.attackEntityAsMob(victim);
            output("DY_REWARD uuid=" + id(victim) + " summon=" + summon + " captured=" + probe.before + " final=" + probe.after
                    + " canceled=" + probe.canceled + " spawnedChest=" + probe.spawned);
            require(victim.getHealth() == 0 && probe.before > 0 && (probe.after == 0) == suppress
                    && probe.canceled == summon && (probe.spawned > 0) != summon, "Native captured equipment reward differed");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(probe);
        }
    }

    private static void transport(WorldServer world, EntityPlayerMP player, List<EntityLiving> cleanup) throws Exception {
        ResourceLocation waystoneId = new ResourceLocation("waystones:waystone");
        Block waystone = ForgeRegistries.BLOCKS.getValue(waystoneId);
        require(waystone != null && waystone != Blocks.AIR, "Waystones integration has no native block");
        BlockPos destination = new BlockPos(30, 4, 6);
        world.setBlockState(destination, waystone.getDefaultState());
        TileEntity tile = world.getTileEntity(destination);
        require(tile != null, "Native Waystone tile did not attach");
        tile.getClass().getMethod("setWaystoneName", String.class).invoke(tile, "Dynamic fixture");
        EntityWolf pet = new EntityWolf(world);
        pet.setTamed(true);
        pet.setOwnerId(player.getUniqueID());
        spawn(world, pet, cleanup, 10.5);
        Item ring = ForgeRegistries.ITEMS.getValue(new ResourceLocation("spellbundle:ring_warpstone"));
        require(ring != null && ring != Items.AIR, "Native Warpstone ring is missing");
        IItemHandlerModifiable baubles = ringHandler(player, ring);
        ItemStack original = baubles.getStackInSlot(1).copy();
        try {
            player.connection.setPlayerLocation(28.55, 4, LANE, -90, 28);
            player.setSneaking(true);
            require(cast(world, player, null, "spellbundle:summon_ally", 0), "Native Summon Ally bind cast failed");
            Class<?> warp = Class.forName("com.windanesz.spellbundle.spell.waystones.Warp");
            Optional<?> bound = (Optional<?>) warp.getMethod("getBoundWaystone", EntityPlayer.class).invoke(null, player);
            require(bound.isPresent(), "Actual Summon Ally cast did not bind its Waystone");
            player.setSneaking(false);
            ProjectileChecks.equipPlayer(player, 8.5);
            require(pet.posX == 10.5, "Summon Ally bind moved the original pet");
            inspect(world.getMinecraftServer(), world.getMinecraftServer(), pet, "EXCLUDED");
            baubles.setStackInSlot(1, new ItemStack(ring));
            require(ItemArtefact.isArtefactActive(player, ring), "Warpstone ring not active");
            double before = pet.posX;
            UUID uuid = pet.getUniqueID();
            require(cast(world, player, pet, "spellbundle:mass_warp", 80), "Native Mass Warp cast failed");
            require(pet.posX > 26 && pet.getUniqueID().equals(uuid) && world.getEntityFromUuid(uuid) == pet,
                    "Actual native transport did not move the same ordinary pet");
            inspect(world.getMinecraftServer(), world.getMinecraftServer(), pet, "EXCLUDED");
            require(!SummonedCreatureData.isSummonedEntity(pet) && !bundleMarked(pet) && !pet.getEntityData().hasKey(MARK),
                    "Pet transport invented summon origin");
            output("DY_TRANSPORT uuid=" + id(pet) + " beforeX=" + before + " afterX=" + pet.posX + " bind=spellbundle:summon_ally"
                    + " move=spellbundle:mass_warp nativeUtils=" + utils(pet).serializeNBT() + " nativeBundle=" + bundle(pet).serializeNBT());
        } finally {
            baubles.setStackInSlot(1, original);
            player.setSneaking(false);
            world.setBlockToAir(destination);
        }
    }

    private static IItemHandlerModifiable ringHandler(EntityPlayerMP player, Item ring) throws ReflectiveOperationException {
        Class<?> api = Class.forName("baubles.api.BaublesApi");
        Object handler = api.getMethod("getBaublesHandler", EntityPlayer.class).invoke(null, player);
        Class<?> type = Class.forName("baubles.api.cap.IBaublesItemHandler");
        boolean valid = (Boolean) type.getMethod("isItemValidForSlot", int.class, ItemStack.class, EntityLivingBase.class)
                .invoke(handler, 1, new ItemStack(ring), player);
        require(valid, "Installed Baubles provider rejects Warpstone ring");
        return (IItemHandlerModifiable) handler;
    }

    private static void startExpiry(WorldServer world, EntityPlayerMP player) {
        List<EntityLiving> spawned = new ArrayList<>();
        EntityWolf fox = fox(world, player, player, spawned, 12.5);
        int lifetime = utils(fox).getLifetime();
        require(expiry == null && lifetime > 25, "Native life trial is unavailable");
        fox.ticksExisted = lifetime - 20;
        expiry = new Expiry(world, fox);
        MinecraftForge.EVENT_BUS.register(expiry);
        output("DY_EXPIRY_START uuid=" + id(fox) + " nativeLifetime=" + lifetime + " age=" + fox.ticksExisted + " tick=" + expiry.start);
    }

    private static void save(WorldServer world, EntityPlayerMP player) throws Exception {
        loadRemote(world);
        ProjectileChecks.floor(world, 1024, 1036);
        EntityWolf fox = fox(world, player, player, new ArrayList<>(), 1024.5);
        requireOrigin(fox, UTILS_ORIGIN, player.getUniqueID(), "PLAYER");
        requireAlternateOwner(world);
        utils(fox).setOwnerId(SECOND_PLAYER);
        fox.onUpdate();
        requireOrigin(fox, UTILS_ORIGIN, SECOND_PLAYER, "PLAYER");
        Files.write(savePath(world), Arrays.asList(id(fox), SECOND_PLAYER.toString(), id(player)), StandardCharsets.US_ASCII);
        output("DY_SAVE_READY uuid=" + id(fox) + " originalCaster=" + id(player) + " owner=" + SECOND_PLAYER
                + " native=" + utils(fox).serializeNBT() + " mark=" + mark(fox));
    }

    private static void restore(WorldServer world) throws Exception {
        Path folder = savePath(world).getParent();
        Files.copy(folder.resolve("region/r.2.0.mca"), folder.resolve("wst-dynamic-region.mca"));
        loadRemote(world);
        List<String> ids = Files.readAllLines(savePath(world), StandardCharsets.US_ASCII);
        UUID owner = UUID.fromString(ids.get(1));
        Entity restored = world.getEntityFromUuid(UUID.fromString(ids.get(0)));
        require(restored instanceof EntityWolf && FOXHOUND.equals(EntityList.getKey(restored)) && restored.isEntityAlive(),
                "Same native saved Foxhound UUID did not load");
        EntityWolf fox = (EntityWolf) restored;
        require(world.getMinecraftServer().getPlayerList().getPlayerByUUID(owner) == null && utils(fox).getCaster() == null
                && owner.equals(utils(fox).getOwnerId()) && SummonedCreatureData.isSummonedEntity(fox) && !bundleMarked(fox),
                "Native reload ownership or absence inconsistent");
        requireOrigin(fox, UTILS_ORIGIN, owner, "PLAYER");
        inspect(world.getMinecraftServer(), world.getMinecraftServer(), fox, "AFFECTED");
        output("DY_RESTORE_READY uuid=" + id(fox) + " offlineOwner=" + owner);
    }

    private static void checkRestoredCombat(WorldServer world, EntityPlayerMP player, boolean protect) throws Exception {
        List<String> ids = Files.readAllLines(savePath(world), StandardCharsets.US_ASCII);
        UUID owner = UUID.fromString(ids.get(1));
        Entity candidate = world.getEntityFromUuid(UUID.fromString(ids.get(0)));
        require(candidate instanceof EntityWolf && FOXHOUND.equals(EntityList.getKey(candidate)) && candidate.isEntityAlive(),
                "The original restored actor is unavailable for combat");
        EntityWolf fox = (EntityWolf) candidate;
        require(ids.get(2).equals(id(player)) && !owner.equals(player.getUniqueID())
                && world.getMinecraftServer().getPlayerList().getPlayerByUUID(owner) == null && utils(fox).getCaster() == null,
                "Restored combat must use the joined non-owner while the current owner stays offline");
        ProjectileChecks.equipPlayer(player, 1026.5);
        attack(fox, player, protect, "restored-player", null);
        playerHit(player, fox, protect, "restored-summon");
        output("DY_RESTORE_PASS uuid=" + id(fox) + " offlineOwner=" + owner + " native=" + utils(fox).serializeNBT() + " mark=" + mark(fox));
    }

    private static void loadRemote(WorldServer world) {
        for (int x = 61; x <= 67; x++) for (int z = -3; z <= 3; z++) world.getChunk(x, z);
    }

    private static Path savePath(WorldServer world) {
        return world.getSaveHandler().getWorldDirectory().toPath().resolve("wst-dynamic-ids.txt");
    }

    private static void attack(EntityWolf fox, EntityLivingBase victim, boolean blocked, String label, CombatProbe probe) {
        victim.setHealth(victim.getMaxHealth());
        float absorption = victim instanceof EntityPlayer ? 1 : 0;
        victim.setAbsorptionAmount(absorption);
        if (victim instanceof EntityPlayer) {
            victim.setItemStackToSlot(EntityEquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE, 1, 7));
        }
        victim.hurtResistantTime = 0;
        victim.extinguish();
        victim.motionX = victim.motionZ = 0;
        float before = victim.getHealth();
        int armor = victim.getItemStackFromSlot(EntityEquipmentSlot.CHEST).getItemDamage();
        int sourceStart = probe == null ? 0 : probe.sources.size();
        fox.attackEntityAsMob(victim);
        float after = victim.getHealth();
        double impulse = Math.hypot(victim.motionX, victim.motionZ);
        output("DY_ATTACK kind=" + label + " fox=" + id(fox) + " victim=" + id(victim) + " before=" + before + " after="
                + after + " fire=" + victim.isBurning() + " absorptionBefore=" + absorption + " absorptionAfter="
                + victim.getAbsorptionAmount() + " armorBefore=" + armor + " armorAfter="
                + victim.getItemStackFromSlot(EntityEquipmentSlot.CHEST).getItemDamage()
                + " impulse=" + impulse + " seen=" + (probe == null ? 0 : probe.sources.size() - sourceStart));
        require((after < before) != blocked && (!blocked || !victim.isBurning()), "Foxhound native hit disagreed with controls");
        if (victim instanceof EntityPlayer) {
            int armorAfter = victim.getItemStackFromSlot(EntityEquipmentSlot.CHEST).getItemDamage();
            require((victim.getAbsorptionAmount() == absorption) == blocked && (impulse > 0.05) != blocked,
                    "Native Foxhound hit did not distinguish charged absorption and knockback");
            if (blocked) require(armor == armorAfter, "Blocked Foxhound damage wore the equipped armor");
            if ("restored-player".equals(label) && !blocked) {
                require(armorAfter > armor && victim.isBurning(), "Direct offline hit did not reach native fire and armor wear");
            }
        }
    }

    private static void playerHit(EntityPlayerMP player, EntityWolf fox, boolean blocked, String label) throws Exception {
        fox.setHealth(fox.getMaxHealth());
        fox.setAbsorptionAmount(1);
        fox.hurtResistantTime = 0;
        fox.motionX = fox.motionZ = 0;
        fox.setItemStackToSlot(EntityEquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE, 1, 11));
        float before = fox.getHealth();
        MeleeChecks.preparePlayerAttack(player, Enchantments.SHARPNESS);
        player.getHeldItem(EnumHand.MAIN_HAND).addEnchantment(Enchantments.KNOCKBACK, 1);
        player.attackTargetEntityWithCurrentItem(fox);
        double impulse = Math.hypot(fox.motionX, fox.motionZ);
        output("DY_PLAYER_HIT kind=" + label + " actor=" + id(player) + " victim=" + id(fox) + " before=" + before
                + " after=" + fox.getHealth() + " absorption=" + fox.getAbsorptionAmount() + " armor="
                + fox.getItemStackFromSlot(EntityEquipmentSlot.CHEST) + " impulse=" + impulse);
        require((fox.getHealth() < before) != blocked && (fox.getAbsorptionAmount() == 1) == blocked
                && (impulse > 0.05) != blocked,
                "Ordinary player hit disagreed with dynamic summon rule");
        require(fox.getItemStackFromSlot(EntityEquipmentSlot.CHEST).getItem() == Items.IRON_CHESTPLATE
                && fox.getItemStackFromSlot(EntityEquipmentSlot.CHEST).getItemDamage() == 11,
                "The native Foxhound did not retain its equipped chestplate");
    }

    private static EntityWolf fox(WorldServer world, EntityLivingBase caster, EntityLivingBase target, List<EntityLiving> cleanup, double x) {
        Set<Entity> already = new HashSet<>(world.loadedEntityList);
        require(cast(world, caster, target, FOX_SPELL, 0), "Native Conjure Foxhound cast returned false");
        EntityWolf selected = null;
        for (Entity entity : world.loadedEntityList) {
            if (already.contains(entity) || !FOXHOUND.equals(EntityList.getKey(entity))) continue;
            EntityWolf fox = (EntityWolf) entity;
            cleanup.add(fox);
            if (selected == null) selected = fox;
            else fox.setDead();
        }
        require(selected != null && SummonedCreatureData.isSummonedEntity(selected) && !bundleMarked(selected)
                        && caster.getUniqueID().equals(utils(selected).getOwnerId()), "Native cast did not establish Utils Foxhound provenance");
        selected.setNoAI(true);
        selected.setPositionAndUpdate(x, 4, LANE);
        return selected;
    }

    private static Spell spell(String id) {
        Spell spell = Spell.get(id);
        require(spell != null && id.equals(String.valueOf(spell.getRegistryName())) && spell.isEnabled(), "Spell unavailable: " + id);
        return spell;
    }

    private static boolean cast(WorldServer world, EntityLivingBase caster, EntityLivingBase target, String name, int ticks) {
        Spell spell = spell(name);
        boolean result = caster instanceof EntityPlayerMP
                ? spell.cast(world, (EntityPlayerMP) caster, EnumHand.MAIN_HAND, ticks, new SpellModifiers())
                : spell.cast(world, (EntityLiving) caster, EnumHand.MAIN_HAND, ticks, target, new SpellModifiers());
        output("DY_CAST id=" + spell.getRegistryName() + " caster=" + id(caster) + " target=" + id(target) + " ticks=" + ticks + " result=" + result);
        return result;
    }

    private static EntityWolf newFox(WorldServer world) {
        Entity created = EntityList.createEntityByIDFromName(FOXHOUND, world);
        require(created instanceof EntityWolf, "Unable to construct the ordinary registered Foxhound");
        return (EntityWolf) created;
    }

    private static void spawn(WorldServer world, EntityLiving entity, List<EntityLiving> cleanup, double x) {
        entity.setNoAI(true);
        entity.setPosition(x, 4, LANE);
        require(world.spawnEntity(entity), "Native world rejected " + entity.getClass().getName());
        cleanup.add(entity);
    }

    private static SummonedCreatureData utils(EntityWolf wolf) { return SummonedCreatureData.get(wolf); }

    private static com.windanesz.spellbundle.capability.SummonedCreatureData bundle(EntityWolf wolf) {
        return com.windanesz.spellbundle.capability.SummonedCreatureData.get(wolf);
    }

    private static boolean bundleMarked(EntityWolf wolf) {
        return com.windanesz.spellbundle.capability.SummonedCreatureData.isSummonedEntity(wolf);
    }

    private static NBTTagCompound mark(Entity entity) { return entity.getEntityData().getCompoundTag(MARK); }

    private static void requireAlternateOwner(WorldServer world) {
        require(Arrays.asList(world.getMinecraftServer().getPlayerList().getAvailablePlayerDat()).contains(SECOND_PLAYER.toString())
                        && world.getMinecraftServer().getPlayerList().getPlayerByUUID(SECOND_PLAYER) == null,
                "Known alternate player must be offline");
    }

    private static void requireOrigin(Entity entity, String origin, UUID owner, String kind) {
        NBTTagCompound mark = mark(entity);
        boolean matchingOwner = owner == null ? !mark.hasKey("ownerUUID") : owner.toString().equals(mark.getString("ownerUUID"));
        require(mark.getInteger("schema") == 1 && origin.equals(mark.getString("originProvider"))
                && matchingOwner && kind.equals(mark.getString("ownerKind")),
                "Native actor's stored origin or owner differs: " + mark);
    }

    private static String id(Entity entity) { return entity == null ? "none" : entity.getUniqueID().toString(); }

    public static final class AiTrial {
        final WorldServer world;
        final EntityPlayerMP owner;
        final EntityWolf fox;
        final EntityPlayerMP other;
        final boolean protect;
        final boolean hasOwnerTask;
        final boolean hasNearestTask;
        int playerTargets;
        int enemyTargets;
        EntityZombie enemy;

        AiTrial(WorldServer world, EntityPlayerMP owner, EntityWolf fox, EntityPlayerMP other, boolean protect) {
            this.world = world;
            this.owner = owner;
            this.fox = fox;
            this.other = other;
            this.protect = protect;
            hasOwnerTask = fox.targetTasks.taskEntries.stream().anyMatch(entry ->
                    entry.action.getClass().getName().equals("com.windanesz.wizardryutils.entity.ai.EntityAIMinionOwnerHurtByTarget"));
            hasNearestTask = fox.targetTasks.taskEntries.stream().anyMatch(entry ->
                    entry.action.getClass() == net.minecraft.entity.ai.EntityAINearestAttackableTarget.class);
        }

        void observeRetaliationAndSeedStale() throws IllegalAccessException {
            EntityLivingBase target = fox.getAttackTarget();
            output("DY_AI_RETALIATION actor=" + id(fox) + " player=" + id(other) + " stored=" + id(target) + " playerTargets=" + playerTargets);
            require(protect ? target == null && playerTargets == 0 : target == other && playerTargets > 0,
                    "Native owner-retaliation task did not distinguish the protection");
            fox.setNoAI(true);
            ATTACK_TARGET.set(fox, other);
            output("DY_AI_STALE_SEEDED actor=" + id(fox) + " rawTarget=" + id(fox.getAttackTarget()) + " player=" + id(other));
        }

        void observeStaleAndStartCompetition() throws Exception {
            EntityLivingBase stale = fox.getAttackTarget();
            output("DY_AI_STALE_OBSERVED actor=" + id(fox) + " player=" + id(other) + " stored=" + id(stale));
            require(stale == (protect ? null : other), "Native living ticks left an unexpected stale target");
            owner.setRevengeTarget(null);
            fox.setAttackTarget(null);
            fox.setPositionAndUpdate(11.5, 4, LANE);
            ProjectileChecks.equipPlayer(other, 12.65);
            require(world.spawnEntity(other), "Native world did not admit the alternate player entity");
            enemy = new EntityZombie(world);
            enemy.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(40);
            enemy.setHealth(40);
            spawn(world, enemy, new ArrayList<>(), 14.5);
            AxisAlignedBB search = fox.getEntityBoundingBox().grow(8);
            require(world.getEntitiesWithinAABB(EntityLivingBase.class, search).contains(other)
                    && world.getEntitiesWithinAABB(EntityLivingBase.class, search).contains(enemy)
                    && fox.getDistance(other) < fox.getDistance(enemy), "Both candidates must enter the real nearest-target query");
            playerTargets = enemyTargets = 0;
            fox.setNoAI(false);
            output("DY_AI_COMPETITION_SETUP actor=" + id(fox) + " player=" + id(other) + " enemy=" + id(enemy)
                    + " playerRange=" + fox.getDistance(other) + " enemyRange=" + fox.getDistance(enemy));
        }

        void observeCompetition() {
            EntityLivingBase target = fox.getAttackTarget();
            output("DY_AI_COMPETITION_RESULT actor=" + id(fox) + " player=" + id(other) + " enemy=" + id(enemy)
                    + " stored=" + id(target) + " playerTargets=" + playerTargets + " enemyTargets=" + enemyTargets
                    + " enemyHealth=" + enemy.getHealth() + " playerHealth=" + other.getHealth());
            require(protect ? target == enemy && enemyTargets > 0 && playerTargets == 0 && enemy.getHealth() < enemy.getMaxHealth()
                            : target == other && playerTargets > 0 && other.getHealth() < other.getMaxHealth(),
                    "Original nearest-target AI did not recover to the expected eligible target and hit it");
        }

        void stop() {
            MinecraftForge.EVENT_BUS.unregister(this);
            fox.setDead();
            if (enemy != null) enemy.setDead();
            if (other.isAddedToWorld()) world.removeEntityDangerously(other);
            else other.setDead();
            owner.setRevengeTarget(null);
        }

        @SubscribeEvent
        public void target(LivingSetAttackTargetEvent event) {
            if (event.getEntityLiving() != fox) return;
            if (event.getTarget() == other) playerTargets++;
            if (event.getTarget() != null && event.getTarget() == enemy) enemyTargets++;
        }
    }

    public static final class CombatProbe {
        final EntityWolf fox;
        final List<DamageSource> sources = new ArrayList<>();
        int playerTargets;

        CombatProbe(EntityWolf fox) { this.fox = fox; }

        @SubscribeEvent(receiveCanceled = true)
        public void attack(LivingAttackEvent event) {
            DamageSource source = event.getSource();
            if (source.getImmediateSource() != fox) return;
            sources.add(source);
            output("DY_NATIVE_HIT victim=" + id(event.getEntityLiving()) + " type=" + source.damageType
                    + " source=" + source.getClass().getName() + " actual=" + id(source.getImmediateSource())
                    + " credit=" + id(source.getTrueSource()) + " bypassesArmor=" + source.isUnblockable()
                    + " canceled=" + event.isCanceled());
        }

        @SubscribeEvent
        public void target(LivingSetAttackTargetEvent event) {
            if (event.getEntityLiving() == fox && event.getTarget() instanceof EntityPlayer) playerTargets++;
        }
    }

    public static final class DropProbe {
        final WorldServer world;
        final EntityWolf victim;
        int before = -1;
        int after = -1;
        int spawned;
        boolean canceled;

        DropProbe(WorldServer world, EntityWolf victim) { this.world = world; this.victim = victim; }

        @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
        public void before(LivingDropsEvent event) {
            if (event.getEntityLiving() == victim) before = countChest(event.getDrops());
        }

        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
        public void after(LivingDropsEvent event) {
            if (event.getEntityLiving() == victim) {
                after = countChest(event.getDrops());
                canceled = event.isCanceled();
            }
        }

        @SubscribeEvent
        public void joined(EntityJoinWorldEvent event) {
            if (event.getWorld() == world && event.getEntity() instanceof EntityItem && chest((EntityItem) event.getEntity())) spawned++;
        }

        private static int countChest(List<EntityItem> items) { return (int) items.stream().filter(DropProbe::chest).count(); }
        private static boolean chest(EntityItem item) { return item.getItem().getItem() == Items.IRON_CHESTPLATE; }
    }

    public static final class Expiry {
        final WorldServer world;
        final EntityWolf summon;
        final long start;
        long removal = -1;

        Expiry(WorldServer world, EntityWolf summon) { this.world = world; this.summon = summon; start = world.getTotalWorldTime(); }

        @SubscribeEvent
        public void tick(TickEvent.WorldTickEvent event) {
            if (event.world == world && event.phase == TickEvent.Phase.END && summon.isDead && removal < 0) {
                removal = world.getTotalWorldTime();
            }
        }
    }
}
