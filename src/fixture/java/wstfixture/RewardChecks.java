package wstfixture;

import electroblob.wizardry.entity.living.EntitySpiritWolf;
import electroblob.wizardry.entity.living.ISummonedCreature;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.monster.EntitySnowman;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static wstfixture.IdentityFixture.inspect;
import static wstfixture.IdentityFixture.loadArena;
import static wstfixture.IdentityFixture.output;
import static wstfixture.IdentityFixture.require;

/** Collects actual world rewards after the native death and XP phases. */
public final class RewardChecks {
    private static final AxisAlignedBB ARENA = new AxisAlignedBB(18, -32, 18, 32, 36, 32);
    private static Stage active;

    public static void run(MinecraftServer server, ICommandSender sender, String[] args) throws Exception {
        EntityPlayerMP player = CommandBase.getPlayer(server, sender, args[3]);
        if (args[1].equals("setup")) {
            loadArena(player.getServerWorld());
            player.setGameType(GameType.SURVIVAL);
            player.connection.setPlayerLocation(0.5, 4, 0.5, 0, 0);
            ClientFeedbackProbe.verify(player);
            return;
        }
        if (args[1].equals("start")) {
            require(active == null, "previous reward case did not finish");
            active = new Stage(args[2], player, Boolean.parseBoolean(args[4]));
            active.start(server, sender);
            return;
        }
        if (args[1].equals("retreat")) {
            require(active != null && active.name.equals("joined-sword") && active.victim.getHealth() == 0,
                    "joined-player click did not cause lethal damage");
            active.victim.updateBlocked = false;
            player.connection.setPlayerLocation(0.5, 4, 0.5, 0, 0);
            player.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
            return;
        }
        require(args[1].equals("collect") && active != null && args[2].equals(active.name), "wrong reward case to collect");
        try {
            active.collect();
        } finally {
            active.cleanup();
            active = null;
        }
    }

    private static final class Stage {
        final String name;
        final EntityPlayerMP player;
        final WorldServer world;
        final boolean suppression;
        final List<EntityLivingBase> entities = new ArrayList<>();
        RewardTrace trace;
        EntityLiving victim;
        EntityLivingBase attacker;
        boolean trueDeath = true;
        boolean affected = true;

        Stage(String name, EntityPlayerMP player, boolean suppression) {
            this.name = name;
            this.player = player;
            this.world = player.getServerWorld();
            this.suppression = suppression;
        }

        void start(MinecraftServer server, ICommandSender sender) throws Exception {
            require(world.getGameRules().getBoolean("doMobLoot"), "doMobLoot needs to be true");
            require(items().isEmpty() && orbs().isEmpty(), "unclean reward arena");
            Runnable action;
            String classification = "AFFECTED";
            switch (name) {
                case "native-snow":
                    victim = OriginChecks.checkSpawn(world, player, "summon_snow_golem", EntitySnowman.class, false);
                    place(victim, 24.5);
                    entities.add(victim);
                    giveBoots(victim, "native-supplied");
                    action = this::killAsPlayer;
                    break;
                case "enemy-killed-by-summon": {
                    victim = new EntityZombie(world);
                    giveBoots(victim, "enemy-supplied");
                    add(victim, 24.5);
                    List<EntityLivingBase> summoned = new ArrayList<>();
                    attacker = MeleeChecks.summon(player, world, summoned, "ebwizardry:summon_zombie", EntityZombie.class);
                    entities.addAll(summoned);
                    place(attacker, 22.5);
                    attacker.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(100);
                    classification = "EXCLUDED";
                    affected = false;
                    action = () -> attacker.attackEntityAsMob(victim);
                    break;
                }
                case "npc-victim": {
                    EntityZombie npc = new EntityZombie(world);
                    add(npc, 20.5);
                    victim = reward(npc.getUniqueID());
                    classification = "EXCLUDED";
                    affected = false;
                    action = this::killAsPlayer;
                    break;
                }
                case "unresolved-victim":
                    victim = reward(UUID.fromString("47e673ff-2996-4c84-94ed-c4fd12528252"));
                    classification = "UNRESOLVED";
                    affected = false;
                    action = this::killAsPlayer;
                    break;
                case "mob-kill":
                    victim = reward(player.getUniqueID());
                    attacker = new EntityZombie(world);
                    add((EntityLiving) attacker, 22.5);
                    attacker.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(100);
                    action = () -> require(attacker.attackEntityAsMob(victim), "native mob kill was blocked");
                    break;
                case "environment-kill":
                    victim = reward(player.getUniqueID());
                    victim.setHealth(0.5F);
                    action = () -> victim.setFire(3);
                    break;
                case "supplied-gear":
                    victim = reward(player.getUniqueID());
                    giveBoots(victim, "player-supplied");
                    action = this::killAsPlayer;
                    break;
                case "picked-gear": {
                    RewardZombie zombie = reward(player.getUniqueID());
                    victim = zombie;
                    ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
                    sword.setStackDisplayName("picked-gear");
                    EntityItem item = new EntityItem(world, victim.posX, victim.posY, victim.posZ, sword);
                    require(world.spawnEntity(item), "the supplied item did not enter the world");
                    zombie.setCanPickUpLoot(true);
                    zombie.pickUpNormally(item);
                    require(item.isDead && ItemStack.areItemStacksEqual(sword, victim.getHeldItemMainhand()),
                            "normal pickup did not equip the unique item");
                    output("REWARD_PICKED actor=" + victim.getUniqueID() + " droppedEntity=" + item.getUniqueID()
                            + " held=" + victim.getHeldItemMainhand().writeToNBT(new net.minecraft.nbt.NBTTagCompound()));
                    action = this::killAsPlayer;
                    break;
                }
                case "live-producer":
                    victim = reward(player.getUniqueID());
                    trueDeath = false;
                    action = () -> victim.entityDropItem(new ItemStack(Items.FEATHER), 0);
                    break;
                case "direct-removal":
                    victim = reward(player.getUniqueID());
                    trueDeath = false;
                    action = victim::setDead;
                    break;
                case "operator-kill":
                    victim = reward(player.getUniqueID());
                    action = victim::onKillCommand;
                    break;
                case "provider-expiry": {
                    List<EntityLivingBase> summoned = new ArrayList<>();
                    victim = MeleeChecks.summon(player, world, summoned, "ebwizardry:summon_zombie", EntityZombie.class);
                    entities.addAll(summoned);
                    place(victim, 24.5);
                    trueDeath = false;
                    int lifetime = ((ISummonedCreature) victim).getLifetime();
                    require(lifetime > 0, "native expiry case needs a timed summon");
                    action = () -> {
                        victim.ticksExisted = lifetime - 1;
                        output("REWARD_EXPIRY victim=" + victim.getUniqueID() + " nativeLifetime=" + lifetime);
                    };
                    break;
                }
                case "provider-dismissal":
                    victim = OriginChecks.checkSpawn(world, player, "summon_spirit_wolf", EntitySpiritWolf.class, false);
                    place(victim, 24.5);
                    entities.add(victim);
                    trueDeath = false;
                    action = () -> entities.add(OriginChecks.checkSpawn(
                            world, player, "summon_spirit_wolf", EntitySpiritWolf.class, false));
                    break;
                case "player-kill":
                case "already-canceled":
                    victim = reward(player.getUniqueID());
                    action = this::killAsPlayer;
                    break;
                case "joined-sword":
                    victim = reward(player.getUniqueID());
                    victim.setHealth(3);
                    victim.updateBlocked = true;
                    player.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
                    player.connection.setPlayerLocation(victim.posX, victim.posY, victim.posZ - 1.8, 0, 0);
                    action = null;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown reward case " + name);
            }
            inspect(server, sender, victim, classification);
            trace = new RewardTrace(victim, name.equals("already-canceled"));
            MinecraftForge.EVENT_BUS.register(trace);
            output("REWARD_READY case=" + name + " victim=" + victim.getUniqueID()
                    + " entityId=" + victim.getEntityId() + " tick=" + world.getTotalWorldTime() + " player=" + player.getUniqueID());
            if (action == null) return; // The joined-player action arrives from Toolkit after this setup.
            if (suppression && affected && trueDeath) {
                Set<String> packets = ClientFeedbackProbe.capture(player, action);
                output("REWARD_FEEDBACK case=" + name + " packets=" + packets);
                require(packets.isEmpty(), name + " sent feedback on a protected reward event");
            } else {
                action.run();
            }
        }

        void collect() {
            List<EntityItem> worldItems = items();
            Map<String, Integer> actual = stacks(worldItems);
            int experience = orbs().stream().mapToInt(EntityXPOrb::getXpValue).sum();
            boolean live = world.getEntityFromUuid(victim.getUniqueID()) == victim && !victim.isDead;
            output("REWARD_RESULT case=" + name + " victim=" + victim.getUniqueID() + " live=" + live
                    + " death=" + trace.death + " source=" + trace.damageType + " actor=" + trace.actor + " credit=" + trace.credit
                    + " itemInput=" + trace.itemInput + " itemAfter=" + trace.itemAfter + " itemCanceled=" + trace.itemsCanceled
                    + " xpInput=" + trace.xpInput + " xpAfter=" + trace.xpAfter + " xpCanceled=" + trace.xpCanceled
                    + " xpCredit=" + trace.xpCredit + " worldItems=" + actual + " worldXp=" + experience
                    + " tick=" + world.getTotalWorldTime());
            if (!trueDeath) {
                require(trace.death == 0 && trace.itemInput == null && trace.xpInput == -1, "removal or production triggered death rewards");
                boolean producing = name.equals("live-producer");
                Map<String, Integer> expected = producing ? Collections.singletonMap("minecraft:feather", 1) : Collections.emptyMap();
                require(live == producing && actual.equals(expected) && experience == 0, "wrong living or removal result");
                return;
            }

            require(!live && trace.death == 1 && trace.itemInput != null && !trace.itemInput.isEmpty(),
                    name + " did not reach a true death and nonzero item-producing path");
            boolean preCanceled = name.equals("already-canceled");
            boolean blocked = suppression && affected;
            require(trace.itemsCanceled == (blocked || preCanceled), name + " has the wrong item event cancellation");
            require(trace.itemAfter.equals(blocked ? Collections.emptyMap() : trace.itemInput), name + " changed item capture incorrectly");
            require(actual.equals(blocked || preCanceled ? Collections.emptyMap() : trace.itemInput), name + " has wrong actual item reward");
            require(experience == (blocked || preCanceled ? 0 : Math.max(0, trace.xpInput)), name + " has wrong actual XP reward");
            if (trace.xpInput >= 0) {
                require(trace.xpAfter == (blocked ? 0 : trace.xpInput) && trace.xpCanceled == (blocked || preCanceled),
                        name + " has wrong XP event outcome");
            }
            if (name.equals("player-kill") || name.equals("joined-sword") || name.equals("already-canceled") || name.equals("supplied-gear")
                    || name.equals("picked-gear") || name.equals("npc-victim") || name.equals("unresolved-victim")) {
                require(trace.xpInput == 7, name + " did not reach the native positive fixture XP boundary");
            }
            if (name.equals("enemy-killed-by-summon")) {
                require(attacker.getUniqueID().equals(trace.actor) && player.getUniqueID().equals(trace.credit)
                        && player.getUniqueID().equals(trace.xpCredit) && trace.xpInput > 0,
                        "native summon kill lost the original actor or credit");
            }
            if (name.equals("native-snow") || name.equals("enemy-killed-by-summon") || name.equals("supplied-gear")) {
                require(trace.itemInput.keySet().stream().anyMatch(item -> item.startsWith("minecraft:golden_boots|")),
                        "the native death did not offer the supplied boots");
            }
            if (name.equals("picked-gear")) {
                require(trace.itemInput.containsKey("minecraft:diamond_sword|picked-gear"), "native death did not offer the picked-up equipment");
            }
            if (name.equals("environment-kill")) {
                require("onFire".equals(trace.damageType) && trace.actor == null && trace.credit == null,
                        "the actual environmental source was not reached");
            }
        }

        private RewardZombie reward(UUID owner) {
            RewardZombie result = new RewardZombie(world);
            result.setOwnerId(owner);
            add(result, 24.5);
            return result;
        }

        private void killAsPlayer() {
            require(victim.attackEntityFrom(DamageSource.causePlayerDamage(player), 100), "native player lethal damage was blocked");
        }

        private void add(EntityLiving entity, double x) {
            place(entity, x);
            require(world.spawnEntity(entity), "reward fixture spawn was rejected");
            entities.add(entity);
        }

        private void place(EntityLivingBase entity, double x) {
            entity.setPosition(x, 4, 24.5);
            ((EntityLiving) entity).setNoAI(true);
        }

        private List<EntityItem> items() {
            return world.getEntitiesWithinAABB(EntityItem.class, ARENA, item -> !item.isDead);
        }

        private List<EntityXPOrb> orbs() {
            return world.getEntitiesWithinAABB(EntityXPOrb.class, ARENA, orb -> !orb.isDead);
        }

        void cleanup() {
            if (trace != null) MinecraftForge.EVENT_BUS.unregister(trace);
            for (EntityLivingBase entity : entities) world.removeEntityDangerously(entity);
            for (EntityItem item : items()) world.removeEntityDangerously(item);
            for (EntityXPOrb orb : orbs()) world.removeEntityDangerously(orb);
        }
    }

    private static void giveBoots(EntityLiving entity, String name) {
        ItemStack boots = new ItemStack(Items.GOLDEN_BOOTS);
        boots.setStackDisplayName(name);
        entity.setItemStackToSlot(EntityEquipmentSlot.FEET, boots);
        entity.setDropChance(EntityEquipmentSlot.FEET, 2);
    }

    private static Map<String, Integer> stacks(List<EntityItem> entities) {
        Map<String, Integer> counts = new TreeMap<>();
        for (EntityItem item : entities) {
            ItemStack stack = item.getItem();
            String key = stack.getItem().getRegistryName().toString();
            if (stack.hasDisplayName()) key += "|" + stack.getDisplayName();
            counts.merge(key, stack.getCount(), Integer::sum);
        }
        return counts;
    }

    public static final class RewardTrace {
        final EntityLiving victim;
        final boolean preCancel;
        int death;
        String damageType;
        UUID actor;
        UUID credit;
        UUID xpCredit;
        Map<String, Integer> itemInput;
        Map<String, Integer> itemAfter;
        boolean itemsCanceled;
        int xpInput = -1;
        int xpAfter = -1;
        boolean xpCanceled;

        RewardTrace(EntityLiving victim, boolean preCancel) {
            this.victim = victim;
            this.preCancel = preCancel;
        }

        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
        public void death(LivingDeathEvent event) {
            if (event.getEntityLiving() != victim || event.isCanceled()) return;
            death++;
            DamageSource source = event.getSource();
            damageType = source.damageType;
            actor = id(source.getImmediateSource());
            credit = id(source.getTrueSource());
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
        public void itemsBefore(LivingDropsEvent event) {
            if (event.getEntityLiving() != victim) return;
            itemInput = stacks(event.getDrops());
            if (preCancel) event.setCanceled(true);
        }

        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
        public void itemsAfter(LivingDropsEvent event) {
            if (event.getEntityLiving() != victim) return;
            itemAfter = stacks(event.getDrops());
            itemsCanceled = event.isCanceled();
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
        public void xpBefore(LivingExperienceDropEvent event) {
            if (event.getEntityLiving() != victim) return;
            xpInput = event.getDroppedExperience();
            xpCredit = id(event.getAttackingPlayer());
            if (preCancel) event.setCanceled(true);
        }

        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
        public void xpAfter(LivingExperienceDropEvent event) {
            if (event.getEntityLiving() != victim) return;
            xpAfter = event.getDroppedExperience();
            xpCanceled = event.isCanceled();
        }

        private static UUID id(Entity entity) {
            return entity == null ? null : entity.getUniqueID();
        }
    }
}
