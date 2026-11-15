package org.hyfoxus.cobblemoncaughtbiome.client.journal;

import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.hyfoxus.cobblemoncaughtbiome.soullink.SoulLinkColors;

public final class BiomeJournalScreen extends Screen {
    private static final int CARD_HEIGHT = 150;
    private static final int CARD_HEIGHT_COMPACT = 100;
    private static final int GAP = 8;
    private final Mode mode;
    private BiomeTab biomeTab;
    private LogsTab logsTab;
    private RunsTab runsTab;
    private ContentTab tab = ContentTab.BIOMES;
    // Set by the Runs view's Logs button: the next biome screen opened from a journal payload starts on Logs.
    private static boolean openLogsNext;
    private CompoundTag header;
    private CompoundTag online;
    private EditBox search;
    private int left, panelWidth, contentTop, contentBottom, columns, cardWidth, cardHeight;
    private boolean compact;
    private int initialPage;
    private int refreshTicks;
    private int endConfirmTicks;

    private enum Mode { BIOME, RUNS }
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
        if (openLogsNext) {
            openLogsNext = false;
            tab = ContentTab.LOGS;
        }
    }

    public BiomeJournalScreen(CompoundTag online) {
        super(Component.literal("Soul Link · Runs"));
        this.mode = Mode.RUNS;
        this.runsTab = new RunsTab(this::addRenderableWidget, this::removeWidget, this::sendCommand);
        this.online = online.copy();
        runsTab.setData(this.online);
    }

    boolean isRunsMode() { return mode == Mode.RUNS; }

    boolean isBiomeMode() { return mode == Mode.BIOME; }

    static void clearPendingLogs() { openLogsNext = false; }

    private boolean hasSelectedRun() { return online != null && online.contains("selected", Tag.TAG_STRING); }

    void update(CompoundTag header, List<CompoundTag> rows) {
        this.header = header.copy();
        biomeTab.setHeader(this.header);
        biomeTab.setRows(rows);
        refreshTicks = 0;
        rebuildWidgets();
    }

    void updateOnline(CompoundTag online) {
        boolean hadSelected = hasSelectedRun();
        this.online = online.copy();
        // A Logs request that fell back to this view (nothing selected) must not linger.
        if (!hasSelectedRun()) openLogsNext = false;
        runsTab.setData(this.online);
        if (hadSelected != hasSelectedRun()) rebuildWidgets();
        else runsTab.rebuildWidgets();
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
            addRenderableWidget(new JournalButton(left + 14, compact ? 31 : 50, 44, Component.literal("Runs"), false,
                    button -> sendCommand("soullink online")));
            addRenderableWidget(new JournalButton(left + 62, compact ? 31 : 50, 50, Component.literal("Biomes"),
                    tab == ContentTab.BIOMES, button -> { tab = ContentTab.BIOMES; rebuildWidgets(); }));
            addRenderableWidget(new JournalButton(left + 116, compact ? 31 : 50, 50, Component.literal("Logs"),
                    tab == ContentTab.LOGS, button -> { tab = ContentTab.LOGS; sendCommand("soullink logs"); rebuildWidgets(); }));
            addRenderableWidget(new JournalButton(left + panelWidth - 262, compact ? 31 : 50, 74,
                    Component.literal(header.getBoolean("paused") ? "Resume" : "Pause"), false,
                    button -> actAndRefresh(header.getBoolean("paused") ? "soullink resume" : "soullink stop")));
            addRenderableWidget(new JournalButton(left + panelWidth - 180, compact ? 31 : 50, 44,
                    Component.literal(endConfirmTicks > 0 ? "Confirm?" : "End"), false,
                    button -> {
                        String runId = header.getString("runId");
                        String endCommand = runId.isEmpty() ? "soullink end" : "soullink end " + runId;
                        if (endConfirmTicks > 0) {
                            endConfirmTicks = 0;
                            actAndRefresh(endCommand + " confirm");
                        } else {
                            endConfirmTicks = 100;
                            sendCommand(endCommand);
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
                search = new EditBox(font, left + 178, compact ? 36 : 55,
                        Math.max(30, panelWidth - 440), 18, Component.literal("Search biomes"));
                search.setBordered(false);
                search.setTextColor(SoulLinkColors.TEXT);
                search.setHint(Component.literal("Search biomes…"));
                search.setMaxLength(100);
                search.setValue(biomeTab.query());
                search.setResponder(value -> { biomeTab.setQuery(value); applyFilters(); });
                addRenderableWidget(search);
                List<BiomeTab.Filter> filters = biomeTab.filters();
                int tabWidth = (panelWidth - 28 - GAP * (filters.size() - 1)) / filters.size();
                for (int i = 0; i < filters.size(); i++) {
                    BiomeTab.Filter value = filters.get(i);
                    addRenderableWidget(new JournalButton(left + 14 + i * (tabWidth + GAP), compact ? 59 : 79, tabWidth,
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
            runsTab.setFont(font);
            runsTab.setLayout(left, panelWidth, contentTop, contentBottom);
            addRenderableWidget(new JournalButton(left + 14, compact ? 31 : 50, 44, Component.literal("Runs"), true,
                    button -> refreshOnline()));
            if (hasSelectedRun()) {
                addRenderableWidget(new JournalButton(left + 62, compact ? 31 : 50, 50, Component.literal("Biomes"), false,
                        button -> sendCommand("soullink menu")));
                addRenderableWidget(new JournalButton(left + 116, compact ? 31 : 50, 50, Component.literal("Logs"), false,
                        button -> {
                            openLogsNext = true;
                            sendCommand("soullink menu");
                            sendCommand("soullink logs");
                        }));
            }
            addRenderableWidget(new JournalButton(left + panelWidth - 128, compact ? 31 : 50, 66, Component.literal("Refresh"), false,
                    button -> refreshOnline()));
            addRenderableWidget(new JournalButton(left + panelWidth - 58, compact ? 31 : 50, 44, Component.literal("Close"), false,
                    button -> onClose()));
            runsTab.rebuildWidgets();
        }
    }

    private void applyFilters() {
        if (tab != ContentTab.BIOMES || search == null) return;
        biomeTab.applyFilters();
    }

    private void refresh() {
        if (minecraft != null && minecraft.getConnection() != null && refreshTicks == 0) {
            refreshTicks = 100;
            // The server has no "caught" filter; filtering is client-side and update() keeps the current filter.
            BiomeTab.Filter filter = biomeTab.filter() == BiomeTab.Filter.CAUGHT ? BiomeTab.Filter.ALL : biomeTab.filter();
            String command = tab == ContentTab.BIOMES
                    ? "soullink biomes " + filter.name().toLowerCase(Locale.ROOT) : "soullink logs";
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
        if (mode == Mode.RUNS) runsTab.tick();
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
            runsTab.renderBackgrounds(graphics);
        }
        // text drawn before super.render() comes out blurry on this screen, so everything else goes after
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.renderOutline(left, 8, panelWidth, height - 16, 0xFF34434F);
        graphics.fill(left, 8, left + panelWidth, 10, 0xFF000000 | SoulLinkColors.TEAL);
        if (mode == Mode.BIOME) {
            graphics.drawString(font, fit("SOUL LINK  /  BIOME JOURNAL", panelWidth - 28), left + 14,
                    compact ? 12 : 18, SoulLinkColors.TEAL, false);
            graphics.drawString(font, fit(headerLine(), panelWidth - 28), left + 14, compact ? 24 : 33, SoulLinkColors.TEXT, false);
            if (tab == ContentTab.BIOMES) {
                biomeTab.renderScrollbar(graphics);
                biomeTab.renderCardLabels(graphics);
                if (biomeTab.isEmpty()) {
                    graphics.drawCenteredString(font, fit("No biomes match your search or filter.", panelWidth - 28), width / 2,
                            contentTop + 14, SoulLinkColors.TEXT);
                }
                String counts = biomeTab.solo()
                        ? "Available " + biomeTab.count(BiomeTab.Filter.AVAILABLE) + "   ·   Caught " + biomeTab.count(BiomeTab.Filter.CAUGHT)
                        : "Available " + biomeTab.count(BiomeTab.Filter.AVAILABLE) + "   ·   Waiting " + biomeTab.count(BiomeTab.Filter.WAITING)
                        + "   ·   Linked " + biomeTab.count(BiomeTab.Filter.LINKED);
                graphics.drawString(font, fit(counts, panelWidth - 28), left + 14, height - 25, SoulLinkColors.TEXT, false);
            } else {
                logsTab.renderScrollbar(graphics);
                logsTab.renderLabels(graphics);
            }
        } else {
            graphics.drawString(font, fit("SOUL LINK  /  RUNS", panelWidth - 28), left + 14,
                    compact ? 12 : 18, SoulLinkColors.TEAL, false);
            String selected = runsTab.selectedTitle();
            String line = selected != null ? "Selected: " + selected : "Free play  ·  no run selected";
            graphics.drawString(font, fit(line, panelWidth - 28), left + 14, compact ? 24 : 33, SoulLinkColors.TEXT, false);
            runsTab.renderScrollbar(graphics);
            runsTab.renderLabels(graphics);
        }
    }

    /** Solo: "<player>  ·  <run title>  ·  Active"; shared: today's "<first>  ↔  <second>  ·  Active", led by the
     *  run title only when it differs from the default "<first> & <second>". */
    private String headerLine() {
        String first = header.getString("firstName");
        String title = header.getString("runTitle");
        String state = header.getBoolean("paused") ? "  ·  Paused" : "  ·  Active";
        if (header.getBoolean("solo")) {
            return first + "  ·  " + (title.isEmpty() ? "Solo" : title) + state;
        }
        String second = header.getString("secondName");
        String names = first + "  ↔  " + second + state;
        return title.isEmpty() || title.equals(first + " & " + second) ? names : title + "  ·  " + names;
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
            handled = runsTab.mouseScrolled(mouseX, mouseY, vertical);
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
