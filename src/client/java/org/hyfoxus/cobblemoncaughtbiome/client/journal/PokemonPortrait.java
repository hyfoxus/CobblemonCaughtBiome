package org.hyfoxus.cobblemoncaughtbiome.client.journal;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.hyfoxus.cobblemoncaughtbiome.soullink.SoulLinkColors;
import org.joml.Quaternionf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class PokemonPortrait extends AbstractWidget {
    private static final Logger LOGGER = LoggerFactory.getLogger(PokemonPortrait.class);
    private FloatingState state;
    private RenderablePokemon pokemon;
    private final boolean captured;
    private boolean failed;
    private int ticks;

    PokemonPortrait(CompoundTag capture, String owner) {
        super(0, 0, 42, 42, Component.literal(owner + " · " + (capture == null ? "No catch yet" : capture.getString("name"))));
        captured = capture != null;
        if (capture != null) {
            ResourceLocation id = ResourceLocation.tryParse(capture.getString("species"));
            var species = id == null ? null : PokemonSpecies.getByIdentifier(id);
            if (species != null) {
                Set<String> aspects = new HashSet<>();
                capture.getList("aspects", Tag.TAG_STRING).forEach(value -> aspects.add(value.getAsString()));
                pokemon = new RenderablePokemon(species, aspects, ItemStack.EMPTY);
            }
        }
        setTooltip(Tooltip.create(getMessage().copy().append(captured && pokemon == null
                ? "\nPortrait unavailable for this older catch" : "")));
    }

    void tick() { ticks++; }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX(), y = getY();
        graphics.fill(x, y, x + width, y + height, 0xFF243E46);
        graphics.renderOutline(x, y, width, height,
                isHoveredOrFocused() ? 0xFF000000 | SoulLinkColors.GOLD : 0xFF30424D);
        if (pokemon == null || failed) {
            graphics.drawCenteredString(Minecraft.getInstance().font, captured ? "?" : "+", x + width / 2, y + 17,
                    captured ? SoulLinkColors.GOLD : 0xFF52636F);
            return;
        }
        graphics.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);
        graphics.pose().pushPose();
        try {
            if (state == null) state = new FloatingState();
            // Cobblemon's PC slot uses a fixed 25px box, scale that up to our widget size.
            float ratio = width / 25F;
            graphics.pose().translate(x + width / 2.0, y + 1.0 * ratio, 100);
            graphics.pose().scale(2.5F * ratio, 2.5F * ratio, 1F);
            state.updateAge(ticks);
            PokemonGuiUtilsKt.drawProfilePokemon(pokemon, graphics.pose(),
                    new Quaternionf().rotationXYZ((float) Math.toRadians(13), (float) Math.toRadians(35), 0),
                    PoseType.PROFILE, state, partialTick, 4.5F, true, false, 1F, 1F, 1F, 1F, 0F, 0F);
        } catch (RuntimeException exception) {
            failed = true;
            LOGGER.warn("Could not render Soul Link portrait for {}", getMessage().getString(), exception);
        } finally {
            graphics.pose().popPose();
            graphics.disableScissor();
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
    }
}
