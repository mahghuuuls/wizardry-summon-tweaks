package wstfixture;

import com.mojang.authlib.GameProfile;
import electroblob.wizardry.entity.living.ISummonedCreature;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.MinionDamage;
import electroblob.wizardry.util.MagicDamage.DamageType;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.IEventListener;
import net.minecraftforge.fml.common.eventhandler.ListenerList;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static wstfixture.IdentityFixture.*;

/** Exercises the game melee entry points with live entity state and scoped Forge observations. */
public final class MeleeChecks {
    private static final String CLIENT_TARGET_TAG = "wst_client_melee";
    private static final Field RESPAWN_IMMUNITY = ReflectionHelper.findField(EntityPlayerMP.class,
            "respawnInvulnerabilityTicks", "field_147101_bU");
    private static final Field BUS_ID = ReflectionHelper.findField(EventBus.class, "busID");
    private static final Field MELEE_COOLDOWN = ReflectionHelper.findField(EntityLivingBase.class,
            "ticksSinceLastSwing", "field_184617_aD");

    public static void run(MinecraftServer server, ICommandSender sender, String[] args) throws Exception {
        EntityPlayerMP player = CommandBase.getPlayer(server, sender, args[2]);
        WorldServer world = player.getServerWorld();
        String caseName = args[1];
        loadArena(world);
        player.setGameType(GameType.SURVIVAL);
        RESPAWN_IMMUNITY.setInt(player, 0);
        player.setPositionAndUpdate(0.5, 4.0, 0.5);
        boolean protectPlayers = Boolean.parseBoolean(args[3]);
        boolean protectSummons = Boolean.parseBoolean(args[4]);
        if (caseName.equals("client-aim")) {
            aimClient(player, world);
            return;
        }
        if (caseName.equals("client-inspect")) {
            inspectClientHit(player, world, protectSummons);
            return;
        }
        output("MELEE_ATTEMPT case=" + caseName + " tick=" + world.getTotalWorldTime());
        if (caseName.equals("feedback-control")) {
            ClientFeedbackProbe.verify(player);
            return;
        }
        List<EntityLivingBase> spawned = new ArrayList<>();
        try {
            EntityZombie minion = summon(player, world, spawned, "ebwizardry:summon_zombie", EntityZombie.class);
            place(minion, 1.5, 0.5);
            EntityPlayerMP other = secondPlayer(server, world);
            switch (caseName) {
                case "owner-sword":
                case "other-sword":
                case "owner-sword-repeat": {
                    EntityPlayerMP actor = caseName.equals("other-sword") ? other : player;
                    preparePlayerAttack(actor, Enchantments.FIRE_ASPECT);
                    Runnable attack = () -> actor.attackTargetEntityWithCurrentItem(minion);
                    Trace sword = check(caseName, minion, protectSummons,
                            protectSummons ? ClientFeedbackProbe.silent(player, caseName, attack) : attack);
                    if (protectSummons) require(sword.attacks.isEmpty(), "sword guard allowed the native hurt attempt to begin");
                    else require(minion.isBurning(), "unprotected Fire Aspect control did not ignite the minion");
                    break;
                }
                case "owner-sword-event":
                case "other-sword-event": {
                    EntityPlayerMP actor = caseName.equals("owner-sword-event") ? player : other;
                    Trace fallback = check(caseName, minion, protectSummons,
                            () -> minion.attackEntityFrom(DamageSource.causePlayerDamage(actor), 8));
                    require(fallback.attacks.size() == 1, "direct player attack did not reach Forge");
                    break;
                }
                case "summon-to-owner":
                case "summon-to-other":
                case "summon-to-owner-repeat":
                    expectProviderDamage(caseName, minion, caseName.equals("summon-to-other") ? other : player, player, protectPlayers);
                    require(!((ISummonedCreature) minion).isValidTarget(player), "Wizardry lost its original owner target restriction");
                    break;
                case "already-rewritten":
                    check(caseName, other, protectPlayers,
                            () -> other.attackEntityFrom(new MinionDamage("mob", minion, player, DamageType.MAGIC, false), 8));
                    break;
                case "wrapped-thorns": {
                    Trace thorns = check(caseName, other, false,
                            () -> other.attackEntityFrom(DamageSource.causeThornsDamage(minion), 8));
                    require(thorns.hurt.get(0) instanceof MinionDamage && thorns.hurt.get(0).getImmediateSource() == minion,
                            "native Thorns did not reach Wizardry's rewrite");
                    break;
                }
                case "spider-poison": {
                    EntitySpider spider = summon(player, world, spawned, "ebwizardry:spider_swarm", EntitySpider.class);
                    place(spider, 1.5, 0.5);
                    expectProviderDamage(caseName, spider, other, player, protectPlayers);
                    require(other.isPotionActive(MobEffects.POISON) != protectPlayers,
                            "Wizardry poison callback did not match the positive/blocked control");
                    break;
                }
                case "summon-to-summon": {
                    EntityZombie peer = summon(player, world, spawned, "ebwizardry:summon_zombie", EntityZombie.class);
                    place(peer, 2.0, 0.5);
                    expectProviderDamage(caseName, minion, peer, player, false);
                    break;
                }
                case "enemy-to-summon": {
                    EntityZombie enemy = enemy(world, spawned);
                    check(caseName, minion, false, () -> enemy.attackEntityAsMob(minion));
                    break;
                }
                case "fall":
                    check(caseName, minion, false, () -> minion.attackEntityFrom(DamageSource.FALL, 8));
                    break;
                case "sweep-secondary":
                    checkSweep(player, enemy(world, spawned), minion, enemy(world, spawned), protectSummons);
                    break;
                case "npc-owned-to-player": {
                    EntityZombie enemy = enemy(world, spawned);
                    ((ISummonedCreature) minion).setCaster(enemy);
                    expectProviderDamage(caseName, minion, player, enemy, false);
                    break;
                }
                case "unknown-owner-to-player": {
                    ((ISummonedCreature) minion).setOwnerId(UUID.fromString("cb7a0d61-d084-4d0d-9811-ba6ead96dfa9"));
                    Trace unknown = check(caseName, player, false, () -> minion.attackEntityAsMob(player));
                    require(unknown.attacks.size() == 1 && unknown.hurt.get(0).getTrueSource() == minion,
                            "unknown owner changed the original source");
                    break;
                }
                case "wizardry-first":
                case "protection-first":
                    withOrder(minion, player, caseName.equals("wizardry-first"), protectPlayers);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown melee case " + caseName);
            }
        } finally {
            for (EntityLivingBase entity : spawned) world.removeEntityDangerously(entity);
            player.setItemStackToSlot(EntityEquipmentSlot.CHEST, ItemStack.EMPTY);
            player.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
            player.setAbsorptionAmount(0);
            player.extinguish();
            player.clearActivePotions();
        }
    }

    private static void aimClient(EntityPlayerMP player, WorldServer world) throws IllegalAccessException {
        List<EntityLivingBase> spawned = new ArrayList<>();
        EntityZombie target = summon(player, world, spawned, "ebwizardry:summon_zombie", EntityZombie.class);
        require(spawned.size() == 1, "client melee needs one native target");
        player.connection.setPlayerLocation(0.5, 4.0, 0.5, 0.0F, 0.0F);
        place(target, 0.5, 2.2);
        target.setNoGravity(true);
        target.updateBlocked = true;
        reset(target);
        preparePlayerAttack(player, Enchantments.FIRE_ASPECT);
        require(target.addTag(CLIENT_TARGET_TAG), "could not tag the client melee target");
        output("MELEE_CLIENT_READY entityId=" + target.getEntityId() + " uuid=" + target.getUniqueID()
                + " tick=" + world.getTotalWorldTime() + " before=" + state(target));
    }

    private static void inspectClientHit(EntityPlayerMP player, WorldServer world, boolean blocked) {
        EntityZombie target = null;
        for (Entity entity : world.loadedEntityList) {
            if (entity instanceof EntityZombie && entity.getTags().contains(CLIENT_TARGET_TAG)) {
                require(target == null, "multiple tagged client melee targets");
                target = (EntityZombie) entity;
            }
        }
        require(target != null, "client melee target is missing");
        try {
            output("MELEE_CLIENT_RESULT entityId=" + target.getEntityId() + " uuid=" + target.getUniqueID()
                    + " tick=" + world.getTotalWorldTime() + " after=" + state(target));
            if (blocked) {
                require(target.getHealth() == target.getMaxHealth() && target.getAbsorptionAmount() == 2
                        && target.getItemStackFromSlot(EntityEquipmentSlot.CHEST).getItemDamage() == 0
                        && !target.isBurning() && target.getActivePotionEffects().isEmpty()
                        && target.motionX == 0 && target.motionY == 0 && target.motionZ == 0,
                        "joined owner's blocked click changed live state");
            } else {
                require((target.getHealth() < target.getMaxHealth() || target.getAbsorptionAmount() < 2)
                        && target.isBurning(), "joined owner's allowed click did not land and ignite");
            }
        } finally {
            world.removeEntityDangerously(target);
            player.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
        }
    }

    private static EntityZombie enemy(WorldServer world, List<EntityLivingBase> spawned) {
        EntityZombie enemy = new EntityZombie(world);
        enemy.setNoAI(true);
        place(enemy, 1.5, 0.5);
        require(world.spawnEntity(enemy), "enemy spawn rejected");
        spawned.add(enemy);
        enemy.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(8);
        return enemy;
    }

    private static EntityPlayerMP secondPlayer(MinecraftServer server, WorldServer world)
            throws IllegalAccessException {
        EntityPlayerMP other = new EntityPlayerMP(server, world, new GameProfile(SECOND_PLAYER, "WstOtherMelee"),
                new PlayerInteractionManager(world));
        // Native server player damage and attack methods, with an in-process connection that discards packets.
        new NetHandlerPlayServer(server, new NetworkManager(EnumPacketDirection.SERVERBOUND), other) {
            @Override public void sendPacket(Packet<?> packet) {}
        };
        other.setGameType(GameType.SURVIVAL);
        RESPAWN_IMMUNITY.setInt(other, 0);
        place(other, 0.5, 0.5);
        // Direct native actions in this server tick do not need a world join or a second socket.
        return other;
    }

    private static <T extends EntityLivingBase> T summon(EntityPlayerMP caster, WorldServer world,
                                                        List<EntityLivingBase> cleanup, String spellId, Class<T> type) {
        Set<Entity> existing = new HashSet<>(world.loadedEntityList);
        Spell spell = Spell.get(spellId);
        require(spell != null && spell.cast(world, caster, EnumHand.MAIN_HAND, 0, new SpellModifiers()), spellId + " native cast failed");
        T first = null;
        for (Entity entity : world.loadedEntityList) {
            if (existing.contains(entity) || !type.isInstance(entity) || !(entity instanceof ISummonedCreature)) continue;
            T summon = type.cast(entity);
            require(caster.getUniqueID().equals(((ISummonedCreature) summon).getOwnerId()), "native cast owner mismatch");
            ((net.minecraft.entity.EntityLiving) summon).setNoAI(true);
            summon.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(8);
            cleanup.add(summon);
            if (first == null) first = summon;
        }
        require(first != null, spellId + " native cast did not spawn the expected minion");
        return first;
    }

    private static void place(EntityLivingBase entity, double x, double z) {
        entity.setPosition(x, 4, z);
    }

    private static void preparePlayerAttack(EntityPlayerMP player, Enchantment enchantment) throws IllegalAccessException {
        // Set the same full charge a player would get by waiting between attacks.
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.addEnchantment(enchantment, 2);
        player.setHeldItem(EnumHand.MAIN_HAND, sword);
        MELEE_COOLDOWN.setInt(player, 30);
        player.setSprinting(false);
        player.onGround = true;
        player.fallDistance = 0;
    }

    private static void reset(EntityLivingBase target) {
        target.clearActivePotions();
        target.setHealth(target.getMaxHealth());
        target.setAbsorptionAmount(2);
        target.hurtResistantTime = 0;
        target.hurtTime = 0;
        target.extinguish();
        target.motionX = target.motionY = target.motionZ = 0;
        target.setItemStackToSlot(EntityEquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
    }

    private static String state(EntityLivingBase target) {
        NBTTagCompound nbt = target.writeToNBT(new NBTTagCompound());
        return "hp=" + target.getHealth() + ",absorption=" + target.getAbsorptionAmount()
                + ",chestDamage=" + target.getItemStackFromSlot(EntityEquipmentSlot.CHEST).getItemDamage()
                + ",fire=" + nbt.getShort("Fire") + ",effects=" + target.getActivePotionEffects()
                + ",velocity=" + target.motionX + "/" + target.motionY + "/" + target.motionZ;
    }

    private static Trace check(String name, EntityLivingBase target, boolean blocked, Runnable attack) throws Exception {
        reset(target);
        String before = state(target);
        Trace trace = new Trace(target);
        trace.observe(attack);
        String after = state(target);
        output("MELEE_CASE " + name + " victim=" + target.getUniqueID() + " before=" + before + " after=" + after
                + " attack=" + trace.details + " hurt=" + trace.hurt.size());
        if (blocked) {
            require(before.equals(after) && trace.hurt.isEmpty(), name + " blocked hit changed victim state");
        } else {
            require(target.getHealth() < target.getMaxHealth() || target.getAbsorptionAmount() < 2,
                    name + " allowed positive control did not consume health or absorption");
            require(trace.hurt.size() == 1, name + " must apply exactly one native hurt event");
        }
        return trace;
    }

    private static void expectProviderDamage(String name, EntityLivingBase minion, EntityLivingBase target,
                                             EntityLivingBase owner, boolean blocked) throws Exception {
        Runnable attack = () -> minion.attackEntityAsMob(target);
        if (blocked && target == owner && target instanceof EntityPlayerMP) {
            attack = ClientFeedbackProbe.silent((EntityPlayerMP) target, name, attack);
        }
        Trace trace = check(name, target, blocked, attack);
        require(trace.attacks.size() == (blocked ? 1 : 2), name + " unexpected original/rewritten event count");
        if (!blocked) {
            DamageSource applied = trace.hurt.get(0);
            require(applied instanceof MinionDamage && applied.getImmediateSource() == minion
                    && applied.getTrueSource() == owner && trace.attacks.contains(applied),
                    name + " lost Wizardry's original rewritten source identity or credit");
        }
    }

    private static void checkSweep(EntityPlayerMP player, EntityZombie primary, EntityZombie minion,
                                   EntityZombie secondary, boolean blocked) throws Exception {
        place(primary, 1.5, 0.5);
        place(minion, 1.5, 1.1);
        place(secondary, 1.8, 1.2);
        reset(primary);
        reset(secondary);
        String secondaryBefore = state(secondary);
        preparePlayerAttack(player, Enchantments.SWEEPING);
        Trace sweep = check("sweep-secondary", minion, blocked, () -> player.attackTargetEntityWithCurrentItem(primary));
        output("MELEE_SWEEP_ELIGIBLE uuid=" + secondary.getUniqueID() + " before=" + secondaryBefore + " after=" + state(secondary));
        if (blocked) require(sweep.attacks.isEmpty(), "blocked secondary entered the sweep attack");
        require(primary.getHealth() < primary.getMaxHealth() || primary.getAbsorptionAmount() < 2,
                "sweep primary positive control did not land");
        require(secondary.getHealth() < secondary.getMaxHealth() || secondary.getAbsorptionAmount() < 2,
                "sweep skipped damage for an eligible secondary");
        require(secondary.motionX != 0 || secondary.motionY != 0 || secondary.motionZ != 0,
                "sweep skipped knockback for an eligible secondary");
    }

    private static void withOrder(EntityZombie minion, EntityPlayerMP target, boolean wizardryFirst,
                                  boolean blocked) throws Exception {
        int bus = BUS_ID.getInt(MinecraftForge.EVENT_BUS);
        ListenerList list = new LivingAttackEvent(target, DamageSource.causeMobDamage(minion), 8).getListenerList();
        IEventListener wizardry = find(list, bus, "ISummonedCreature onLivingAttackEvent");
        IEventListener mod = find(list, bus, "combat.MeleeProtection@", " onLivingAttack");
        boolean original = index(list, bus, wizardry) < index(list, bus, mod);
        arrange(list, bus, wizardry, mod, wizardryFirst);
        try {
            require((index(list, bus, wizardry) < index(list, bus, mod)) == wizardryFirst, "actual bus order did not change");
            expectProviderDamage(wizardryFirst ? "wizardry-first" : "protection-first", minion, target, target, blocked);
            output("MELEE_ORDER_PASS wizardryFirst=" + wizardryFirst + " protected=" + blocked);
        } finally {
            arrange(list, bus, wizardry, mod, original);
        }
    }

    private static IEventListener find(ListenerList list, int bus, String... parts) {
        for (IEventListener listener : list.getListeners(bus)) {
            boolean matches = true;
            for (String part : parts) matches &= listener.toString().contains(part);
            if (matches) return listener;
        }
        throw new IllegalStateException("missing listener " + java.util.Arrays.toString(parts));
    }

    private static int index(ListenerList list, int bus, IEventListener listener) {
        return java.util.Arrays.asList(list.getListeners(bus)).indexOf(listener);
    }

    private static void arrange(ListenerList list, int bus, IEventListener wizardry, IEventListener mod, boolean wizardryFirst) {
        list.unregister(bus, wizardry);
        list.unregister(bus, mod);
        list.register(bus, EventPriority.HIGHEST, wizardryFirst ? wizardry : mod);
        list.register(bus, EventPriority.HIGHEST, wizardryFirst ? mod : wizardry);
    }

    private static final class Trace {
        final EntityLivingBase target;
        final List<DamageSource> attacks = new ArrayList<>();
        final List<DamageSource> hurt = new ArrayList<>();
        final List<String> details = new ArrayList<>();

        Trace(EntityLivingBase target) {
            this.target = target;
        }

        void observe(Runnable action) throws Exception {
            int bus = BUS_ID.getInt(MinecraftForge.EVENT_BUS);
            ListenerList attackList = new LivingAttackEvent(target, DamageSource.GENERIC, 1).getListenerList();
            ListenerList hurtList = new LivingHurtEvent(target, DamageSource.GENERIC, 1).getListenerList();
            IEventListener attackTrace = event -> {
                LivingAttackEvent hit = (LivingAttackEvent) event;
                if (hit.getEntityLiving() != target) return;
                DamageSource source = hit.getSource();
                attacks.add(source);
                details.add(source.getClass().getSimpleName() + ":" + source.damageType
                        + " immediate=" + id(source.getImmediateSource()) + " credit=" + id(source.getTrueSource())
                        + " canceled=" + hit.isCanceled());
            };
            IEventListener hurtTrace = event -> {
                LivingHurtEvent hit = (LivingHurtEvent) event;
                if (hit.getEntityLiving() == target && !hit.isCanceled()) hurt.add(hit.getSource());
            };
            attackList.register(bus, EventPriority.LOWEST, attackTrace);
            hurtList.register(bus, EventPriority.LOWEST, hurtTrace);
            try {
                action.run();
            } finally {
                attackList.unregister(bus, attackTrace);
                hurtList.unregister(bus, hurtTrace);
            }
        }

        private static String id(Entity entity) {
            return entity == null ? "none" : entity.getUniqueID().toString();
        }
    }
}
