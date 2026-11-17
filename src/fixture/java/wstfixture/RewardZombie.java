package wstfixture;

import electroblob.wizardry.entity.living.ISummonedCreature;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

import java.util.UUID;

/** A native Forge reward source with the same Wizardry recognition contract. */
public final class RewardZombie extends EntityZombie implements ISummonedCreature {
    private UUID owner;
    private int lifetime;

    public RewardZombie(World world) {
        super(world);
        experienceValue = 7;
    }

    @Override public UUID getOwnerId() { return owner; }
    @Override public void setOwnerId(UUID owner) { this.owner = owner; }
    @Override public int getLifetime() { return lifetime; }
    @Override public void setLifetime(int lifetime) { this.lifetime = lifetime; }
    @Override public void onSpawn() {}
    @Override public void onDespawn() {}
    @Override public boolean hasParticleEffect() { return false; }

    @Override
    protected void dropLoot(boolean recentlyHit, int looting, DamageSource source) {
        entityDropItem(new ItemStack(Items.EMERALD), 0);
        dropEquipment(recentlyHit, looting);
    }

    void pickUpNormally(EntityItem item) {
        updateEquipmentIfNeeded(item);
    }
}
