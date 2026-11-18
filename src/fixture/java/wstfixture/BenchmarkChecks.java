package wstfixture;

import electroblob.wizardry.entity.living.EntityZombieMinion;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static wstfixture.IdentityFixture.getPlayer;
import static wstfixture.IdentityFixture.output;
import static wstfixture.IdentityFixture.require;

/** Records actual server ticks in a fixed arena with native Wizardry casts and mob AI. */
public final class BenchmarkChecks {
    private static final long WORLD_SEED = 761208324L;
    private static final int NATIVE_LIFETIME = 600;
    private static final int TARGET_COUNT = 8;
    private static final int[] POPULATIONS = {0, 16, 64, 0, 16, 64};
    private static Experiment running;

    public static void run(MinecraftServer server, ICommandSender sender, String[] args) throws Exception {
        require(running == null && "start".equals(args[1]) && server.isDedicatedServer(), "Wrong benchmark context");
        EntityPlayerMP caster = getPlayer(server, sender, args[2]);
        int warm = Integer.parseInt(args[3]);
        int sample = Integer.parseInt(args[4]);
        require(warm > 0 && warm <= 40 && sample > 0 && sample <= 120, "Measurement durations outside scope");
        boolean combatProbe = args.length == 6 && "64-active".equals(args[5]);
        require(args.length == 5 || combatProbe, "The only isolated probe is the full-population combat phase");
        WorldServer world = caster.getServerWorld();
        require(world.getSeed() == WORLD_SEED, "Benchmark did not start with the fixed world seed");
        for (int x = -4; x <= 6; x++) {
            for (int z = -4; z <= 4; z++) world.getChunk(x, z);
        }
        buildArena(world);
        Path outputFolder = world.getSaveHandler().getWorldDirectory().toPath().resolve("wst-benchmark");
        Files.createDirectory(outputFolder);
        Experiment experiment = new Experiment(world, caster, outputFolder, warm * 1_000_000_000L,
                sample * 1_000_000_000L, combatProbe ? POPULATIONS.length - 1 : 0);
        running = experiment;
        MinecraftForge.EVENT_BUS.register(experiment);
        try {
            experiment.begin();
        } catch (Exception | Error failure) {
            experiment.close();
            throw failure;
        }
    }

    private static void buildArena(WorldServer world) {
        for (int x = -8; x <= 62; x++) {
            for (int z = -2; z <= 16; z++) {
                world.setBlockState(new BlockPos(x, 3, z), Blocks.STONE.getDefaultState());
                boolean wall = x == -8 || x == 24 || x == 62 || z == -2 || z == 16;
                for (int y = 4; y <= 10; y++) {
                    world.setBlockState(new BlockPos(x, y, z), (wall ? Blocks.STONE : Blocks.AIR).getDefaultState());
                }
            }
        }
    }

    public static final class Experiment {
        private final WorldServer world;
        private final EntityPlayerMP caster;
        private final Path outputFolder;
        private final long warmNanos;
        private final long sampleNanos;
        private final Spell spell;
        private final Set<Entity> liveSummons = Collections.newSetFromMap(new IdentityHashMap<>());
        private int phaseIndex;
        private Phase phase;
        private EntityZombieMinion joined;
        private boolean casting;
        private long tickStarted;

        Experiment(WorldServer world, EntityPlayerMP caster, Path outputFolder, long warmNanos, long sampleNanos,
                   int firstPhase) {
            this.world = world;
            this.caster = caster;
            this.outputFolder = outputFolder;
            this.warmNanos = warmNanos;
            this.sampleNanos = sampleNanos;
            this.phaseIndex = firstPhase;
            this.spell = Spell.get("ebwizardry:summon_zombie");
            require(spell != null, "Exact Wizardry Zombie summon spell is missing");
        }

        void begin() throws Exception {
            phase = new Phase(POPULATIONS[phaseIndex], phaseIndex >= 3);
            NativeFixture.equipPlayer(caster, phase.combat ? 34.5 : 4.5);
            for (int i = 0; i < phase.targets.length; i++) {
                EntityZombie target = new EntityZombie(world);
                target.setPosition(38.5 + 4 * (i % 4), 4, 4.5 + 4 * (i / 4));
                target.enablePersistence();
                target.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(1024);
                target.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
                target.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0);
                target.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(0.25);
                target.setHealth(target.getMaxHealth());
                require(world.spawnEntity(target) && !target.isAIDisabled(), "Ordinary live benchmark target did not join");
                phase.targets[i] = target;
                if (phase.combat && phase.summons.length > 0) {
                    NativeFixture.preparePlayerAttack(caster, Enchantments.SHARPNESS);
                    caster.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
                    float before = target.getHealth();
                    caster.attackTargetEntityWithCurrentItem(target);
                    require(target.getHealth() < before, "Real player opener did not reach the ordinary target");
                    phase.openings++;
                }
            }
            for (int i = 0; i < phase.summons.length; i++) spawn(i);
            require(liveSummons.size() == phase.summons.length, "Benchmark population could not be established");
            phase.warmStart = System.nanoTime();
            output("BENCH_READY phase=" + phase.label + " count=" + phase.summons.length + " targets=" + TARGET_COUNT
                    + " seed=" + WORLD_SEED + " openings=" + phase.openings + " warmSeconds=" + warmNanos / 1_000_000_000L
                    + " sampleSeconds=" + sampleNanos / 1_000_000_000L + " product=" + Loader.isModLoaded("wizardrysummontweaks")
                    + " threatworks=" + Loader.isModLoaded("threatworks"));
        }

        private void spawn(int slot) {
            joined = null;
            casting = true;
            try {
                require(spell.cast(world, caster, EnumHand.MAIN_HAND, 0, new SpellModifiers()), "Original benchmark summon cast failed");
            } finally {
                casting = false;
            }
            EntityZombieMinion actor = joined;
            joined = null;
            require(actor != null && actor.isAddedToWorld() && actor.getLifetime() == NATIVE_LIFETIME
                    && actor.getOwnerId().equals(caster.getUniqueID()) && !actor.isAIDisabled(),
                    "Expected one real native actor with original owner, lifetime and AI");
            if (phase.combat) {
                EntityZombie target = phase.targets[slot % TARGET_COUNT];
                double offset = 0.8 + (slot / TARGET_COUNT % 4) * 0.5;
                actor.setPositionAndUpdate(target.posX + (slot / TARGET_COUNT < 4 ? offset : -offset),
                        4, target.posZ + (slot / TARGET_COUNT % 2 == 0 ? 0.65 : -0.65));
                actor.setAttackTarget(target);
            } else {
                actor.setPositionAndUpdate((slot % 8) * 1.15 - 0.5, 4, (slot / 8) * 0.9 + 2.5);
            }
            phase.summons[slot] = actor;
            liveSummons.add(actor);
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onNativeJoin(EntityJoinWorldEvent event) {
            if (!casting || event.getWorld() != world || !(event.getEntity() instanceof EntityZombieMinion)) return;
            require(joined == null, "A single original cast attempted multiple benchmark actors");
            joined = (EntityZombieMinion) event.getEntity();
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onNativeDamage(LivingDamageEvent event) {
            if (phase == null || event.getAmount() <= 0 || !liveSummons.contains(event.getSource().getImmediateSource())) return;
            for (EntityZombie target : phase.targets) {
                if (target == event.getEntityLiving()) {
                    phase.hits++;
                    return;
                }
            }
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void beforeTick(TickEvent.ServerTickEvent event) {
            if (event.phase == TickEvent.Phase.START) tickStarted = System.nanoTime();
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void afterTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END || tickStarted == 0) return;
            long finished = System.nanoTime();
            try {
                require(caster.isEntityAlive() && caster.world == world, "Benchmark caster left the active native arena");
                int targeted = maintain();
                if (!phase.combat) require(targeted == 0, "Idle native population had an attack target");
                if (finished - phase.warmStart < warmNanos) return;
                if (phase.sampleStart == 0) phase.startSampling(finished);
                phase.row(finished, finished - tickStarted, world.getMinecraftServer().getTickCounter(),
                        liveSummons.size(), targeted);
                if (finished - phase.sampleStart < sampleNanos) return;
                finish(finished);
                if (++phaseIndex == POPULATIONS.length) {
                    close();
                    output("BENCH_COMPLETE");
                } else {
                    begin();
                }
            } catch (Exception | LinkageError failure) {
                close();
                org.apache.logging.log4j.LogManager.getLogger("WSTFixture").error("WST_BENCH_FAILED", failure);
            }
        }

        private int maintain() {
            int targeted = 0;
            for (int slot = 0; slot < phase.summons.length; slot++) {
                EntityZombieMinion actor = phase.summons[slot];
                if (actor.isDead) {
                    int age = actor.ticksExisted;
                    if (age < NATIVE_LIFETIME || actor.getHealth() <= 0) {
                        throw workloadFailure(actor, slot, "unexpected removal");
                    }
                    phase.removals++;
                    phase.minimumExpiry = Math.min(phase.minimumExpiry, age);
                    phase.maximumExpiry = Math.max(phase.maximumExpiry, age);
                    liveSummons.remove(actor);
                    spawn(slot);
                    actor = phase.summons[slot];
                }
                if (!actor.isAddedToWorld() || !actor.isEntityAlive()) {
                    throw workloadFailure(actor, slot, "native participant missing or dying");
                }
                if (actor.getHealth() < actor.getMaxHealth() / 2) {
                    phase.recoverySources.merge(lastDamageType(actor), 1, Integer::sum);
                    phase.summonRecoveries++;
                    actor.setHealth(actor.getMaxHealth());
                }
                if (phase.combat && actor.getAttackTarget() == null) actor.setAttackTarget(phase.targets[slot % TARGET_COUNT]);
                if (actor.getAttackTarget() != null) targeted++;
            }
            for (EntityZombie target : phase.targets) {
                require(target.isEntityAlive(), "An ordinary stationary target did not survive the phase");
                if (target.getHealth() < 512) target.setHealth(target.getMaxHealth());
            }
            return targeted;
        }

        private IllegalStateException workloadFailure(EntityZombieMinion actor, int slot, String reason) {
            return new IllegalStateException(reason + " phase=" + phase.label + " slot=" + slot
                    + " uuid=" + actor.getUniqueID() + " age=" + actor.ticksExisted + " health=" + actor.getHealth()
                    + " loaded=" + actor.isAddedToWorld() + " dead=" + actor.isDead
                    + " pos=" + actor.posX + "/" + actor.posY + "/" + actor.posZ
                    + " lastDamage=" + lastDamageType(actor));
        }

        private String lastDamageType(EntityZombieMinion actor) {
            net.minecraft.util.DamageSource source = actor.getLastDamageSource();
            return source == null ? "none" : source.getDamageType();
        }

        private void finish(long end) throws Exception {
            long nativeHits = phase.hits - phase.hitsAtSample;
            int expiries = phase.removals - phase.expiriesAtSample;
            require(phase.summons.length == 0 || !phase.combat || nativeHits > 0,
                    "Original provider AI did not deal any allowed damage during the measured window");
            String header = "elapsed_ns,tick,tick_ns,live_summons,live_targets,targeted_summons,native_hits,expiries,used_heap_bytes\n";
            Files.write(outputFolder.resolve(phase.label + ".csv"), (header + phase.rows).getBytes(StandardCharsets.US_ASCII));
            output("BENCH_STATE phase=" + phase.label + " sampleNanos=" + (end - phase.sampleStart)
                    + " warmNanos=" + (phase.sampleStart - phase.warmStart) + " rows=" + phase.samples
                    + " count=" + phase.summons.length + " targets=" + TARGET_COUNT + " nativeHits=" + nativeHits
                    + " expiries=" + expiries + " minimumExpiry=" + (phase.removals == 0 ? 0 : phase.minimumExpiry)
                    + " maximumExpiry=" + phase.maximumExpiry
                    + " gcRuns=" + (gc(false) - phase.startGcCount) + " gcMillis=" + (gc(true) - phase.startGcTime)
                    + " summonRecoveries=" + (phase.summonRecoveries - phase.recoveriesAtSample));
            output("BENCH_RETENTION phase=" + phase.label + " warmAndSampleSources=" + phase.recoverySources);
            clearPhase();
        }

        private void clearPhase() {
            if (phase == null) return;
            liveSummons.forEach(Entity::setDead);
            liveSummons.clear();
            for (EntityZombie enemy : phase.targets) {
                if (enemy != null) enemy.setDead();
            }
            phase = null;
        }

        private void close() {
            clearPhase();
            MinecraftForge.EVENT_BUS.unregister(this);
            running = null;
        }
    }

    private static long gc(boolean millis) {
        long total = 0;
        for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
            total += Math.max(0, millis ? collector.getCollectionTime() : collector.getCollectionCount());
        }
        return total;
    }

    private static final class Phase {
        final boolean combat;
        final String label;
        final EntityZombieMinion[] summons;
        final EntityZombie[] targets = new EntityZombie[TARGET_COUNT];
        final StringBuilder rows = new StringBuilder();
        long warmStart;
        long sampleStart;
        long startGcCount;
        long startGcTime;
        long hits;
        long hitsAtSample;
        int removals;
        int expiriesAtSample;
        long recoveriesAtSample;
        long summonRecoveries;
        final Map<String, Integer> recoverySources = new TreeMap<>();
        int minimumExpiry = Integer.MAX_VALUE;
        int maximumExpiry;
        int samples;
        int openings;

        Phase(int count, boolean combat) {
            this.combat = combat;
            label = count + (combat ? "-active" : "-idle");
            summons = new EntityZombieMinion[count];
        }

        void startSampling(long start) {
            sampleStart = start;
            startGcCount = gc(false);
            startGcTime = gc(true);
            hitsAtSample = hits;
            expiriesAtSample = removals;
            recoveriesAtSample = summonRecoveries;
        }

        void row(long now, long duration, int tick, int live, int targeted) {
            long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            rows.append(now - sampleStart).append(',').append(tick).append(',').append(duration).append(',')
                    .append(live).append(',').append(targets.length).append(',').append(targeted).append(',')
                    .append(hits - hitsAtSample).append(',').append(removals - expiriesAtSample).append(',').append(used).append('\n');
            samples++;
        }
    }
}
