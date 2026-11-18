package wstfixture;

import com.mojang.authlib.GameProfile;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.CommandResultStats;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.LogManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/** Native actions inspect source state; fault injection separately exercises the tracker component. */
@Mod(modid = "wstfixture", name = "WST Identity Test Fixture", version = "1.0", acceptableRemoteVersions = "*")
public final class IdentityFixture extends CommandBase {
    public static final UUID SECOND_PLAYER = UUID.nameUUIDFromBytes("wst-second-player".getBytes(StandardCharsets.UTF_8));

    @Mod.EventHandler public void starting(FMLServerStartingEvent event) { event.registerServerCommand(this); }
    @Override public String getName() { return "wstfixture"; }
    @Override public String getUsage(ICommandSender sender) { return "wstfixture <case> [args]"; }
    @Override public int getRequiredPermissionLevel() { return 2; }

    @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) throw new CommandException(getUsage(sender));
        try {
            WorldServer world = server.worlds[0];
            switch (args[0]) {
                case "faults":
                    FaultChecks.run(server);
                    break;
                case "melee":
                    Class.forName("wstfixture.MeleeChecks")
                            .getMethod("run", MinecraftServer.class, ICommandSender.class, String[].class)
                            .invoke(null, server, sender, args);
                    break;
                case "targets":
                    Class.forName("wstfixture.TargetChecks")
                            .getMethod("run", MinecraftServer.class, ICommandSender.class, String[].class)
                            .invoke(null, server, sender, args);
                    break;
                case "origins":
                    Class.forName("wstfixture.OriginChecks")
                            .getMethod("run", MinecraftServer.class, ICommandSender.class, String[].class)
                            .invoke(null, server, sender, args);
                    break;
                case "rewards":
                    Class.forName("wstfixture.RewardChecks")
                            .getMethod("run", MinecraftServer.class, ICommandSender.class, String[].class)
                            .invoke(null, server, sender, args);
                    break;
                case "projectiles":
                    Class.forName("wstfixture.ProjectileChecks")
                            .getMethod("run", MinecraftServer.class, ICommandSender.class, String[].class)
                            .invoke(null, server, sender, args);
                    break;
                case "spells":
                    Class.forName("wstfixture.SpellChecks")
                            .getMethod("run", MinecraftServer.class, ICommandSender.class, String[].class)
                            .invoke(null, server, sender, args);
                    break;
                case "load":
                    loadArena(world);
                    output("ARENA_LOADED");
                    break;
                case "empty": {
                    world.getChunk(new BlockPos(0, 4, 3));
                    EntityWolf wolf = new EntityWolf(world);
                    wolf.setPosition(0, 4, 3);
                    wolf.setNoAI(true);
                    wolf.setTamed(true);
                    wolf.setOwnerId(server.getPlayerList().getPlayers().isEmpty() ? SECOND_PLAYER
                            : server.getPlayerList().getPlayers().get(0).getUniqueID());
                    wolf.setCustomNameTag("wst_ordinary");
                    require(world.spawnEntity(wolf), "ordinary control spawn rejected");
                    inspect(server, sender, wolf, "EXCLUDED");
                    output("EMPTY_PASS");
                    break;
                }
                case "native":
                case "transfer":
                case "prepare-save":
                case "restored":
                case "dimension":
                case "chunk-out":
                case "chunk-unload":
                case "chunk-check":
                case "chunk-load":
                    Class.forName("wstfixture.WizardryChecks").getMethod("run", MinecraftServer.class, ICommandSender.class, String[].class)
                            .invoke(null, server, sender, args);
                    break;
                case "player-inspect":
                    inspect(server, sender, getPlayer(server, sender, args[1]), "EXCLUDED");
                    output("PLAYER_INSPECT_PASS");
                    break;
                case "ambiguous": {
                    Entity original = named(server, "wst_ordinary");
                    WorldServer other = server.getWorld(-1);
                    other.getChunk(0, 0);
                    EntityArmorStand duplicate = new EntityArmorStand(other, 0.5, 80, 0.5);
                    duplicate.setUniqueId(original.getUniqueID());
                    require(other.spawnEntity(duplicate), "duplicate UUID control could not spawn in second dimension");
                    Capture capture = new Capture(server);
                    require(server.getCommandManager().executeCommand(capture, "wizardrysummontweaks inspect " + original.getUniqueID()) == 0
                            && capture.text.toString().contains("ambiguous"), "ambiguous loaded UUID accepted");
                    duplicate.setDead();
                    output("AMBIGUITY_PASS " + capture.text);
                    break;
                }
                case "leave":
                    require(server.isSinglePlayer(), "leave is only an integrated-client test action");
                    Class.forName("wstfixture.ClientChecks").getMethod("leave").invoke(null);
                    output("LEAVE_SCHEDULED");
                    break;
                case "inspect": {
                    Entity entity = named(server, args[1]);
                    inspect(server, sender, entity, args[2]);
                    output("INSPECT_PASS name=" + args[1] + " uuid=" + entity.getUniqueID());
                    break;
                }
                case "owner": {
                    Entity entity = named(server, args[1]);
                    Capture capture = new Capture(server);
                    server.getCommandManager().executeCommand(capture, "wizardrysummontweaks inspect " + entity.getUniqueID());
                    require(capture.text.toString().contains("Owner=PLAYER uuid=" + SECOND_PLAYER), "wrong confirmed owner " + capture.text);
                    require(entity.serializeNBT().getCompoundTag("ForgeData").getCompoundTag("wizardrysummontweaks")
                            .getString("ownerUUID").equals(SECOND_PLAYER.toString()), "confirmed owner not persisted");
                    output("OWNER_PASS ticksExisted=" + entity.ticksExisted + " " + capture.text);
                    break;
                }
                case "stored-unresolved": {
                    Entity entity = named(server, "wst_keep");
                    NBTTagCompound saved = entity.serializeNBT().getCompoundTag("ForgeData").getCompoundTag("wizardrysummontweaks");
                    output("TRANSFER_STATE ticksExisted=" + entity.ticksExisted + " saved=" + saved);
                    require(entity.ticksExisted > 0, "transferred entity has never ticked");
                    require(saved.getString("ownerUUID").equals(SECOND_PLAYER.toString())
                            && saved.getString("ownerKind").equals("UNRESOLVED"), "changed unknown owner not persisted");
                    break;
                }
                case "permissions": {
                    FakePlayer nonOperator = FakePlayerFactory.get(world, new GameProfile(SECOND_PLAYER, "WstSecondPlayer"));
                    require(!nonOperator.canUseCommand(2, "wizardrysummontweaks"), "fixture sender is privileged");
                    Capture denied = new Capture(nonOperator);
                    int count = server.getCommandManager().executeCommand(denied, "wizardrysummontweaks inspect " + named(server, args[1]).getUniqueID());
                    require(count == 0 && !denied.text.toString().contains("Classification="), "non-operator inspection accepted");
                    Capture invalid = new Capture(server);
                    require(server.getCommandManager().executeCommand(invalid, "wizardrysummontweaks inspect invalid") == 0, "invalid UUID accepted");
                    Capture missing = new Capture(server);
                    require(server.getCommandManager().executeCommand(missing, "wizardrysummontweaks inspect 00000000-0000-0000-0000-000000000001") == 0, "unloaded UUID accepted");
                    output("PERMISSIONS_PASS denied=" + denied.text + " invalid=" + invalid.text + " unloaded=" + missing.text);
                    break;
                }
                case "ray": {
                    EntityPlayerMP player = getPlayer(server, sender, args[1]);
                    world.getChunk(2, 0);
                    player.setPositionAndUpdate(32.5, 4, 0.5);
                    player.rotationYaw = 0;
                    player.rotationPitch = 0;
                    EntityArmorStand near = stand(world, "wst_ray_near", 3.5);
                    EntityArmorStand far = stand(world, "wst_ray_far", 6.5);
                    require(server.getCommandManager().executeCommand(player, "wizardrysummontweaks inspect") == 1, "native player ray selection failed");
                    Capture capture = new Capture(player);
                    require(server.getCommandManager().executeCommand(capture, "wizardrysummontweaks inspect") == 1, "wrapped player ray selection failed: " + capture.text);
                    require(capture.text.toString().contains(near.getUniqueID().toString()), "ray did not select nearest");
                    world.setBlockState(new BlockPos(32, 5, 2), net.minecraft.init.Blocks.STONE.getDefaultState());
                    require(server.getCommandManager().executeCommand(new Capture(player), "wizardrysummontweaks inspect") == 0, "ray crossed solid obstruction");
                    world.setBlockToAir(new BlockPos(32, 5, 2));
                    near.setDead(); far.setDead();
                    EntityArmorStand outside = stand(world, "wst_ray_outside", 10.5);
                    require(server.getCommandManager().executeCommand(new Capture(player), "wizardrysummontweaks inspect") == 0, "ray exceeded eight blocks");
                    outside.setDead();
                    EntityArmorStand boundary = stand(world, "wst_ray_boundary", 9);
                    boundary.setPosition(32.5, 4, 0.5 + 8 + boundary.width / 2.0 + boundary.getCollisionBorderSize());
                    require(server.getCommandManager().executeCommand(new Capture(player), "wizardrysummontweaks inspect") == 1, "exactly eight-block intersection rejected");
                    boundary.setDead();
                    net.minecraft.entity.item.EntityItem item = new net.minecraft.entity.item.EntityItem(world, 32.5, 5.5, 3.5,
                            new net.minecraft.item.ItemStack(net.minecraft.init.Items.STICK));
                    item.setNoGravity(true);
                    item.setPickupDelay(32767);
                    require(world.spawnEntity(item), "item ray control spawn rejected");
                    Capture itemResult = new Capture(player);
                    require(server.getCommandManager().executeCommand(itemResult, "wizardrysummontweaks inspect") == 1
                            && itemResult.text.toString().contains(item.getUniqueID().toString()), "non-collidable entity selection failed");
                    item.setDead();
                    output("RAY_PASS");
                    break;
                }
                case "config": {
                    Path file = Paths.get("config/wizardrysummontweaks.cfg").toAbsolutePath();
                    String content;
                    if (args[1].equals("invalid")) content = "general {\nB:suppressSummonDrops=false\nB:protectPlayers=invalid\nB:protectSummons=false\n}\n";
                    else if (args[1].equals("false")) content = "general {\nB:suppressSummonDrops=false\nB:protectPlayers=false\nB:protectSummons=false\n}\n";
                    else if (args[1].equals("malformed")) content = "general {\nB:protectPlayers=false\n";
                    else content = "general {\nB:suppressSummonDrops=true\nB:protectPlayers=true\nB:protectSummons=true\n}\n";
                    Files.write(file, content.getBytes(StandardCharsets.UTF_8));
                    output("CONFIG_WRITTEN mode=" + args[1] + " file=" + file);
                    break;
                }
                case "values": {
                    Capture capture = new Capture(server);
                    Entity entity = named(server, args[1]);
                    server.getCommandManager().executeCommand(capture, "wizardrysummontweaks inspect " + entity.getUniqueID());
                    require(capture.text.toString().contains("suppressSummonDrops=" + args[2]), "wrong reward active value " + capture.text);
                    require(capture.text.toString().contains("protectPlayers=" + args[3]), "wrong player active value " + capture.text);
                    require(capture.text.toString().contains("protectSummons=" + args[4]), "wrong summon active value " + capture.text);
                    output("VALUES_PASS " + capture.text);
                    break;
                }
                default: throw new IllegalArgumentException("Unknown case " + args[0]);
            }
        } catch (Exception failure) {
            Throwable cause = failure instanceof java.lang.reflect.InvocationTargetException ? failure.getCause() : failure;
            LogManager.getLogger("WSTFixture").error("Fixture failed", cause);
            throw new CommandException("WST fixture failed: " + cause.toString());
        }
    }

    static void loadArena(WorldServer world) {
        // Forge checks a 32-block neighborhood before ticking a non-player entity.
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) world.getChunk(x, z);
    }

    private EntityArmorStand stand(WorldServer world, String name, double z) {
        EntityArmorStand stand = new EntityArmorStand(world, 32.5, 4, z);
        stand.setNoGravity(true);
        stand.setCustomNameTag(name);
        require(world.spawnEntity(stand), "ray control spawn rejected");
        return stand;
    }

    public static Entity named(MinecraftServer server, String name) {
        if ("wst_keep".equals(name)) {
            UUID uuid = keeperId(server);
            for (WorldServer world : server.worlds) {
                Entity entity = world.getEntityFromUuid(uuid);
                if (entity != null && !entity.isDead) return entity;
            }
            throw new IllegalStateException("Keeper UUID is not loaded: " + uuid);
        }
        for (WorldServer world : server.worlds) for (Entity entity : world.loadedEntityList) {
            if (!entity.isDead && name.equals(entity.getCustomNameTag())) return entity;
        }
        throw new IllegalStateException("Missing loaded fixture " + name);
    }

    public static UUID keeperId(MinecraftServer server) {
        try {
            return UUID.fromString(new String(Files.readAllBytes(server.worlds[0].getSaveHandler().getWorldDirectory()
                    .toPath().resolve("wst-kept-uuid.txt")), StandardCharsets.UTF_8));
        } catch (java.io.IOException failure) { throw new IllegalStateException("Missing fixture UUID record", failure); }
    }

    public static void inspect(MinecraftServer server, ICommandSender sender, Entity entity, String status) {
        NBTTagCompound before = entity.writeToNBT(new NBTTagCompound()).copy();
        Capture capture = new Capture(sender);
        int count = server.getCommandManager().executeCommand(capture, "wizardrysummontweaks inspect " + entity.getUniqueID());
        require(count == 1, "inspection command failed: " + capture.text);
        require(capture.text.toString().contains("Classification=" + status), "wrong classification " + capture.text);
        require(before.equals(entity.writeToNBT(new NBTTagCompound())), "inspection changed entity NBT");
        output("RAW uuid=" + entity.getUniqueID() + " before=" + before + " inspect=" + capture.text);
    }

    public static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    public static void output(String text) { LogManager.getLogger("WSTFixture").info("WST_" + text); }

    public static final class Capture implements ICommandSender {
        private final ICommandSender delegate;
        public final StringBuilder text = new StringBuilder();
        public Capture(ICommandSender delegate) { this.delegate = delegate; }
        @Override public String getName() { return delegate.getName(); }
        @Override public ITextComponent getDisplayName() { return delegate.getDisplayName(); }
        @Override public void sendMessage(ITextComponent message) { text.append(message.getUnformattedText()).append(" | "); }
        @Override public boolean canUseCommand(int level, String command) { return delegate.canUseCommand(level, command); }
        @Override public BlockPos getPosition() { return delegate.getPosition(); }
        @Override public Vec3d getPositionVector() { return delegate.getPositionVector(); }
        @Override public World getEntityWorld() { return delegate.getEntityWorld(); }
        @Override public Entity getCommandSenderEntity() { return delegate.getCommandSenderEntity(); }
        @Override public boolean sendCommandFeedback() { return false; }
        @Override public void setCommandStat(CommandResultStats.Type type, int amount) { delegate.setCommandStat(type, amount); }
        @Override public MinecraftServer getServer() { return delegate.getServer(); }
    }
}
