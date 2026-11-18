package wstfixture;

import electroblob.wizardry.entity.construct.EntityStormcloud;
import electroblob.wizardry.entity.living.ISpellCaster;
import electroblob.wizardry.entity.living.ISummonedCreature;
import electroblob.wizardry.item.ItemArtefact;
import electroblob.wizardry.registry.WizardryItems;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.AllyDesignationSystem;
import electroblob.wizardry.util.IndirectMinionDamage;
import electroblob.wizardry.util.MinionDamage;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.items.IItemHandlerModifiable;

import java.lang.reflect.Field;
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

public final class SpellChecks {
    private static final String MAGE = "ancientspellcraft:raise_skeleton_mage";
    private static final String MAGE_CLASS = "com.windanesz.ancientspellcraft.entity.living.EntitySkeletonMageMinion";
    private static final String RAY = "ebwizardry:lightning_ray";
    private static final String PARALYSIS = "ebwizardry:paralysis";
    private static final String FIRESTORM = "ebwizardry:firestorm";
    private static final String STORMCLOUD = "ebwizardry:stormcloud";
    private static final String HEAL = "ebwizardry:heal_ally";
    private static final String WITHDRAW = "ancientspellcraft:withdraw_life";
    private static final String SPIRIT = "summon_spirit_wolf";
    private static final double LANE = 6.5;
    private static final Field INDIRECT_MINION = ReflectionHelper.findField(IndirectMinionDamage.class, "minion");
    private static Trial active;

    public static void run(MinecraftServer server, ICommandSender sender, String[] args) throws Exception {
        WorldServer world = server.getWorld(0);
        String action = args[1];
        if ("restore".equals(action)) {
            restore(world);
            return;
        }
        if ("act".equals(action) || "collect".equals(action)) {
            require(active != null && active.name.equals(args[2]), "Wrong armed spell case");
            Trial trial = active;
            if ("act".equals(action)) {
                try { trial.act(); } catch (Exception | Error failure) {
                    active = null;
                    trial.close();
                    throw failure;
                }
            } else {
                active = null;
                try { trial.collect(); } finally { trial.close(); }
            }
            return;
        }
        EntityPlayerMP player = server.getPlayerList().getPlayerByUsername(args[3]);
        require(player != null && !player.isDead && player.world == world, "A joined living player is required");
        if ("setup".equals(action)) {
            loadArena(world);
            NativeFixture.floor(world, 0, 48);
            world.setSpawnPoint(new BlockPos(12, 4, 6));
            NativeFixture.equipPlayer(player, 8.5);
            for (String id : Arrays.asList(MAGE, RAY, PARALYSIS, FIRESTORM, STORMCLOUD, HEAL, WITHDRAW)) {
                output("SPELL_REGISTERED id=" + spell(id).getRegistryName() + " implementation=" + spell(id).getClass().getName());
            }
            output("SPELL_ARENA_READY");
        } else if ("save".equals(action)) {
            save(world, player);
        } else {
            require(active == null, "A spell case is already armed");
            Trial trial = new Trial(world, player, args[2], Boolean.parseBoolean(args[4]));
            try {
                trial.prepare();
                active = trial;
            } catch (Exception | Error failure) {
                trial.close();
                throw failure;
            }
        }
    }

    private static final class Trial {
        final WorldServer world;
        final EntityPlayerMP player;
        final String name;
        final boolean protection;
        final List<Entity> cleanup = new ArrayList<>();
        final List<DamageSource> frontAttacks = new ArrayList<>();
        final List<DamageSource> laterAttacks = new ArrayList<>();
        final List<DamageSource> peerAttacks = new ArrayList<>();
        EntityLivingBase front;
        EntityLivingBase later;
        EntityLivingBase peer;
        EntityLiving mage;
        EntityStormcloud cloud;
        State initialFront;
        State initialLater;
        State initialPeer;
        float ownerStart;
        int deaths;
        int rewards;
        long startTick;
        long removedAt = -1;
        int expectedExpiry;
        int cloudInitialLifetime;
        double firstCloudMotionX = Double.NaN;
        int victimKnockbacks;
        boolean nativeEligible;
        IItemHandlerModifiable baubles;
        ItemStack previousRing;
        BlockPos flame;

        Trial(WorldServer world, EntityPlayerMP player, String name, boolean protection) {
            this.world = world;
            this.player = player;
            this.name = name;
            this.protection = protection;
        }

        void prepare() throws Exception {
            NativeFixture.equipPlayer(player, 8.5);
            MinecraftForge.EVENT_BUS.register(this);
            switch (name) {
                case "ray":
                case "environment":
                    front = alternateSummon(world, player, cleanup, 12.5);
                    break;
                case "heal":
                    front = summoned(world, player, player, "ebwizardry:summon_zombie", null, cleanup, 11.5);
                    front.setHealth(8);
                    break;
                case "ray-owner":
                    front = summoned(world, player, player, "ebwizardry:summon_zombie", null, cleanup, 12.5);
                    break;
                case "ray-ordinary":
                    front = ordinary(world, cleanup, 12.5, LANE);
                    break;
                case "mixed":
                case "mixed-owner":
                case "linger":
                case "linger-owner":
                case "linger-ring":
                    front = name.endsWith("-owner")
                            ? summoned(world, player, player, "ebwizardry:summon_zombie", null, cleanup, 11.5)
                            : alternateSummon(world, player, cleanup, 11.5);
                    later = ordinary(world, cleanup, 10.5, 7.7);
                    break;
                case "mage":
                    mage = mage(world, player, player, cleanup, 8.5);
                    NativeFixture.equipPlayer(player, 13.5);
                    front = player;
                    later = ordinary(world, cleanup, 13.5, 6.5);
                    position(later, 14.5, 13.5);
                    peer = alternateSummon(world, player, cleanup, 18.5);
                    position(peer, 18.5, 13.5);
                    break;
                case "npc-mage": {
                    EntityVillager npc = new EntityVillager(world);
                    npc.setNoAI(true);
                    position(npc, 3.5, LANE);
                    require(world.spawnEntity(npc), "NPC caster spawn failed");
                    cleanup.add(npc);
                    mage = mage(world, npc, player, cleanup, 8.5);
                    NativeFixture.equipPlayer(player, 13.5);
                    front = player;
                    break;
                }
                case "cost":
                    front = summoned(world, player, player, "ebwizardry:summon_zombie", null, cleanup, 11.5);
                    later = summoned(world, player, player, "ebwizardry:summon_skeleton", null, cleanup, 12.5);
                    later.setHealth(0.5F);
                    later.setItemStackToSlot(EntityEquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
                    player.setHealth(5);
                    break;
                case "dismiss":
                    front = OriginChecks.checkSpawn(world, player, SPIRIT, EntityWolf.class, false);
                    cleanup.add(front);
                    break;
                case "expiry":
                    front = mage(world, player, player, cleanup, 12.5);
                    int lifetime = ((ISummonedCreature) front).getLifetime();
                    require(lifetime > 24, "Native lifetime unavailable");
                    front.ticksExisted = lifetime - 20;
                    expectedExpiry = 21;
                    output("SPELL_EXPIRY_INPUT lifetime=" + lifetime + " actorAge=" + front.ticksExisted + " uuid=" + id(front));
                    break;
                default:
                    throw new IllegalArgumentException("Unknown spell case " + name);
            }
            if (name.startsWith("ray")) {
                front.setAbsorptionAmount(1);
                front.setItemStackToSlot(EntityEquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE, 1, 11));
                front.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
            }
            initialFront = new State(front);
            if (later != null) initialLater = new State(later);
            if (peer != null) initialPeer = new State(peer);
            ownerStart = player.getHealth();
            output("SPELL_READY case=" + name + " protects=" + protection + " caster=" + id(player)
                    + " mage=" + id(mage) + " front=" + id(front) + " later=" + id(later)
                    + " peer=" + id(peer) + " frontState=" + initialFront + " laterState=" + initialLater);
        }

        void act() throws Exception {
            startTick = world.getTotalWorldTime();
            switch (name) {
                case "ray":
                case "ray-owner":
                case "ray-ordinary":
                    double initialX = front.motionX;
                    double initialZ = front.motionZ;
                    cast(world, player, front, PARALYSIS);
                    boolean rayShouldHit = "ray-ordinary".equals(name) || !protection;
                    double rayImpulse = Math.hypot(front.motionX - initialX, front.motionZ - initialZ);
                    output("SPELL_RAY_MOTION case=" + name + " victim=" + id(front) + " actor=" + id(player)
                            + " beforeX=" + initialX + " beforeZ=" + initialZ + " afterX=" + front.motionX
                            + " afterZ=" + front.motionZ + " impulse=" + rayImpulse + " events=" + victimKnockbacks);
                    require((rayImpulse > 0.05) == rayShouldHit && (victimKnockbacks > 0) == rayShouldHit,
                            "Native Paralysis knockback disagreed with the protected and allowed cases");
                    break;
                case "heal":
                    cast(world, player, front, HEAL);
                    break;
                case "environment":
                    flame = front.getPosition();
                    world.setBlockState(flame, Blocks.FIRE.getDefaultState());
                    require(world.getBlockState(flame).getBlock() == Blocks.FIRE, "The actual environmental fire block is absent");
                    front.move(MoverType.SELF, 0.03, 0.0, 0.0);
                    break;
                case "mixed":
                case "mixed-owner":
                    nativeEligible = AllyDesignationSystem.isValidTarget(player, front);
                    require(name.endsWith("-owner") || nativeEligible, "Native Firestorm must allow the non-owner target");
                    cast(world, player, null, FIRESTORM);
                    break;
                case "linger":
                case "linger-owner":
                case "linger-ring":
                    player.connection.setPlayerLocation(8.5, 4, LANE, -90, 63);
                    boolean ring = "linger-ring".equals(name);
                    if (ring) equipStormRing();
                    require(ItemArtefact.isArtefactActive(player, WizardryItems.ring_stormcloud) == ring,
                            "Stormcloud ring is not in the intended native state");
                    cast(world, player, null, STORMCLOUD);
                    require(cloud != null && id(player).equals(String.valueOf(cloud.getOwnerId()))
                            && cloud.getCaster() == player, "Original Stormcloud must retain the actual caster");
                    nativeEligible = cloud.isValidTarget(front);
                    require(name.endsWith("-owner") || nativeEligible, "Native Stormcloud must allow the non-owner target");
                    AxisAlignedBB activeArea = cloud.getEntityBoundingBox().expand(0, -10, 0);
                    double centerX = (activeArea.minX + activeArea.maxX) * 0.5;
                    double centerZ = (activeArea.minZ + activeArea.maxZ) * 0.5;
                    position(front, centerX + (ring ? 0.18 : 0.45), centerZ);
                    position(later, centerX - (ring ? 1.1 : 0.45), centerZ);
                    require(activeArea.intersects(front.getEntityBoundingBox()) && activeArea.intersects(later.getEntityBoundingBox()),
                            "Both victims must be inside the native cloud damage volume");
                    front.ticksExisted = 148;
                    later.ticksExisted = ring ? 143 : 148;
                    cloudInitialLifetime = cloud.lifetime;
                    NativeFixture.equipPlayer(player, 2.5);
                    output("SPELL_CLOUD uuid=" + id(cloud) + " caster=" + cloud.getOwnerId() + " life=" + cloud.lifetime
                            + " activeArea=" + activeArea + " front=" + id(front) + " later=" + id(later));
                    break;
                case "mage":
                case "npc-mage":
                    require(((ISpellCaster) mage).getSpells().contains(spell(RAY)), "Native mage spell list does not contain the ray");
                    double motionX = player.motionX;
                    double motionZ = player.motionZ;
                    cast(world, mage, player, RAY);
                    boolean shouldHit = "npc-mage".equals(name) || !protection;
                    double impulse = Math.hypot(player.motionX - motionX, player.motionZ - motionZ);
                    output("SPELL_MAGE_MOTION case=" + name + " victim=" + id(player) + " actor=" + id(mage)
                            + " beforeX=" + motionX + " beforeZ=" + motionZ + " afterX=" + player.motionX
                            + " afterZ=" + player.motionZ + " impulse=" + impulse + " events=" + victimKnockbacks);
                    // Lightning Ray restores motion after the wrapped hit. The event reveals the native attempt.
                    require(impulse < 0.00001 && (victimKnockbacks > 0) == shouldHit,
                            "Native knockback attempt or final ray motion disagreed with the protection");
                    if (later != null) {
                        State aimedPlayer = new State(front);
                        output("SPELL_MAGE_AIM_RESULT case=" + name + " before=" + initialFront + " after=" + aimedPlayer);
                        require(initialFront.loss(aimedPlayer) != protection && !frontAttacks.isEmpty(),
                                "The aimed mage ray did not reach the required protected player control");
                        NativeFixture.equipPlayer(player, 3.5);
                        position(later, 13.5, LANE);
                        cast(world, mage, later, RAY);
                        position(later, 13.5, 13.5);
                        position(peer, 13.5, LANE);
                        cast(world, mage, peer, RAY);
                    }
                    break;
                case "cost":
                    cast(world, player, null, WITHDRAW);
                    State afterCost = new State(front);
                    require(!front.isDead && initialFront.loss(afterCost) && later.isDead && player.getHealth() > ownerStart,
                            "The native spell cost or owner benefit did not occur");
                    output("SPELL_COST_RESULT front=" + id(front) + " survivor=" + afterCost + " removed="
                            + id(later) + " ownerHealth=" + player.getHealth() + " deaths=" + deaths + " rewards=" + rewards);
                    player.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.WOODEN_SWORD));
                    player.attackTargetEntityWithCurrentItem(front);
                    require(afterCost.loss(new State(front)) != protection, "Ordinary player damage disagreed with the active switch");
                    break;
                case "dismiss":
                    cleanup.add(OriginChecks.checkSpawn(world, player, SPIRIT, EntityWolf.class, false));
                    break;
                case "expiry":
                    require(front.isEntityAlive(), "Mage did not begin the expiry window alive");
                    break;
                default:
                    throw new IllegalStateException(name);
            }
        }

        void collect() throws Exception {
            State end = new State(front);
            State endLater = later == null ? null : new State(later);
            output("SPELL_RESULT case=" + name + " before=" + initialFront + " after=" + end
                    + " laterBefore=" + initialLater + " laterAfter=" + endLater + " ownerBefore=" + ownerStart
                    + " ownerAfter=" + player.getHealth() + " removedAt=" + removedAt + " start=" + startTick
                    + " deaths=" + deaths + " rewards=" + rewards + " originalFront=" + sources(frontAttacks)
                    + " originalLater=" + sources(laterAttacks) + " nativeEligible=" + nativeEligible);
            switch (name) {
                case "ray":
                case "ray-owner":
                case "ray-ordinary":
                    boolean hit = "ray-ordinary".equals(name) || !protection;
                    ItemStack equipped = front.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
                    output("SPELL_RAY_ARMOR case=" + name + " item=" + equipped.getItem().getRegistryName()
                            + " beforeDamage=" + initialFront.armor + " afterDamage=" + end.armor);
                    // Vanilla mob armor affects damage but its native damageArmor method does not wear the item.
                    require((end.health < initialFront.health) == hit && (end.absorption < initialFront.absorption) == hit
                                    && (end.effects > initialFront.effects) == hit && equipped.getItem() == Items.IRON_CHESTPLATE
                                    && initialFront.armor == 11 && end.armor == initialFront.armor
                                    && !frontAttacks.isEmpty() == hit,
                            "Direct ray damage/status baseline did not distinguish the protection");
                    break;
                case "heal":
                    require(end.health > initialFront.health && frontAttacks.isEmpty(), "Native non-damaging heal was not applied");
                    break;
                case "environment":
                    require(initialFront.loss(end) && frontAttacks.stream().anyMatch(
                            damage -> "inFire".equals(damage.damageType) || "onFire".equals(damage.damageType)),
                            "Real environmental fire was not reached");
                    break;
                case "mixed":
                case "mixed-owner":
                    boolean expectedIgnition = nativeEligible && !protection;
                    require(end.fire == expectedIgnition && endLater.fire && initialLater.loss(endLater),
                            "Firestorm did not leave the expected individual ignitions");
                    require(initialFront.loss(end) == expectedIgnition, "Protected Firestorm victim took damage or baseline missed");
                    break;
                case "linger":
                case "linger-owner":
                case "linger-ring":
                    require(initialFront.loss(end) == (nativeEligible && !protection) && initialLater.loss(endLater),
                            "Stormcloud did not reach the expected mixed native pulse");
                    require(laterAttacks.stream().anyMatch(damage -> damage.getImmediateSource() == cloud && damage.getTrueSource() == player),
                            "Native lingering source did not retain caster credit");
                    if ("linger-ring".equals(name)) {
                        int spentLifetime = cloudInitialLifetime - cloud.lifetime;
                        output("SPELL_CLOUD_RING uuid=" + id(cloud) + " lifeBefore=" + cloudInitialLifetime + " lifeAfter="
                                + cloud.lifetime + " firstMotionX=" + firstCloudMotionX);
                        require(spentLifetime >= 80 && spentLifetime % 40 == 0, "Original per-victim ring duration cost was not reached");
                        require(protection ? firstCloudMotionX < -0.1 : firstCloudMotionX > 0.1,
                                "The ring's cloud did not move toward the expected native target");
                    }
                    break;
                case "mage":
                    State endPeer = new State(peer);
                    require(initialLater.loss(endLater) && hasCredit(laterAttacks, mage, player)
                            && initialPeer.loss(endPeer) && hasCredit(peerAttacks, mage, player),
                            "Allowed native mage hits did not keep actual caster and owner credit");
                    output("SPELL_MAGE_PEER uuid=" + id(peer) + " before=" + initialPeer + " after=" + endPeer
                            + " source=" + sources(peerAttacks));
                    break;
                case "npc-mage":
                    require(initialFront.loss(end) && !frontAttacks.isEmpty(), "NPC mage's caster attack was changed");
                    break;
                case "cost":
                    require(!front.isDead && initialFront.health > end.health && later.isDead
                            && player.getHealth() > ownerStart && deaths == 0 && rewards == 0,
                            "Native cost did not reduce one summon, remove the other and benefit the owner");
                    break;
                case "dismiss":
                    require(front.isDead && deaths == 0 && rewards == 0, "Recast did not dismiss the original spirit cleanly");
                    break;
                case "expiry":
                    require(removedAt - startTick == expectedExpiry && front.isDead && deaths == 0 && rewards == 0,
                            "Native mage expiry changed or never completed");
                    break;
                default:
                    throw new IllegalStateException(name);
            }
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
        public void attack(LivingAttackEvent event) {
            if (event.getEntityLiving() == front) frontAttacks.add(event.getSource());
            if (event.getEntityLiving() == later) laterAttacks.add(event.getSource());
            if (event.getEntityLiving() == peer) peerAttacks.add(event.getSource());
        }

        @SubscribeEvent(receiveCanceled = true)
        public void knockback(LivingKnockBackEvent event) {
            Entity actor = event.getAttacker();
            if (event.getEntityLiving() == front && actor != null && (actor == mage || (name.startsWith("ray") && actor == player))) {
                victimKnockbacks++;
                output("SPELL_NATIVE_KNOCKBACK case=" + name + " victim=" + id(front) + " actor=" + id(actor)
                        + " strength=" + event.getStrength() + " ratioX=" + event.getRatioX()
                        + " ratioZ=" + event.getRatioZ() + " canceled=" + event.isCanceled());
            }
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void applied(LivingHurtEvent event) throws IllegalAccessException {
            if (event.getEntityLiving() == front || event.getEntityLiving() == later || event.getEntityLiving() == peer) {
                output("SPELL_NATIVE_HURT case=" + name + " victim=" + id(event.getEntityLiving())
                        + " amount=" + event.getAmount() + " source=" + source(event.getSource()));
            }
        }

        @SubscribeEvent
        public void joined(EntityJoinWorldEvent event) {
            if (event.getWorld() != world) return;
            if (event.getEntity() instanceof EntityStormcloud && name.startsWith("linger")) {
                cloud = (EntityStormcloud) event.getEntity();
                cleanup.add(cloud);
            }
            if (("cost".equals(name) || "dismiss".equals(name) || "expiry".equals(name))
                    && (event.getEntity() instanceof EntityItem || event.getEntity() instanceof EntityXPOrb)) rewards++;
        }

        @SubscribeEvent
        public void death(LivingDeathEvent event) {
            if (event.getEntityLiving() == front || event.getEntityLiving() == later) deaths++;
        }

        @SubscribeEvent
        public void tick(TickEvent.WorldTickEvent event) {
            if (event.phase != TickEvent.Phase.END || event.world != world || front == null) return;
            if (front.isDead && removedAt < 0) removedAt = world.getTotalWorldTime();
            if (cloud != null && "linger-ring".equals(name) && Double.isNaN(firstCloudMotionX) && cloud.ticksExisted > 0) {
                firstCloudMotionX = cloud.motionX;
            }
        }

        void close() {
            MinecraftForge.EVENT_BUS.unregister(this);
            if (baubles != null && previousRing != null) baubles.setStackInSlot(1, previousRing);
            if (flame != null) world.setBlockToAir(flame);
            for (Entity entity : cleanup) entity.setDead();
            player.extinguish();
            player.clearActivePotions();
            if (player.isEntityAlive()) player.setHealth(player.getMaxHealth());
        }

        private void equipStormRing() throws ReflectiveOperationException {
            Class<?> api = Class.forName("baubles.api.BaublesApi");
            Object handler = api.getMethod("getBaublesHandler", EntityPlayer.class).invoke(null, player);
            Class<?> type = Class.forName("baubles.api.cap.IBaublesItemHandler");
            ItemStack ring = new ItemStack(WizardryItems.ring_stormcloud);
            boolean valid = (Boolean) type.getMethod("isItemValidForSlot", int.class, ItemStack.class, EntityLivingBase.class)
                    .invoke(handler, 1, ring, player);
            require(valid, "Installed Baubles provider rejects the Stormcloud ring in its native slot");
            baubles = (IItemHandlerModifiable) handler;
            previousRing = baubles.getStackInSlot(1).copy();
            baubles.setStackInSlot(1, ring);
            output("SPELL_RING_BOUND provider=" + handler.getClass().getName() + " item=" + ring.getItem().getRegistryName());
        }
    }

    private static final class State {
        final float health;
        final float absorption;
        final boolean fire;
        final int effects;
        final int armor;

        State(EntityLivingBase entity) {
            health = entity.getHealth();
            absorption = entity.getAbsorptionAmount();
            fire = entity.isBurning();
            effects = entity.getActivePotionEffects().size();
            armor = entity.getItemStackFromSlot(EntityEquipmentSlot.CHEST).getItemDamage();
        }

        boolean loss(State next) { return next.health < health || next.absorption < absorption || next.armor > armor; }

        @Override public String toString() {
            return health + "/" + absorption + "/armor=" + armor + "/fire=" + fire + "/effects=" + effects;
        }
    }

    private static EntityLiving alternateSummon(WorldServer world, EntityPlayerMP player, List<Entity> cleanup, double x) {
        require(Arrays.asList(world.getMinecraftServer().getPlayerList().getAvailablePlayerDat()).contains(SECOND_PLAYER.toString()),
                "Alternate native player data is missing");
        EntityLiving summoned = summoned(world, player, player, "ebwizardry:summon_zombie", null, cleanup, x);
        ((ISummonedCreature) summoned).setOwnerId(SECOND_PLAYER);
        inspect(world.getMinecraftServer(), world.getMinecraftServer(), summoned, "AFFECTED");
        return summoned;
    }

    private static EntityLiving mage(WorldServer world, EntityLivingBase summoner, EntityLivingBase target, List<Entity> cleanup, double x) {
        EntityLiving mage = summoned(world, summoner, target, MAGE, MAGE_CLASS, cleanup, x);
        require(mage instanceof ISpellCaster, "Native mage caster contract missing");
        List<Spell> loadout = ((ISpellCaster) mage).getSpells();
        loadout.clear();
        loadout.add(spell(RAY));
        inspect(world.getMinecraftServer(), world.getMinecraftServer(), mage, summoner instanceof EntityPlayerMP ? "AFFECTED" : "EXCLUDED");
        return mage;
    }

    private static EntityLiving summoned(WorldServer world, EntityLivingBase summoner, EntityLivingBase target,
                                          String id, String expectedClass, List<Entity> cleanup, double x) {
        Set<Entity> prior = new HashSet<>(world.loadedEntityList);
        cast(world, summoner, target, id);
        EntityLiving found = null;
        for (Entity entity : world.loadedEntityList) {
            if (prior.contains(entity) || !(entity instanceof EntityLiving) || !(entity instanceof ISummonedCreature)) continue;
            cleanup.add(entity);
            if (found == null && (expectedClass == null || expectedClass.equals(entity.getClass().getName()))
                    && summoner.getUniqueID().equals(((ISummonedCreature) entity).getOwnerId())) {
                found = (EntityLiving) entity;
            } else {
                entity.setDead();
            }
        }
        require(found != null, "Native spell did not produce the expected owned summon: " + id);
        found.setNoAI(true);
        position(found, x, LANE);
        found.setHealth(found.getMaxHealth());
        output("SPELL_SUMMON spell=" + id + " entity=" + id(found) + " owner=" + ((ISummonedCreature) found).getOwnerId());
        return found;
    }

    private static EntityZombie ordinary(WorldServer world, List<Entity> cleanup, double x, double z) {
        EntityZombie entity = new EntityZombie(world);
        position(entity, x, z);
        entity.setNoAI(true);
        require(world.spawnEntity(entity), "Eligible ordinary victim did not join");
        cleanup.add(entity);
        return entity;
    }

    private static void save(WorldServer world, EntityPlayerMP player) throws Exception {
        loadRemote(world);
        NativeFixture.floor(world, 1024, 1036);
        EntityLiving saved = mage(world, player, player, new ArrayList<>(), 1024.5);
        require(((ISummonedCreature) saved).getLifetime() > 40, "Mage unavailable for native save");
        Files.write(savePath(world), Arrays.asList(id(saved), id(player)), StandardCharsets.US_ASCII);
        output("SPELL_SAVE_READY mage=" + id(saved) + " caster=" + id(player)
                + " saved=" + saved.getEntityData().getCompoundTag("wizardrysummontweaks"));
    }

    private static void restore(WorldServer world) throws Exception {
        Path folder = savePath(world).getParent();
        Files.copy(folder.resolve("region/r.2.0.mca"), folder.resolve("wst-spell-region.mca"));
        loadRemote(world);
        List<String> ids = Files.readAllLines(savePath(world), StandardCharsets.US_ASCII);
        Entity saved = world.getEntityFromUuid(UUID.fromString(ids.get(0)));
        require(saved != null && saved.getClass().getName().equals(MAGE_CLASS) && !saved.isDead, "Native saved mage is absent");
        require(world.getMinecraftServer().getPlayerList().getPlayerByUUID(UUID.fromString(ids.get(1))) == null
                && ((ISummonedCreature) saved).getCaster() == null, "Saved mage's caster is present");
        inspect(world.getMinecraftServer(), world.getMinecraftServer(), saved, "AFFECTED");
        require(ids.get(1).equals(saved.getEntityData().getCompoundTag("wizardrysummontweaks").getString("ownerUUID")),
                "Saved mage owner changed");
        output("SPELL_RESTORE_PASS mage=" + id(saved) + " offlineCaster=" + ids.get(1)
                + " nativeOwner=" + ((ISummonedCreature) saved).getOwnerId());
    }

    private static void loadRemote(WorldServer world) {
        for (int x = 61; x <= 67; x++) for (int z = -3; z <= 3; z++) world.getChunk(x, z);
    }

    private static Path savePath(WorldServer world) {
        return world.getSaveHandler().getWorldDirectory().toPath().resolve("wst-spell-ids.txt");
    }

    private static void position(EntityLivingBase entity, double x, double z) {
        entity.setPositionAndUpdate(x, 4, z);
    }

    private static Spell spell(String id) {
        Spell spell = Spell.get(id);
        require(spell != null && id.equals(String.valueOf(spell.getRegistryName())), "Unregistered spell " + id);
        return spell;
    }

    private static void cast(WorldServer world, EntityLivingBase caster, EntityLivingBase target, String id) {
        Spell spell = spell(id);
        boolean returned = caster instanceof EntityPlayerMP
                ? spell.cast(world, (EntityPlayerMP) caster, EnumHand.MAIN_HAND, 0, new SpellModifiers())
                : spell.cast(world, (EntityLiving) caster, EnumHand.MAIN_HAND, 0, target, new SpellModifiers());
        output("SPELL_CAST id=" + spell.getRegistryName() + " caster=" + id(caster) + " intended=" + id(target) + " returned=" + returned);
    }

    private static boolean hasCredit(List<DamageSource> sources, Entity actual, Entity credited) {
        return sources.stream().anyMatch(source -> source instanceof MinionDamage
                && source.getImmediateSource() == actual && source.getTrueSource() == credited);
    }

    private static String sources(List<DamageSource> values) throws IllegalAccessException {
        List<String> result = new ArrayList<>();
        for (DamageSource source : values) result.add(source(source));
        return result.toString();
    }

    private static String source(DamageSource source) throws IllegalAccessException {
        Entity immediate = source.getImmediateSource();
        Entity actor = source instanceof IndirectMinionDamage ? (Entity) INDIRECT_MINION.get(source)
                : source instanceof MinionDamage ? immediate : source.getTrueSource();
        return source.getClass().getSimpleName() + ":" + source.damageType + ":immediate=" + id(immediate)
                + ":actor=" + id(actor) + ":credit=" + id(source.getTrueSource());
    }

    private static String id(Entity entity) {
        return entity == null ? "none" : entity.getUniqueID().toString();
    }
}
