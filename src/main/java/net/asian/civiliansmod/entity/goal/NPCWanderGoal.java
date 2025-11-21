package net.asian.civiliansmod.entity.goal;

import net.asian.civiliansmod.entity.NPCEntity;
import net.minecraft.entity.ai.FuzzyTargeting;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

public class NPCWanderGoal extends WanderAroundGoal {

    private final NPCEntity npc;

    public NPCWanderGoal(NPCEntity npc, double speed) {
        super(npc, speed, 10);
        this.npc = npc;
    }

    @Override
    public boolean canStart() {
        return !npc.isPaused() && !npc.isFollowing() && !npc.isBattleBuddy() && super.canStart();
    }

    @Override
    public boolean shouldContinue() {
        return canStart();
    }

    @Nullable
    @Override
    protected Vec3d getWanderTarget() {

        BlockPos anchor = npc.getWanderAnchor();
        float radius = npc.getWanderRadius();
        Random random = npc.getRandom();

        for (int i = 0; i < 10; i++) {

            double angle = random.nextFloat() * Math.PI * 2;
            double dist = radius * Math.sqrt(random.nextDouble());

            double x = anchor.getX() + 0.5 + dist * Math.cos(angle);
            double z = anchor.getZ() + 0.5 + dist * Math.sin(angle);

            // Vanilla random walk logic with terrain detection
            Vec3d target = FuzzyTargeting.findTo(npc, 12, 7, new Vec3d(x, npc.getY(), z));

            if (target != null) return target;
        }

        // Fallback = Anchor
        return anchor.toCenterPos();
    }
}