package wstfixture;

import com.Fishmod.mod_LavaCow.client.Modconfig;
import com.Fishmod.mod_LavaCow.entities.EntitySludgeLord;
import com.Fishmod.mod_LavaCow.entities.tameable.EntityLilSludge;
import com.Fishmod.mod_LavaCow.init.FishItems;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.EntityAIOwnerHurtByTarget;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static wstfixture.IdentityFixture.*;

public final class FishChecks {
    private static final ResourceLocation SLUDGE = new ResourceLocation("mod_lavacow:lilsludge");
    private static final String WAND = "mod_lavacow:sludge_wand";
    private static final String MARK = "wizardrysummontweaks";
    private static final double LANE = 6.5;
    private static Expiry expiry;

    public static void run(MinecraftServer server, ICommandSender sender, String[] args) throws Exception {
        WorldServer world = server.worlds[0];
        String action = args[1];
        if ("restore".equals(action)) {
            restore(world);
            return;
        }
        if ("collect-expiry".equals(action)) {
            collectExpiry();
            return;
        }
        EntityPlayerMP player = getPlayer(server, sender, args[3]);
        boolean enabled = Boolean.parseBoolean(args[2]);
        NativeFixture.equipPlayer(player, 8.5);
        List<EntityLiving> cleanup = new ArrayList<>();
        try {
            switch (action) {
                case "setup":
                    setup(world);
                    break;
                case "live":
                    live(world, player, enabled, cleanup);
                    break;
                case "overlap":
                    overlap(world, player, cleanup);
                    break;
                case "rewards":
                    rewards(world, player, enabled, cleanup);
                    break;
                case "controls":
                    controls(world, player, enabled, cleanup);
                    break;
                case "expiry":
                    startExpiry(world, player, cleanup);
                    break;
                case "owner-loss":
                    ownerLoss(world, player, cleanup);
                    break;
                case "save":
                    save(world, player, cleanup);
                    break;
                case "restored-hit":
                    restoredCombat(world, player, enabled);
                    break;
                default:
                    throw new IllegalArgumentException(action);
            }
        } finally {
            for (EntityLiving entity : cleanup) entity.setDead();
        }
        output("FISH_CASE_PASS case=" + action + " enabled=" + enabled);
    }

    private static void setup(WorldServer world) {
        loadArena(world);
        NativeFixture.floor(world, -6, 40);
        require(Loader.isModLoaded("mod_lavacow") && !Loader.isModLoaded("ebwizardry")
                && !Loader.isModLoaded("wizardryutils") && !Loader.isModLoaded("spellbundle")
                && !Loader.isModLoaded("threatworks"), "Independent Fish profile contains the wrong providers");
        require(EntityList.getClass(SLUDGE) == EntityLilSludge.class && WAND.equals(String.valueOf(FishItems.SLUDGE_WAND.getRegistryName())),
                "Real selected staff or creature registry is absent");
        output("FISH_REGISTRY item=" + FishItems.SLUDGE_WAND.getRegistryName() + " entity=" + SLUDGE
                + " sourceLifetimeSeconds=" + Modconfig.LilSludge_Lifespan + " ownerLoss=" + Modconfig.Suicidal_Minion);
    }

    private static void live(WorldServer world, EntityPlayerMP player, boolean enabled, List<EntityLiving> cleanup) throws Exception {
        List<EntityLilSludge> created = staff(world, player, cleanup);
        EntityLilSludge first = created.get(0);
        EntityLilSludge second = created.get(1);
        spawnTrace(world, first, player.getUniqueID(), "player");
        EntityPlayerMP other = NativeFixture.secondPlayer(world.getMinecraftServer(), world);
        NativeFixture.equipPlayer(other, 13.5);
        EntityZombie enemy = new EntityZombie(world);
        spawn(world, enemy, cleanup, 14.5);
        try {
            first.setAttackTarget(other);
            output("FISH_TARGET actor=" + id(first) + " player=" + id(other) + " target=" + id(first.getAttackTarget()));
            require(first.getAttackTarget() == (enabled ? null : other), "Native player target assignment differed");
            first.setAttackTarget(enemy);
            require(first.getAttackTarget() == enemy, "Original eligible enemy target unavailable");
            outgoing(first, other, enabled, "player");
            outgoing(first, enemy, false, "enemy");
            incoming(player, first, enabled, "owner");
            incoming(other, first, enabled, "nonowner");
            checkOwnerTask(player, other, enemy, first, second, enabled);
        } finally {
            other.setDead();
            player.setRevengeTarget(null);
        }
    }

    private static void checkOwnerTask(EntityPlayerMP owner, EntityPlayerMP other, EntityZombie enemy,
                                       EntityLilSludge first, EntityLilSludge second, boolean enabled) throws Exception {
        first.setAttackTarget(null);
        owner.setHealth(owner.getMaxHealth());
        owner.hurtResistantTime = 0;
        float before = owner.getHealth();
        NativeFixture.preparePlayerAttack(other, Enchantments.SHARPNESS);
        other.attackTargetEntityWithCurrentItem(owner);
        require(owner.getHealth() < before && owner.getRevengeTarget() == other, "Native player provocation did not land");
        EntityAIBase reprisal = ownerTask(first);
        require(reprisal.shouldExecute(), "Native owner retaliation did not accept its actual provoking actor");
        reprisal.startExecuting();
        require(first.getAttackTarget() == (enabled ? null : other), "Native owner reprisal crossed the player target rule");
        output("FISH_REPRISAL actor=" + id(first) + " player=" + id(other) + " target=" + id(first.getAttackTarget()));
        owner.hurtResistantTime = 0;
        enemy.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(8);
        require(enemy.attackEntityAsMob(owner) && owner.getRevengeTarget() == enemy, "Native enemy provocation did not land");
        EntityAIBase defense = ownerTask(second);
        require(defense.shouldExecute(), "Native owner retaliation did not accept its allowed enemy");
        defense.startExecuting();
        require(second.getAttackTarget() == enemy, "Native retaliation failed to select the eligible mob");
        output("FISH_REPRISAL_ENEMY actor=" + id(second) + " enemy=" + id(enemy) + " target=" + id(second.getAttackTarget()));
    }

    private static EntityAIBase ownerTask(EntityLilSludge actor) {
        return actor.targetTasks.taskEntries.stream().map(entry -> entry.action)
                .filter(task -> task.getClass() == EntityAIOwnerHurtByTarget.class).findFirst()
                .orElseThrow(() -> new IllegalStateException("Native Fish owner retaliation task unavailable"));
    }

    private static void overlap(WorldServer world, EntityPlayerMP player, List<EntityLiving> cleanup) throws Exception {
        EntitySludgeLord lord = new EntitySludgeLord(world);
        spawn(world, lord, cleanup, 16.5);
        lord.ticksExisted = 1;
        lord.setAttackTarget(player);
        EntityAIBase spell = lord.tasks.taskEntries.stream().map(entry -> entry.action)
                .filter(task -> task.getClass().getName().endsWith("EntitySludgeLord$AIUseSpell")).findFirst()
                .orElseThrow(() -> new IllegalStateException("Original Sludge Lord spell task unavailable"));
        require(spell.shouldExecute(), "Original Sludge Lord spell was unable to begin");
        spell.startExecuting();
        int remaining = ReflectionHelper.findField(spell.getClass(), "spellWarmup").getInt(spell);
        require(remaining > 1, "Sludge Lord spell did not establish a native warmup");
        for (int tick = 1; tick < remaining; tick++) spell.updateTask();
        long before = world.getTotalWorldTime();
        List<EntityLilSludge> players = staff(world, player, cleanup);
        Set<Entity> existing = new HashSet<>(world.loadedEntityList);
        spell.updateTask();
        List<EntityLilSludge> npc = spawnedSince(world, existing, cleanup);
        require(before == world.getTotalWorldTime() && !npc.isEmpty(), "Staff and NPC spell did not create separate actors in the same tick");
        for (EntityLilSludge sludge : npc) {
            require(lord.getUniqueID().equals(sludge.getOwnerId()) && !sludge.getEntityData().hasKey(MARK),
                    "Native NPC spawn acquired player staff origin");
            inspect(world.getMinecraftServer(), world.getMinecraftServer(), sludge, "UNRESOLVED");
        }
        output("FISH_OVERLAP tick=" + before + " player=" + id(player) + " playerCount=" + players.size() + " boss=" + id(lord)
                + " npcCount=" + npc.size() + " npcActor=" + id(npc.get(0)) + " npcOwner=" + npc.get(0).getOwnerId()
                + " warmup=" + remaining);
        outgoing(npc.get(0), player, false, "npc-created");
    }

    private static void rewards(WorldServer world, EntityPlayerMP player, boolean enabled, List<EntityLiving> cleanup) {
        List<EntityLilSludge> mobs = staff(world, player, cleanup);
        EntityZombie killer = new EntityZombie(world);
        spawn(world, killer, cleanup, 13.5);
        killer.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(100);
        rewardCase(mobs.get(0), killer, "stock-staff", false, false);
        EntityLilSludge ordinary = new EntityLilSludge(world);
        spawn(world, ordinary, cleanup, 15.5);
        rewardCase(ordinary, killer, "stock-ordinary", false, true);
        rewardCase(mobs.get(1), killer, "captured-equipment-producer", true, !enabled);
    }

    private static void rewardCase(EntityLilSludge victim, EntityZombie killer, String label, boolean producer, boolean shouldDrop) {
        victim.setItemStackToSlot(EntityEquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        victim.setDropChance(EntityEquipmentSlot.CHEST, 2);
        LootProbe trace = new LootProbe(victim, producer);
        MinecraftForge.EVENT_BUS.register(trace);
        try {
            require(killer.attackEntityAsMob(victim) && victim.getHealth() == 0, "The real native death was not reached");
            output("FISH_REWARD case=" + label + " victim=" + id(victim) + " captured=" + trace.original + " supplied=" + trace.supplied
                    + " final=" + trace.remaining + " spawned=" + trace.spawned + " death=" + trace.deaths);
            require(trace.deaths == 1 && trace.original >= 0 && (trace.remaining > 0) == shouldDrop
                    && (trace.spawned > 0) == shouldDrop && trace.supplied == (producer ? 1 : 0),
                    "Captured equipment or genuine emitted item did not match the selected rule");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(trace);
        }
    }

    private static void controls(WorldServer world, EntityPlayerMP player, boolean enabled, List<EntityLiving> cleanup) throws Exception {
        EntityWolf pet = new EntityWolf(world);
        pet.setTamed(true);
        pet.setOwnerId(player.getUniqueID());
        spawn(world, pet, cleanup, 12.5);
        inspect(world.getMinecraftServer(), world.getMinecraftServer(), pet, "EXCLUDED");
        EntityLilSludge ordinary = new EntityLilSludge(world);
        ordinary.setTamed(true);
        ordinary.setOwnerId(player.getUniqueID());
        ordinary.setLimitedLife(Modconfig.LilSludge_Lifespan * 20);
        spawn(world, ordinary, cleanup, 13.5);
        inspect(world.getMinecraftServer(), world.getMinecraftServer(), ordinary, "UNRESOLVED");
        incoming(player, ordinary, false, "unmarked-same-class");

        EntityLilSludge transferred = staff(world, player, cleanup).get(0);
        requireOfflineOwner(world);
        transferred.setOwnerId(SECOND_PLAYER);
        transferred.onUpdate();
        spawnTrace(world, transferred, SECOND_PLAYER, "transferred");
        incoming(player, transferred, enabled, "offline-current-owner");
        transferred.setTamed(false);
        transferred.setOwnerId(null);
        transferred.onUpdate();
        inspect(world.getMinecraftServer(), world.getMinecraftServer(), transferred, "EXCLUDED");
        require("UNOWNED".equals(mark(transferred).getString("ownerKind")) && !mark(transferred).hasKey("ownerUUID"),
                "Verified native owner removal did not replace the saved owner");
        incoming(player, transferred, false, "explicit-removal");
        output("FISH_TRANSFER actor=" + id(transferred) + " final=" + mark(transferred));

        SpawnVeto veto = new SpawnVeto(world, player.getUniqueID());
        MinecraftForge.EVENT_BUS.register(veto);
        try {
            nativeWand(world, player);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(veto);
        }
        require(veto.canceled.size() == 2, "Original staff must attempt both spawns after a single activation");
        for (EntityLilSludge rejected : veto.canceled) {
            require(!rejected.isAddedToWorld() && !rejected.getEntityData().hasKey(MARK), "Rejected native spawn retained provenance");
        }
        output("FISH_CANCELED attempts=" + veto.canceled.size() + " ids=" + id(veto.canceled.get(0)) + "," + id(veto.canceled.get(1))
                + " added=false retainedMark=false");
    }

    private static void startExpiry(WorldServer world, EntityPlayerMP player, List<EntityLiving> cleanup) {
        require(expiry == null, "Already measuring a Fish expiry");
        EntityLilSludge actor = staff(world, player, cleanup).get(0);
        cleanup.remove(actor);
        int original = life(actor) + actor.ticksExisted;
        require(original > 20 && original == Modconfig.LilSludge_Lifespan * 20, "Native staff lifetime unavailable");
        actor.ticksExisted = original - 20;
        expiry = new Expiry(world, actor, original);
        MinecraftForge.EVENT_BUS.register(expiry);
        output("FISH_EXPIRY_READY actor=" + id(actor) + " original=" + original + " remaining=" + life(actor) + " start=" + expiry.start);
    }

    private static void collectExpiry() {
        Expiry observed = expiry;
        require(observed != null, "No active native Fish expiry");
        try {
            output("FISH_EXPIRY_RESULT actor=" + id(observed.actor) + " start=" + observed.start + " gone=" + observed.gone
                    + " original=" + observed.original + " death=" + observed.deaths);
            require(observed.actor.isDead && observed.gone - observed.start == 20 && observed.deaths == 0,
                    "Original age-based removal did not occur after twenty ticks");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(observed);
            observed.actor.setDead();
            expiry = null;
        }
    }

    private static void ownerLoss(WorldServer world, EntityPlayerMP player, List<EntityLiving> cleanup) {
        require(Modconfig.Suicidal_Minion, "Original owner-loss behavior must be enabled for this control");
        EntityLilSludge actor = staff(world, player, cleanup).get(0);
        requireOfflineOwner(world);
        actor.setOwnerId(SECOND_PLAYER);
        require(actor.getOwner() == null && actor.isEntityAlive() && life(actor) > 20, "Native offline owner-loss condition is not reached");
        int before = life(actor);
        actor.onLivingUpdate();
        output("FISH_OWNER_LOSS actor=" + id(actor) + " owner=" + SECOND_PLAYER + " beforeLifeTicks=" + before + " removed=" + actor.isDead);
        require(actor.isDead, "Source-native owner loss must remove the actor");
    }

    private static void save(WorldServer world, EntityPlayerMP player, List<EntityLiving> cleanup) throws Exception {
        require(!Modconfig.Suicidal_Minion, "The saved actor needs the original retention configuration");
        loadRemote(world);
        NativeFixture.floor(world, 1024, 1036);
        EntityLilSludge actor = staff(world, player, cleanup).get(0);
        cleanup.remove(actor);
        actor.setPositionAndUpdate(1024.5, 4, LANE);
        requireOfflineOwner(world);
        actor.setOwnerId(SECOND_PLAYER);
        actor.onUpdate();
        spawnTrace(world, actor, SECOND_PLAYER, "save-after-transfer");
        Files.write(savePath(world), Arrays.asList(id(actor), SECOND_PLAYER.toString(), id(player)), StandardCharsets.US_ASCII);
        output("FISH_SAVE_READY actor=" + id(actor) + " owner=" + SECOND_PLAYER + " initialCaster=" + id(player)
                + " life=" + life(actor) + " mark=" + mark(actor));
    }

    private static void restore(WorldServer world) throws Exception {
        Path folder = savePath(world).getParent();
        Files.copy(folder.resolve("region/r.2.0.mca"), folder.resolve("wst-fish-region.mca"));
        loadRemote(world);
        EntityLilSludge actor = savedActor(world);
        inspect(world.getMinecraftServer(), world.getMinecraftServer(), actor, "AFFECTED");
        output("FISH_RESTORE_READY actor=" + id(actor) + " owner=" + actor.getOwnerId() + " life=" + life(actor) + " mark=" + mark(actor));
    }

    private static void restoredCombat(WorldServer world, EntityPlayerMP player, boolean enabled) throws Exception {
        EntityLilSludge actor = savedActor(world);
        List<String> ids = Files.readAllLines(savePath(world), StandardCharsets.US_ASCII);
        require(id(player).equals(ids.get(2)) && !player.getUniqueID().equals(actor.getOwnerId()), "Restored hit requires the joined non-owner");
        NativeFixture.equipPlayer(player, 1026.5);
        outgoing(actor, player, enabled, "restored-player");
        incoming(player, actor, enabled, "restored-summon");
        output("FISH_RESTORE_PASS actor=" + id(actor) + " owner=" + actor.getOwnerId() + " nonOwner=" + id(player));
    }

    private static EntityLilSludge savedActor(WorldServer world) throws Exception {
        List<String> ids = Files.readAllLines(savePath(world), StandardCharsets.US_ASCII);
        Entity found = world.getEntityFromUuid(UUID.fromString(ids.get(0)));
        require(found instanceof EntityLilSludge && found.isEntityAlive(), "Original native saved Lil' Sludge is unavailable");
        EntityLilSludge actor = (EntityLilSludge) found;
        requireOfflineOwner(world);
        require(SECOND_PLAYER.equals(actor.getOwnerId()) && actor.getOwner() == null && life(actor) > 0 && actor.isTamed(),
                "Native saved ownership, absence or life is inconsistent");
        return actor;
    }

    private static Path savePath(WorldServer world) {
        return world.getSaveHandler().getWorldDirectory().toPath().resolve("wst-fish-ids.txt");
    }

    private static void loadRemote(WorldServer world) {
        for (int x = 61; x <= 67; x++) for (int z = -3; z <= 3; z++) world.getChunk(x, z);
    }

    private static List<EntityLilSludge> staff(WorldServer world, EntityPlayerMP player, List<EntityLiving> cleanup) {
        Set<Entity> existing = new HashSet<>(world.loadedEntityList);
        nativeWand(world, player);
        List<EntityLilSludge> created = spawnedSince(world, existing, cleanup);
        require(created.size() == 2, "Native Sludge Wand must join two genuine actors");
        for (int i = 0; i < created.size(); i++) {
            EntityLilSludge actor = created.get(i);
            actor.setPositionAndUpdate(11.5 + i, 4, LANE);
            require(actor.isTamed() && player.getUniqueID().equals(actor.getOwnerId()) && life(actor) > 20,
                    "Native staff did not initialize the actual owner or lifetime");
            require(WAND.equals(mark(actor).getString("originProvider")) && "PLAYER".equals(mark(actor).getString("ownerKind")),
                    "Exact native spawn did not submit provenance");
        }
        return created;
    }

    private static void nativeWand(WorldServer world, EntityPlayerMP player) {
        ItemStack wand = new ItemStack(FishItems.SLUDGE_WAND);
        wand.addEnchantment(Enchantments.FIRE_ASPECT, 2);
        wand.addEnchantment(Enchantments.KNOCKBACK, 1);
        player.setHeldItem(EnumHand.MAIN_HAND, wand);
        ActionResult<ItemStack> result = wand.getItem().onItemRightClick(world, player, EnumHand.MAIN_HAND);
        output("FISH_CAST item=" + wand.getItem().getRegistryName() + " caster=" + id(player)
                + " action=" + result.getType() + " wandDamage=" + wand.getItemDamage());
        require(result.getType() == EnumActionResult.SUCCESS && result.getResult() == wand && wand.getItemDamage() == 2,
                "The original staff result, returned stack or durability cost changed");
    }

    private static List<EntityLilSludge> spawnedSince(WorldServer world, Set<Entity> existing, List<EntityLiving> cleanup) {
        List<EntityLilSludge> added = new ArrayList<>();
        for (Entity entity : world.loadedEntityList) {
            if (existing.contains(entity) || !(entity instanceof EntityLilSludge)) continue;
            EntityLilSludge actor = (EntityLilSludge) entity;
            actor.setNoAI(true);
            added.add(actor);
            cleanup.add(actor);
        }
        return added;
    }

    private static void outgoing(EntityLilSludge actor, EntityLivingBase victim, boolean blocked, String label) {
        victim.setHealth(victim.getMaxHealth());
        victim.hurtResistantTime = 0;
        victim.extinguish();
        victim.motionX = victim.motionZ = 0;
        victim.setAbsorptionAmount(1);
        victim.setItemStackToSlot(EntityEquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE, 1, 7));
        float before = victim.getHealth();
        HitProbe source = new HitProbe(victim);
        MinecraftForge.EVENT_BUS.register(source);
        boolean result;
        try { result = actor.attackEntityAsMob(victim); }
        finally { MinecraftForge.EVENT_BUS.unregister(source); }
        double impulse = Math.hypot(victim.motionX, victim.motionZ);
        int armor = victim.getItemStackFromSlot(EntityEquipmentSlot.CHEST).getItemDamage();
        output("FISH_OUT kind=" + label + " actor=" + id(actor) + " victim=" + id(victim) + " before=" + before + " after=" + victim.getHealth()
                + " absorption=" + victim.getAbsorptionAmount() + " armor=" + armor + " fire=" + victim.isBurning() + " impulse=" + impulse
                + " original=" + id(source.actor) + " canceled=" + source.canceled + " returned=" + result);
        require((victim.getHealth() < before) != blocked && (victim.getAbsorptionAmount() == 1) == blocked
                && (impulse > 0.05) != blocked && source.actor == actor && result != blocked, "Original Fish attack crossed the wrong boundary");
        if (blocked) require(!victim.isBurning() && armor == 7 && source.canceled, "Blocked Fish hit applied an attached effect");
        if ("restored-player".equals(label) && !blocked) {
            require(victim.isBurning() && armor > 7, "The joined disabled victim did not reach native fire and armor loss");
        }
    }

    private static void incoming(EntityPlayerMP attacker, EntityLilSludge victim, boolean blocked, String label) throws Exception {
        victim.setHealth(victim.getMaxHealth());
        victim.setAbsorptionAmount(1);
        victim.hurtResistantTime = 0;
        victim.motionX = victim.motionZ = 0;
        victim.setItemStackToSlot(EntityEquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE, 1, 11));
        float before = victim.getHealth();
        NativeFixture.preparePlayerAttack(attacker, Enchantments.SHARPNESS);
        attacker.getHeldItem(EnumHand.MAIN_HAND).addEnchantment(Enchantments.KNOCKBACK, 1);
        attacker.attackTargetEntityWithCurrentItem(victim);
        double impulse = Math.hypot(victim.motionX, victim.motionZ);
        ItemStack chest = victim.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
        output("FISH_IN kind=" + label + " actor=" + id(attacker) + " victim=" + id(victim) + " before=" + before
                + " after=" + victim.getHealth() + " absorption=" + victim.getAbsorptionAmount() + " chest=" + chest + " impulse=" + impulse);
        require((victim.getHealth() < before) != blocked && (victim.getAbsorptionAmount() == 1) == blocked
                && (impulse > 0.05) != blocked && chest.getItem() == Items.IRON_CHESTPLATE && chest.getItemDamage() == 11,
                "Native player hit or physical chestplate did not match the selected rule");
    }

    private static void spawn(WorldServer world, EntityLiving entity, List<EntityLiving> cleanup, double x) {
        entity.setPosition(x, 4, LANE);
        entity.setNoAI(true);
        require(world.spawnEntity(entity), "Original world spawn failed for " + entity.getClass().getName());
        cleanup.add(entity);
    }

    private static void spawnTrace(WorldServer world, EntityLilSludge actor, UUID owner, String label) {
        inspect(world.getMinecraftServer(), world.getMinecraftServer(), actor, "AFFECTED");
        require(owner.equals(actor.getOwnerId()) && owner.toString().equals(mark(actor).getString("ownerUUID")),
                "Live owner and stored facts differ");
        output("FISH_ORIGIN case=" + label + " actor=" + id(actor) + " owner=" + owner + " life=" + life(actor) + " mark=" + mark(actor));
    }

    private static void requireOfflineOwner(WorldServer world) {
        require(Arrays.asList(world.getMinecraftServer().getPlayerList().getAvailablePlayerDat()).contains(SECOND_PLAYER.toString())
                && world.getMinecraftServer().getPlayerList().getPlayerByUUID(SECOND_PLAYER) == null,
                "Expected an authoritative offline player record");
    }

    private static int life(EntityLilSludge actor) { return actor.writeToNBT(new NBTTagCompound()).getInteger("LifeTicks"); }
    private static NBTTagCompound mark(Entity actor) { return actor.getEntityData().getCompoundTag(MARK); }
    private static String id(Entity actor) { return actor == null ? "none" : actor.getUniqueID().toString(); }

    public static final class SpawnVeto {
        final WorldServer world;
        final UUID owner;
        final List<EntityLilSludge> canceled = new ArrayList<>();
        SpawnVeto(WorldServer world, UUID owner) { this.world = world; this.owner = owner; }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void join(EntityJoinWorldEvent event) {
            if (event.getWorld() != world || !(event.getEntity() instanceof EntityLilSludge)) return;
            EntityLilSludge actor = (EntityLilSludge) event.getEntity();
            if (!owner.equals(actor.getOwnerId())) return;
            require(WAND.equals(mark(actor).getString("originProvider")), "Expected temporary origin at the actual join");
            canceled.add(actor);
            event.setCanceled(true);
        }
    }

    public static final class HitProbe {
        final EntityLivingBase victim;
        Entity actor;
        boolean canceled;
        HitProbe(EntityLivingBase victim) { this.victim = victim; }

        @SubscribeEvent(receiveCanceled = true)
        public void attack(LivingAttackEvent event) {
            if (event.getEntityLiving() == victim) {
                actor = event.getSource().getTrueSource();
                canceled = event.isCanceled();
            }
        }
    }

    public static final class LootProbe {
        final EntityLilSludge victim;
        final boolean producer;
        int original = -1, supplied, remaining, spawned, deaths;
        LootProbe(EntityLilSludge victim, boolean producer) { this.victim = victim; this.producer = producer; }

        @SubscribeEvent(priority = EventPriority.HIGH)
        public void initial(LivingDropsEvent event) {
            if (event.getEntityLiving() != victim) return;
            original = count(event.getDrops());
            if (producer) {
                event.getDrops().add(new EntityItem(victim.world, victim.posX, victim.posY, victim.posZ,
                        victim.getItemStackFromSlot(EntityEquipmentSlot.CHEST).copy()));
                supplied++;
            }
        }

        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
        public void after(LivingDropsEvent event) {
            if (event.getEntityLiving() == victim) remaining = count(event.getDrops());
        }

        @SubscribeEvent
        public void joined(EntityJoinWorldEvent event) {
            if (event.getWorld() == victim.world && event.getEntity() instanceof EntityItem && chest((EntityItem) event.getEntity())) spawned++;
        }

        @SubscribeEvent
        public void died(LivingDeathEvent event) {
            if (event.getEntityLiving() == victim) deaths++;
        }

        private static int count(List<EntityItem> entities) { return (int) entities.stream().filter(LootProbe::chest).count(); }
        private static boolean chest(EntityItem item) { return item.getItem().getItem() == Items.IRON_CHESTPLATE; }
    }

    public static final class Expiry {
        final WorldServer world;
        final EntityLilSludge actor;
        final int original;
        final long start;
        long gone = -1;
        int deaths;
        Expiry(WorldServer world, EntityLilSludge actor, int original) {
            this.world = world;
            this.actor = actor;
            this.original = original;
            start = world.getTotalWorldTime();
        }

        @SubscribeEvent
        public void tick(TickEvent.WorldTickEvent event) {
            if (event.world == world && event.phase == TickEvent.Phase.END && actor.isDead && gone < 0) gone = world.getTotalWorldTime();
        }

        @SubscribeEvent
        public void died(LivingDeathEvent event) {
            if (event.getEntityLiving() == actor) deaths++;
        }
    }
}
