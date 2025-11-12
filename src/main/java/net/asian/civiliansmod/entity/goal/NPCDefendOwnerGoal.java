package net.asian.civiliansmod.entity.goal;

import net.asian.civiliansmod.entity.NPCEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.ai.goal.TrackTargetGoal;
import java.util.EnumSet;

public class NPCDefendOwnerGoal extends TrackTargetGoal {
    private final NPCEntity npc;
    private LivingEntity attacker;
    private int lastAttackedTime;

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
        int i = owner.getLastAttackedTime();
        // Check if the attack is new and if we can target the attacker
        // CORRECTED LINE: Use the getter method for the predicate
        return i != this.lastAttackedTime && this.canTrack(this.attacker, this.getTargetPredicate());
    }

    private TargetPredicate getTargetPredicate() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getTargetPredicate'");
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