package wstfixture;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.monster.EntitySnowman;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static wstfixture.IdentityFixture.getPlayer;
import static wstfixture.IdentityFixture.inspect;
import static wstfixture.IdentityFixture.loadArena;
import static wstfixture.IdentityFixture.output;
import static wstfixture.IdentityFixture.require;

/** Uses native attacks and reads the optional installed Threatworks encounter. */
public final class ThreatChecks {
    private static final String COORDINATOR = "com.mahghuuuls.threatworks.server.EncounterCoordinator";
    private static final String CREDIT_EVENT = "com.mahghuuuls.threatworks.api.SummonThreatCreditEvent";
    private static final String MARK = "wizardrysummontweaks";
    private static final AxisAlignedBB REWARDS = new AxisAlignedBB(-8, 0, -8, 24, 12, 20);
    private static final double LANE = 6.5;
    private static Encounter encounter;

    public static void run(MinecraftServer server, ICommandSender sender, String[] args) throws Exception {
        WorldServer world = server.getWorld(0);
        if ("restore".equals(args[1])) {
            restore(world);
            return;
        }
        EntityPlayerMP player = getPlayer(server, sender, args[2]);
        switch (args[1]) {
            case "setup":
                loadArena(world);
                NativeFixture.floor(world, -8, 55);
                NativeFixture.equipPlayer(player, 14.5);
                output("THREAT_SETUP presence=" + present() + " player=" + id(player));
                break;
            case "encounter":
                require(encounter == null, "Previous threat encounter is still armed");
                Encounter opened = new Encounter(world, player);
                try {
                    opened.start();
                    encounter = opened;
                } catch (Exception | Error failure) {
                    opened.close();
                    throw failure;
                }
                break;
            case "blocked":
                require(encounter != null, "No native encounter was opened");
                encounter.blocked(Boolean.parseBoolean(args[3]));
                break;
            case "finish-fight":
                require(encounter != null, "No native encounter was opened");
                encounter.finishFight();
                break;
            case "rewards":
                require(encounter != null, "No native reward observation is armed");
                try {
                    encounter.collect();
                } finally {
                    encounter.close();
                    encounter = null;
                }
                break;
            case "spawn":
                casts(world, player);
                break;
            case "targets":
                targets(world, player, Boolean.parseBoolean(args[3]));
                break;
            default:
                throw new IllegalArgumentException(args[1]);
        }
    }

    private static boolean present() {
        return Loader.isModLoaded("threatworks");
    }

    private static void targets(WorldServer world, EntityPlayerMP player, boolean protection) {
        List<EntityLivingBase> cleanup = new ArrayList<>();
        EntityZombie summon = MeleeChecks.summon(player, world, cleanup, "ebwizardry:summon_zombie", EntityZombie.class);
        EntityZombie enemy = new EntityZombie(world);
        enemy.setPosition(11.5, 4, LANE);
        enemy.setNoAI(true);
        try {
            require(world.spawnEntity(enemy), "Ordinary native target control did not join");
            summon.setAttackTarget(player);
            EntityLivingBase playerResult = summon.getAttackTarget();
            require(playerResult == (protection ? null : player), "Wrong qualified target result");
            summon.setAttackTarget(enemy);
            require(summon.getAttackTarget() == enemy, "The eligible native target was lost");
            enemy.setAttackTarget(player);
            require(enemy.getAttackTarget() == player, "An ordinary enemy's player target was lost");
            output("THREAT_TARGET actor=" + id(summon) + " player=" + id(player) + " playerResult=" + id(playerResult)
                    + " enemy=" + id(enemy) + " eligibleResult=" + id(summon.getAttackTarget()) + " ordinaryResult=" + id(enemy.getAttackTarget()));
        } finally {
            cleanup.forEach(Entity::setDead);
            enemy.setDead();
        }
    }

    private static final class Encounter {
        final WorldServer world;
        final EntityPlayerMP player;
        final List<EntityLivingBase> cleanup = new ArrayList<>();
        EntityZombie victim;
        EntityZombie summon;
        ExperienceProbe experience;
        double playerOpening;
        double summonThreat;

        Encounter(WorldServer world, EntityPlayerMP player) {
            this.world = world;
            this.player = player;
        }

        void start() throws Exception {
            NativeFixture.equipPlayer(player, 15.5);
            summon = MeleeChecks.summon(player, world, cleanup, "ebwizardry:summon_zombie", EntityZombie.class);
            summon.setPositionAndUpdate(11.5, 4, LANE);
            victim = new EntityZombie(world);
            victim.setPosition(13.5, 4, LANE);
            victim.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(5);
            require(world.spawnEntity(victim), "Native encounter Zombie was not added");
            cleanup.add(victim);
            victim.setItemStackToSlot(EntityEquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
            victim.setDropChance(EntityEquipmentSlot.FEET, 2);
            NativeFixture.preparePlayerAttack(player, Enchantments.SHARPNESS);
            player.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
            float initial = victim.getHealth();
            player.attackTargetEntityWithCurrentItem(victim);
            float afterOpening = victim.getHealth();
            require(afterOpening < initial, "Actual player hit did not open combat");
            Table before = table(victim);
            playerOpening = before.score(player);
            output("THREAT_OPENING present=" + present() + " enemy=" + id(victim) + " player=" + id(player)
                    + " initial=" + initial + " after=" + afterOpening + " status=" + before.status + " score=" + playerOpening);
            require(!present() || before.tracked && playerOpening > 0 && before.score(summon) == 0,
                    "Threatworks did not admit the actual opening player damage");
            victim.hurtResistantTime = 0;
            boolean originalReturned = summon.attackEntityAsMob(victim);
            require(victim.getHealth() < afterOpening, "Actual allowed summon attack did not reach enemy health");
            Table after = table(victim);
            summonThreat = after.score(summon);
            float landed = afterOpening - victim.getHealth();
            output("THREAT_ENCOUNTER present=" + present() + " enemy=" + id(victim) + " player=" + id(player)
                    + " summon=" + id(summon) + " initial=" + initial + " opened=" + afterOpening + " after=" + victim.getHealth()
                    + " playerScore=" + playerOpening + " summonBefore=" + before.score(summon) + " summonAfter=" + summonThreat
                    + " originalReturned=" + originalReturned);
            require(!present() || after.tracked && near(summonThreat, landed) && near(after.score(player), playerOpening),
                    "Actual summon UUID did not receive the original damage threat");
            if (present()) {
                Table admission = table(summon);
                output("THREAT_ADMISSION enemy=" + id(victim) + " enemyStatus=" + after.status + " summon=" + id(summon)
                        + " summonType=" + admission.type + " summonStatus=" + admission.status);
                require("unsupported_type".equals(admission.status) && !admission.tracked,
                        "The native Zombie Minion is unexpectedly admitted as a Threatworks-controlled attacker");
            }
            // The gear attribute updates on a normal tick after the opening hit with an empty hand.
            NativeFixture.preparePlayerAttack(player, Enchantments.FIRE_ASPECT);
        }

        void blocked(boolean protectedPair) throws Exception {
            Table before = table(victim);
            NativeFixture.equipPlayer(player, 15.5);
            player.setAbsorptionAmount(1);
            Snapshot playerBefore = new Snapshot(player);
            boolean result = summon.attackEntityAsMob(player);
            Snapshot playerAfter = new Snapshot(player);
            require((playerAfter.health < playerBefore.health) != protectedPair, "Wrong native summon-to-player health during the encounter");
            require(!protectedPair || playerBefore.same(playerAfter), "Protected player changed during actual attack");
            summon.setHealth(summon.getMaxHealth());
            summon.setAbsorptionAmount(1);
            summon.hurtResistantTime = 0;
            summon.setItemStackToSlot(EntityEquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE, 1, 7));
            Snapshot summonBefore = new Snapshot(summon);
            require(player.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue() > 1,
                    "The joined player's equipped sword has not taken effect");
            player.attackTargetEntityWithCurrentItem(summon);
            Snapshot summonAfter = new Snapshot(summon);
            require((summonAfter.health < summonBefore.health) != protectedPair,
                    "Wrong native player-to-summon result during the encounter");
            require(!protectedPair || summonBefore.same(summonAfter), "Protected summon changed during actual attack");
            Table after = table(victim);
            require(!present() || before.tracked && after.tracked && before.scores.equals(after.scores),
                    "Directed player/summon attacks modified the active enemy's scores");
            output("THREAT_BLOCKED present=" + present() + " protected=" + protectedPair + " enemy=" + id(victim)
                    + " player=" + id(player) + " summon=" + id(summon) + " playerState=" + playerBefore + ">" + playerAfter
                    + " summonState=" + summonBefore + ">" + summonAfter + " scoreBefore=" + before.scores + " scoreAfter=" + after.scores
                    + " originalSummonReturned=" + result);
        }

        void finishFight() throws Exception {
            if (!present()) victim.setAttackTarget(summon);
            require(victim.getAttackTarget() == summon, "Enemy did not select the qualified summon as a target");
            summon.hurtResistantTime = 0;
            float before = summon.getHealth();
            require(victim.attackEntityAsMob(summon) && summon.getHealth() < before, "Allowed enemy attack did not hurt the actual summon");
            output("THREAT_ENEMY_HIT present=" + present() + " enemy=" + id(victim) + " target=" + id(victim.getAttackTarget())
                    + " before=" + before + " after=" + summon.getHealth());
            if (present()) creditChecks();
            victim.hurtResistantTime = 0;
            summon.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(100);
            experience = new ExperienceProbe(victim);
            MinecraftForge.EVENT_BUS.register(experience);
            summon.attackEntityAsMob(victim);
            require(victim.getHealth() == 0, "Original summon did not kill the encounter Zombie");
            NativeFixture.equipPlayer(player, 40.5);
            output("THREAT_DEATH present=" + present() + " enemy=" + id(victim) + " actor=" + id(summon));
        }

        void creditChecks() throws Exception {
            Table before = table(victim);
            String granted = String.valueOf(coordinator().getClass().getMethod("grant", EntityLivingBase.class,
                    EntityLivingBase.class, double.class).invoke(coordinator(), victim, summon, 2.5));
            Table after = table(victim);
            require("APPLIED".equals(granted) && near(after.score(summon) - before.score(summon), 2.5),
                    "Native Threatworks non-damage grant did not retain its intended recipient");
            CreditChoice choice = new CreditChoice(victim, summon, player);
            MinecraftForge.EVENT_BUS.register(choice);
            float health = victim.getHealth();
            try {
                summon.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(2);
                victim.hurtResistantTime = 0;
                summon.attackEntityAsMob(victim);
            } finally {
                MinecraftForge.EVENT_BUS.unregister(choice);
            }
            Table redirected = table(victim);
            float dealt = health - victim.getHealth();
            require(dealt > 0 && choice.count == 1 && near(redirected.score(player) - after.score(player), dealt)
                    && near(redirected.score(summon), after.score(summon)), "Native owner-credit choice was changed");
            output("THREAT_CREDIT enemy=" + id(victim) + " owner=" + id(player) + " actor=" + id(summon)
                    + " granted=" + granted + " grantDelta=" + (after.score(summon) - before.score(summon))
                    + " healthLost=" + dealt + " ownerDelta=" + (redirected.score(player) - after.score(player))
                    + " actorDelta=" + (redirected.score(summon) - after.score(summon)) + " requests=" + choice.count);
        }

        void collect() {
            List<EntityXPOrb> orbs = world.getEntitiesWithinAABB(EntityXPOrb.class, REWARDS);
            List<EntityItem> boots = world.getEntitiesWithinAABB(EntityItem.class, REWARDS,
                    item -> item.getItem().getItem() == Items.IRON_BOOTS);
            int xp = orbs.stream().mapToInt(EntityXPOrb::getXpValue).sum();
            require(experience != null, "Native encounter did not arm its exact victim");
            output("THREAT_REWARD present=" + present() + " enemy=" + id(victim) + " boots=" + boots.size() + " xp=" + xp
                    + " nativeXp=" + experience.value + " xpCredit=" + id(experience.credit) + " xpCanceled=" + experience.canceled);
            require(boots.size() == 1 && xp > 0 && !experience.canceled && experience.credit == player && xp == experience.value,
                    "Native encounter did not retain positive eligible item/XP rewards");
            orbs.forEach(Entity::setDead);
            boots.forEach(Entity::setDead);
        }

        void close() {
            if (experience != null) MinecraftForge.EVENT_BUS.unregister(experience);
            cleanup.forEach(Entity::setDead);
        }
    }

    public static final class ExperienceProbe {
        private final EntityLivingBase victim;
        EntityPlayerMP credit;
        int value;
        boolean canceled;

        ExperienceProbe(EntityLivingBase victim) {
            this.victim = victim;
        }

        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
        public void experience(LivingExperienceDropEvent event) {
            if (event.getEntityLiving() != victim) return;
            credit = event.getAttackingPlayer() instanceof EntityPlayerMP ? (EntityPlayerMP) event.getAttackingPlayer() : null;
            value = event.getDroppedExperience();
            canceled = event.isCanceled();
        }
    }

    public static final class CreditChoice {
        private final EntityLivingBase victim;
        private final EntityLivingBase summon;
        private final EntityPlayerMP player;
        int count;

        CreditChoice(EntityLivingBase victim, EntityLivingBase summon, EntityPlayerMP player) {
            this.victim = victim;
            this.summon = summon;
            this.player = player;
        }

        @SubscribeEvent
        public void requestOwnerCredit(Event event) {
            Class<?> type = event.getClass();
            if (!CREDIT_EVENT.equals(type.getName())) return;
            try {
                if (type.getMethod("getTarget").invoke(event) == victim && type.getMethod("getSummon").invoke(event) == summon
                        && type.getMethod("getOwner").invoke(event) == player) {
                    type.getMethod("creditOwner").invoke(event);
                    count++;
                }
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(failure);
            }
        }
    }

    private static Object coordinator() throws Exception {
        return Class.forName(COORDINATOR).getField("INSTANCE").get(null);
    }

    private static Table table(EntityLivingBase victim) throws Exception {
        Table table = new Table();
        if (!present()) return table;
        Object service = coordinator();
        Object state = service.getClass().getMethod("inspect", EntityLivingBase.class, int.class).invoke(service, victim, 20);
        Class<?> type = state.getClass();
        table.tracked = type.getField("tracked").getBoolean(state);
        table.status = (String) type.getField("status").get(state);
        table.type = (String) type.getField("typeId").get(state);
        for (Object row : (Iterable<?>) type.getField("participants").get(state)) {
            Class<?> entry = row.getClass();
            table.scores.put((UUID) entry.getField("id").get(row), entry.getField("score").getDouble(row));
        }
        return table;
    }

    private static final class Table {
        final Map<UUID, Double> scores = new LinkedHashMap<>();
        boolean tracked;
        String status;
        String type;

        double score(EntityLivingBase participant) {
            return scores.getOrDefault(participant.getUniqueID(), 0d);
        }
    }

    private static final class Snapshot {
        final float health;
        final String state;

        Snapshot(EntityLivingBase entity) {
            health = entity.getHealth();
            ItemStack chest = entity.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
            state = String.join("/", String.valueOf(health), String.valueOf(entity.getAbsorptionAmount()),
                    String.valueOf(chest.getItemDamage()), String.valueOf(entity.isBurning()), String.valueOf(entity.motionX),
                    String.valueOf(entity.motionY), String.valueOf(entity.motionZ));
        }

        boolean same(Snapshot other) {
            return state.equals(other.state);
        }

        @Override
        public String toString() {
            return state;
        }
    }

    private static void casts(WorldServer world, EntityPlayerMP player) throws Exception {
        NativeFixture.equipPlayer(player, 8.5);
        EntitySnowman rejected = OriginChecks.checkSpawn(world, player, "summon_snow_golem", EntitySnowman.class, true);
        require(threatCaster(rejected) == null, "Canceled cast left native Threatworks provenance");
        EntitySnowman snow = OriginChecks.checkSpawn(world, player, "summon_snow_golem", EntitySnowman.class, false);
        UUID originalCredit = threatCaster(snow);
        require(!present() || player.getUniqueID().equals(originalCredit), "The original Threatworks observation was lost");
        loadRemote(world);
        NativeFixture.floor(world, 1024, 1031);
        snow.setNoAI(true);
        snow.setPositionAndUpdate(1024.5, 4, LANE);
        Path folder = worldPath(world);
        Files.write(folder.resolve("wst-threat-snow.txt"), Arrays.asList(id(snow), id(player)), StandardCharsets.US_ASCII);
        if (Boolean.getBoolean("mixin.debug.export")) {
            Path exported = folder.toAbsolutePath().getParent()
                    .resolve(".mixin.out/class/electroblob/wizardry/spell/SummonSnowGolem.class");
            Files.copy(exported, folder.resolve("wst-threat-as-cast.class"));
        }
        output("THREAT_SPAWN present=" + present() + " accepted=" + id(snow) + " caster=" + id(player)
                + " providerObserved=" + originalCredit + " rejected=" + id(rejected) + " rejectedObserved=null");
    }

    private static UUID threatCaster(EntitySnowman snow) throws Exception {
        if (!present()) return null;
        Class<?> source = Class.forName("com.mahghuuuls.threatworks.compat.SnowGolemOrigin");
        return (UUID) source.getMethod("threatworks$wizardryCaster").invoke(snow);
    }

    private static void restore(WorldServer world) throws Exception {
        Path folder = worldPath(world);
        Files.copy(folder.resolve("region/r.2.0.mca"), folder.resolve("wst-threat-anvil.mca"));
        List<String> ids = Files.readAllLines(folder.resolve("wst-threat-snow.txt"), StandardCharsets.US_ASCII);
        loadRemote(world);
        Entity snow = world.getEntityFromUuid(UUID.fromString(ids.get(0)));
        require(snow instanceof EntitySnowman && snow.isEntityAlive(), "Original Snow Golem was not restored");
        inspect(world.getMinecraftServer(), world.getMinecraftServer(), snow, "AFFECTED");
        NBTTagCompound mark = snow.getEntityData().getCompoundTag(MARK);
        require(ids.get(1).equals(mark.getString("ownerUUID")) && "ebwizardry:summon_snow_golem".equals(mark.getString("originProvider")),
                "Original generated owner or origin changed on restart");
        output("THREAT_RESTORE entity=" + id(snow) + " owner=" + ids.get(1) + " origin=" + mark.getString("originProvider"));
    }

    private static void loadRemote(WorldServer world) {
        for (int x = 62; x <= 66; x++) for (int z = -2; z <= 2; z++) world.getChunk(x, z);
    }

    private static Path worldPath(WorldServer world) {
        return world.getSaveHandler().getWorldDirectory().toPath();
    }

    private static boolean near(double actual, double expected) {
        return Math.abs(actual - expected) < 0.001;
    }

    private static String id(Entity entity) {
        return entity == null ? "none" : entity.getUniqueID().toString();
    }
}
