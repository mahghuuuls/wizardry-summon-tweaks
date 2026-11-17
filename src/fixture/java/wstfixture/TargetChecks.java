package wstfixture;

import electroblob.wizardry.entity.living.EntityZombieMinion;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static wstfixture.IdentityFixture.loadArena;
import static wstfixture.IdentityFixture.output;
import static wstfixture.IdentityFixture.require;

/** Records raw target writes and actual target selection in a disposable Wizardry world. */
public final class TargetChecks {
    private static final String MINION_TAG = "wst_target_minion";
    private static final String ENEMY_TAG = "wst_target_enemy";
    private static final Field ATTACK_TARGET = ReflectionHelper.findField(EntityLiving.class, "attackTarget", "field_70696_bz");
    private static final Method PRE_AI = ReflectionHelper.findMethod(EntityLiving.class, "updateEntityActionState", "func_70626_be");

    public static void run(MinecraftServer server, ICommandSender sender, String[] args) throws Exception {
        EntityPlayerMP owner = CommandBase.getPlayer(server, sender, args[2]);
        WorldServer world = owner.getServerWorld();
        boolean protectPlayers = Boolean.parseBoolean(args[3]);
        switch (args[1]) {
            case "aim":
                aim(owner, world);
                break;
            case "observe-click":
                observeClick(owner, world, protectPlayers);
                break;
            case "observe-recovery":
                observeRecovery(owner, world, protectPlayers);
                break;
            case "assignment":
                checkAssignment(server, owner, world, protectPlayers);
                break;
            case "stale":
                checkStale(owner, world, protectPlayers);
                break;
            default:
                throw new IllegalArgumentException("Unknown target scenario " + args[1]);
        }
    }

    private static void aim(EntityPlayerMP owner, WorldServer world) {
        loadArena(world);
        owner.setGameType(GameType.SURVIVAL);
        owner.connection.setPlayerLocation(8.5, 4, 8.5, 0, 0);
        owner.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        EntityZombieMinion minion = nativeMinion(owner, world);
        BlockPos floorCenter = new BlockPos(minion.posX, minion.posY - 1, minion.posZ);
        for (BlockPos floor : BlockPos.getAllInBox(floorCenter.add(-3, 0, -3), floorCenter.add(3, 0, 6))) {
            world.setBlockState(floor, Blocks.STONE.getDefaultState());
            for (int height = 1; height <= 3; height++) world.setBlockToAir(floor.up(height));
        }
        owner.connection.setPlayerLocation(minion.posX, minion.posY, minion.posZ - 1.8, 0, 0);
        minion.setNoGravity(true);
        minion.updateBlocked = true;
        minion.addTag(MINION_TAG);
        output("TARGET_CLICK_READY victim=" + minion.getUniqueID() + " entityId=" + minion.getEntityId()
                + " owner=" + owner.getUniqueID() + " hp=" + minion.getHealth());
    }

    private static void observeClick(EntityPlayerMP owner, WorldServer world, boolean protectPlayers) {
        EntityZombieMinion minion = tagged(world, MINION_TAG, EntityZombieMinion.class);
        output("TARGET_PROVOKE_OWNER actor=" + owner.getUniqueID() + " victim=" + minion.getUniqueID()
                + " hp=" + minion.getHealth() + " revenge=" + id(minion.getRevengeTarget()) + " target=" + id(raw(minion)));
        require(minion.getHealth() < minion.getMaxHealth(), "the joined player's melee did not provoke the minion");
        require(minion.getRevengeTarget() == (protectPlayers ? null : owner), "wrong owner revenge state");
        require(raw(minion) == null, "target must be empty before natural selection resumes");
        require(!minion.getTargetSelector().apply(owner), "native owner selector restriction changed");
        minion.motionX = minion.motionY = minion.motionZ = 0;
        minion.onGround = true;
        if (protectPlayers) {
            EntityZombie enemy = enemy(world, minion.posX, minion.posY, minion.posZ + 3);
            enemy.addTag(ENEMY_TAG);
            output("TARGET_RECOVERY_READY actor=" + minion.getUniqueID() + " enemy=" + enemy.getUniqueID()
                    + " hp=" + enemy.getHealth() + " range=" + minion.getDistance(enemy));
            require(minion.getTargetSelector().apply(enemy), "native selector did not accept the enemy");
        }
        minion.setNoGravity(false);
        minion.setNoAI(false);
        minion.updateBlocked = false;
    }

    private static void observeRecovery(EntityPlayerMP owner, WorldServer world, boolean protectPlayers) {
        EntityZombieMinion minion = tagged(world, MINION_TAG, EntityZombieMinion.class);
        try {
            if (protectPlayers) {
                EntityZombie enemy = tagged(world, ENEMY_TAG, EntityZombie.class);
                output("TARGET_RECOVERY_RESULT actor=" + minion.getUniqueID() + " target=" + id(raw(minion))
                        + " enemy=" + enemy.getUniqueID() + " hp=" + enemy.getHealth() + " range=" + minion.getDistance(enemy));
                if (raw(minion) != enemy || enemy.getHealth() == enemy.getMaxHealth()) {
                    output("TARGET_RECOVERY_DIAGNOSTIC minion=" + minion.getPositionVector() + " enemy=" + enemy.getPositionVector()
                            + " timer=" + minion.ticksExisted + " disabledAI=" + minion.isAIDisabled()
                            + " sameTeam=" + minion.isOnSameTeam(enemy) + " hasPath=" + !minion.getNavigator().noPath()
                            + " suitable=" + net.minecraft.entity.ai.EntityAITarget.isSuitableTarget(minion, enemy, false, true));
                }
                require(raw(minion) == enemy && enemy.getHealth() < enemy.getMaxHealth(), "native enemy pursuit and hit did not occur");
                world.removeEntityDangerously(enemy);
            } else {
                output("TARGET_PROVIDER_OWNER_RESTRICTION actor=" + minion.getUniqueID() + " target=" + id(raw(minion))
                        + " owner=" + owner.getUniqueID());
                require(raw(minion) == null, "disabled protection bypassed vanilla's owner target restriction");
            }
        } finally {
            world.removeEntityDangerously(minion);
            owner.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
        }
    }

    private static void checkAssignment(MinecraftServer server, EntityPlayerMP owner, WorldServer world,
                                        boolean protectPlayers) throws Exception {
        EntityZombieMinion minion = nativeMinion(owner, world);
        EntityZombie enemy = enemy(world, 0.5, 4, 5.5);
        EntityPlayerMP other = MeleeChecks.secondPlayer(server, world);
        WriteTrace trace = new WriteTrace(minion);
        MinecraftForge.EVENT_BUS.register(trace);
        try {
            checkWrite(minion, owner, "owner", protectPlayers, trace);
            checkWrite(minion, other, "other", protectPlayers, trace);
            other.setGameType(GameType.CREATIVE);
            checkWrite(minion, other, "creative", protectPlayers, trace);
            other.setGameType(GameType.SURVIVAL);
            // Model a still-referenced player after that player moved away from the summon's world.
            EntityPlayerMP away = MeleeChecks.secondPlayer(server, server.getWorld(-1));
            require(away.world != minion.world, "off-dimension target control needs a distinct world");
            checkWrite(minion, away, "off-dimension-player", protectPlayers, trace);
            minion.setAttackTarget(null);
            minion.ticksExisted = 1; // Establish a distinct revenge timer for this in-tick fixture.
            float before = minion.getHealth();
            minion.attackEntityFrom(DamageSource.causePlayerDamage(other), 4);
            boolean selected = minion.getTargetSelector().apply(other);
            output("TARGET_PROVOKE_OTHER actor=" + other.getUniqueID() + " hpBefore=" + before
                    + " hpAfter=" + minion.getHealth() + " revenge=" + id(minion.getRevengeTarget())
                    + " selector=" + selected);
            require(minion.getHealth() < before, "non-owner provocation did not land");
            require(minion.getRevengeTarget() == (protectPlayers ? null : other), "wrong native non-owner revenge state");
            require(selected != protectPlayers, "native non-owner selector control failed");
            PRE_AI.invoke(minion);
            output("TARGET_REPRISAL_OTHER victim=" + minion.getUniqueID() + " candidate=" + other.getUniqueID()
                    + " target=" + id(raw(minion)));
            require(raw(minion) == (protectPlayers ? null : other), "native target tasks crossed the wrong player boundary");
            checkWrite(minion, enemy, "ordinary-mob", false, trace);
            enemy.setAttackTarget(owner);
            require(raw(enemy) == owner, "ordinary mob lost its player target");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(trace);
            world.removeEntityDangerously(enemy);
            world.removeEntityDangerously(minion);
        }
    }

    private static void checkWrite(EntityLiving actor, EntityLivingBase candidate, String name, boolean rejected,
                                   WriteTrace trace) {
        actor.setAttackTarget(null);
        trace.targets.clear();
        actor.setAttackTarget(candidate);
        UUID current = id(raw(actor));
        output("TARGET_WRITE case=" + name + " actor=" + actor.getUniqueID() + " candidate=" + candidate.getUniqueID()
                + " rawAfter=" + current + " notified=" + trace.targets);
        require(rejected ? raw(actor) == null && trace.targets.isEmpty()
                        : raw(actor) == candidate && trace.targets.equals(Collections.singletonList(current)),
                name + " target write crossed the wrong boundary");
    }

    private static void checkStale(EntityPlayerMP owner, WorldServer world, boolean protectPlayers) throws Exception {
        EntityZombieMinion loaded = new EntityZombieMinion(world);
        loaded.setPosition(1.5, 4, 0.5);
        loaded.setNoAI(true);
        try {
            loaded.setAttackTarget(owner);
            require(raw(loaded) == owner, "unrecognized minion did not accept stale setup");
            UUID prior = id(raw(loaded));
            loaded.setCaster(owner);
            require(world.spawnEntity(loaded), "stale spawn failed");
            output("TARGET_STALE_JOIN actor=" + loaded.getUniqueID() + " beforeRecognition=" + prior
                    + " afterRecognition=" + id(raw(loaded)));
            require(raw(loaded) == (protectPlayers ? null : owner), "first recognition did not reconcile the raw target");
        } finally {
            world.removeEntityDangerously(loaded);
        }

        NullVetoMinion competing = new NullVetoMinion(world);
        competing.setPosition(1.5, 4, 0.5);
        competing.setAttackTarget(owner);
        competing.setCaster(owner);
        competing.refuseNull = true;
        PRE_AI.invoke(competing);
        output("TARGET_STALE_PRE_AI actor=" + competing.getUniqueID() + " seeded=" + owner.getUniqueID()
                + " vetoes=" + competing.vetoes + " rawAtVeto=" + id(competing.atVeto)
                + " beforeAi=" + id(competing.beforeAi));
        require(competing.beforeAi == (protectPlayers ? null : owner), "stale target reached AI");
        require(protectPlayers ? competing.vetoes == 1 && competing.atVeto == owner : competing.vetoes == 0,
                "competing setter authority was not exercised in the protected control");
    }

    private static EntityZombieMinion nativeMinion(EntityPlayerMP owner, WorldServer world) {
        List<EntityLivingBase> spawned = new ArrayList<>();
        EntityZombieMinion minion = MeleeChecks.summon(owner, world, spawned, "ebwizardry:summon_zombie", EntityZombieMinion.class);
        require(spawned.size() == 1, "expected one native Zombie minion");
        return minion;
    }

    private static EntityZombie enemy(WorldServer world, double x, double y, double z) {
        EntityZombie enemy = new EntityZombie(world);
        enemy.setNoAI(true);
        enemy.setPosition(x, y, z);
        enemy.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.MAX_HEALTH).setBaseValue(40);
        enemy.setHealth(40);
        require(world.spawnEntity(enemy), "enemy spawn failed");
        return enemy;
    }

    private static <T extends Entity> T tagged(WorldServer world, String tag, Class<T> type) {
        for (Entity entity : world.loadedEntityList) {
            if (type.isInstance(entity) && entity.getTags().contains(tag)) return type.cast(entity);
        }
        throw new IllegalStateException("Missing fixture " + tag);
    }

    private static EntityLivingBase raw(EntityLiving actor) {
        try {
            return (EntityLivingBase) ATTACK_TARGET.get(actor);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cannot inspect the actual target field", failure);
        }
    }

    private static UUID id(Entity entity) {
        return entity == null ? null : entity.getUniqueID();
    }

    public static final class WriteTrace {
        private final EntityLiving actor;
        private final List<UUID> targets = new ArrayList<>();

        WriteTrace(EntityLiving actor) {
            this.actor = actor;
        }

        @SubscribeEvent
        public void targetChanged(LivingSetAttackTargetEvent event) {
            if (event.getEntityLiving() == actor) targets.add(id(raw(actor)));
        }
    }

    private static final class NullVetoMinion extends EntityZombieMinion {
        boolean refuseNull;
        int vetoes;
        EntityLivingBase atVeto;
        EntityLivingBase beforeAi;

        NullVetoMinion(WorldServer world) { super(world); }

        @Override
        public void setAttackTarget(EntityLivingBase target) {
            if (refuseNull && target == null) {
                vetoes++;
                atVeto = raw(this);
                return;
            }
            super.setAttackTarget(target);
        }

        @Override
        protected void despawnEntity() {
            // Vanilla calls this before updating either AI selector.
            beforeAi = raw(this);
            super.despawnEntity();
        }
    }
}
