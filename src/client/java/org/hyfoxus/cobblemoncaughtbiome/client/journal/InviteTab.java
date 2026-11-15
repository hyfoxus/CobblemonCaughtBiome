package org.hyfoxus.cobblemoncaughtbiome.client.journal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.hyfoxus.cobblemoncaughtbiome.soullink.SoulLinkColors;

final class InviteTab {
    private static final int ROW_HEIGHT = 24;
    private static final int GAP = 6;
    private static final int BANNER_HEIGHT = 54;

    private final Consumer<AbstractWidget> addWidget;
    private final Consumer<AbstractWidget> removeWidget;
    private final Consumer<String> sendCommand;
    private Font font;
    private List<String> players = List.of();
    private String pendingInviter;
    private String pendingToken = "";
    private int pendingTicksRemaining;
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private int left, panelWidth, contentTop, contentBottom;
    private double scroll;

    InviteTab(Consumer<AbstractWidget> addWidget, Consumer<AbstractWidget> removeWidget, Consumer<String> sendCommand) {
        this.addWidget = addWidget;
        this.removeWidget = removeWidget;
        this.sendCommand = sendCommand;
    }

    void setFont(Font font) { this.font = font; }

    void setLayout(int left, int panelWidth, int contentTop, int contentBottom) {
        this.left = left;
        this.panelWidth = panelWidth;
        this.contentTop = contentTop;
        this.contentBottom = contentBottom;
    }

    void setData(CompoundTag online) {
        List<String> names = new ArrayList<>();
        for (Tag tag : online.getList("players", Tag.TAG_STRING)) names.add(tag.getAsString());
        this.players = names;
        this.pendingInviter = online.contains("pendingInviter") ? online.getString("pendingInviter") : null;
        if (pendingInviter != null && pendingInviter.isEmpty()) pendingInviter = null;
        this.pendingToken = online.getString("pendingToken");
        this.pendingTicksRemaining = online.getInt("pendingTicksRemaining");
        scroll = Math.min(scroll, maxScroll());
    }

    private int bannerHeight() { return pendingInviter != null ? BANNER_HEIGHT : 0; }

    private int listTop() { return contentTop + bannerHeight() + (pendingInviter != null ? GAP : 0); }

    private int maxScroll() {
        return Math.max(0, players.size() * (ROW_HEIGHT + GAP) - GAP - (contentBottom - listTop()));
    }

    void tick() {
        if (pendingInviter == null) return;
        if (pendingTicksRemaining > 0) pendingTicksRemaining--;
        if (pendingTicksRemaining <= 0) {
            pendingInviter = null;
            rebuildWidgets();
        }
    }

    void rebuildWidgets() {
        widgets.forEach(removeWidget);
        widgets.clear();
        if (pendingInviter != null) {
            int bannerTop = contentTop;
            int pairWidth = 90 + GAP + 90;
            int pairLeft = left + panelWidth / 2 - pairWidth / 2;
            AbstractWidget accept = new JournalButton(pairLeft, bannerTop + 28, 90, Component.literal("Accept"), false,
                    button -> { sendCommand.accept("soullink accept " + pendingToken); sendCommand.accept("soullink menu"); });
            AbstractWidget decline = new JournalButton(pairLeft + 90 + GAP, bannerTop + 28, 90, Component.literal("Decline"), false,
                    button -> { sendCommand.accept("soullink decline " + pendingToken); sendCommand.accept("soullink menu"); });
            addWidget.accept(accept);
            addWidget.accept(decline);
            widgets.add(accept);
            widgets.add(decline);
        }
        int top = listTop();
        for (int i = 0; i < players.size(); i++) {
            int y = top + i * (ROW_HEIGHT + GAP) - (int) scroll;
            String name = players.get(i);
            JournalButton invite = new JournalButton(left + panelWidth - 90, y + 2, 76, Component.literal("Invite"), false,
                    button -> sendCommand.accept("soullink invite " + name));
            invite.visible = y + ROW_HEIGHT >= contentTop && y <= contentBottom;
            addWidget.accept(invite);
            widgets.add(invite);
        }
    }

    boolean mouseScrolled(double mouseX, double mouseY, double vertical) {
        if (mouseY >= contentTop && mouseY <= contentBottom && mouseX >= left && mouseX <= left + panelWidth) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - vertical * 20));
            rebuildWidgets();
            return true;
        }
        return false;
    }

    void renderBackgrounds(GuiGraphics graphics) {
        if (pendingInviter != null) {
            int y = contentTop;
            graphics.fill(left + 12, y, left + panelWidth - 12, y + BANNER_HEIGHT - GAP, 0xFF1C2A33);
        }
        if (players.isEmpty()) return;
        graphics.enableScissor(left + 12, contentTop, left + panelWidth - 9, contentBottom);
        for (int i = 0; i < players.size(); i++) {
            int y = listTop() + i * (ROW_HEIGHT + GAP) - (int) scroll;
            if (y + ROW_HEIGHT < contentTop || y > contentBottom) continue;
            graphics.fill(left + 14, y, left + panelWidth - 14, y + ROW_HEIGHT, 0xFF17222B);
        }
        graphics.disableScissor();
    }

    void renderLabels(GuiGraphics graphics) {
        if (pendingInviter != null) {
            int y = contentTop;
            graphics.renderOutline(left + 12, y, panelWidth - 24, BANNER_HEIGHT - GAP, 0xFF000000 | SoulLinkColors.GOLD);
            String countdown = Math.max(0, (pendingTicksRemaining + 19) / 20) + "s";
            graphics.drawString(font, countdown, left + panelWidth - 20 - font.width(countdown), y + 7, SoulLinkColors.TEXT, false);
            graphics.drawCenteredString(font, fit(pendingInviter + " invited you to a run", panelWidth - 40 - font.width(countdown) * 2),
                    left + panelWidth / 2, y + 7, SoulLinkColors.TEXT);
        }
        if (players.isEmpty()) {
            graphics.drawCenteredString(font, "No other players online.", left + panelWidth / 2, listTop() + 10, SoulLinkColors.TEXT);
            return;
        }
        graphics.enableScissor(left + 12, contentTop, left + panelWidth - 9, contentBottom);
        for (int i = 0; i < players.size(); i++) {
            int y = listTop() + i * (ROW_HEIGHT + GAP) - (int) scroll;
            if (y + ROW_HEIGHT < contentTop || y > contentBottom) continue;
            graphics.renderOutline(left + 14, y, panelWidth - 28, ROW_HEIGHT, 0xFF34434F);
            graphics.drawString(font, fit(players.get(i), panelWidth - 130), left + 22, y + 8, SoulLinkColors.TEXT, false);
        }
        graphics.disableScissor();
    }

    private String fit(String text, int maxWidth) {
        return font.width(text) <= maxWidth ? text : font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…";
    }
}
