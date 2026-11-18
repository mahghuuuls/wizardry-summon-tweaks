package wstfixture;

import electroblob.wizardry.entity.living.EntityZombieMinion;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Enchantments;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static wstfixture.IdentityFixture.getPlayer;
import static wstfixture.IdentityFixture.inspect;
import static wstfixture.IdentityFixture.output;
import static wstfixture.IdentityFixture.require;

/** Native gameplay observations outside the benchmark and the Overworld. */
public final class QualificationChecks {
    private static final int X = 1024;
    private static final int Y = 60;

    public static void run(MinecraftServer server, ICommandSender sender, String[] args) throws Exception {
        if ("released".equals(args[1])) {
            released();
            return;
        }
        if ("arm-stop".equals(args[1])) {
            armStop();
            return;
        }
        EntityPlayerMP player = getPlayer(server, sender, args[2]);
        switch (args[1]) {
            case "enter":
                enter(server, player);
                break;
            case "nether":
                interact(server, player, UUID.fromString(args[3]));
                break;
            case "leave":
                require(player.dimension == -1, "Actual Nether player must leave the isolated test area");
                player.changeDimension(0, (destination, moved, yaw) -> moved.setLocationAndAngles(2.5, 4, 6.5, yaw, 0));
                require(player.dimension == 0 && player.world == server.getWorld(0), "Original departure did not reach the Overworld");
                output("QUALIFICATION_LEFT_NETHER player=" + player.getUniqueID());
                break;
            default:
                throw new IllegalArgumentException(args[1]);
        }
    }

    private static void enter(MinecraftServer server, EntityPlayerMP player) throws Exception {
        require(player.dimension == 0, "Expected the same joined Overworld player");
        WorldServer nether = server.getWorld(-1);
        for (int x = 62; x <= 67; x++) {
            for (int z = -3; z <= 3; z++) nether.getChunk(x, z);
        }
        for (int x = X; x <= X + 16; x++) {
            for (int z = 2; z <= 12; z++) {
                nether.setBlockState(new BlockPos(x, Y - 1, z), Blocks.STONE.getDefaultState());
                for (int y = Y; y <= Y + 4; y++) nether.setBlockToAir(new BlockPos(x, y, z));
            }
        }
        player.changeDimension(-1, (destination, moved, yaw) -> moved.setLocationAndAngles(X + 5.5, Y, 6.5, yaw, 0));
        require(player.dimension == -1 && player.world == nether, "Native travel did not load the intended Nether world");
        player.setGameType(GameType.SURVIVAL);
        NativeFixture.clearSpawnImmunity(player);
        NativeFixture.resetProjectileVictim(player);
        output("QUALIFICATION_ENTERED_NETHER player=" + player.getUniqueID() + " dimension=" + player.dimension);
    }

    private static void interact(MinecraftServer server, EntityPlayerMP player, UUID offlineOwner) throws Exception {
        WorldServer world = player.getServerWorld();
        require(player.dimension == -1 && world.getChunkProvider().getLoadedChunk(64, 0) != null
                && world.getPlayerChunkMap().getEntry(64, 0) != null, "Expected a player-tracked remote Nether chunk");
        require(!offlineOwner.equals(player.getUniqueID()) && world.getPlayerEntityByUUID(offlineOwner) == null
                && Arrays.asList(server.getPlayerList().getAvailablePlayerDat()).contains(offlineOwner.toString()),
                "The absent owner must have an established native player save");
        List<EntityLivingBase> summoned = new ArrayList<>();
        EntityZombieMinion actor = MeleeChecks.summon(player, world, summoned, "ebwizardry:summon_zombie", EntityZombieMinion.class);
        require(summoned.size() == 1, "One native Nether minion is required");
        actor.setPositionAndUpdate(X + 7.5, Y, 6.5);
        actor.setOwnerId(offlineOwner);
        actor.onUpdate();
        require(actor.dimension == -1 && actor.getCaster() == null && offlineOwner.equals(actor.getOwnerId())
                && actor.ticksExisted < actor.getLifetime(), "Native actor did not retain the actual absent owner");
        inspect(server, server, actor, "AFFECTED");
        NativeFixture.clearSpawnImmunity(player);
        NativeFixture.resetProjectileVictim(player);
        NativeFixture.resetProjectileVictim(actor);
        float playerHealth = player.getHealth();
        actor.setAttackTarget(player);
        require(actor.getAttackTarget() == null, "Player target persisted in the native Nether actor");
        actor.attackEntityAsMob(player);
        require(player.getHealth() == playerHealth && player.getAbsorptionAmount() == 2 && !player.isBurning()
                && player.motionX == 0 && player.motionZ == 0, "Native Nether player was affected by a blocked hit");
        float summonHealth = actor.getHealth();
        NativeFixture.preparePlayerAttack(player, Enchantments.FIRE_ASPECT);
        player.attackTargetEntityWithCurrentItem(actor);
        require(actor.getHealth() == summonHealth && actor.getAbsorptionAmount() == 2 && !actor.isBurning()
                && actor.motionX == 0 && actor.motionZ == 0, "Native Nether summon was affected by a blocked hit");
        EntityZombie enemy = new EntityZombie(world);
        enemy.setNoAI(true);
        enemy.setPosition(X + 9.5, Y, 6.5);
        require(world.spawnEntity(enemy), "Native Nether target control did not join");
        try {
            actor.setAttackTarget(enemy);
            float before = enemy.getHealth();
            actor.attackEntityAsMob(enemy);
            float after = enemy.getHealth();
            require(actor.getAttackTarget() == enemy && after < before, "Eligible ordinary Nether combat did not land");
            actor.hurtResistantTime = 0;
            enemy.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(8);
            float beforeReturn = actor.getHealth();
            require(enemy.attackEntityAsMob(actor) && actor.getHealth() < beforeReturn, "Eligible Nether counterattack did not land");
            output("QUALIFICATION_NETHER actor=" + actor.getUniqueID() + " owner=" + offlineOwner + " player=" + player.getUniqueID()
                    + " lifetime=" + actor.getLifetime() + " playerHp=" + playerHealth + "/" + player.getHealth()
                    + " actorProtectedHp=" + summonHealth + "/" + beforeReturn + " enemyHp=" + before + "/" + after
                    + " allowedActorHp=" + beforeReturn + "/" + actor.getHealth() + " dimension=" + world.provider.getDimension());
        } finally {
            enemy.setDead();
        }
    }

    private static void released() {
        WorldServer existing = DimensionManager.getWorld(-1);
        boolean loaded = existing != null && existing.getChunkProvider().getLoadedChunk(64, 0) != null;
        int localPlayers = existing == null ? 0 : existing.playerEntities.size();
        output("QUALIFICATION_CHUNK_RELEASED dimensionPresent=" + (existing != null)
                + " chunkLoaded=" + loaded + " localPlayers=" + localPlayers);
        require(!loaded && localPlayers == 0, "Native chunk did not release after the last player left");
    }

    private static void armStop() throws Exception {
        require(IdentityFixture.afterServerStop == null, "Stop observation is already armed");
        Object mod = Loader.instance().getIndexedModList().get("wizardrysummontweaks").getMod();
        String before = cacheState(mod);
        require(before.contains("active=true") && read(mod, "attacks") != null
                && !((Collection<?>) read(read(read(mod, "tracker"), "players"), "known")).isEmpty(),
                "The real server session caches were never populated");
        output("QUALIFICATION_CACHE_BEFORE " + before);
        IdentityFixture.afterServerStop = () -> {
            try {
                String after = cacheState(mod);
                output("QUALIFICATION_CACHE_STOP " + after);
                require(after.equals("active=false playerCount=0 providers=0 quarantined=0 reported=0 handlersCleared=true"),
                        "Retained participants or handlers remain after Forge server stop");
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot read the stopped native session", failure);
            }
        };
    }

    private static String cacheState(Object mod) throws ReflectiveOperationException {
        Object tracker = read(mod, "tracker");
        return "active=" + read(tracker, "active")
                + " playerCount=" + ((Collection<?>) read(read(tracker, "players"), "known")).size()
                + " providers=" + ((Map<?, ?>) read(tracker, "providers")).size()
                + " quarantined=" + ((Map<?, ?>) read(tracker, "failedEntities")).size()
                + " reported=" + ((Collection<?>) read(tracker, "reportedProviders")).size()
                + " handlersCleared=" + (read(mod, "attacks") == null && read(mod, "rewards") == null
                    && read(mod, "targets") == null && read(mod, "combat") == null);
    }

    private static Object read(Object object, String name) throws ReflectiveOperationException {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
}
