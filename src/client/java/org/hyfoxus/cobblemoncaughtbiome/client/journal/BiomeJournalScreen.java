package org.hyfoxus.cobblemoncaughtbiome.client.journal;

import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.hyfoxus.cobblemoncaughtbiome.soullink.SoulLinkColors;

public final class BiomeJournalScreen extends Screen {
    private static final int CARD_HEIGHT = 150;
    private static final int CARD_HEIGHT_COMPACT = 100;
    private static final int GAP = 8;
    private final Mode mode;
    private BiomeTab biomeTab;
    private LogsTab logsTab;
    private InviteTab inviteTab;
    private ContentTab tab = ContentTab.BIOMES;
    private CompoundTag header;
    private CompoundTag online;
    private EditBox search;
    private int left, panelWidth, contentTop, contentBottom, columns, cardWidth, cardHeight;
    private boolean compact;
    private int initialPage;
    private int refreshTicks;
    private int endConfirmTicks;

    private enum Mode { BIOME, INVITE }
    private enum ContentTab { BIOMES, LOGS }

    public BiomeJournalScreen(CompoundTag header, List<CompoundTag> rows) {
        super(Component.literal("Soul Link · Biome Journal"));
        this.mode = Mode.BIOME;
        this.biomeTab = new BiomeTab(this::addRenderableWidget, this::removeWidget, this::clearPortraitFocus);
        this.logsTab = new LogsTab();
        this.header = header.copy();
        biomeTab.setHeader(this.header);
        biomeTab.setRows(rows);
        biomeTab.setFilter(BiomeTab.Filter.parse(header.getString("filter")));
        this.initialPage = Math.max(1, header.getInt("page"));
    }

    public BiomeJournalScreen(CompoundTag online) {
        super(Component.literal("Soul Link · Invite"));
        this.mode = Mode.INVITE;
        this.inviteTab = new InviteTab(this::addRenderableWidget, this::removeWidget, this::sendCommand);
        this.online = online.copy();
        inviteTab.setData(this.online);
    }

    boolean isInviteMode() { return mode == Mode.INVITE; }

    boolean isBiomeMode() { return mode == Mode.BIOME; }

    void update(CompoundTag header, List<CompoundTag> rows) {
        this.header = header.copy();
        biomeTab.setHeader(this.header);
        biomeTab.setRows(rows);
        refreshTicks = 0;
        rebuildWidgets();
    }

    void updateOnline(CompoundTag online) {
        this.online = online.copy();
        inviteTab.setData(this.online);
        inviteTab.rebuildWidgets();
    }

    void updateLogs(List<String> entries) {
        logsTab.setEntries(entries);
    }

    private void clearPortraitFocus() {
        if (getFocused() instanceof PokemonPortrait) setFocused(search);
    }

    private void sendCommand(String command) {
        if (minecraft != null && minecraft.getConnection() != null) minecraft.getConnection().sendCommand(command);
    }

    private void actAndRefresh(String command) {
        sendCommand(command);
        sendCommand(tab == ContentTab.LOGS ? "soullink logs" : "soullink menu");
    }

    @Override
    protected void init() {
        left = Math.max(4, (width - Math.min(700, width - 16)) / 2);
        panelWidth = width - left * 2;
        compact = height < 225;
        if (mode == Mode.BIOME) {
            contentTop = compact ? 88 : 108;
            contentBottom = Math.max(contentTop + 32, height - (compact ? 23 : 34));
            addRenderableWidget(new JournalButton(left + 14, compact ? 31 : 50, 50, Component.literal("Biomes"),
                    tab == ContentTab.BIOMES, button -> { tab = ContentTab.BIOMES; rebuildWidgets(); }));
            addRenderableWidget(new JournalButton(left + 68, compact ? 31 : 50, 50, Component.literal("Logs"),
                    tab == ContentTab.LOGS, button -> { tab = ContentTab.LOGS; sendCommand("soullink logs"); rebuildWidgets(); }));
            addRenderableWidget(new JournalButton(left + panelWidth - 262, compact ? 31 : 50, 74,
                    Component.literal(header.getBoolean("paused") ? "Resume" : "Pause"), false,
                    button -> actAndRefresh(header.getBoolean("paused") ? "soullink resume" : "soullink stop")));
            addRenderableWidget(new JournalButton(left + panelWidth - 180, compact ? 31 : 50, 44,
                    Component.literal(endConfirmTicks > 0 ? "Confirm?" : "End"), false,
                    button -> {
                        if (endConfirmTicks > 0) {
                            endConfirmTicks = 0;
                            actAndRefresh("soullink end confirm");
                        } else {
                            endConfirmTicks = 100;
                            sendCommand("soullink end");
                            rebuildWidgets();
                        }
                    }));
            addRenderableWidget(new JournalButton(left + panelWidth - 128, compact ? 31 : 50, 66, Component.literal("Refresh"), false,
                    button -> refresh()));
            addRenderableWidget(new JournalButton(left + panelWidth - 58, compact ? 31 : 50, 44, Component.literal("Close"), false,
                    button -> onClose()));
            if (tab == ContentTab.BIOMES) {
                cardHeight = compact ? CARD_HEIGHT_COMPACT : CARD_HEIGHT;
                columns = panelWidth >= 490 ? 2 : 1;
                cardWidth = (panelWidth - 28 - GAP * (columns - 1)) / columns;
                biomeTab.setFont(font);
                biomeTab.setLayout(left, panelWidth, contentTop, contentBottom, columns, cardWidth, cardHeight, compact);
                search = new EditBox(font, left + 130, compact ? 36 : 55,
                        Math.max(30, panelWidth - 392), 18, Component.literal("Search biomes"));
                search.setBordered(false);
                search.setTextColor(SoulLinkColors.TEXT);
                search.setHint(Component.literal("Search biomes…"));
                search.setMaxLength(100);
                search.setValue(biomeTab.query());
                search.setResponder(value -> { biomeTab.setQuery(value); applyFilters(); });
                addRenderableWidget(search);
                int tabWidth = (panelWidth - 28 - GAP * 3) / 4;
                for (BiomeTab.Filter value : BiomeTab.Filter.values()) {
                    addRenderableWidget(new JournalButton(left + 14 + value.ordinal() * (tabWidth + GAP), compact ? 59 : 79, tabWidth,
                            Component.literal(value.label), biomeTab.filter() == value, button -> {
                                biomeTab.selectFilter(value);
                                rebuildWidgets();
                            }));
                }
                applyFilters();
                if (initialPage > 1) {
                    biomeTab.scrollToPage(initialPage);
                    initialPage = 1;
                }
            } else {
                logsTab.setFont(font);
                logsTab.setLayout(left, panelWidth, contentTop, contentBottom);
            }
        } else {
            contentTop = compact ? 60 : 80;
            contentBottom = Math.max(contentTop + 32, height - (compact ? 23 : 34));
            inviteTab.setFont(font);
            inviteTab.setLayout(left, panelWidth, contentTop, contentBottom);
            addRenderableWidget(new JournalButton(left + panelWidth - 128, compact ? 31 : 50, 66, Component.literal("Refresh"), false,
                    button -> refreshOnline()));
            addRenderableWidget(new JournalButton(left + panelWidth - 58, compact ? 31 : 50, 44, Component.literal("Close"), false,
                    button -> onClose()));
            inviteTab.rebuildWidgets();
        }
    }

    private void applyFilters() {
        if (tab != ContentTab.BIOMES || search == null) return;
        biomeTab.applyFilters();
    }

    private void refresh() {
        if (minecraft != null && minecraft.getConnection() != null && refreshTicks == 0) {
            refreshTicks = 100;
            String command = tab == ContentTab.BIOMES
                    ? "soullink biomes " + biomeTab.filter().name().toLowerCase(Locale.ROOT) : "soullink logs";
            minecraft.getConnection().sendCommand(command);
        }
    }

    private void refreshOnline() {
        if (minecraft != null && minecraft.getConnection() != null && refreshTicks == 0) {
            refreshTicks = 100;
            minecraft.getConnection().sendCommand("soullink online");
        }
    }

    @Override
    public void tick() {
        if (refreshTicks > 0) refreshTicks--;
        if (endConfirmTicks > 0) {
            endConfirmTicks--;
            if (endConfirmTicks == 0) rebuildWidgets();
        }
        if (mode == Mode.BIOME && tab == ContentTab.BIOMES) biomeTab.tick();
        if (mode == Mode.INVITE) inviteTab.tick();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xD9080D14);
        graphics.fill(left, 8, left + panelWidth, height - 8, 0xF0121C27);
        graphics.fill(left + 14, compact ? 31 : 50, left + panelWidth - 134,
                compact ? 51 : 70, 0xFF0D1721);
        if (mode == Mode.BIOME) {
            if (tab == ContentTab.BIOMES) biomeTab.renderCardBackgrounds(graphics);
            else logsTab.renderBackgrounds(graphics);
        } else {
            inviteTab.renderBackgrounds(graphics);
        }
        // text drawn before super.render() comes out blurry on this screen, so everything else goes after
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.renderOutline(left, 8, panelWidth, height - 16, 0xFF34434F);
        graphics.fill(left, 8, left + panelWidth, 10, 0xFF000000 | SoulLinkColors.TEAL);
        if (mode == Mode.BIOME) {
            graphics.drawString(font, fit("SOUL LINK  /  BIOME JOURNAL", panelWidth - 28), left + 14,
                    compact ? 12 : 18, SoulLinkColors.TEAL, false);
            String names = header.getString("firstName") + "  ↔  " + header.getString("secondName")
                    + (header.getBoolean("paused") ? "  ·  Paused" : "  ·  Active");
            graphics.drawString(font, fit(names, panelWidth - 28), left + 14, compact ? 24 : 33, SoulLinkColors.TEXT, false);
            if (tab == ContentTab.BIOMES) {
                biomeTab.renderScrollbar(graphics);
                biomeTab.renderCardLabels(graphics);
                if (biomeTab.isEmpty()) {
                    graphics.drawCenteredString(font, fit("No biomes match your search or filter.", panelWidth - 28), width / 2,
                            contentTop + 14, SoulLinkColors.TEXT);
                }
                String counts = "Available " + biomeTab.count(BiomeTab.Filter.AVAILABLE) + "   ·   Waiting " + biomeTab.count(BiomeTab.Filter.WAITING)
                        + "   ·   Linked " + biomeTab.count(BiomeTab.Filter.LINKED);
                graphics.drawString(font, fit(counts, panelWidth - 28), left + 14, height - 25, SoulLinkColors.TEXT, false);
            } else {
                logsTab.renderScrollbar(graphics);
                logsTab.renderLabels(graphics);
            }
        } else {
            graphics.drawString(font, fit("SOUL LINK  /  INVITE", panelWidth - 28), left + 14,
                    compact ? 12 : 18, SoulLinkColors.TEAL, false);
            inviteTab.renderLabels(graphics);
        }
    }

    private String fit(String text, int maxWidth) {
        return font.width(text) <= maxWidth ? text : font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…";
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        boolean handled;
        if (mode == Mode.BIOME) {
            handled = tab == ContentTab.BIOMES ? biomeTab.mouseScrolled(mouseX, mouseY, vertical)
                    : logsTab.mouseScrolled(mouseX, mouseY, vertical);
        } else {
            handled = inviteTab.mouseScrolled(mouseX, mouseY, vertical);
        }
        if (handled) return true;
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mode == Mode.BIOME && tab == ContentTab.BIOMES && biomeTab.mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (mode == Mode.BIOME && tab == ContentTab.BIOMES && biomeTab.mouseDragged(mouseX, mouseY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (mode == Mode.BIOME && tab == ContentTab.BIOMES) biomeTab.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (mode == Mode.BIOME && tab == ContentTab.BIOMES && !search.isFocused() && biomeTab.keyPressed(key)) return true;
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
