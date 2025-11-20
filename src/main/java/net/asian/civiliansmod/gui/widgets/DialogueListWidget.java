package net.asian.civiliansmod.gui.widgets;

import net.asian.civiliansmod.chat.NpcChat;
import net.asian.civiliansmod.entity.NPCEntity;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DialogueListWidget{

    private final NPCEntity npc;

    private final int x, y, width, height;

    private final List<DialogueCategoryWidget> categories = new ArrayList<>();

    private double scrollY = 0;
    private double maxScrollY = 0;

    public DialogueListWidget(NPCEntity npc, int x, int y, int width, int height) {
        this.npc = npc;

        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;

        reloadFromNPC();
    }

    // load dialogues

    public void reloadFromNPC() {

        categories.clear();

        String lang = MinecraftClient.getInstance().getLanguageManager().getLanguage();

        Map<NpcChat.ChatReason, List<String>> map = npc.getChatManager().getTranslatedDialogues(lang);

        for (NpcChat.ChatReason reason : NpcChat.ChatReason.values()) {

            List<String> list = map.getOrDefault(reason, new ArrayList<>());
            DialogueCategoryWidget category = new DialogueCategoryWidget(npc, x + 4, 0, width - 12, reason, list, this::recalculate);
            categories.add(category);
        }
        recalculate();
    }

    // calc height and scroll

    private void recalculate() {

        int totalHeight = 0;

        for (DialogueCategoryWidget cat : categories) {
            totalHeight += cat.getHeight() + 4;
        }
        maxScrollY = Math.max(0, totalHeight - height);
        scrollY = MathHelper.clamp(scrollY, 0, maxScrollY);
    }

    // API

    public void setSelectionMode(boolean mode) {
        categories.forEach(c -> c.setSelectionMode(mode));
    }

    public List<String> getSelectedDialogues() {
        List<String> out = new ArrayList<>();
        categories.forEach(c -> out.addAll(c.getSelected()));
        return out;
    }

    public List<String> getAllDialoguesFor(NpcChat.ChatReason reason) {
        for (DialogueCategoryWidget c : categories) {
            if (c.getCategory() == reason)
                return c.getAllDialogues();
        }
        return new ArrayList<>();
    }

    public void deleteLocal(List<String> remove) {
        categories.forEach(c -> c.deleteLocal(remove));
        recalculate();
    }

    public void deleteAllLocal() {
        categories.forEach(DialogueCategoryWidget::deleteAllLocal);
        recalculate();
    }

    // RENDER

    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {

        context.enableScissor(x, y, x + width, y + height);

        int offsetY = y - (int) scrollY;

        for (DialogueCategoryWidget cat : categories) {

            cat.setX(x + 4);
            cat.setY(offsetY);

            cat.render(context, mouseX, mouseY, delta);

            offsetY += cat.getHeight() + 4;
        }

        context.disableScissor();

        renderScrollbar(context);
    }

    private void renderScrollbar(DrawContext context) {

        if (maxScrollY <= 0) return;

        int barWidth = 6;
        int barX = x + width - barWidth - 2;

        context.fill(barX, y, barX + barWidth, y + height, 0x22000000);

        float ratio = (float) (height / (float) (maxScrollY + height));
        int barHeight = Math.max(24, (int) (height * ratio));

        int barY = y + (int) ((scrollY / maxScrollY) * (height - barHeight));

        double mx = MinecraftClient.getInstance().mouse.getX() / MinecraftClient.getInstance().getWindow().getScaleFactor();
        double my = MinecraftClient.getInstance().mouse.getY() / MinecraftClient.getInstance().getWindow().getScaleFactor();

        boolean hovered =
                mx >= barX && mx <= barX + barWidth &&
                        my >= barY && my <= barY + barHeight;

        int thumbColor = hovered ? 0xFFFFFFFF : 0xFF999999;

        context.fill(barX + 1, barY + 1, barX + barWidth - 1, barY + barHeight - 1, thumbColor);
    }

    // mouseinput
    public boolean mouseClicked(double mouseX, double mouseY, int button) {

        if (mouseX < x || mouseX > x + width ||
                mouseY < y || mouseY > y + height)
            return false;

        for (DialogueCategoryWidget cat : categories) {
            if (cat.mouseClicked(mouseX, mouseY, button))
                return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {

        if (mouseX < x || mouseX > x + width ||
                mouseY < y || mouseY > y + height)
            return false;

        scrollY = MathHelper.clamp(scrollY - amount * 10, 0, maxScrollY);
        return true;
    }
}
