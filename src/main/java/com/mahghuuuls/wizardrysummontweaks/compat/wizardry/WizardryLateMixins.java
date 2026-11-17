package com.mahghuuuls.wizardrysummontweaks.compat.wizardry;

import net.minecraftforge.fml.common.Loader;
import zone.rong.mixinbooter.ILateMixinLoader;

import java.util.Collections;
import java.util.List;

public final class WizardryLateMixins implements ILateMixinLoader {
    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("mixins.wizardrysummontweaks.wizardry.json");
    }

    @Override
    public boolean shouldMixinConfigQueue(String config) {
        return Loader.isModLoaded("ebwizardry");
    }
}
