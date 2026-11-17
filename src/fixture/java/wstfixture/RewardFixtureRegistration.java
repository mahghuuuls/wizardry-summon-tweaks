package wstfixture;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;

@Mod.EventBusSubscriber(modid = "wstfixture")
public final class RewardFixtureRegistration {
    @SubscribeEvent
    public static void register(RegistryEvent.Register<EntityEntry> event) {
        if (Loader.isModLoaded("ebwizardry")) {
            event.getRegistry().register(EntityEntryBuilder.create()
                    .entity(RewardZombie.class)
                    .id(new ResourceLocation("wstfixture", "reward_zombie"), 1)
                    .name("reward_zombie").tracker(48, 3, true).build());
        }
    }
}
