package wstfixture;

import com.Fishmod.mod_LavaCow.entities.tameable.EntityLilSludge;
import com.windanesz.wizardryutils.capability.SummonedCreatureData;
import electroblob.wizardry.Wizardry;
import electroblob.wizardry.entity.living.EntitySkeletonMinion;
import electroblob.wizardry.entity.living.EntityZombieMinion;
import electroblob.wizardry.entity.living.ISummonedCreature;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.AllyDesignationSystem;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.LootPool;
import net.minecraft.world.storage.loot.LootTable;
import net.minecraftforge.fml.common.Loader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import static wstfixture.IdentityFixture.getPlayer;
import static wstfixture.IdentityFixture.inspect;
import static wstfixture.IdentityFixture.output;
import static wstfixture.IdentityFixture.require;

/** Paired observation of the copied pack inputs and the installed native paths they activate. */
public final class PackChecks {
    private static final String ZOMBIE = "ebwizardry:summon_zombie";
    private static final String SKELETON = "ebwizardry:summon_skeleton";
    private static final String MAGE = "ancientspellcraft:raise_skeleton_mage";
    private static final String PARALYSIS = "ebwizardry:paralysis";
    private static final ResourceLocation FOX_SPELL = new ResourceLocation("spellbundle:conjure_foxhound");
    private static final ResourceLocation FOXHOUND = new ResourceLocation("quark:foxhound");
    private static final String CHEST = "minecraft:chests/simple_dungeon";
    private static final String QUARK_CHEST = "quark:chests/pirate_chest";
    private static final String[] POOLS = { "electroblobGeneralLoot", "as_advanced", "as_master", "as_legendary" };
    private static final String REVENGE_MINION = "wst_pack_revenge_minion";
    private static final String REVENGE_GOLEM = "wst_pack_revenge_golem";

    public static void run(MinecraftServer server, ICommandSender sender, String[] args) throws Exception {
        EntityPlayerMP owner = getPlayer(server, sender, args[1]);
        WorldServer world = owner.getServerWorld();
        boolean product = "current".equals(args[2]);
        require(product == Loader.isModLoaded("wizardrysummontweaks"), "Incorrect product state for paired pack test");
        if (args.length == 4) {
            switch (args[3]) {
                case "elemental":
                    elementalFriendlyFire(world, owner, product);
                    break;
                case "revenge-prepare":
                    prepareRevenge(world, owner);
                    break;
                case "revenge-start":
                    startRevenge(world);
                    break;
                case "revenge-finish":
                    finishRevenge(world);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown pack probe " + args[3]);
            }
            return;
        }
        require("all".equalsIgnoreCase(Wizardry.settings.friendlyFire.name()) && Wizardry.settings.minionRevengeTargeting,
                "Original TestPACK target and friendly-fire settings are not active");
        Spell originalSlot = activeOverride(ZOMBIE, "SummonZombieOverride");
        Spell skeletonSlot = activeOverride(SKELETON, "SummonSkeletonOverride");
        output("PACK_SETTINGS product=" + product + " friendlyFire=" + Wizardry.settings.friendlyFire
                + " minionRevengeTargeting=" + Wizardry.settings.minionRevengeTargeting
                + " spell=" + originalSlot.getRegistryName() + " override=" + originalSlot.getClass().getName()
                + " skeletonSpell=" + skeletonSlot.getRegistryName() + " skeletonOverride=" + skeletonSlot.getClass().getName());

        NativeFixture.floor(world, -8, 42);
        NativeFixture.equipPlayer(owner, 8.5);
        List<EntityLivingBase> originalSpells = new ArrayList<>();
        List<EntityLiving> addonSummons = new ArrayList<>();
        EntityZombie enemy = new EntityZombie(world);
        enemy.setNoAI(true);
        enemy.setPosition(13.5, 4, 6.5);
        require(world.spawnEntity(enemy), "Ordinary hostile target failed to join the pack profile");
        try {
            EntityZombieMinion zombie = MeleeChecks.summon(owner, world, originalSpells, ZOMBIE, EntityZombieMinion.class);
            EntitySkeletonMinion skeleton = MeleeChecks.summon(owner, world, originalSpells, SKELETON, EntitySkeletonMinion.class);
            Class<? extends EntityLiving> mageType = Class.forName(
                    "com.windanesz.ancientspellcraft.entity.living.EntitySkeletonMageMinion").asSubclass(EntityLiving.class);
            EntityLiving mage = MeleeChecks.summon(owner, world, originalSpells, MAGE, mageType);
            EntityWolf fox = foxhound(world, owner, addonSummons);
            Set<Entity> previous = new HashSet<>(world.loadedEntityList);
            FishChecks.nativeWand(world, owner);
            List<EntityLilSludge> fish = FishChecks.spawnedSince(world, previous, addonSummons);
            require(!fish.isEmpty(), "Native Fish wand did not produce a live original minion");
            for (EntityLilSludge sludge : fish) {
                require(sludge.isTamed() && owner.getUniqueID().equals(sludge.getOwnerId()), "Native Fish owner differs");
            }
            List<Entity> representatives = new ArrayList<>(originalSpells);
            representatives.add(fox);
            representatives.addAll(fish);
            for (Entity actor : representatives) {
                if (product) inspect(server, sender, actor, "AFFECTED");
                output("PACK_ACTOR type=" + EntityList.getKey(actor) + " class=" + actor.getClass().getName()
                        + " uuid=" + actor.getUniqueID() + " owner=" + owner.getUniqueID());
            }
            output("PACK_LIFETIMES zombie=" + zombie.getLifetime() + " skeleton=" + skeleton.getLifetime()
                    + " ancient=" + ((ISummonedCreature) mage).getLifetime());

            zombie.setPositionAndUpdate(11.5, 4, 6.5);
            zombie.setAttackTarget(enemy);
            float before = enemy.getHealth();
            zombie.attackEntityAsMob(enemy);
            require(zombie.getAttackTarget() == enemy && enemy.getHealth() < before, "Pack's ordinary enemy was excluded");
            NativeFixture.resetProjectileVictim(zombie);
            float summonBefore = zombie.getHealth();
            NativeFixture.preparePlayerAttack(owner, Enchantments.FIRE_ASPECT);
            owner.attackTargetEntityWithCurrentItem(zombie);
            if (product) {
                require(zombie.getHealth() == summonBefore && zombie.getAbsorptionAmount() == 2 && !zombie.isBurning(),
                        "Pack's All friendly-fire setting disabled product protection");
            }
            output("PACK_COMBAT enemy=" + before + "/" + enemy.getHealth() + " summon=" + summonBefore + "/" + zombie.getHealth()
                    + " absorption=" + zombie.getAbsorptionAmount() + " burning=" + zombie.isBurning());

            samplePools(world, CHEST);
            samplePools(world, QUARK_CHEST);
            output("PACK_COMPLETE product=" + product);
        } finally {
            originalSpells.forEach(Entity::setDead);
            addonSummons.forEach(Entity::setDead);
            enemy.setDead();
        }
    }

    private static Spell activeOverride(String id, String name) {
        Spell spell = Spell.get(id);
        require(spell != null && spell.isEnabled() && spell.getClass().getSimpleName().equals(name),
                "Arcane Apprentices' native replacement missing from " + id);
        return spell;
    }

    private static void elementalFriendlyFire(WorldServer world, EntityPlayerMP owner, boolean product) throws Exception {
        NativeFixture.equipPlayer(owner, 8.5);
        Spell spell = Spell.get(PARALYSIS);
        require(spell != null && spell.isEnabled(), "The original elemental spell is unavailable");
        List<EntityLivingBase> spawned = new ArrayList<>();
        EntityZombieMinion minion = MeleeChecks.summon(owner, world, spawned, ZOMBIE, EntityZombieMinion.class);
        EntityZombie ordinary = new EntityZombie(world);
        try {
            minion.setPositionAndUpdate(12.5, 4, 6.5);
            require(AllyDesignationSystem.isAllied(owner, minion), "The source must recognize the caster's ally");
            float before = minion.getHealth();
            boolean summonCast = spell.cast(world, owner, EnumHand.MAIN_HAND, 0, new SpellModifiers());
            float after = minion.getHealth();
            require(product ? before == after : before > after, "The original elemental summon case did not distinguish protection");
            minion.setPositionAndUpdate(12.5, 4, 13.5);

            ordinary.setNoAI(true);
            ordinary.setPosition(12.5, 4, 6.5);
            require(world.spawnEntity(ordinary), "The ordinary elemental damage control failed to join");
            if (product) inspect(world.getMinecraftServer(), world.getMinecraftServer(), ordinary, "EXCLUDED");
            float ordinaryBefore = ordinary.getHealth();
            boolean ordinaryCast = spell.cast(world, owner, EnumHand.MAIN_HAND, 0, new SpellModifiers());
            require(ordinaryCast && ordinary.getHealth() < ordinaryBefore, "Native elemental damage did not reach the ordinary actor");
            output("PACK_ELEMENTAL spell=" + spell.getRegistryName() + " summon=" + minion.getUniqueID()
                    + " summonCast=" + summonCast + " summonHp=" + before + "/" + after + " ordinary=" + ordinary.getUniqueID()
                    + " ordinaryCast=" + ordinaryCast + " ordinaryHp=" + ordinaryBefore + "/" + ordinary.getHealth());
        } finally {
            spawned.forEach(Entity::setDead);
            ordinary.setDead();
        }
    }

    private static void prepareRevenge(WorldServer world, EntityPlayerMP owner) throws Exception {
        NativeFixture.floor(world, -8, 42);
        NativeFixture.equipPlayer(owner, 8.5);
        List<EntityLivingBase> spawned = new ArrayList<>();
        EntityZombieMinion minion = MeleeChecks.summon(owner, world, spawned, ZOMBIE, EntityZombieMinion.class);
        require(spawned.size() == 1, "The revenge probe needs the single original minion");
        minion.addTag(REVENGE_MINION);
        minion.setPositionAndUpdate(10.5, 4, 6.5);
        minion.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(60);
        minion.setHealth(60);

        EntityIronGolem golem = new EntityIronGolem(world);
        golem.setNoAI(true);
        golem.setPosition(12.0, 4, 6.5);
        golem.addTag(REVENGE_GOLEM);
        require(world.spawnEntity(golem), "The ordinary neutral Golem did not join");
    }

    private static void startRevenge(WorldServer world) {
        EntityZombieMinion minion = tagged(world, REVENGE_MINION, EntityZombieMinion.class);
        EntityIronGolem golem = tagged(world, REVENGE_GOLEM, EntityIronGolem.class);
        boolean valid = minion.isValidTarget(golem);
        boolean revengeAllowed = minion.shouldRevengeTarget(golem);
        require(minion.ticksExisted > 0 && !valid && revengeAllowed
                        && minion.getAttackTarget() == null && minion.getRevengeTarget() == null,
                "The neutral Golem must be reachable only by original revenge");
        float before = minion.getHealth();
        require(golem.attackEntityAsMob(minion) && minion.getHealth() < before, "The real Golem did not provoke the minion");
        require(minion.getRevengeTimer() > 0 && minion.getRevengeTarget() == golem && minion.getAttackTarget() == null,
                "The original hurt path did not record the neutral Golem before its AI tick");
        minion.motionX = minion.motionY = minion.motionZ = 0;
        minion.setNoAI(false);
        output("PACK_REVENGE_READY minion=" + minion.getUniqueID() + " golem=" + golem.getUniqueID()
                + " regularCandidate=" + valid + " revengeAllowed=" + revengeAllowed + " preAiTarget=none"
                + " revengeTimer=" + minion.getRevengeTimer() + " incomingHp=" + before + "/" + minion.getHealth());
    }

    private static void finishRevenge(WorldServer world) {
        EntityZombieMinion minion = tagged(world, REVENGE_MINION, EntityZombieMinion.class);
        EntityIronGolem golem = tagged(world, REVENGE_GOLEM, EntityIronGolem.class);
        try {
            EntityLivingBase target = minion.getAttackTarget();
            output("PACK_REVENGE_RESULT minion=" + minion.getUniqueID() + " golem=" + golem.getUniqueID()
                    + " target=" + (target == null ? "none" : target.getUniqueID())
                    + " distance=" + minion.getDistance(golem) + " outgoingHp=" + golem.getMaxHealth() + "/" + golem.getHealth());
            require(!minion.isAIDisabled() && target == golem && golem.getHealth() < golem.getMaxHealth(),
                    "Original revenge AI did not select and hurt the neutral Golem");
        } finally {
            minion.setDead();
            golem.setDead();
        }
    }

    private static <T extends Entity> T tagged(WorldServer world, String tag, Class<T> type) {
        for (Entity entity : world.loadedEntityList) {
            if (type.isInstance(entity) && entity.getTags().contains(tag)) return type.cast(entity);
        }
        throw new IllegalStateException("Missing pack actor " + tag);
    }

    private static EntityWolf foxhound(WorldServer world, EntityPlayerMP owner, List<EntityLiving> cleanup) {
        Set<Entity> existing = new HashSet<>(world.loadedEntityList);
        Spell spell = Spell.get(FOX_SPELL.toString());
        require(spell != null && spell.isEnabled() && FOX_SPELL.equals(spell.getRegistryName())
                        && spell.cast(world, owner, EnumHand.MAIN_HAND, 0, new SpellModifiers()),
                "The original Spell Bundle Foxhound spell did not cast");
        EntityWolf actor = null;
        for (Entity entity : world.loadedEntityList) {
            if (existing.contains(entity) || !FOXHOUND.equals(EntityList.getKey(entity))) continue;
            EntityWolf fox = (EntityWolf) entity;
            cleanup.add(fox);
            if (actor == null) actor = fox;
        }
        require(actor != null && SummonedCreatureData.isSummonedEntity(actor)
                        && owner.getUniqueID().equals(SummonedCreatureData.get(actor).getOwnerId())
                        && !com.windanesz.spellbundle.capability.SummonedCreatureData.isSummonedEntity(actor),
                "The original Quark Foxhound lacks its Wizardry Utils summon and owner evidence");
        actor.setNoAI(true);
        return actor;
    }

    private static void samplePools(WorldServer world, String id) {
        LootTable table = world.getLootTableManager().getLootTableFromLocation(new ResourceLocation(id));
        LootContext context = new LootContext.Builder(world).build();
        for (String name : POOLS) {
            LootPool pool = table.getPool(name);
            require(pool != null, "Original copied reward script did not register " + name + " in " + id);
            Random random = new Random(761208324L);
            List<ItemStack> rolls = new ArrayList<>();
            for (int index = 0; index < 2048; index++) pool.generateLoot(rolls, random, context);
            Map<String, Integer> rewards = new TreeMap<>();
            for (ItemStack stack : rolls) {
                if (!stack.isEmpty()) rewards.merge(String.valueOf(stack.getItem().getRegistryName()), stack.getCount(), Integer::sum);
            }
            require(!rewards.isEmpty(), "Registered script pool produced no native reward in " + id);
            output("PACK_LOOT table=" + id + " pool=" + name + " rolls=2048 rewards=" + rewards);
        }
    }
}
