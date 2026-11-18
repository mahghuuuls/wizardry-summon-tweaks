package wstfixture;

import electroblob.wizardry.data.WizardData;
import electroblob.wizardry.entity.living.EntityZombieMinion;
import electroblob.wizardry.util.AllyDesignationSystem;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.Loader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static wstfixture.IdentityFixture.getPlayer;
import static wstfixture.IdentityFixture.inspect;
import static wstfixture.IdentityFixture.loadArena;
import static wstfixture.IdentityFixture.output;
import static wstfixture.IdentityFixture.require;

/** Observes server policy through vanilla and provider actions in isolated sessions. */
public final class AuthorityChecks {
    private static final String VALUES_SUBJECT = "wst_authority_values";
    private static final String WAND = "ebwizardry:magic_wand";
    private static final String BLADE = "Authority summon sword";
    private static final String HELMET = "Authority summon helmet";
    private static final int REMOTE_X = 1024;

    public static void run(MinecraftServer server, ICommandSender sender, String[] args) throws Exception {
        EntityPlayerMP player = getPlayer(server, sender, args[2]);
        WorldServer world = player.getServerWorld();
        switch (args[1]) {
            case "setup":
                setup(world, player);
                break;
            case "relations":
                relations(server, world, player, Boolean.parseBoolean(args[3]));
                break;
            case "save":
                save(world, player, Boolean.parseBoolean(args[3]));
                break;
            case "restore":
                restored(world, player, Boolean.parseBoolean(args[3]), false);
                break;
            case "removed":
                restored(world, player, false, true);
                break;
            case "plain-quit":
                quitPlainClient(world);
                break;
            default:
                throw new IllegalArgumentException(args[1]);
        }
    }

    private static void setup(WorldServer world, EntityPlayerMP player) throws Exception {
        loadArena(world);
        NativeFixture.floor(world, -8, 55);
        NativeFixture.equipPlayer(player, 8.5);
        if (world.loadedEntityList.stream().noneMatch(entity -> VALUES_SUBJECT.equals(entity.getCustomNameTag()))) {
            EntityArmorStand observer = new EntityArmorStand(world, 35.5, 4, 6.5);
            observer.setCustomNameTag(VALUES_SUBJECT);
            observer.setNoGravity(true);
            require(world.spawnEntity(observer), "The inspection control did not enter the world");
        }
        output("AUTHORITY_SETUP player=" + player.getUniqueID() + " name=" + player.getName()
                + " dedicated=" + world.getMinecraftServer().isDedicatedServer()
                + " product=" + Loader.isModLoaded("wizardrysummontweaks")
                + " toolkit=" + Loader.isModLoaded("agenttesttoolkit")
                + " wizardry=" + Loader.isModLoaded("ebwizardry"));
    }

    private static void relations(MinecraftServer server, WorldServer world, EntityPlayerMP player, boolean protection) throws Exception {
        NativeFixture.equipPlayer(player, 8.5);
        EntityPlayerMP other = NativeFixture.secondPlayer(server, world);
        NativeFixture.equipPlayer(other, 10.5);
        List<EntityLivingBase> summoned = new ArrayList<>();
        EntityZombieMinion actor = MeleeChecks.summon(player, world, summoned,
                "ebwizardry:summon_zombie", EntityZombieMinion.class);
        actor.setPositionAndUpdate(9.5, 4, 6.5);
        Scoreboard board = world.getScoreboard();
        ScorePlayerTeam left = board.createTeam("wst_left");
        ScorePlayerTeam right = board.createTeam("wst_right");
        left.setAllowFriendlyFire(false);
        right.setAllowFriendlyFire(false);
        boolean originalPvp = server.isPVPEnabled();
        WizardData wizard = WizardData.get(player);
        require(wizard != null && !wizard.isPlayerAlly(other), "The native ally control already exists");
        try {
            board.addPlayerToTeam(player.getName(), left.getName());
            board.addPlayerToTeam(other.getName(), right.getName());
            server.setAllowPvp(true);
            relation("different-teams-pvp-on", actor, player, other, protection, true);
            server.setAllowPvp(false);
            relation("different-teams-pvp-off", actor, player, other, protection, false);
            server.setAllowPvp(true);
            board.addPlayerToTeam(other.getName(), left.getName());
            relation("same-team-no-friendly-fire", actor, player, other, protection, false);
            board.addPlayerToTeam(other.getName(), right.getName());
            wizard.toggleAlly(other);
            require(AllyDesignationSystem.isPlayerAlly(player, other), "Native Wizardry ally flag was not established");
            relation("wizardry-ally", actor, player, other, protection, true);
            wizard.toggleAlly(other);
            other.setGameType(GameType.CREATIVE);
            boolean creativeProvider = AllyDesignationSystem.isValidTarget(player, other);
            boolean creativeSelection = actor.getTargetSelector().apply(other);
            actor.setAttackTarget(other);
            require(creativeSelection == (creativeProvider && !protection)
                    && actor.getAttackTarget() == (protection ? null : other), "Wrong native creative target eligibility");
            output("AUTHORITY_CREATIVE candidate=" + other.getUniqueID() + " native=" + creativeProvider
                    + " selector=" + creativeSelection + " stored=" + id(actor.getAttackTarget()));
        } finally {
            if (wizard.isPlayerAlly(other)) wizard.toggleAlly(other);
            server.setAllowPvp(originalPvp);
            board.removeTeam(left);
            board.removeTeam(right);
            summoned.forEach(Entity::setDead);
            other.setDead();
        }
        output("AUTHORITY_RELATIONS_PASS protection=" + protection);
    }

    private static void relation(String label, EntityZombieMinion actor, EntityPlayerMP owner,
                                 EntityPlayerMP other, boolean protection, boolean vanillaDamage) throws Exception {
        other.setHealth(other.getMaxHealth());
        other.setAbsorptionAmount(0);
        other.setItemStackToSlot(EntityEquipmentSlot.CHEST, ItemStack.EMPTY);
        other.hurtResistantTime = 0;
        other.extinguish();
        require(!owner.capabilities.disableDamage && !other.capabilities.disableDamage,
                "Survival relation check needs two damageable players");
        boolean canAttack = owner.canAttackPlayer(other);
        NativeFixture.preparePlayerAttack(owner, Enchantments.SHARPNESS);
        float before = other.getHealth();
        owner.attackTargetEntityWithCurrentItem(other);
        boolean landed = other.getHealth() < before;
        require(canAttack == vanillaDamage && landed == vanillaDamage, "Original PvP/friendly-fire control failed for " + label);
        boolean nativeSelection = AllyDesignationSystem.isValidTarget(owner, other);
        boolean actualSelection = actor.getTargetSelector().apply(other);
        require(actualSelection == (nativeSelection && !protection), "Wizardry native target policy changed for " + label);
        actor.setAttackTarget(null);
        actor.setAttackTarget(other);
        require(actor.getAttackTarget() == (protection ? null : other), "Direct summon target authority changed for " + label);
        output("AUTHORITY_RELATION case=" + label + " owner=" + owner.getUniqueID() + " other=" + other.getUniqueID()
                + " pvp=" + owner.getServer().isPVPEnabled() + " vanillaCanAttack=" + canAttack + " before=" + before
                + " after=" + other.getHealth() + " wizardryAlly=" + AllyDesignationSystem.isPlayerAlly(owner, other)
                + " nativeSelector=" + nativeSelection + " actualSelector=" + actualSelection + " target=" + id(actor.getAttackTarget()));
    }

    private static void save(WorldServer world, EntityPlayerMP owner, boolean protectedIncoming) throws Exception {
        loadRemote(world);
        NativeFixture.floor(world, REMOTE_X, REMOTE_X + 12);
        NativeFixture.equipPlayer(owner, REMOTE_X + 3.5);
        List<EntityLivingBase> summoned = new ArrayList<>();
        EntityZombieMinion actor = MeleeChecks.summon(owner, world, summoned,
                "ebwizardry:summon_zombie", EntityZombieMinion.class);
        require(summoned.size() == 1, "Expected one native saved Zombie Minion");
        actor.setPositionAndUpdate(REMOTE_X + 0.5, 4, 6.5);
        strike(owner, actor, owner.getUniqueID(), protectedIncoming, "owner");
        actor.setHealth(actor.getMaxHealth());
        actor.extinguish();
        actor.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, namedEquipment(Items.GOLDEN_SWORD, 13, BLADE));
        actor.setItemStackToSlot(EntityEquipmentSlot.HEAD, namedEquipment(Items.IRON_HELMET, 15, HELMET));
        checkEquipment(actor);
        Item nativeWand = Item.getByNameOrId(WAND);
        require(nativeWand != null, "Wizardry inventory control was not registered");
        owner.inventory.setInventorySlotContents(7, new ItemStack(nativeWand));
        ItemStack item = new ItemStack(Items.DIAMOND, 3);
        item.setStackDisplayName("Authority inventory witness");
        owner.inventory.setInventorySlotContents(8, item);
        checkInventory(owner);
        require(actor.getLifetime() > actor.ticksExisted, "Native lifespan was unavailable");
        Files.write(worldPath(world).resolve("wst-authority-ids.txt"),
                Arrays.asList(id(actor), id(owner), Integer.toString(actor.getLifetime())), StandardCharsets.US_ASCII);
        output("AUTHORITY_SAVE actor=" + id(actor) + " owner=" + id(owner) + " nativeLifetime=" + actor.getLifetime()
                + " equipment=" + owner.inventory.getStackInSlot(7).serializeNBT() + " inventory=" + item.serializeNBT());
    }

    private static void restored(WorldServer world, EntityPlayerMP player, boolean blocked, boolean removed) throws Exception {
        Path folder = worldPath(world);
        List<String> state = Files.readAllLines(folder.resolve("wst-authority-ids.txt"), StandardCharsets.US_ASCII);
        UUID uniqueId = UUID.fromString(state.get(0));
        UUID owner = UUID.fromString(state.get(1));
        loadRemote(world);
        Entity result = world.getEntityFromUuid(uniqueId);
        require(result instanceof EntityZombieMinion && result.isEntityAlive(), "Original saved native Zombie Minion is missing");
        EntityZombieMinion actor = (EntityZombieMinion) result;
        require(owner.equals(actor.getOwnerId()) && actor.getLifetime() == Integer.parseInt(state.get(2))
                && actor.ticksExisted < actor.getLifetime(), "Original provider owner or lifespan changed");
        checkEquipment(actor);
        require(Loader.isModLoaded("wizardrysummontweaks") != removed, "Wrong product inventory for restored case");
        if (removed) {
            require(owner.equals(player.getUniqueID()), "Removal case needs the original player's native inventory");
            checkInventory(player);
        } else {
            require(!owner.equals(player.getUniqueID()) && world.getMinecraftServer().getPlayerList().getPlayerByUUID(owner) == null,
                    "Distinct actual non-owner requires the original owner to be absent");
            require(Files.exists(folder.resolve("playerdata").resolve(owner + ".dat")), "Original owner player data is missing");
            inspect(world.getMinecraftServer(), world.getMinecraftServer(), actor, "AFFECTED");
        }
        NativeFixture.equipPlayer(player, REMOTE_X + 3.5);
        strike(player, actor, owner, blocked, removed ? "product-removed" : "actual-nonowner");
        output("AUTHORITY_RESTORE actor=" + id(actor) + " player=" + id(player) + " nativeOwner=" + owner
                + " nativeLifetime=" + actor.getLifetime() + " productRemoved=" + removed);
    }

    private static void strike(EntityPlayerMP player, EntityZombieMinion actor, UUID owner, boolean blocked, String label)
            throws IllegalAccessException {
        actor.setHealth(actor.getMaxHealth());
        actor.setAbsorptionAmount(1);
        actor.hurtResistantTime = 0;
        actor.motionX = actor.motionY = actor.motionZ = 0;
        float before = actor.getHealth();
        NativeFixture.preparePlayerAttack(player, Enchantments.FIRE_ASPECT);
        Runnable hit = () -> player.attackTargetEntityWithCurrentItem(actor);
        if (blocked) ClientFeedbackProbe.silent(player, "authority-" + label, hit).run();
        else hit.run();
        output("AUTHORITY_MELEE case=" + label + " player=" + id(player) + " nativeOwner=" + owner
                + " actor=" + id(actor) + " survival=" + !player.capabilities.disableDamage + " blockedExpected=" + blocked
                + " hpBefore=" + before + " hpAfter=" + actor.getHealth() + " absorption=" + actor.getAbsorptionAmount()
                + " burning=" + actor.isBurning() + " impulse=" + Math.hypot(actor.motionX, actor.motionZ));
        boolean changed = actor.getHealth() < before || actor.getAbsorptionAmount() < 1;
        require(changed != blocked, "Native sword did not reach its expected survival outcome");
        require(!blocked || !actor.isBurning() && actor.motionX == 0 && actor.motionZ == 0, "Blocked sword applied a secondary effect");
    }

    private static void checkInventory(EntityPlayerMP player) {
        ItemStack wand = player.inventory.getStackInSlot(7);
        ItemStack witness = player.inventory.getStackInSlot(8);
        require(!wand.isEmpty() && WAND.equals(String.valueOf(wand.getItem().getRegistryName()))
                && witness.getItem() == Items.DIAMOND && witness.getCount() == 3
                && witness.getDisplayName().equals("Authority inventory witness"), "Native inventory did not survive");
        output("AUTHORITY_INVENTORY player=" + id(player) + " wand=" + wand.serializeNBT() + " witness=" + witness.serializeNBT());
    }

    private static ItemStack namedEquipment(Item item, int damage, String name) {
        return new ItemStack(item, 1, damage).setStackDisplayName(name);
    }

    private static void checkEquipment(EntityZombieMinion actor) {
        ItemStack blade = actor.getItemStackFromSlot(EntityEquipmentSlot.MAINHAND);
        ItemStack helmet = actor.getItemStackFromSlot(EntityEquipmentSlot.HEAD);
        require(ItemStack.areItemStacksEqual(blade, namedEquipment(Items.GOLDEN_SWORD, 13, BLADE))
                && ItemStack.areItemStacksEqual(helmet, namedEquipment(Items.IRON_HELMET, 15, HELMET)),
                "The native creature's supplied equipment changed");
        output("AUTHORITY_ENTITY_EQUIPMENT actor=" + id(actor) + " mainHand=" + blade.serializeNBT() + " head=" + helmet.serializeNBT());
    }

    private static void quitPlainClient(WorldServer world) throws Exception {
        require(world.getMinecraftServer().isDedicatedServer() && !Loader.isModLoaded("agenttesttoolkit"), "Expected the isolated plain client");
        Path server = worldPath(world).toAbsolutePath().normalize().getParent();
        require(server.getFileName().toString().equals("server"), "Expected an isolated server directory");
        Path signal = server.resolveSibling("client").resolve("config/wstfixture-quit-ready.flag");
        Files.createFile(signal);
        output("AUTHORITY_QUIT_SIGNAL");
    }

    private static void loadRemote(WorldServer world) {
        for (int x = 62; x <= 66; x++) {
            for (int z = -2; z <= 2; z++) world.getChunk(x, z);
        }
    }

    private static Path worldPath(WorldServer world) {
        return world.getSaveHandler().getWorldDirectory().toPath();
    }

    private static String id(Entity entity) {
        return entity == null ? "none" : entity.getUniqueID().toString();
    }
}
