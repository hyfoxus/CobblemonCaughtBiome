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

final class RunsTab {
    private static final int ROW_HEIGHT = 24;
    private static final int RUN_HEIGHT = 34;
    private static final int LABEL_HEIGHT = 16;
    private static final int GAP = 6;
    private static final int BANNER_HEIGHT = 54;
    private static final int BUTTON_HEIGHT = 20;
    private static final int RUN_BUTTON_WIDTH = 60;

    private final Consumer<AbstractWidget> addWidget;
    private final Consumer<AbstractWidget> removeWidget;
    private final Consumer<String> sendCommand;
    private Font font;
    private List<RunRow> runs = List.of();
    private boolean hasSelected;
    private List<String> players = List.of();
    private String pendingInviter;
    private String pendingToken = "";
    private int pendingTicksRemaining;
    private String endingRunId;
    private int endingTicks;
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private int left, panelWidth, contentTop, contentBottom;
    private double scroll;

    RunsTab(Consumer<AbstractWidget> addWidget, Consumer<AbstractWidget> removeWidget, Consumer<String> sendCommand) {
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
        List<RunRow> parsed = new ArrayList<>();
        for (Tag tag : online.getList("runs", Tag.TAG_COMPOUND)) {
            CompoundTag run = (CompoundTag) tag;
            parsed.add(new RunRow(run.getString("id"), run.getString("title"), run.getString("members"),
                    run.getBoolean("solo"), run.getBoolean("paused"), run.getBoolean("selected"),
                    run.getInt("caught"), run.getInt("linked")));
        }
        this.runs = parsed;
        this.hasSelected = online.contains("selected", Tag.TAG_STRING);
        if (endingRunId != null && parsed.stream().noneMatch(run -> run.id().equals(endingRunId))) {
            endingRunId = null;
            endingTicks = 0;
        }
        List<String> names = new ArrayList<>();
        for (Tag tag : online.getList("players", Tag.TAG_STRING)) names.add(tag.getAsString());
        this.players = names;
        this.pendingInviter = online.contains("pendingInviter") ? online.getString("pendingInviter") : null;
        if (pendingInviter != null && pendingInviter.isEmpty()) pendingInviter = null;
        this.pendingToken = online.getString("pendingToken");
        this.pendingTicksRemaining = online.getInt("pendingTicksRemaining");
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    /** Title of the selected run, or null in free play. */
    String selectedTitle() {
        if (!hasSelected) return null;
        return runs.stream().filter(RunRow::selected).map(RunRow::title).findFirst().orElse(null);
    }

    // Offsets below are relative to the top of the scrolling list, before scrolling.
    private int runsTop() { return ROW_HEIGHT + GAP; }

    private int runTop(int index) { return runsTop() + index * (RUN_HEIGHT + GAP); }

    private int bannerTop() {
        return runsTop() + (runs.isEmpty() ? LABEL_HEIGHT + GAP : runs.size() * (RUN_HEIGHT + GAP));
    }

    private int inviteLabelTop() { return bannerTop() + (pendingInviter != null ? BANNER_HEIGHT : 0); }

    private int playerTop(int index) { return inviteLabelTop() + LABEL_HEIGHT + index * (ROW_HEIGHT + GAP); }

    private int totalHeight() {
        return inviteLabelTop() + LABEL_HEIGHT + (players.isEmpty() ? LABEL_HEIGHT : players.size() * (ROW_HEIGHT + GAP) - GAP);
    }

    private int maxScroll() { return Math.max(0, totalHeight() - (contentBottom - contentTop)); }

    private int screenY(int offset) { return contentTop + offset - (int) scroll; }

    private boolean inView(int y, int height) { return y + height >= contentTop && y <= contentBottom; }

    private boolean buttonVisible(int y) { return y >= contentTop && y + BUTTON_HEIGHT <= contentBottom; }

    private int switchX() { return left + panelWidth - 14 - 6 - RUN_BUTTON_WIDTH * 2 - GAP; }

    private int endX() { return left + panelWidth - 14 - 6 - RUN_BUTTON_WIDTH; }

    void tick() {
        if (endingTicks > 0) {
            endingTicks--;
            if (endingTicks == 0) {
                endingRunId = null;
                rebuildWidgets();
            }
        }
        if (pendingInviter == null) return;
        if (pendingTicksRemaining > 0) pendingTicksRemaining--;
        if (pendingTicksRemaining <= 0) {
            pendingInviter = null;
            scroll = Math.max(0, Math.min(scroll, maxScroll()));
            rebuildWidgets();
        }
    }

    void rebuildWidgets() {
        widgets.forEach(removeWidget);
        widgets.clear();
        int topY = screenY(0) + 2;
        add(new JournalButton(left + 14, topY, 100, Component.literal("New solo run"), false, button -> {
            sendCommand.accept("soullink solo");
            sendCommand.accept("soullink menu");
        }), buttonVisible(topY));
        add(new JournalButton(left + 14 + 100 + GAP, topY, 80, Component.literal("Free play"), !hasSelected, button -> {
            sendCommand.accept("soullink free");
            sendCommand.accept("soullink online");
        }), buttonVisible(topY));
        for (int i = 0; i < runs.size(); i++) {
            RunRow run = runs.get(i);
            String id = run.id();
            int buttonY = screenY(runTop(i)) + (RUN_HEIGHT - BUTTON_HEIGHT) / 2;
            if (!run.selected()) {
                add(new JournalButton(switchX(), buttonY, RUN_BUTTON_WIDTH, Component.literal("Switch"), false, button -> {
                    sendCommand.accept("soullink switch " + id);
                    sendCommand.accept("soullink menu");
                }), buttonVisible(buttonY));
            }
            boolean armed = id.equals(endingRunId) && endingTicks > 0;
            add(new JournalButton(endX(), buttonY, RUN_BUTTON_WIDTH, Component.literal(armed ? "Confirm?" : "End"), false,
                    button -> {
                        if (id.equals(endingRunId) && endingTicks > 0) {
                            endingRunId = null;
                            endingTicks = 0;
                            sendCommand.accept("soullink end " + id + " confirm");
                            sendCommand.accept("soullink online");
                        } else {
                            endingRunId = id;
                            endingTicks = 100;
                            sendCommand.accept("soullink end " + id);
                            rebuildWidgets();
                        }
                    }), buttonVisible(buttonY));
        }
        if (pendingInviter != null) {
            int bannerY = screenY(bannerTop());
            int pairWidth = 90 + GAP + 90;
            int pairLeft = left + panelWidth / 2 - pairWidth / 2;
            String token = pendingToken;
            add(new JournalButton(pairLeft, bannerY + 28, 90, Component.literal("Accept"), false,
                    button -> { sendCommand.accept("soullink accept " + token); sendCommand.accept("soullink menu"); }),
                    buttonVisible(bannerY + 28));
            add(new JournalButton(pairLeft + 90 + GAP, bannerY + 28, 90, Component.literal("Decline"), false,
                    button -> { sendCommand.accept("soullink decline " + token); sendCommand.accept("soullink online"); }),
                    buttonVisible(bannerY + 28));
        }
        for (int i = 0; i < players.size(); i++) {
            int y = screenY(playerTop(i));
            String name = players.get(i);
            add(new JournalButton(left + panelWidth - 90, y + 2, 76, Component.literal("Invite"), false,
                    button -> sendCommand.accept("soullink invite " + name)), buttonVisible(y + 2));
        }
    }

    private void add(AbstractWidget widget, boolean visible) {
        widget.visible = visible;
        addWidget.accept(widget);
        widgets.add(widget);
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
        graphics.enableScissor(left + 12, contentTop, left + panelWidth - 9, contentBottom);
        for (int i = 0; i < runs.size(); i++) {
            int y = screenY(runTop(i));
            if (!inView(y, RUN_HEIGHT)) continue;
            graphics.fill(left + 14, y, left + panelWidth - 14, y + RUN_HEIGHT, 0xFF17222B);
        }
        if (pendingInviter != null) {
            int y = screenY(bannerTop());
            if (inView(y, BANNER_HEIGHT - GAP)) {
                graphics.fill(left + 12, y, left + panelWidth - 12, y + BANNER_HEIGHT - GAP, 0xFF1C2A33);
            }
        }
        for (int i = 0; i < players.size(); i++) {
            int y = screenY(playerTop(i));
            if (!inView(y, ROW_HEIGHT)) continue;
            graphics.fill(left + 14, y, left + panelWidth - 14, y + ROW_HEIGHT, 0xFF17222B);
        }
        graphics.disableScissor();
    }

    void renderLabels(GuiGraphics graphics) {
        graphics.enableScissor(left + 12, contentTop, left + panelWidth - 9, contentBottom);
        int textWidth = switchX() - (left + 22) - 8;
        if (runs.isEmpty()) {
            graphics.drawCenteredString(font, fit("No runs yet. Start a solo run or invite a player.", panelWidth - 40),
                    left + panelWidth / 2, screenY(runsTop()) + 4, SoulLinkColors.TEXT);
        }
        for (int i = 0; i < runs.size(); i++) {
            RunRow run = runs.get(i);
            int y = screenY(runTop(i));
            if (!inView(y, RUN_HEIGHT)) continue;
            graphics.renderOutline(left + 14, y, panelWidth - 28, RUN_HEIGHT,
                    run.selected() ? 0xFF000000 | SoulLinkColors.TEAL : 0xFF34434F);
            graphics.drawString(font, fit(run.title(), textWidth), left + 22, y + 7,
                    run.selected() ? SoulLinkColors.TEAL : SoulLinkColors.TEXT, false);
            String counts = run.solo() ? run.caught() + " caught" : run.caught() + " caught  ·  " + run.linked() + " linked";
            String details = run.members() + "  ·  " + (run.paused() ? "Paused" : "Active") + "  ·  " + counts;
            graphics.drawString(font, fit(details, textWidth), left + 22, y + 20, SoulLinkColors.TEXT, false);
            if (run.selected()) {
                graphics.drawCenteredString(font, "Selected", switchX() + RUN_BUTTON_WIDTH / 2,
                        y + (RUN_HEIGHT - 8) / 2, SoulLinkColors.TEAL);
            }
        }
        if (pendingInviter != null) {
            int y = screenY(bannerTop());
            if (inView(y, BANNER_HEIGHT - GAP)) {
                graphics.renderOutline(left + 12, y, panelWidth - 24, BANNER_HEIGHT - GAP, 0xFF000000 | SoulLinkColors.GOLD);
                String countdown = Math.max(0, (pendingTicksRemaining + 19) / 20) + "s";
                graphics.drawString(font, countdown, left + panelWidth - 20 - font.width(countdown), y + 7, SoulLinkColors.TEXT, false);
                graphics.drawCenteredString(font, fit(pendingInviter + " invited you to a run", panelWidth - 40 - font.width(countdown) * 2),
                        left + panelWidth / 2, y + 7, SoulLinkColors.TEXT);
            }
        }
        graphics.drawString(font, "Invite a player", left + 16, screenY(inviteLabelTop()) + 4, SoulLinkColors.TEAL, false);
        if (players.isEmpty()) {
            graphics.drawCenteredString(font, "No other players online.", left + panelWidth / 2,
                    screenY(playerTop(0)) + 4, SoulLinkColors.TEXT);
        }
        for (int i = 0; i < players.size(); i++) {
            int y = screenY(playerTop(i));
            if (!inView(y, ROW_HEIGHT)) continue;
            graphics.renderOutline(left + 14, y, panelWidth - 28, ROW_HEIGHT, 0xFF34434F);
            graphics.drawString(font, fit(players.get(i), panelWidth - 130), left + 22, y + 8, SoulLinkColors.TEXT, false);
        }
        graphics.disableScissor();
    }

    void renderScrollbar(GuiGraphics graphics) {
        if (maxScroll() <= 0) return;
        int x = left + panelWidth - 7;
        int track = contentBottom - contentTop;
        int thumb = Math.max(16, track * track / (track + maxScroll()));
        int y = contentTop + (int) ((track - thumb) * scroll / maxScroll());
        graphics.fill(x, contentTop, x + 2, contentBottom, 0xFF293A46);
        graphics.fill(x, y, x + 2, y + thumb, 0xFF000000 | SoulLinkColors.TEAL);
    }

    private String fit(String text, int maxWidth) {
        return font.width(text) <= maxWidth ? text : font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…";
    }

    private record RunRow(String id, String title, String members, boolean solo, boolean paused, boolean selected,
            int caught, int linked) { }
}
