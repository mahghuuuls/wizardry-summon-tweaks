package wstfixture;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.network.play.server.SPacketChat;
import net.minecraft.network.play.server.SPacketCustomSound;
import net.minecraft.network.play.server.SPacketEffect;
import net.minecraft.network.play.server.SPacketParticles;
import net.minecraft.network.play.server.SPacketSoundEffect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentString;

import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;

import static wstfixture.IdentityFixture.output;
import static wstfixture.IdentityFixture.require;

/** Reads feedback packets on the real local player's connection without intercepting delivery. */
final class ClientFeedbackProbe {
    private ClientFeedbackProbe() {}

    static Runnable silent(EntityPlayerMP player, String name, Runnable action) {
        return () -> {
            Set<String> packets = capture(player, action);
            require(packets.isEmpty(), name + " sent feedback " + packets);
            output("MELEE_FEEDBACK case=" + name + " packets=" + packets);
        };
    }

    static void verify(EntityPlayerMP player) {
        Set<String> packets = capture(player, () -> {
            player.sendMessage(new TextComponentString("Melee validation packet control"));
            player.connection.sendPacket(new SPacketSoundEffect(SoundEvents.ENTITY_PLAYER_ATTACK_STRONG,
                    SoundCategory.PLAYERS, player.posX, player.posY, player.posZ, 1, 1));
            player.connection.sendPacket(new SPacketParticles(EnumParticleTypes.CRIT, false,
                    (float) player.posX, (float) player.posY + 1, (float) player.posZ, 0, 0, 0, 0, 1));
        });
        require(packets.contains("chat") && packets.contains("sound") && packets.contains("particles"),
                "feedback probe did not see its positive controls " + packets);
        output("MELEE_FEEDBACK_CONTROL packets=" + packets);
    }

    private static Set<String> capture(EntityPlayerMP player, Runnable action) {
        Channel channel = player.connection.getNetworkManager().channel();
        Set<String> observed = new ConcurrentSkipListSet<>();
        ChannelOutboundHandlerAdapter observer = new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext context, Object packet, ChannelPromise promise) {
                if (packet instanceof SPacketChat) observed.add("chat");
                if (packet instanceof SPacketSoundEffect || packet instanceof SPacketCustomSound) observed.add("sound");
                if (packet instanceof SPacketParticles) observed.add("particles");
                if (packet instanceof SPacketEffect) observed.add("world-effect");
                context.write(packet, promise);
            }
        };
        channel.pipeline().addLast("wst-melee-feedback", observer);
        try {
            awaitWrites(channel);
            action.run();
            awaitWrites(channel);
            return new TreeSet<>(observed);
        } finally {
            channel.pipeline().remove(observer);
        }
    }

    private static void awaitWrites(Channel channel) {
        require(channel.eventLoop().submit(() -> {}).awaitUninterruptibly(3, TimeUnit.SECONDS),
                "local connection did not drain its queued packets");
    }
}
