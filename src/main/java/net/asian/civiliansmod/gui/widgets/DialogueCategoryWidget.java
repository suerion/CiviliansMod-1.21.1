package net.asian.civiliansmod.gui.widgets;

import net.asian.civiliansmod.chat.NpcChat;
import net.asian.civiliansmod.entity.NPCEntity;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class DialogueCategoryWidget extends ClickableWidget {

    private final NPCEntity npc;
    private final NpcChat.ChatReason category;
    private final Runnable refreshScreen;

    private final List<DialogueEntryWidget> entries = new ArrayList<>();
    private DialogueAddButtonWidget addButton;

    private boolean open = true;

    private static final int HEADER_BG        = 0xFF2B2B2B;
    private static final int HEADER_BG_HOVER  = 0xFF3A3A3A;
    private static final int HEADER_BORDER    = 0xFF555555;
    private static final int HEADER_TEXT      = 0xFFFFFFFF;
    private static final int HEADER_COUNT     = 0xFFBBBBBB;

    // Layout constants
    private static final int REASON_HEIGHT = 18;
    private static final int ENTRY_HEIGHT = 14;
    private static final int COLUMN_WIDTH = 115;
    private static final int COLUMN_SPACING = 8;

    // Arrow icons
    private static final Identifier ARROW_OPEN = Identifier.of("civiliansmod", "textures/gui/open_arrow.png");
    private static final Identifier ARROW_CLOSED = Identifier.of("civiliansmod", "textures/gui/close_arrow.png");
    private static final Identifier ARROW_OPEN_HOVER = Identifier.of("civiliansmod", "textures/gui/open_arrow_hover.png");
    private static final Identifier ARROW_CLOSED_HOVER = Identifier.of("civiliansmod", "textures/gui/close_arrow_hover.png");

    public DialogueCategoryWidget(NPCEntity npc, int x, int y, int width, NpcChat.ChatReason category, List<String> dialogues, Runnable refreshScreen) {
        super(x, y, width, REASON_HEIGHT, Text.literal(""));
        this.npc = npc;
        this.category = category;
        this.refreshScreen = refreshScreen;

        initEntries(dialogues);
    }

    private void initEntries(List<String> list) {

        entries.clear();

        List<String> safeList = new ArrayList<>(list); // FIX: Copy!

        int index = 0;
        for (String s : safeList) {
            entries.add(new DialogueEntryWidget(npc, 0, 0, COLUMN_WIDTH, ENTRY_HEIGHT, category, s, index++, refreshScreen));
        }
        addButton = new DialogueAddButtonWidget(npc, 0, 0, COLUMN_WIDTH, ENTRY_HEIGHT, category, refreshScreen);
    }

    public void setSelectionMode(boolean mode) {
        entries.forEach(e -> e.setSelectionMode(mode));
    }

    public List<String> getSelected() {
        List<String> list = new ArrayList<>();
        for (DialogueEntryWidget e : entries) {
            if (e.isSelected()) list.add(e.getDialogue());
        }
        return list;
    }

    public List<String> getAllDialogues() {
        List<String> out = new ArrayList<>();
        for (DialogueEntryWidget e : entries) out.add(e.getDialogue());
        return out;
    }

    public void deleteLocal(List<String> remove) {
        entries.removeIf(e -> remove.contains(e.getDialogue()));
        refreshScreen.run();
    }

    public void deleteAllLocal() {
        entries.clear();
        refreshScreen.run();
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {}

    public NpcChat.ChatReason getCategory() {
        return category;
    }

    public boolean isOpen() {
        return open;
    }

    @Override
    public int getHeight() {
        if (!open)
            return REASON_HEIGHT;

        int total = entries.size() + 1;
        int rows = (int) Math.ceil(total / 2.0);
        return REASON_HEIGHT + rows * (ENTRY_HEIGHT + 2) + 4;
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {

        // REASON
        int background = isHovered() ? HEADER_BG_HOVER : HEADER_BG;
        context.fill(getX(), getY(), getX() + getWidth(), getY() + REASON_HEIGHT, background);

        // bottom border line
        context.fill(getX(), getY() + REASON_HEIGHT - 1, getX() + getWidth(), getY() + REASON_HEIGHT, HEADER_BORDER);

        boolean headerHovered =
                mouseX >= getX() && mouseX <= getX() + width &&
                        mouseY >= getY() && mouseY <= getY() + REASON_HEIGHT;

        Identifier arrow =
                open
                        ? (headerHovered ? ARROW_OPEN_HOVER : ARROW_OPEN)
                        : (headerHovered ? ARROW_CLOSED_HOVER : ARROW_CLOSED);

        int arrowY = getY() + (REASON_HEIGHT - 8) / 2;
        context.drawTexture(RenderPipelines.GUI_TEXTURED, arrow, getX() + 6, arrowY, 0, 0, 8, 8, 8, 8, 8, 8, -1);

        // Title
        var title = MinecraftClient.getInstance().textRenderer;
        int textY = getY() + (REASON_HEIGHT - title.fontHeight) / 2;

        String betterCategory= category.getName().substring(0,1).toUpperCase() + category.getName().substring(1).toLowerCase();
        context.drawText(title, Text.literal(betterCategory), getX() + 18, textY, HEADER_TEXT, false);


        // Count
        String count = "(" + entries.size() + ")";
        int countW = title.getWidth(count);
        context.drawText(title, Text.literal(count), getX() + getWidth() - countW - 6, textY, 0xAAAAAA, false);


        if (!open) return;

        int totalSpacing = COLUMN_SPACING;
        int usableWidth = getWidth() - totalSpacing - 4;

        int colWidth = usableWidth / 2;

        // ENTRIES
        int startY = getY() + REASON_HEIGHT + 3;

        int i = 0;
        for (DialogueEntryWidget entry : entries) {
            int col = i % 2;
            int row = i / 2;

            int ex = getX() + 2 + col * (colWidth + COLUMN_SPACING);
            int ey = startY + row * (ENTRY_HEIGHT + 2);

            entry.setWidth(colWidth);
            entry.setHeight(ENTRY_HEIGHT);
            entry.setX(ex);
            entry.setY(ey);
            entry.renderWidget(context, mouseX, mouseY, delta);
            i++;
        }

        // ADD BUTTON
        int total = entries.size();
        int addCol = total % 2;
        int addRow = total / 2;

        int ax = getX() + 2 + addCol * (colWidth + COLUMN_SPACING);
        int ay = startY + addRow * (ENTRY_HEIGHT + 2);

        addButton.setWidth(colWidth);
        addButton.setX(ax);
        addButton.setY(ay);
        addButton.renderWidget(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {

        // HEADER click
        if (mouseY >= getY() && mouseY <= getY() + REASON_HEIGHT &&
                mouseX >= getX() && mouseX <= getX() + getWidth()) {
            open = !open;
            this.setHeight(getHeight());
            refreshScreen.run();
            return true;
        }

        if (!open)
            return false;

        for (DialogueEntryWidget e : entries) {
            if (e.mouseClicked(mouseX, mouseY, button))
                return true;
        }

        return addButton.mouseClicked(mouseX, mouseY, button);
    }
}
