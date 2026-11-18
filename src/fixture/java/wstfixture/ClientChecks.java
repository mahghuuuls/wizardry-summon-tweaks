package wstfixture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.nio.file.Files;
import java.nio.file.Paths;

/** Physical-client test action, deferred by class name from the common test command. */
@Mod.EventBusSubscriber(modid = "wstfixture", value = Side.CLIENT)
public final class ClientChecks {
    private static int lastWorld;
    private static boolean shutdownRequested;

    public static void leave() {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            Minecraft mc = Minecraft.getMinecraft();
            mc.world.sendQuittingDisconnectingPacket();
            mc.loadWorld(null);
            mc.displayGuiScreen(new GuiMainMenu());
            IdentityFixture.output("LEFT_WORLD");
        });
    }

    @SubscribeEvent
    public static void observeAuthorityClient(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Boolean.getBoolean("wstfixture.authorityClient")) return;
        Minecraft client = Minecraft.getMinecraft();
        if (client.world == null || client.player == null) {
            lastWorld = 0;
            return;
        }
        int world = System.identityHashCode(client.world);
        if (world != lastWorld) {
            lastWorld = world;
            IdentityFixture.output("AUTHORITY_CLIENT player=" + client.player.getUniqueID()
                    + " product=" + Loader.isModLoaded("wizardrysummontweaks")
                    + " toolkit=" + Loader.isModLoaded("agenttesttoolkit")
                    + " wizardry=" + Loader.isModLoaded("ebwizardry")
                    + " integrated=" + client.isIntegratedServerRunning());
        }
        if (!shutdownRequested && Boolean.getBoolean("wstfixture.authorityQuit")
                && Files.isRegularFile(Paths.get("config", "wstfixture-quit-ready.flag"))) {
            shutdownRequested = true;
            IdentityFixture.output("AUTHORITY_CLIENT_EXIT player=" + client.player.getUniqueID());
            client.shutdown();
        }
    }
}
