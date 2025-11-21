package net.asian.civiliansmod.entity.goal;

import net.asian.civiliansmod.entity.NPCEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.world.WorldView;
import java.util.EnumSet;

public class NPCFollowOwnerGoal extends Goal {
    private final NPCEntity npc;
    private LivingEntity owner;
    /**
     *
     */
    private final WorldView world;
    public WorldView getWorld() {
        return world;
    }

    private final double speed;
    private final net.minecraft.entity.ai.pathing.EntityNavigation navigation;
    private int updateCountdownTicks;
    private final float maxDistance;
    private final float minDistance;
    private float oldWaterPathfindingPenalty;

    public NPCFollowOwnerGoal(NPCEntity npc, double speed, float minDistance, float maxDistance) {
        this.npc = npc;
        this.world = npc.getWorld();
        this.speed = speed;
        this.navigation = npc.getNavigation();
        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
        this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
    }

    @Override
    public boolean canStart() {
        // Only follow if Battle Buddy or follow is active and the NPC is not paused.
        if (!(this.npc.isFollowing() || this.npc.isBattleBuddy())) {
            return false;
        }

        if (this.npc.isPaused()) {
            return false;
        }

        LivingEntity livingEntity = this.npc.getOwner();
        if (livingEntity == null || livingEntity.isSpectator() || this.npc.squaredDistanceTo(livingEntity) < (double)(this.minDistance * this.minDistance)) {
            return false;
        }
        
        this.owner = livingEntity;
        return true;
    }

    @Override
    public boolean shouldContinue() {
        return (this.npc.isFollowing() || this.npc.isBattleBuddy()) && !this.npc.isPaused();
    }

    @Override
    public void start() {
        this.updateCountdownTicks = 0;
        this.oldWaterPathfindingPenalty = this.npc.getPathfindingPenalty(PathNodeType.WATER);
        this.npc.setPathfindingPenalty(PathNodeType.WATER, 0.0f);
    }

    @Override
    public void stop() {
        this.owner = null;
        this.navigation.stop();
        this.npc.setPathfindingPenalty(PathNodeType.WATER, this.oldWaterPathfindingPenalty);
    }

    @Override
    public void tick() {
        this.npc.getLookControl().lookAt(this.owner, 10.0f, (float)this.npc.getMaxLookPitchChange());
        if (--this.updateCountdownTicks > 0) {
            return;
        }
        this.updateCountdownTicks = this.getTickCount(10);
        
        if (!this.npc.isLeashed() && !this.npc.hasVehicle()) {
            this.navigation.startMovingTo(this.owner, this.speed);
        }
    }
}