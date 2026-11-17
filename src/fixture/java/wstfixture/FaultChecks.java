package wstfixture;

import com.mahghuuuls.wizardrysummontweaks.summon.SummonFacts;
import com.mahghuuuls.wizardrysummontweaks.summon.SummonProvider;
import com.mahghuuuls.wizardrysummontweaks.summon.SummonTracker;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;

import static wstfixture.IdentityFixture.*;

/** Component fault injection in a real server; this does not establish native spell provenance. */
public final class FaultChecks {
    public static void run(MinecraftServer server) {
        Entity bad = new EntityArmorStand(server.worlds[0]);
        Entity good = new EntityArmorStand(server.worlds[0]);
        Entity ordinary = new EntityArmorStand(server.worlds[0]);
        int[] failures = {0};
        SummonProvider provider = new SummonProvider() {
            @Override public boolean matches(Entity entity) {
                return entity.getUniqueID().equals(bad.getUniqueID()) || entity == good;
            }
            @Override public SummonFacts observe(Entity entity) {
                if (entity.getUniqueID().equals(bad.getUniqueID())) {
                    failures[0]++;
                    throw new IllegalStateException("intentional fixture observation failure");
                }
                return new SummonFacts("fixture:verified_contract", SECOND_PLAYER, SummonFacts.OwnerKind.PLAYER, "fixture_player");
            }
        };
        SummonTracker tracker = new SummonTracker(LogManager.getLogger("WSTFaultFixture"));
        tracker.start(server, provider);
        String activeState = tracker.providerState();
        NBTTagCompound before = bad.writeToNBT(new NBTTagCompound()).copy();
        require(tracker.inspect(bad).status == SummonFacts.Status.UNRESOLVED, "failure accepted as established evidence");
        require(tracker.inspect(good).affected(), "failed inspection changed valid entity classification");
        require(tracker.inspect(ordinary).reason.equals("no_verified_spell_origin"), "failure leaked to ordinary control");
        require(activeState.equals(tracker.providerState()), "inspection changed provider state");
        require(before.equals(bad.writeToNBT(new NBTTagCompound())), "inspection wrote entity data");
        tracker.refresh(bad);
        tracker.refresh(bad);
        require(failures[0] == 2, "failed entity was repeatedly observed after quarantine");
        require(tracker.inspect(good).affected(), "refresh failure disabled a valid entity");
        require(tracker.inspect(ordinary).status == SummonFacts.Status.EXCLUDED, "refresh failure changed ordinary control");
        require(!tracker.inspect(bad).mayPersist, "failed observation can overwrite saved facts");
        Entity reloaded = new EntityArmorStand(server.worlds[0]);
        reloaded.setUniqueId(bad.getUniqueID());
        tracker.inspect(reloaded);
        require(failures[0] == 3, "new loaded instance retained another instance's quarantine");
        tracker.stop();
        tracker.start(server, provider);
        tracker.inspect(bad);
        require(failures[0] == 4, "server reopening retained stale quarantine");
        tracker.stop();
        output("FAULT_ISOLATION_PASS observations=" + failures[0]);
    }
}
