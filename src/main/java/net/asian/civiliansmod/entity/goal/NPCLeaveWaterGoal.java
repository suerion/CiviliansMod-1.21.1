package net.asian.civiliansmod.entity.goal;

import net.asian.civiliansmod.entity.NPCEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.EnumSet;

public class NPCLeaveWaterGoal extends Goal {

    private final NPCEntity npc;
    private final double speed;

    private BlockPos targetPos;

    public NPCLeaveWaterGoal(NPCEntity npc, double speed) {
        this.npc = npc;
        this.speed = speed;
        this.setControls(EnumSet.of(Control.MOVE, Control.JUMP));
    }

    @Override
    public boolean canStart() {
        // Only when he is in water
        if (!npc.isTouchingWater()) return false;

        // Try to find nearby dry land
        this.targetPos = findNearestLand(npc.getWorld(), npc.getBlockPos(), 8, 4, 8);
        return this.targetPos != null;
    }

    @Override
    public boolean shouldContinue() {
        // Continue while in water and path not finished
        return npc.isTouchingWater() && !npc.getNavigation().isIdle();
    }

    @Override
    public void start() {
        if (this.targetPos != null) {
            // Move to center of block on X/Z
            npc.getNavigation().startMovingTo(
                    targetPos.getX() + 0.5,
                    targetPos.getY(),
                    targetPos.getZ() + 0.5,
                    this.speed
            );
        }
    }

    // Simple search for nearest non-water block with solid ground under it
    private BlockPos findNearestLand(World world, BlockPos origin, int radiusX, int radiusY, int radiusZ) {
        BlockPos bestPos = null;
        double bestDistanceSq = Double.MAX_VALUE;

        for (int dx = -radiusX; dx <= radiusX; dx++) {
            for (int dy = -radiusY; dy <= radiusY; dy++) {
                for (int dz = -radiusZ; dz <= radiusZ; dz++) {
                    BlockPos pos = origin.add(dx, dy, dz);

                    // Skip if still water at this pos
                    if (world.getFluidState(pos).isIn(FluidTags.WATER)) continue;

                    // Need solid block below to stand on
                    BlockPos below = pos.down();
                    if (!world.getBlockState(below).isSolidBlock(world, below)) continue;

                    double distSq = pos.getSquaredDistance(origin);
                    if (distSq < bestDistanceSq) {
                        bestDistanceSq = distSq;
                        bestPos = pos.toImmutable();
                    }
                }
            }
        }

        return bestPos;
    }
}
