package com.mahghuuuls.wizardrysummontweaks.command;

import com.mahghuuuls.wizardrysummontweaks.config.ServerRules;
import com.mahghuuuls.wizardrysummontweaks.summon.SummonFacts;
import com.mahghuuuls.wizardrysummontweaks.summon.SummonTracker;
import com.mojang.authlib.GameProfile;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import java.util.UUID;
import java.util.function.Supplier;

public final class InspectCommand extends CommandBase {
    private final SummonTracker tracker;
    private final Supplier<ServerRules> rules;

    public InspectCommand(SummonTracker tracker, Supplier<ServerRules> rules) {
        this.tracker = tracker;
        this.rules = rules;
    }

    @Override public String getName() { return "wizardrysummontweaks"; }
    @Override public String getUsage(ICommandSender sender) { return "/wizardrysummontweaks inspect [uuid]"; }
    @Override public int getRequiredPermissionLevel() { return 2; }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 1 || args.length > 2 || !"inspect".equals(args[0])) {
            throw new CommandException(getUsage(sender));
        }
        Entity entity = args.length == 2 ? byUUID(server, args[1]) : byRay(sender);
        SummonFacts.Result result = tracker.inspect(entity);
        SummonFacts facts = result.facts;
        ResourceLocation type = EntityList.getKey(entity);
        sender.sendMessage(new TextComponentString("Entity " + (type == null ? entity.getClass().getName() : type) + " uuid=" + entity.getUniqueID()
                + " dimension=" + entity.dimension + " source=" + entity.getClass().getName()));
        sender.sendMessage(new TextComponentString("Classification=" + result.status + " reason=" + result.reason
                + " origin=" + (facts == null ? "unknown" : facts.originProvider)));
        String owner = "unknown";
        if (facts != null) {
            GameProfile profile = facts.ownerKind == SummonFacts.OwnerKind.PLAYER && facts.ownerUUID != null
                    ? server.getPlayerProfileCache().getProfileByUUID(facts.ownerUUID) : null;
            owner = facts.ownerKind + " uuid=" + facts.ownerUUID + " name=" + (profile == null ? "unknown" : profile.getName());
        }
        sender.sendMessage(new TextComponentString("Owner=" + owner));
        ServerRules active = rules.get();
        sender.sendMessage(new TextComponentString("suppressSummonDrops=" + active.suppressSummonDrops
                + " applicable=" + (active.suppressSummonDrops && result.affected())
                + "; protectPlayers=" + active.protectPlayers + " applicable=" + (active.protectPlayers && result.affected())
                + "; protectSummons=" + active.protectSummons + " applicable=" + (active.protectSummons && result.affected())));
        ModContainer threatworks = Loader.instance().getIndexedModList().get("threatworks");
        sender.sendMessage(new TextComponentString("Active values apply until server restart/world reopening. Wizardry adapter="
                + tracker.providerState() + "; Threatworks=" + (threatworks == null ? "absent" : threatworks.getVersion())
                + "; dedicated Threatworks adapter=none"));
    }

    private Entity byUUID(MinecraftServer server, String text) throws CommandException {
        UUID uuid;
        try {
            uuid = UUID.fromString(text);
            if (!uuid.toString().equalsIgnoreCase(text)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException invalid) { throw new CommandException("Expected a complete entity UUID"); }
        Entity found = null;
        for (WorldServer world : server.worlds) {
            Entity entity = world.getEntityFromUuid(uuid);
            if (entity == null || entity.isDead) continue;
            if (found != null) throw new CommandException("UUID is ambiguous across loaded worlds");
            found = entity;
        }
        if (found == null) throw new CommandException("No loaded entity has UUID " + uuid);
        return found;
    }

    private Entity byRay(ICommandSender sender) throws CommandException {
        Entity source = sender.getCommandSenderEntity();
        if (!(source instanceof EntityPlayer)) throw new CommandException("Console inspection requires an entity UUID");
        EntityPlayer player = (EntityPlayer) source;
        Vec3d start = player.getPositionEyes(1.0F);
        Vec3d direction = player.getLook(1.0F).scale(8.0);
        Vec3d end = start.add(direction);
        RayTraceResult block = player.world.rayTraceBlocks(start, end, false, true, false);
        double distance = block == null ? Double.POSITIVE_INFINITY : start.distanceTo(block.hitVec);
        Entity selected = null;
        boolean ambiguous = false;
        AxisAlignedBB region = player.getEntityBoundingBox().expand(direction.x, direction.y, direction.z).grow(1.0);
        for (Entity entity : player.world.getEntitiesInAABBexcluding(player, region, candidate -> !candidate.isDead)) {
            AxisAlignedBB box = entity.getEntityBoundingBox().grow(entity.getCollisionBorderSize());
            RayTraceResult hit = box.calculateIntercept(start, end);
            double candidateDistance = box.contains(start) ? 0 : hit == null ? Double.POSITIVE_INFINITY : start.distanceTo(hit.hitVec);
            if (candidateDistance <= 8.0 && candidateDistance < distance) {
                distance = candidateDistance;
                selected = entity;
                ambiguous = false;
            } else if (selected != null && Math.abs(candidateDistance - distance) < 0.000001) {
                ambiguous = true;
            }
        }
        if (selected == null) throw new CommandException("No entity in view within eight blocks before an obstruction");
        if (ambiguous) throw new CommandException("Selection is ambiguous; supply a UUID");
        return selected;
    }
}
