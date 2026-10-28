package dev.justin.infinium.client;

import dev.justin.infinium.client.mixin.ContainerScreenPosAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Interactive toggle buttons added to the inventory screen (survival and creative inventory tab)
 * for Full Bright (M3: X-ray moved to the pulse ability).
 *
 * Collision Resolution:
 * For composite helmets (Infinity Helmet) that possess both capabilities, buttons render
 * side-by-side above the inventory container frame:
 *   [☀ Bright: ON/OFF]  (6px gap)  [✦ X-Ray: ON/OFF]
 * Positioned cleanly within container bounds across all GUI scales (Auto, Small, Medium, Large)
 * without clipping off-screen or colliding with inventory slots or tabs.
 */
public final class InventoryHelmetVisionButtons {

    public static void register() {
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (!(screen instanceof InventoryScreen) && !(screen instanceof CreativeModeInventoryScreen)) return;
            AbstractContainerScreen<?> cs = (AbstractContainerScreen<?>) screen;
            FullBrightButton fbBtn = new FullBrightButton();
            Screens.getWidgets(screen).add(fbBtn);
            update(fbBtn, cs);
            ScreenEvents.afterTick(screen).register(s -> update(fbBtn, (AbstractContainerScreen<?>) s));
        });
    }

    /** M3: only Full Bright remains here (any Infinium helmet). X-ray is now the pulse ability on the HUD bar. */
    private static void update(FullBrightButton fbBtn, AbstractContainerScreen<?> s) {
        Player p = Minecraft.getInstance().player;
        boolean creative = s instanceof CreativeModeInventoryScreen;
        boolean invOpen = !creative || ((CreativeModeInventoryScreen) s).isInventoryOpen();
        boolean show = p != null && invOpen && dev.justin.infinium.Infinium.setWorn(p) && HelmetVisionClient.isFullBrightEligible(p.getItemBySlot(EquipmentSlot.HEAD));
        fbBtn.visible = show;
        fbBtn.active = show;
        if (!show) return;
        var acc = (ContainerScreenPosAccessor) s;
        fbBtn.setPosition(acc.infinium$leftPos() + 8, acc.infinium$topPos() - 18);
        fbBtn.setSize(78, 14);
        fbBtn.updateTooltip();
    }

    private static final class FullBrightButton extends AbstractButton {
        FullBrightButton() {
            super(0, 0, 78, 14, Component.literal("Full Bright"));
        }

        void updateTooltip() {
            boolean active = HelmetVisionClient.isFullBrightActive();
            String status = active ? "ON" : "OFF";
            String action = active ? "disable" : "enable";
            setTooltip(Tooltip.create(Component.literal("Full Bright: " + status + "\nClick to " + action + " (Hotkey: " + (InfiniumKeys.TOGGLE_FULLBRIGHT == null ? "?" : InfiniumKeys.TOGGLE_FULLBRIGHT.getTranslatedKeyMessage().getString()) + ")")));
        }

        @Override
        public void onPress(InputWithModifiers in) {
            Player p = Minecraft.getInstance().player;
            if (p != null) {
                HelmetVisionClient.toggleFullBright(p);
                updateTooltip();
            }
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float a) {
            Font font = Minecraft.getInstance().font;
            if (font == null) return;

            boolean active = HelmetVisionClient.isFullBrightActive();
            boolean hovered = isHoveredOrFocused();

            int x = getX(), y = getY(), w = getWidth(), h = getHeight();

            int borderColor = active ? 0xFFFF00AA : (hovered ? 0xFF888888 : 0xFF444444);
            int bgColor = active ? 0xCC1A0814 : 0xCC12061A;
            int accentBar = active ? 0xFFFF00AA : 0xFF555555;
            int textColor = active ? 0xFFFF66CC : (hovered ? 0xFFFFFFFF : 0xFFAAAAAA);

            // Container fill and border
            g.fill(x, y, x + w, y + h, borderColor);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, bgColor);

            // Left vertical status indicator strip
            g.fill(x + 1, y + 1, x + 3, y + h - 1, accentBar);

            String label = active ? "☀ Bright: ON" : "☀ Bright: OFF";
            int textX = x + (w - font.width(label)) / 2 + 1;
            int textY = y + (h - font.lineHeight) / 2 + 1;
            g.text(font, label, textX, textY, textColor, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }

    private InventoryHelmetVisionButtons() {}
}
