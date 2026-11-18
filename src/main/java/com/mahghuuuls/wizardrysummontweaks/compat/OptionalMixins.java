package com.mahghuuuls.wizardrysummontweaks.compat;

import net.minecraftforge.fml.common.Loader;
import zone.rong.mixinbooter.ILateMixinLoader;

import java.util.Arrays;
import java.util.List;

public final class OptionalMixins implements ILateMixinLoader {
    private static final String WIZARDRY = "mixins.wizardrysummontweaks.wizardry.json";
    private static final String WIZARDRY_UTILS = "mixins.wizardrysummontweaks.wizardryutils.json";
    private static final String SPELL_BUNDLE = "mixins.wizardrysummontweaks.spellbundle.json";

    @Override
    public List<String> getMixinConfigs() {
        return Arrays.asList(WIZARDRY, WIZARDRY_UTILS, SPELL_BUNDLE);
    }

    @Override
    public boolean shouldMixinConfigQueue(String config) {
        if (WIZARDRY.equals(config)) return Loader.isModLoaded("ebwizardry");
        if (WIZARDRY_UTILS.equals(config)) return Loader.isModLoaded("wizardryutils");
        return SPELL_BUNDLE.equals(config) && Loader.isModLoaded("spellbundle");
    }
}
