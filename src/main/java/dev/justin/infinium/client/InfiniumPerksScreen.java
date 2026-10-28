package dev.justin.infinium.client;

import dev.justin.infinium.Info;
import dev.justin.infinium.Perk;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * R2 page 1: "Infinium Perk Settings". A row of variant buttons at the top jumps to that variant; each variant is fully
 * independent (no "All styles", no search). Rows: the 4 piece perks, the full-set perks, then that full set's two
 * particle toggles (armor / weapon). Every hover shows the real value for the variant being viewed (Info / PerkValues).
 * Particles, the inventory HUD and sounds live on the visual settings page.
 * "Sound settings" opens page 2.
 */
public class InfiniumPerksScreen extends OptionsSubScreen {
    private final int fam;

    public InfiniumPerksScreen(Screen parent) { this(parent, 0); }
    public InfiniumPerksScreen(Screen parent, int fam) {
        super(parent, Minecraft.getInstance().options, Component.literal("Infinium Perk Settings"));
        this.fam = fam;
    }

    static String label(Perk p) { return Component.translatable(p.translationKey()).getString(); }

    /** Real value text for this perk in this variant. */
    static String value(Perk p) {
        if (p == Perk.ABILITY_WARP) return "Quantum Tunnel (Astral / Singularity / Infinity leggings): mid-air tunnel + forward launch on " + Info.key("warp", "Quantum Tunnel", null) + ". Needs a full set.";
        if (p == Perk.ABILITY_XRAY_PULSE) return "X-Ray Pulse (every helmet): scans ores, chests, hostiles and players around you (" + Info.key("xray_pulse", "X-Ray Pulse", null) + "). Needs a full set.";
        if (p.kind.equals("bootjump")) return "Boots jump: " + Info.blocks(p.family == 0 ? 1 : p.family == 2 ? 2 : 3) + " jump height (hold jump for full height; a tap is a normal jump).";
        if (!p.isSet()) return Info.pieceLine(p.family, p.piece);
        if (p.kind.equals("light")) return "Armor light: light level " + (p.family == 4 ? 12 : p.family >= 2 ? 8 : 4) + " around you.";
        for (Object[] e : Info.setLines(p.family)) if (e[1] == p) return (String) e[0];
        return label(p) + ".";
    }

    /** Perks that no longer exist as their own effect (leggings speed is per piece since R2). */
    static boolean hidden(Perk p) { return p.kind.equals("setlegs"); }

    private long builtMask;
    private boolean builtFb;

    /** M5: if the toggles change elsewhere (matrix screen, server ack/reject), rebuild so this menu never shows stale state. */
    @Override
    public void tick() {
        super.tick();
        if (minecraft != null && minecraft.player != null && (ClientPerks.mask(minecraft.player) != builtMask || HelmetVisionClient.isFullBrightActive() != builtFb)) rebuildWidgets();
    }

    @Override
    protected void addOptions() {
        boolean connected = ClientPerks.canSend();
        long mask = ClientPerks.mask(minecraft.player);
        builtMask = mask;

        List<AbstractWidget> jump = new ArrayList<>();
        for (int f = 0; f < 5; f++) {
            final int ff = f;
            Button b = Button.builder(Component.literal(Perk.FAMILY_NAMES[f]).withStyle(f == fam ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.WHITE),
                btn -> minecraft.gui.setScreen(new InfiniumPerksScreen(lastScreen, ff))).build();
            b.active = f != fam;
            jump.add(b);
        }
        list.addBig(Button.builder(Component.literal("✦ Visual & Sound Settings ✦").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
            b -> minecraft.gui.setScreen(new InfiniumSettingsScreen(this))).build());

        list.addHeader(Component.literal("Armor Sets").withStyle(ChatFormatting.LIGHT_PURPLE));
        list.addSmall(jump.get(0), jump.get(1));
        list.addSmall(jump.get(2), jump.get(3));
        list.addSmall(jump.get(4), (AbstractWidget) null);
        if (!connected) list.addBig(new StringWidget(Component.literal("Not connected to an Infinium server").withStyle(ChatFormatting.DARK_PURPLE), font));

        // M5: abilities are one toggle across every set (shared with the matrix and the HUD bar)
        List<AbstractWidget> row = new ArrayList<>();
        list.addHeader(Component.literal("Abilities (all sets)").withStyle(ChatFormatting.LIGHT_PURPLE));
        add(row, Perk.ABILITY_WARP, mask, connected);
        add(row, Perk.ABILITY_XRAY_PULSE, mask, connected);
        flush(row);
        // M6(b): Full Bright stays a client-side toggle (no server effect) but lives here too, in sync with its key.
        builtFb = HelmetVisionClient.isFullBrightActive();
        CycleButton<Boolean> fb = CycleButton.onOffBuilder(builtFb).create(Component.literal("Full Bright"), (btn, v) -> {
            if (minecraft.player != null && v != HelmetVisionClient.isFullBrightActive()) HelmetVisionClient.toggleFullBright(minecraft.player);
            builtFb = HelmetVisionClient.isFullBrightActive();
            if (builtFb != v) rebuildWidgets();   // refused (no eligible helmet): snap back
        });
        String fbKey = InfiniumKeys.TOGGLE_FULLBRIGHT == null ? "?" : InfiniumKeys.TOGGLE_FULLBRIGHT.getTranslatedKeyMessage().getString();
        fb.setTooltip(Tooltip.create(Component.literal("Full Bright (any Infinium helmet). Same toggle as the " + fbKey + " key and the inventory button. Client-side only.")));
        list.addSmall(fb, (AbstractWidget) null);

        list.addHeader(Component.literal(Perk.FAMILY_NAMES[fam] + " pieces").withStyle(ChatFormatting.LIGHT_PURPLE));
        add(row, Perk.piece(fam, EquipmentSlot.HEAD), mask, connected);
        add(row, Perk.piece(fam, EquipmentSlot.CHEST), mask, connected);
        add(row, Perk.piece(fam, EquipmentSlot.LEGS), mask, connected);
        flush(row);
        add(row, Perk.piece(fam, EquipmentSlot.FEET), mask, connected);
        add(row, Perk.bootJump(fam), mask, connected);
        flush(row);
        list.addHeader(Component.literal(Perk.FAMILY_NAMES[fam] + " full set (matrix)").withStyle(ChatFormatting.GRAY));
        for (Perk p : Perk.values()) if (p.isSet() && p.family == fam && !hidden(p)) add(row, p, mask, connected);
        flush(row);
    }

    private void add(List<AbstractWidget> row, Perk p, long mask, boolean connected) {
        if (p == null) return;
        boolean on = (mask & p.bit()) == 0;
        CycleButton<Boolean> b = CycleButton.onOffBuilder(on).create(Component.literal(label(p)), (btn, v) -> {
            ClientPerks.set(p, v);
            builtMask = ClientPerks.mask(minecraft.player);   // our own click: no rebuild
        });
        b.setTooltip(Tooltip.create(Component.literal(value(p) + (p.kind.equals("stacks") ? "\nTaking off a piece asks first: items over the normal limit drop at your feet (backpacks too)." : ""))));
        b.active = connected;
        row.add(b);
        if (row.size() == 2) { list.addSmall(row.get(0), row.get(1)); row.clear(); }
    }
    private void flush(List<AbstractWidget> row) { if (!row.isEmpty()) { list.addSmall(row.get(0), (AbstractWidget) null); row.clear(); } }

    /** Rebuild only the list entries in place. Vanilla's rebuildWidgets re-runs init(), which adds a second OptionsList
     *  (and title/footer) to the final HeaderAndFooterLayout, so the stale list stayed drawn on top as a frozen copy. */
    @Override
    protected void rebuildWidgets() {
        if (list == null) { super.rebuildWidgets(); return; }
        double scroll = list.scrollAmount();
        list.setFocused(null);
        list.replaceEntries(java.util.List.of());
        addOptions();
        list.setScrollAmount(scroll);
    }

    @Override
    public void removed() { InfiniumClientConfig.save(); super.removed(); }
}
