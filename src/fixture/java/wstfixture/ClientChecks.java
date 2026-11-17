package wstfixture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;

/** Physical-client test action, deferred by class name from the common test command. */
public final class ClientChecks {
    public static void leave() {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            Minecraft mc = Minecraft.getMinecraft();
            mc.world.sendQuittingDisconnectingPacket();
            mc.loadWorld(null);
            mc.displayGuiScreen(new GuiMainMenu());
            IdentityFixture.output("LEFT_WORLD");
        });
    }
}
