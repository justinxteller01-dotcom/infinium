package dev.justin.infinium.client.ability;

import dev.justin.infinium.Infinium;
import dev.justin.infinium.client.InfiniumKeys;
import dev.justin.infinium.client.mixin.KeyBindsCategoryEntryAccessor;
import dev.justin.infinium.client.mixin.KeyBindsScreenAccessor;
import java.util.List;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

/**
 * "Bind key" pill: drawn by {@link AbilityHud} (always visible on the HUD while the ability is unbound) and made
 * clickable whenever a screen that shows the HUD underneath and frees the mouse is open: any container screen
 * (inventory, creative, chests...) or chat. During normal play there is no cursor, so it is display-only; the
 * player opens the inventory (E) or chat (T) and clicks it. No prompt is ever shown.
 *
 * The click opens vanilla Key Binds (KeyBindsScreen), scrolled to the Infinium category. Mods like Controlling
 * enhance that screen themselves. Also used by the "Open Controls" button in Infinium settings.
 */
public final class BindKeyHandler {
    private static boolean scrollPending = false;

    public static void register() {
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (applies(screen)) {
                // Early phase: JEI registers its own allowMouseClick per screen (before us) and takes clicks over its
                // ingredient list, which covers the pill on the right; we must see the click first.
                var allow = ScreenMouseEvents.allowMouseClick(screen);
                allow.addPhaseOrdering(EARLY, Event.DEFAULT_PHASE);
                allow.register(EARLY, (s, ev) -> {
                    if (ev.button() != 0) return true;
                    double[] c = hudCursor(mc, s, ev.x(), ev.y());
                    for (AbilityHud.Slot slot : AbilityHud.layout(mc, mc.player)) {
                        if (slot.bindHit(c[0], c[1])) { openControls(mc, s); return false; }
                    }
                    return true;
                });
            }
            if (scrollPending && screen instanceof KeyBindsScreen kbs) {
                scrollPending = false;
                scrollToInfinium(kbs);
            }
        });
    }

    static boolean applies(Screen s) { return s instanceof AbstractContainerScreen<?> || s instanceof ChatScreen; }

    static boolean hovered(AbilityHud.Slot slot) {
        Minecraft mc = Minecraft.getInstance();
        if (!applies(mc.gui.screen())) return false;
        double[] c = hudCursor(mc, mc.gui.screen(), mc.mouseHandler.getScaledXPos(mc.getWindow()), mc.mouseHandler.getScaledYPos(mc.getWindow()));
        return slot.bindHit(c[0], c[1]);
    }

    private static final Identifier EARLY = Infinium.id("bind_key_pill");

    /** Cursor in HUD (gui-scaled window) space. The pill is drawn flat on the HUD, but Spatial GUI remaps the screen's
     *  mouse position into its 3D panel's space, so with Spatial hooking the screen we use its physical cursor instead. */
    static double[] hudCursor(Minecraft mc, Screen s, double x, double y) {
        if (SPATIAL) {
            double[] src = dev.justin.infinium.compat.SpatialMargin.hudCursor(s);
            if (src != null) {
                var w = mc.getWindow();
                return new double[] { src[0] * w.getGuiScaledWidth() / w.getScreenWidth(), src[1] * w.getGuiScaledHeight() / w.getScreenHeight() };
            }
        }
        return new double[] { x, y };
    }
    private static final boolean SPATIAL = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("spatial-gui");

    public static void openControls(Minecraft mc, Screen parent) {
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        Screen parentForControls = parent instanceof ChatScreen ? null : parent;   // don't return into a half-typed chat
        // batch1 C3: always the vanilla Key Binds screen; the pack's "Controls"/Controlling mod enhances it itself.
        scrollPending = true;
        mc.gui.setScreen(new KeyBindsScreen(parentForControls, mc.options));
    }

    /** Scrolls the vanilla list so the Infinium category header is at the top. Best effort, never throws. */
    static void scrollToInfinium(KeyBindsScreen kbs) {
        try {
            KeyBindsList list = ((KeyBindsScreenAccessor) kbs).infinium$keyBindsList();
            if (list == null) return;
            String want = InfiniumKeys.CATEGORY.label().getString();
            List<KeyBindsList.Entry> entries = list.children();
            for (int i = 0; i < entries.size(); i++) {
                if (isInfiniumHeader(entries.get(i), want)) {
                    list.setScrollAmount(list.scrollAmount() + (list.getRowTop(i) - list.getY()) - 4);
                    return;
                }
            }
        } catch (Throwable t) {
            Infinium.LOG.debug("Could not scroll key binds to Infinium: {}", t.toString());
        }
    }

    /** Vanilla header by label; Controlling replaces the list with its own CategoryEntry (public category()). */
    private static boolean isInfiniumHeader(KeyBindsList.Entry e, String want) {
        if (e instanceof KeyBindsList.CategoryEntry ce)
            return ((KeyBindsCategoryEntryAccessor) ce).infinium$categoryName().getMessage().getString().equals(want);
        try {
            return InfiniumKeys.CATEGORY.equals(e.getClass().getMethod("category").invoke(e));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    private BindKeyHandler() {}
}
