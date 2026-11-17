package com.mahghuuuls.wizardrysummontweaks.summon;

import net.minecraft.entity.Entity;

/** Optional source knowledge; no policy or persistence belongs in a provider. */
public interface SummonProvider {
    boolean matches(Entity entity);
    SummonFacts observe(Entity entity);
}
