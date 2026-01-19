package net.asian.civiliansmod.entity.goal;

import net.asian.civiliansmod.entity.NPCEntity;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import static net.asian.civiliansmod.CiviliansMod.LOGGER;
import static net.asian.civiliansmod.CiviliansMod.DEBUG_AI;
import static net.asian.civiliansmod.CiviliansMod.DEBUG_AI_WANDER;

public class NPCWanderGoal extends WanderAroundGoal {

    private final NPCEntity npc;

    public NPCWanderGoal(NPCEntity npc, double speed) {
        super(npc, speed, 80);
        this.npc = npc;
    }

    @Override
    public boolean canStart() {
        return !npc.isPaused() && !npc.isFollowing() && !npc.isBattleBuddy() && super.canStart();
    }

    @Override
    public boolean shouldContinue() {
        boolean cont = !npc.getNavigation().isIdle();

        if (DEBUG_AI_WANDER) {
            LOGGER.info("[CIVILIANS][WANDER] shouldContinue={} (idle={})",
                    cont, npc.getNavigation().isIdle());
        }

        return cont;
    }

    @Nullable
    @Override
    protected Vec3d getWanderTarget() {

        BlockPos anchor = npc.getWanderAnchor();
        float radius = npc.getWanderRadius();
        Random random = npc.getRandom();

        if (DEBUG_AI_WANDER || DEBUG_AI) {
            LOGGER.info("[CIVILIANS][WANDER] NPC={} Anchor={} Radius={}",
                    npc.getName().getString(), anchor, radius);
        }

        if (radius < 1.0f) radius = 1.0f;

        // try 30 random points
        for (int i = 0; i < 30; i++) {

            double angle = random.nextDouble() * Math.PI * 2.0;
            double dist = radius * Math.sqrt(random.nextDouble());

            int px = anchor.getX() + (int) Math.round(Math.cos(angle) * dist);
            int pz = anchor.getZ() + (int) Math.round(Math.sin(angle) * dist);

            BlockPos pos = new BlockPos(px, anchor.getY(), pz);

            if (DEBUG_AI_WANDER) {
                LOGGER.debug("[CIVILIANS][WANDER] Try {} - Candidate Pos=({}, {}, {})",
                        i, pos.getX(), pos.getY(), pos.getZ());
            }

            BlockPos ground = findGround(pos, 6);

            if (ground == null) {
                if (DEBUG_AI_WANDER) {
                    LOGGER.debug("[CIVILIANS][WANDER] rejected: no ground");
                }
                continue;
            }

            BlockPos below = ground.down();
            if (!npc.getWorld().getBlockState(below).isSolidBlock(npc.getWorld(), below)) {
                if (DEBUG_AI_WANDER) {
                    LOGGER.debug("[CIVILIANS][WANDER] rejected: not solid below");
                }
                continue;
            }

            if (DEBUG_AI_WANDER) {
                LOGGER.info("[CIVILIANS][WANDER] accepted - {}", ground);
            }

            return Vec3d.ofCenter(ground);
        }

        // fallback
        if (DEBUG_AI_WANDER) {
            LOGGER.warn("[CIVILIANS][WANDER] !!! FALLBACK TO ANCHOR FOR {} !!!",
                    npc.getName().getString());
        }
        return anchor.toCenterPos();
    }

    @Nullable
    private BlockPos findGround(BlockPos start, int range) {
        BlockPos.Mutable pos = start.mutableCopy();

        // search downward
        for (int i = 0; i < range; i++) {
            BlockPos below = pos.down();
            if (npc.getWorld().getBlockState(below).isSolidBlock(npc.getWorld(), below)) {
                return pos.toImmutable();
            }
            pos.move(Direction.DOWN);
        }

        // search upward
        pos = start.mutableCopy();
        for (int i = 0; i < range; i++) {
            BlockPos below = pos.down();
            if (npc.getWorld().getBlockState(below).isSolidBlock(npc.getWorld(), below)) {
                return pos.toImmutable();
            }
            pos.move(Direction.UP);
        }
        return null;
    }
}