package net.asian.civiliansmod.gui;

import net.asian.civiliansmod.entity.NPCEntity;
import net.asian.civiliansmod.util.NPCUtil;
import net.asian.civiliansmod.util.SkinIdentifier;
import java.util.List;
import java.util.stream.IntStream;

public class SlimNPCScreen extends AbstractNPCScreen {
    public SlimNPCScreen(NPCEntity npc) {
        super(npc, Tab.SKINS); // Start on the Skins tab
    }

    @Override
    protected List<Integer> getSkinsToRender() {
        // Return only slim skins
        return IntStream.range(0, NPCUtil.getSkins().size())
                .filter(i -> {
                    SkinIdentifier skin = NPCUtil.getSkins().get(i);
                    return skin.slim() && !skin.custom();
                })
                .boxed()
                .toList();
    }
}