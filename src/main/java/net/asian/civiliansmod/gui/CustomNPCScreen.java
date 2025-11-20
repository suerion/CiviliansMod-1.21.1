package net.asian.civiliansmod.gui;

import net.asian.civiliansmod.CiviliansMod;
import net.asian.civiliansmod.chat.NpcChat;
import net.asian.civiliansmod.custom_skins.SkinFolderManager;
import net.asian.civiliansmod.entity.NPCEntity;
import net.asian.civiliansmod.util.NPCUtil;
import net.asian.civiliansmod.util.SkinIdentifier;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.stream.IntStream;


public class CustomNPCScreen extends AbstractNPCScreen {
    public CustomNPCScreen(NPCEntity npc) {
        super(npc, Tab.SKINS); // Start on the Skins tab
    }

    @Override
    protected List<Integer> getSkinsToRender() {
        // Return only custom skins
        return IntStream.range(0, NPCUtil.getSkins().size())
                .filter(i -> {
                    SkinIdentifier skin = NPCUtil.getSkins().get(i);
                    return skin.custom();
                })
                .boxed()
                .toList();
    }

    @Override
    protected void init() {
        super.init(); // This sets up the main layout and tabs from AbstractNPCScreen

        // The following buttons are specific to the Custom Skin screen
        if (this.currentTab == Tab.SKINS) {
            
            // Add buttons to open the custom skin folders, positioned on the left panel
            this.addDrawableChild(ButtonWidget.builder(Text.literal("↑ Open Wide Folder"), button -> SkinFolderManager.openFolder(SkinFolderManager.NPCModel.WIDE))
                    .dimensions(containerX + 8, containerY + 94, 100, 20).build());

            this.addDrawableChild(ButtonWidget.builder(Text.literal("↑ Open Slim Folder"), button -> SkinFolderManager.openFolder(SkinFolderManager.NPCModel.SLIM))
                    .dimensions(containerX + 8, containerY + 116, 100, 20).build());
            
        }
    }
}