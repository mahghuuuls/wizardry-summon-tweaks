package wstfixture;

import com.mojang.authlib.GameProfile;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;

import static wstfixture.IdentityFixture.SECOND_PLAYER;

/** Common vanilla test setup. Initializing it does not load an optional provider. */
final class NativeFixture {
    private static final Field RESPAWN_IMMUNITY = ReflectionHelper.findField(EntityPlayerMP.class,
            "respawnInvulnerabilityTicks", "field_147101_bU");
    private static final Field MELEE_COOLDOWN = ReflectionHelper.findField(EntityLivingBase.class,
            "ticksSinceLastSwing", "field_184617_aD");

    private NativeFixture() {}

    static void clearSpawnImmunity(EntityPlayerMP player) throws IllegalAccessException {
        RESPAWN_IMMUNITY.setInt(player, 0);
    }

    static void equipPlayer(EntityPlayerMP player, double x) throws IllegalAccessException {
        player.setGameType(GameType.SURVIVAL);
        clearSpawnImmunity(player);
        player.connection.setPlayerLocation(x, 4, 6.5, -90, 0);
        resetProjectileVictim(player);
    }

    static void resetProjectileVictim(EntityLivingBase entity) {
        entity.clearActivePotions();
        entity.extinguish();
        entity.setHealth(entity.getMaxHealth());
        entity.setAbsorptionAmount(2);
        entity.hurtResistantTime = 0;
        entity.setArrowCountInEntity(0);
        entity.setItemStackToSlot(EntityEquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        entity.motionX = entity.motionY = entity.motionZ = 0;
    }

    static void floor(WorldServer world, int from, int to) {
        for (int x = from; x <= to; x++) {
            for (int z = 2; z <= 10; z++) world.setBlockState(new BlockPos(x, 3, z), Blocks.STONE.getDefaultState());
            for (int y = 4; y <= 8; y++) for (int z = 5; z <= 7; z++) world.setBlockToAir(new BlockPos(x, y, z));
        }
    }

    static EntityPlayerMP secondPlayer(MinecraftServer server, WorldServer world) throws IllegalAccessException {
        EntityPlayerMP other = new EntityPlayerMP(server, world, new GameProfile(SECOND_PLAYER, "WstOtherMelee"),
                new PlayerInteractionManager(world));
        // Native server combat with an in-process connection that discards outgoing vanilla packets.
        new NetHandlerPlayServer(server, new NetworkManager(EnumPacketDirection.SERVERBOUND), other) {
            @Override public void sendPacket(Packet<?> packet) {}
        };
        other.setGameType(GameType.SURVIVAL);
        clearSpawnImmunity(other);
        other.setPosition(0.5, 4, 0.5);
        return other;
    }

    static void preparePlayerAttack(EntityPlayerMP player, Enchantment enchantment) throws IllegalAccessException {
        // Set the same full charge a player would get by waiting between attacks.
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.addEnchantment(enchantment, 2);
        player.setHeldItem(EnumHand.MAIN_HAND, sword);
        MELEE_COOLDOWN.setInt(player, 30);
        player.setSprinting(false);
        player.onGround = true;
        player.fallDistance = 0;
    }
}
