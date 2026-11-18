package wstfixture;

import electroblob.wizardry.entity.living.EntitySkeletonMinion;
import electroblob.wizardry.entity.living.ISummonedCreature;
import electroblob.wizardry.util.IndirectMinionDamage;
import electroblob.wizardry.util.MagicDamage.DamageType;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.monster.EntitySnowman;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntitySnowball;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.init.PotionTypes;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionUtils;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static wstfixture.IdentityFixture.*;
import static wstfixture.NativeFixture.*;

public final class ProjectileChecks {
    private static final String LAUNCH = "wizardrysummontweaks:launch";
    private static final double LANE = 6.5;
    private static final Field RAW_MINION = ReflectionHelper.findField(IndirectMinionDamage.class, "minion");
    private static final Field THROWER_NAME = ReflectionHelper.findField(net.minecraft.entity.projectile.EntityThrowable.class,
            "throwerName", "field_85053_h");
    private static final Field THROWER = ReflectionHelper.findField(net.minecraft.entity.projectile.EntityThrowable.class,
            "thrower", "field_70192_c");
    private static final Field PROJECTILE_RANDOM = ReflectionHelper.findField(Entity.class, "rand", "field_70146_Z");
    private static Scenario active;

    public static void run(MinecraftServer server, ICommandSender sender, String[] args) throws Exception {
        WorldServer world = server.getWorld(0);
        String action = args[1];
        if ("collect".equals(action)) {
            require(active != null && active.name.equals(args[2]), "Wrong projectile collection");
            Scenario complete = active;
            active = null;
            try {
                complete.collect();
                if (complete.restored) output("PROJECTILE_RESTORE_PASS");
            } finally {
                complete.close();
            }
            return;
        }
        if ("restore".equals(action)) {
            restore(world, Boolean.parseBoolean(args[2]));
            return;
        }
        EntityPlayerMP player = server.getPlayerList().getPlayerByUsername(args[3]);
        require(player != null && player.world == world && !player.isDead, "A joined living player in the arena is required");
        switch (action) {
            case "setup":
                loadArena(world);
                floor(world, 0, 55);
                world.setSpawnPoint(new BlockPos(12, 4, 6));
                player.setGameType(GameType.SURVIVAL);
                output("PROJECTILE_ARENA_READY");
                return;
            case "order":
                checkOrder(server, world, player, Boolean.parseBoolean(args[4]));
                return;
            case "close-snow":
                checkCloseSnow(world, player);
                return;
            case "save":
                prepareSave(world, player);
                return;
            default:
                require(active == null, "A projectile case is already armed");
                Scenario scenario = new Scenario(world, player, args[2], Boolean.parseBoolean(args[4]));
                try {
                    scenario.start();
                    active = scenario;
                } catch (Exception | Error failure) {
                    scenario.close();
                    throw failure;
                }
        }
    }

    private static final class Scenario {
        final WorldServer world;
        final EntityPlayerMP player;
        final String name;
        final boolean protectedDirection;
        final List<EntityLivingBase> mobs = new ArrayList<>();
        final List<String> impacts = new ArrayList<>();
        EntityLivingBase launcher;
        EntityLivingBase front;
        EntityLivingBase eligible;
        Entity shot;
        State frontBefore;
        State eligibleBefore;
        DamageSource frontDamage;
        DamageSource eligibleDamage;
        ScorePlayerTeam noFriendlyFire;
        boolean intersectsFront;
        boolean offlineAtFront;
        boolean slow;
        boolean restored;
        boolean conflictingOwner;
        int observedFlightTicks;
        double maxFrontPush;

        Scenario(WorldServer world, EntityPlayerMP player, String name, boolean protectedDirection) {
            this.world = world;
            this.player = player;
            this.name = name;
            this.protectedDirection = protectedDirection;
        }

        void start() throws Exception {
            MinecraftForge.EVENT_BUS.register(this);
            if ("plain-bow".equals(name) || "tipped-bow".equals(name) || "non-owner-bow".equals(name) || "logout".equals(name)) {
                boolean logout = "logout".equals(name);
                equipPlayer(player, logout ? 2.5 : 8.5);
                launcher = player;
                front = zombieSummon(player, world, mobs);
                position(front, logout ? 20.5 : 13.5);
                if ("non-owner-bow".equals(name)) {
                    require(java.util.Arrays.asList(world.getMinecraftServer().getPlayerList().getAvailablePlayerDat()).contains(SECOND_PLAYER.toString()),
                            "Alternate player's saved native record is absent");
                    ((ISummonedCreature) front).setOwnerId(SECOND_PLAYER);
                    inspect(world.getMinecraftServer(), world.getMinecraftServer(), front, "AFFECTED");
                    output("PROJECTILE_NON_OWNER case=" + name + " shooter=" + player.getUniqueID()
                            + " nativeOwner=" + ((ISummonedCreature) front).getOwnerId() + " summon=" + id(front));
                }
                eligible = enemy(world, mobs, false, logout ? 23.5 : 17.5);
                arm();
                if (logout) {
                    spawnArrow(world, player, 2.5, 0.36F, false);
                } else {
                    player.inventory.clear();
                    player.inventory.currentItem = 0;
                    player.inventory.setInventorySlotContents(0, bow());
                    ItemStack ammunition = name.equals("tipped-bow") ? PotionUtils.addPotionToItemStack(
                            new ItemStack(Items.TIPPED_ARROW, 8), PotionTypes.SLOWNESS) : new ItemStack(Items.ARROW, 8);
                    player.inventory.setInventorySlotContents(1, ammunition);
                    player.inventoryContainer.detectAndSendChanges();
                }
                return;
            }
            if ("vanilla-team-bypass".equals(name)) {
                equipPlayer(player, 12.5);
                front = player;
                launcher = NativeFixture.secondPlayer(world.getMinecraftServer(), world);
                noFriendlyFire = world.getScoreboard().createTeam("wst_no_ff");
                noFriendlyFire.setAllowFriendlyFire(false);
                world.getScoreboard().addPlayerToTeam(player.getName(), noFriendlyFire.getName());
                world.getScoreboard().addPlayerToTeam(launcher.getName(), noFriendlyFire.getName());
                require(!((EntityPlayerMP) launcher).canAttackPlayer(player), "Vanilla did not refuse teammate damage");
                eligible = enemy(world, mobs, false, 16.5);
                arm();
                output("PROJECTILE_NATIVE_TEAM case=" + name + " nativeCanAttack=false");
                spawnArrow(world, launcher, 8.5, 1.2F, false);
                return;
            }
            if ("skeleton-ai".equals(name) || "snow".equals(name) || "directed-snow".equals(name)) {
                boolean snowball = !"skeleton-ai".equals(name);
                equipPlayer(player, 11.5);
                front = player;
                eligible = enemy(world, mobs, snowball, name.equals("directed-snow") ? 13.5 : 14.0);
                if (snowball) {
                    EntitySnowman snow = OriginChecks.checkSpawn(world, player, "summon_snow_golem", EntitySnowman.class, false);
                    mobs.add(snow);
                    snow.setNoAI(true);
                    launcher = snow;
                    position(snow, 8.5);
                    arm();
                    EntityLivingBase intended = "directed-snow".equals(name) ? front : eligible;
                    output("PROJECTILE_AIM case=" + name + " intended=" + id(intended));
                    snow.attackEntityWithRangedAttack(intended, 1);
                } else {
                    eligible.setPosition(eligible.posX, 4.25, LANE);
                    EntitySkeletonMinion skeleton = skeleton(player, world, mobs);
                    launcher = skeleton;
                    position(skeleton, 7.5);
                    skeleton.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND, bow());
                    skeleton.setAttackTarget(eligible);
                    arm();
                    skeleton.setNoAI(false);
                }
                return;
            }
            if ("conflict".equals(name)) {
                equipPlayer(player, 11.5);
                front = player;
                EntitySnowman snow = OriginChecks.checkSpawn(world, player, "summon_snow_golem", EntitySnowman.class, false);
                mobs.add(snow);
                snow.setNoAI(true);
                launcher = snow;
                position(snow, 7.5);
                arm();
                snow.attackEntityWithRangedAttack(front, 1);
                require(shot instanceof EntitySnowball && shot.getEntityData().hasKey(LAUNCH), "No original Snow Golem launch");
                return;
            }
            if ("summon-peer".equals(name) || "reassigned".equals(name) || "env".equals(name)) {
                equipPlayer(player, 45.5);
                front = zombieSummon(player, world, mobs);
                position(front, 12.5);
                if ("summon-peer".equals(name)) {
                    EntitySkeletonMinion skeleton = skeleton(player, world, mobs);
                    launcher = skeleton;
                    position(skeleton, 7.5);
                    arm();
                    skeleton.attackEntityWithRangedAttack(front, 1);
                } else {
                    launcher = player;
                    arm();
                    if ("reassigned".equals(name)) {
                        EntityTippedArrow arrow = spawnArrow(world, player, 8.5, 1, false);
                        EntityZombie reassigned = new EntityZombie(world);
                        reassigned.setNoAI(true);
                        mobs.add(reassigned);
                        position(reassigned, 0.5);
                        require(world.spawnEntity(reassigned), "new launcher did not join");
                        arrow.shootingEntity = reassigned;
                        output("PROJECTILE_REASSIGNED projectile=" + arrow.getUniqueID() + " newActor=" + reassigned.getUniqueID());
                    } else {
                        front.attackEntityFrom(DamageSource.IN_FIRE, 6);
                    }
                }
                return;
            }
            throw new IllegalArgumentException("Unknown projectile case " + name);
        }

        void arm() {
            resetProjectileVictim(front);
            frontBefore = new State(front);
            if (eligible != null) eligibleBefore = new State(eligible);
            output("PROJECTILE_READY case=" + name + " protected=" + protectedDirection + " caster=" + (player == null ? null : player.getUniqueID())
                    + " launcher=" + id(launcher) + " front=" + id(front) + " later=" + id(eligible) + " before=" + frontBefore);
        }

        void collect() throws Exception {
            State after = new State(front);
            output("PROJECTILE_RESULT case=" + name + " projectile=" + id(shot) + " intersection=" + intersectsFront
                    + " offlineAtFront=" + offlineAtFront + " effects=" + after + " maxPush=" + maxFrontPush
                    + " laterBefore=" + eligibleBefore + " laterAfter=" + (eligible == null ? null : new State(eligible))
                    + " impacts=" + impacts + " frontSource=" + source(frontDamage) + " laterSource=" + source(eligibleDamage));
            if ("env".equals(name)) {
                require(frontDamage == DamageSource.IN_FIRE && frontBefore.changed(after), "unattributed environmental positive did not land");
                return;
            }
            require(shot != null, "native projectile did not launch");
            if ("logout".equals(name)) require(offlineAtFront, "owner was still online at shot passage");
            if ("conflict".equals(name)) require(conflictingOwner && nativeLauncher(shot) == null, "conflicting native state was not active");
            if ("reassigned".equals(name)) {
                require(shot.getEntityData().hasKey(LAUNCH), "reassigned shot had no stale original launch facts at impact");
                require(!shot.serializeNBT().getCompoundTag("ForgeData").hasKey(LAUNCH), "save revived superseded player launch evidence");
                output("PROJECTILE_REASSIGNED_SAVE projectile=" + id(shot) + " staleRecord=false");
            }
            if (protectedDirection) {
                require(intersectsFront, "native projectile did not intersect the protected target corridor");
                require(!frontBefore.changed(after) && maxFrontPush < 0.01 && frontDamage == null && !impacts.contains(id(front)),
                        "protected front entity received a projectile effect");
                require(eligible != null && eligibleBefore.changed(new State(eligible)) && eligibleDamage != null
                        && eligibleDamage.getImmediateSource() == shot && impacts.contains(id(eligible)),
                        "eligible target never received a native later hit from the same projectile");
            } else {
                require(impacts.contains(id(front)), "disabled/baseline front collision did not execute");
                if (!(shot instanceof EntitySnowball)) {
                    require(frontBefore.changed(after) && frontDamage != null && frontDamage.getImmediateSource() == shot,
                            "disabled or allowed arrow produced no positive native damage");
                    if ("tipped-bow".equals(name)) require(slow && after.slowness, "tipped arrow had no native status effect");
                } else {
                    require(shot.isDead, "baseline Snow Golem impact did not consume the native snowball");
                }
            }
            DamageSource actual = protectedDirection ? eligibleDamage : frontDamage;
            if (actual == null) return;
            if (launcher instanceof EntitySkeletonMinion) {
                require(actual instanceof IndirectMinionDamage && RAW_MINION.get(actual) == launcher && actual.getTrueSource() == player,
                        "native provider changed minion actor or credited owner");
            } else if (launcher instanceof EntityPlayerMP && !"reassigned".equals(name) && !restored) {
                require(actual.getTrueSource() == launcher, "native player credit was changed");
            }
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void joined(EntityJoinWorldEvent event) {
            Entity candidate = event.getEntity();
            if (event.getWorld() != world || launcher == null || shot != null) return;
            Entity actual = nativeLauncher(candidate);
            if (actual != launcher) return;
            shot = candidate;
            if ("skeleton-ai".equals(name)) ((EntityLiving) launcher).setNoAI(true);
            slow = candidate instanceof EntityTippedArrow && candidate.serializeNBT().getString("Potion").equals("minecraft:slowness");
            output("PROJECTILE_JOIN case=" + name + " projectile=" + id(candidate) + " nativeLauncher=" + id(actual)
                    + " motion=" + candidate.getPositionVector() + " velocity=" + new Vec3d(candidate.motionX, candidate.motionY, candidate.motionZ)
                    + " tipped=" + slow + " launch=" + candidate.getEntityData().getCompoundTag(LAUNCH));
            observeFrontIntersection();
        }

        @SubscribeEvent
        public void seedShot(EntityEvent.EntityConstructing event) {
            Entity entity = event.getEntity();
            if (entity.world != world || launcher == null || shot != null
                    || !(entity instanceof EntityTippedArrow || entity instanceof EntitySnowball)) return;
            // The original constructor and shoot methods still compute the entire ballistic trajectory.
            try {
                ((Random) PROJECTILE_RANDOM.get(entity)).setSeed(377);
            } catch (IllegalAccessException failure) {
                throw new IllegalStateException(failure);
            }
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void impact(ProjectileImpactEvent event) {
            if (event.getEntity() != shot || event.getRayTraceResult().typeOfHit != RayTraceResult.Type.ENTITY) return;
            Entity victim = event.getRayTraceResult().entityHit;
            impacts.add(id(victim));
            output("PROJECTILE_IMPACT case=" + name + " projectile=" + id(shot) + " victim=" + id(victim)
                    + " tick=" + world.getTotalWorldTime() + " live=" + id(nativeLauncher(shot)));
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void hurt(LivingHurtEvent event) {
            if (!"env".equals(name) && (shot == null || event.getSource().getImmediateSource() != shot)) return;
            if (event.getEntityLiving() == front) frontDamage = event.getSource();
            if (event.getEntityLiving() == eligible) eligibleDamage = event.getSource();
        }

        @SubscribeEvent
        public void tick(TickEvent.WorldTickEvent event) {
            if (event.world != world || shot == null || front == null) return;
            if (event.phase == TickEvent.Phase.END) {
                maxFrontPush = Math.max(maxFrontPush, Math.hypot(front.motionX, front.motionZ));
                return;
            }
            if ("logout".equals(name) && !intersectsFront && observedFlightTicks++ % 25 == 0) {
                output("PROJECTILE_LOGOUT_FLIGHT projectile=" + id(shot) + " x=" + shot.posX + " dead=" + shot.isDead
                        + " loaded=" + world.isAreaLoaded(shot.getPosition(), 32) + " originalPlayerPresent="
                        + (world.getMinecraftServer().getPlayerList().getPlayerByUUID(player.getUniqueID()) != null));
            }
            if (intersectsFront || shot.isDead) return;
            if ("conflict".equals(name) && !conflictingOwner && shot.posX > 8.4) {
                // Leave the shooter's bounding box before substituting the pending owner.
                UUID pending = UUID.randomUUID();
                try {
                    THROWER.set(shot, null);
                    THROWER_NAME.set(shot, launcher.getUniqueID().toString());
                    inspectProjectile(world, shot, "Projectile actor=" + id(launcher) + " role=AFFECTED_SUMMON evidence=native_launcher");
                    require(THROWER.get(shot) == null, "Inspect mutated the provider's cached thrower");
                    THROWER_NAME.set(shot, pending.toString());
                    inspectProjectile(world, shot, "Projectile actor=unknown role=OTHER evidence=unresolved reason=conflicting_native_launcher");
                    require(THROWER.get(shot) == null, "Inspect mutated the contradictory native thrower");
                } catch (IllegalAccessException failure) {
                    throw new IllegalStateException(failure);
                }
                conflictingOwner = true;
                output("PROJECTILE_CONFLICT projectile=" + id(shot) + " pending=" + pending
                        + " saved=" + shot.getEntityData().getCompoundTag(LAUNCH).getString("actor"));
            }
            observeFrontIntersection();
        }

        private void observeFrontIntersection() {
            if (front == null || intersectsFront || shot.isDead) return;
            Vec3d start = shot.getPositionVector();
            Vec3d end = new Vec3d(start.x + shot.motionX, start.y + shot.motionY, start.z + shot.motionZ);
            if (front.getEntityBoundingBox().grow(0.30000001192092896D).calculateIntercept(start, end) == null) return;
            intersectsFront = true;
            offlineAtFront = player == null || world.getMinecraftServer().getPlayerList().getPlayerByUUID(player.getUniqueID()) == null;
            output("PROJECTILE_PASSAGE case=" + name + " projectile=" + id(shot) + " front=" + id(front)
                    + " tick=" + world.getTotalWorldTime() + " segment=" + start + "-" + end + " offline=" + offlineAtFront);
        }

        void close() {
            MinecraftForge.EVENT_BUS.unregister(this);
            if (noFriendlyFire != null) world.getScoreboard().removeTeam(noFriendlyFire);
            if (shot != null) shot.setDead();
            for (EntityLivingBase mob : mobs) mob.setDead();
            if (player != null && player.isEntityAlive()) {
                resetProjectileVictim(player);
                position(player, 3.5);
            }
        }
    }

    private static void checkCloseSnow(WorldServer world, EntityPlayerMP player) throws Exception {
        Scenario close = new Scenario(world, player, "close-snow", false);
        MinecraftForge.EVENT_BUS.register(close);
        try {
            equipPlayer(player, 8.9);
            world.updateEntityWithOptionalForce(player, false);
            close.front = player;
            close.eligible = enemy(world, close.mobs, true, 9.55);
            EntitySnowman snow = OriginChecks.checkSpawn(world, player, "summon_snow_golem", EntitySnowman.class, false);
            close.mobs.add(snow);
            snow.setNoAI(true);
            position(snow, 8.5);
            close.launcher = snow;
            close.arm();
            snow.attackEntityWithRangedAttack(close.eligible, 1);
            Entity shot = close.shot;
            require(shot instanceof EntitySnowball && shot.isAddedToWorld(), "close shot did not come from the native Snow Golem");
            List<Entity> candidates = world.getEntitiesWithinAABBExcludingEntity(shot,
                    shot.getEntityBoundingBox().expand(shot.motionX, shot.motionY, shot.motionZ).grow(1.0));
            candidates.removeIf(entity -> !entity.canBeCollidedWith());
            require(new ArrayList<>(new java.util.LinkedHashSet<>(candidates)).equals(java.util.Arrays.asList(close.front, close.eligible, snow)),
                    "Unexpected native first-tick candidate order: " + candidates);
            output("PROJECTILE_CLOSE_CANDIDATES shot=" + id(shot) + " player=" + id(close.front)
                    + " blaze=" + id(close.eligible) + " snow=" + id(snow) + " rawCount=" + candidates.size());
            // Run the native update before unrelated entity movement can change the point-blank ordering.
            shot.onUpdate();
            output("PROJECTILE_CLOSE_RAW shot=" + id(shot) + " impacts=" + close.impacts
                    + " player=" + new State(player) + " blaze=" + new State(close.eligible)
                    + " source=" + source(close.eligibleDamage));
            require(!close.frontBefore.changed(new State(player)), "close throw changed the player");
            require(close.impacts.equals(java.util.Collections.singletonList(id(close.eligible)))
                    && close.eligibleDamage != null && close.eligibleDamage.getImmediateSource() == shot
                    && close.eligible.getHealth() == close.eligibleBefore.health - 3,
                    "native close throw skipped the eligible Blaze");
            output("PROJECTILE_CLOSE_PASS");
        } finally {
            close.close();
        }
    }

    private static final class State {
        final float health;
        final float absorption;
        final int durability;
        final int arrows;
        final boolean burning;
        final boolean slowness;

        State(EntityLivingBase entity) {
            health = entity.getHealth();
            absorption = entity.getAbsorptionAmount();
            durability = entity.getItemStackFromSlot(EntityEquipmentSlot.CHEST).getItemDamage();
            arrows = entity.getArrowCountInEntity();
            burning = entity.isBurning();
            slowness = entity.isPotionActive(MobEffects.SLOWNESS);
        }

        boolean changed(State after) { return !toString().equals(after.toString()); }

        @Override public String toString() {
            return health + "/" + absorption + "/armor=" + durability + "/arrows=" + arrows + "/fire=" + burning + "/slow=" + slowness;
        }
    }

    private static void checkOrder(MinecraftServer server, WorldServer world, EntityPlayerMP owner, boolean protect) throws Exception {
        List<EntityLivingBase> mobs = new ArrayList<>();
        EntitySkeletonMinion minion = skeleton(owner, world, mobs);
        EntityPlayerMP victim = NativeFixture.secondPlayer(server, world);
        try {
            for (boolean wizardryFirst : new boolean[]{true, false}) {
                for (boolean wrapped : new boolean[]{false, true}) {
                    resetProjectileVictim(victim);
                    EntityTippedArrow arrow = new EntityTippedArrow(world);
                    if (!wrapped) arrow.shootingEntity = minion;
                    DamageSource source = wrapped ? new IndirectMinionDamage("arrow", arrow, minion, owner, DamageType.MAGIC, false).setProjectile()
                            : DamageSource.causeArrowDamage(arrow, minion);
                    if (wrapped) require(RAW_MINION.get(source) == minion && source.getTrueSource() == owner, "fixture wrapper fields are wrong");
                    State before = new State(victim);
                    MeleeChecks.withAttackOrder(new LivingAttackEvent(victim, source, 7), wizardryFirst,
                            () -> victim.attackEntityFrom(source, 7));
                    State after = new State(victim);
                    require(before.changed(after) != protect, "projectile bus order or wrapper actor changed protection");
                    output("PROJECTILE_ORDER_PASS wizardryFirst=" + wizardryFirst + " wrapped=" + wrapped + " protected=" + protect
                            + " raw=" + source(source) + " before=" + before + " after=" + after);
                }
            }
        } finally {
            for (EntityLivingBase mob : mobs) mob.setDead();
        }
    }

    private static void prepareSave(WorldServer world, EntityPlayerMP player) throws Exception {
        final int base = 1024;
        loadRemote(world, base);
        floor(world, base, base + 45);
        List<EntityLivingBase> mobs = new ArrayList<>();
        final EntityZombie front = zombieSummon(player, world, mobs);
        position(front, base + 29.5);
        for (EntityLivingBase mob : mobs) if (mob != front) mob.setDead();
        EntityZombie behind = (EntityZombie) enemy(world, new ArrayList<>(), false, base + 31.5);
        resetProjectileVictim(front);
        EntityTippedArrow arrow = spawnArrow(world, player, base + 0.5, 0.36F, false);
        require(arrow.getEntityData().hasKey(LAUNCH) && arrow.motionX > 0.05 && !arrow.isDead, "saved arrow never started active flight");
        Files.write(savePath(world), java.util.Arrays.asList(id(arrow), id(front), id(behind), id(player)), StandardCharsets.US_ASCII);
        output("PROJECTILE_SAVE_READY projectile=" + id(arrow) + " front=" + id(front) + " later=" + id(behind) + " original=" + id(player)
                + " pos=" + arrow.getPositionVector() + " velocity=" + arrow.motionX + " evidence=" + arrow.getEntityData().getCompoundTag(LAUNCH));
    }

    private static void restore(WorldServer world, boolean protectedDirection) throws Exception {
        Path folder = savePath(world).getParent();
        Files.copy(folder.resolve("region/r.2.0.mca"), folder.resolve("wst-projectile-region.mca"));
        loadRemote(world, 1024);
        List<String> ids = Files.readAllLines(savePath(world), StandardCharsets.US_ASCII);
        EntityTippedArrow arrow = (EntityTippedArrow) world.getEntityFromUuid(UUID.fromString(ids.get(0)));
        require(arrow != null && !arrow.isDead && arrow.motionX > 0 && arrow.shootingEntity == null
                && world.getMinecraftServer().getPlayerList().getPlayerByUUID(UUID.fromString(ids.get(3))) == null, "saved native arrow did not survive offline in flight");
        NBTTagCompound record = arrow.getEntityData().getCompoundTag(LAUNCH);
        require(ids.get(3).equals(record.getString("actor")), "saved launcher identity is wrong");
        inspectProjectile(world, arrow, "Projectile actor=" + ids.get(3) + " role=PLAYER evidence=saved_launch");
        require(arrow.shootingEntity == null, "Inspect set the reloaded native shooter");
        Scenario scenario = new Scenario(world, null, "restored", protectedDirection);
        scenario.shot = arrow;
        scenario.front = (EntityLivingBase) world.getEntityFromUuid(UUID.fromString(ids.get(1)));
        scenario.eligible = (EntityLivingBase) world.getEntityFromUuid(UUID.fromString(ids.get(2)));
        require(scenario.front != null && scenario.eligible != null, "persisted target roster missing");
        scenario.restored = true;
        scenario.mobs.add(scenario.front);
        scenario.mobs.add(scenario.eligible);
        scenario.arm();
        active = scenario;
        MinecraftForge.EVENT_BUS.register(scenario);
        output("PROJECTILE_RESTORE_READY projectile=" + id(arrow) + " nativeLauncher=" + id(nativeLauncher(arrow)) + " original=" + ids.get(3));
    }

    private static void loadRemote(WorldServer world, int x) {
        int center = x >> 4;
        for (int dx = -3; dx <= 5; dx++) for (int z = -3; z <= 3; z++) world.getChunk(center + dx, z);
    }

    private static Path savePath(WorldServer world) {
        return world.getSaveHandler().getWorldDirectory().toPath().resolve("wst-projectile-ids.txt");
    }

    private static void inspectProjectile(WorldServer world, Entity projectile, String expected) {
        MinecraftServer server = world.getMinecraftServer();
        Capture capture = new Capture(server);
        require(server.getCommandManager().executeCommand(capture, "wizardrysummontweaks inspect " + id(projectile)) == 1
                && capture.text.toString().contains(expected), "Projectile inspection was incorrect: " + capture.text);
        output("PROJECTILE_INSPECT projectile=" + id(projectile) + " " + expected);
    }

    private static EntityTippedArrow spawnArrow(WorldServer world, EntityLivingBase owner, double x, float speed, boolean ignited) {
        EntityTippedArrow arrow = new EntityTippedArrow(world, owner);
        arrow.setPosition(x, 5.55, LANE);
        arrow.shoot(1, 0, 0, speed, 0);
        arrow.setNoGravity(true);
        if (ignited) arrow.setFire(100);
        require(world.spawnEntity(arrow), "native arrow entity did not join");
        return arrow;
    }

    private static EntityZombie zombieSummon(EntityPlayerMP player, WorldServer world, List<EntityLivingBase> mobs) {
        int previous = mobs.size();
        EntityZombie result = MeleeChecks.summon(player, world, mobs, "ebwizardry:summon_zombie", EntityZombie.class);
        for (EntityLivingBase mob : mobs.subList(previous, mobs.size())) if (mob != result) mob.setDead();
        return result;
    }

    private static EntitySkeletonMinion skeleton(EntityPlayerMP player, WorldServer world, List<EntityLivingBase> mobs) {
        int previous = mobs.size();
        EntitySkeletonMinion result = MeleeChecks.summon(player, world, mobs, "ebwizardry:summon_skeleton", EntitySkeletonMinion.class);
        for (EntityLivingBase mob : mobs.subList(previous, mobs.size())) if (mob != result) mob.setDead();
        return result;
    }

    private static EntityLivingBase enemy(WorldServer world, List<EntityLivingBase> mobs, boolean blaze, double x) {
        EntityLiving enemy = blaze ? new EntityBlaze(world) : new EntityZombie(world);
        enemy.setNoAI(true);
        enemy.setNoGravity(true);
        position(enemy, x);
        mobs.add(enemy);
        require(world.spawnEntity(enemy), "eligible target could not join");
        return enemy;
    }

    private static void position(EntityLivingBase entity, double x) {
        entity.setPositionAndUpdate(x, 4, LANE);
    }

    private static ItemStack bow() {
        ItemStack bow = new ItemStack(Items.BOW);
        bow.addEnchantment(Enchantments.FLAME, 1);
        bow.addEnchantment(Enchantments.PUNCH, 1);
        return bow;
    }

    private static Entity nativeLauncher(Entity entity) {
        if (entity instanceof EntityTippedArrow) return ((EntityTippedArrow) entity).shootingEntity;
        if (entity instanceof EntitySnowball) return ((EntitySnowball) entity).getThrower();
        return null;
    }

    private static String id(Entity entity) { return entity == null ? "none" : entity.getUniqueID().toString(); }

    private static String source(DamageSource damage) throws IllegalAccessException {
        if (damage == null) return "none";
        return damage.getClass().getSimpleName() + ":" + damage.damageType + ":immediate=" + id(damage.getImmediateSource())
                + ":actor=" + (damage instanceof IndirectMinionDamage ? id((Entity) RAW_MINION.get(damage)) : id(damage.getTrueSource()))
                + ":credit=" + id(damage.getTrueSource());
    }
}
