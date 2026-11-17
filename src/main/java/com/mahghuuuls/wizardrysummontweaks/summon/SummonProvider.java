package com.mahghuuuls.wizardrysummontweaks.summon;

import net.minecraft.entity.Entity;

/** Optional source knowledge; no policy or persistence belongs in a provider. */
public interface SummonProvider {
    boolean matches(Entity entity);
    /** Return verified origin and current ownership evidence, or null when origin cannot be proved. */
    SummonFacts observe(Entity entity, SummonFacts saved);
}
