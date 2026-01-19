package net.asian.civiliansmod.entity.goal;

import net.asian.civiliansmod.entity.NPCEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.ai.goal.TrackTargetGoal;
import net.minecraft.server.world.ServerWorld;

import java.util.EnumSet;

public class NPCDefendOwnerGoal extends TrackTargetGoal {
    private final NPCEntity npc;
    private LivingEntity attacker;
    private int lastAttackedTime;

    private final TargetPredicate defendPredicate = TargetPredicate.createAttackable().ignoreVisibility();


    public NPCDefendOwnerGoal(NPCEntity npc) {
        super(npc, false);
        this.npc = npc;
        this.setControls(EnumSet.of(Control.TARGET));
    }

    @Override
    public boolean canStart() {
        // Only run if in Battle Buddy mode
        if (!this.npc.isBattleBuddy()) {
            return false;
        }

        LivingEntity owner = this.npc.getOwner();
        if (owner == null || owner.isDead()) {
            return false;
        }

        // Get the entity that last attacked the owner
        this.attacker = owner.getAttacker();
        int attackedTime = owner.getLastAttackedTime();

        // If no attacker → skip
        if (attacker == null || !attacker.isAlive()) return false;

        if (attacker == this.npc) return false;

        // Only react to NEW hits
        if (attackedTime == this.lastAttackedTime) return false;

        // Validate attacker using predicate
        return defendPredicate.test((ServerWorld) npc.getWorld(), npc, attacker);
    }

    @Override
    public void start() {
        // Set the NPC's target to the owner's attacker
        this.mob.setTarget(this.attacker);
        LivingEntity owner = this.npc.getOwner();
        if (owner != null) {
            this.lastAttackedTime = owner.getLastAttackedTime();
        }
        super.start();
    }
}