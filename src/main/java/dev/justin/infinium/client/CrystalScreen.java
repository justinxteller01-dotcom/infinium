package dev.justin.infinium.client;

import dev.justin.infinium.CrystalCharge;
import dev.justin.infinium.Info;
import dev.justin.infinium.Infinium;
import dev.justin.infinium.InfiniumStyle;
import dev.justin.infinium.Perk;
import dev.justin.infinium.PerkValues;
import dev.justin.infinium.SetLook;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Overhauled Spacetime Crystal Matrix screen.
 * Supports full-set upgrades (slotIndex < 0) as well as single-piece fallback (slotIndex >= 0).
 * Displays an animated cosmic energy bar with clearly marked perk activation milestones and clean perk cards.
 */
public final class CrystalScreen extends Screen {
    private static final int PANEL_W = 336;
    private static final int PANEL_H = 208;
    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private final Screen back;
    private final int containerId;
    private final int slotIndex;
    private float displayRatio = -1f;

    private int panelX;
    private int panelY;
    private int barX;
    private int barY;
    private int barW;
    private int barH;
    private ItemStack crystalStack;

    private ItemStack getCrystalStack() {
        if (crystalStack == null && Infinium.SPACETIME_CRYSTAL != null) {
            crystalStack = new ItemStack(Infinium.SPACETIME_CRYSTAL);
        }
        return crystalStack;
    }

    private void drawCrystalIcon(GuiGraphicsExtractor g, float x, float y, float scale) {
        ItemStack stack = getCrystalStack();
        if (stack == null || stack.isEmpty()) return;
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        g.item(stack, 0, 0);
        pose.popMatrix();
    }

    public CrystalScreen(Screen back, int containerId, int slotIndex) {
        super(Component.literal("Spacetime Crystal Matrix"));
        this.back = back;
        this.containerId = containerId;
        this.slotIndex = slotIndex;
    }

    private final java.util.List<int[]> abilityChips = new java.util.ArrayList<>();
    private String chipTip;

    private int currentFamily() {
        if (minecraft == null || minecraft.player == null) return -1;
        SetLook look = SetLook.of(minecraft.player);
        return look != null ? look.ordinal() : -1;   // M4: full set only
    }

    private int currentCap() {
        if (minecraft == null || minecraft.player == null) return 0;
        SetLook look = SetLook.of(minecraft.player);
        return look != null ? CrystalCharge.setSlots(look) : 0;
    }

    private int currentFilled() {
        if (minecraft == null || minecraft.player == null) return 0;
        SetLook look = SetLook.of(minecraft.player);
        return look != null ? CrystalCharge.setFilled(minecraft.player) : 0;
    }

    private static int countInventoryCrystals(Player p) {
        int total = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(Infinium.SPACETIME_CRYSTAL)) total += s.getCount();
        }
        ItemStack carried = p.containerMenu.getCarried();
        if (carried.is(Infinium.SPACETIME_CRYSTAL)) total += carried.getCount();
        return total;
    }

    @Override
    protected void init() {
        super.init();
        panelX = (this.width - PANEL_W) / 2;
        panelY = (this.height - PANEL_H) / 2;
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(this.width / 2 - 36, panelY + PANEL_H - 24, 72, 18).build());
    }

    /** M4 (bug 5): the matrix is only reachable with a full set on; close if the set is broken while open. */
    @Override
    public void tick() {
        super.tick();
        if (minecraft != null && minecraft.player != null && !Infinium.setWorn(minecraft.player)) onClose();
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(back);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        panelX = (this.width - PANEL_W) / 2;
        panelY = (this.height - PANEL_H) / 2;

        // Outer cosmic frame & dark background
        g.fill(panelX - 2, panelY - 2, panelX + PANEL_W + 2, panelY + PANEL_H + 2, 0xCC06020A);
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xFF6A2A78);
        g.fill(panelX + 1, panelY + 1, panelX + PANEL_W - 1, panelY + PANEL_H - 1, 0xFF0A0410);
        // Corner accents
        int cAcc = 0xFFC45AD4;
        g.fill(panelX, panelY, panelX + 8, panelY + 2, cAcc);
        g.fill(panelX, panelY, panelX + 2, panelY + 8, cAcc);
        g.fill(panelX + PANEL_W - 8, panelY, panelX + PANEL_W, panelY + 2, cAcc);
        g.fill(panelX + PANEL_W - 2, panelY, panelX + PANEL_W, panelY + 8, cAcc);
        g.fill(panelX, panelY + PANEL_H - 2, panelX + 8, panelY + PANEL_H, cAcc);
        g.fill(panelX, panelY + PANEL_H - 8, panelX + 2, panelY + PANEL_H, cAcc);
        g.fill(panelX + PANEL_W - 8, panelY + PANEL_H - 2, panelX + PANEL_W, panelY + PANEL_H, cAcc);
        g.fill(panelX + PANEL_W - 2, panelY + PANEL_H - 8, panelX + PANEL_W, panelY + PANEL_H, cAcc);

        drawBackgroundMotes(g, panelX + 2, panelY + 2, PANEL_W - 4, PANEL_H - 4);

        int fam = currentFamily();
        int cap = currentCap();
        if (fam < 0 || cap <= 0 || minecraft == null || minecraft.player == null) {
            String err = "Equip an Infinium armor set or chestplate to access the Spacetime Crystal Matrix.";
            g.text(this.font, err, this.width / 2 - this.font.width(err) / 2, this.height / 2 - 4, 0xFFE8D4EE, false);
            return;
        }

        int filled = currentFilled();
        float targetRatio = Math.min(1f, Math.max(0f, filled / (float) cap));
        if (displayRatio < 0f) displayRatio = targetRatio;
        else displayRatio += (targetRatio - displayRatio) * 0.18f;
        if (Math.abs(targetRatio - displayRatio) < 0.002f) displayRatio = targetRatio;

        // Header section: Set title + worn armor icons + crystal count
        int accent = familyAccent(fam);
        SetLook look = SetLook.of(minecraft.player);
        String title = (Perk.FAMILY_NAMES[fam] + " Set") + " Matrix";
        g.text(this.font, title, panelX + 14, panelY + 10, accent, false);
        String progressText = filled + " / " + cap + (filled >= cap ? " (MAX)" : " Crystals");
        int ptw = this.font.width(progressText);
        int ptx = panelX + PANEL_W - 14 - ptw;
        drawCrystalIcon(g, ptx - 11, panelY + 9, 0.55f);
        g.text(this.font, progressText, ptx, panelY + 10, 0xFFE8D4EE, false);

        // Worn armor piece icons on the left of the sub-header
        int iconY = panelY + 22;
        if (look != null) {
            for (int i = 0; i < ARMOR_SLOTS.length; i++) {
                ItemStack piece = minecraft.player.getItemBySlot(ARMOR_SLOTS[i]);
                int ix = panelX + 14 + i * 18;
                g.fill(ix - 1, iconY - 1, ix + 17, iconY + 17, 0xFF3A1848);
                g.fill(ix, iconY, ix + 16, iconY + 16, 0xFF14081C);
                if (!piece.isEmpty()) g.item(piece, ix, iconY);
            }
        } else {
            ItemStack piece = minecraft.player.getItemBySlot(EquipmentSlot.CHEST);
            int ix = panelX + 14;
            g.fill(ix - 1, iconY - 1, ix + 17, iconY + 17, 0xFF3A1848);
            g.fill(ix, iconY, ix + 16, iconY + 16, 0xFF14081C);
            if (!piece.isEmpty()) g.item(piece, ix, iconY);
        }

        // M6(a): owned abilities as lit logo chips next to the armor icons; click = lights off/on (same toggle as Perk Settings).
        abilityChips.clear();
        int chipX = panelX + 14 + 4 * 18 + 8;
        for (var ab : dev.justin.infinium.ability.Abilities.all()) {
            Perk ap = Perk.ability(ab.id().getPath());
            if (ap == null || !ab.owned().test(minecraft.player)) continue;
            boolean on = ClientPerks.enabled(ap);
            MatrixLogo.draw(g, ab.id().getPath(), chipX, iconY - 1, 18, on ? MatrixLogo.LIT : MatrixLogo.OFF, accent, ap.ordinal());
            abilityChips.add(new int[]{chipX, iconY - 1, ap.ordinal()});
            if (mouseX >= chipX && mouseX < chipX + 18 && mouseY >= iconY - 1 && mouseY < iconY + 17) {
                chipTip = Component.translatable("ability." + ab.id().getNamespace() + "." + ab.id().getPath()).getString()
                    + (on ? ": On. Click to turn the lights off." : ": Lights off. Click to turn it back on.");
            }
            chipX += 22;
        }

        // Baseline perk note on sub-header right (scaled cleanly so it never overlaps armor icons)
        String baseNote = (look == null) ? "Chestplate Mode: Elytra wings scale per perk unlock"
            : switch (fam) {
            case 2, 3, 4 -> dev.justin.infinium.Info.tierNoteShort(fam);
            default -> "Unlock perks to advance set power & wings";
        };
        float bnScale = 0.72f;
        int bnW = (int) (this.font.width(baseNote) * bnScale);
        drawScaledText(g, baseNote, panelX + PANEL_W - 14 - bnW, iconY + 5, bnScale, 0xFFB898C8);

        // Animated Energy Progress Bar
        barW = PANEL_W - 28; // 308 px
        barH = 12;
        barX = panelX + 14;
        barY = panelY + 44;

        // Bar outer frame & dark track
        g.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0xFF7A3290);
        g.fill(barX, barY, barX + barW, barY + barH, 0xFF12061A);
        // Subtle grid ticks per crystal inside the bar track
        for (int c = 1; c < cap; c++) {
            int tx = barX + c * barW / cap;
            g.fill(tx, barY + barH - 3, tx + 1, barY + barH, 0x445A2870);
        }

        // Animated energy fill
        int fw = Math.round(displayRatio * barW);
        long ms = System.currentTimeMillis();
        if (fw > 0) {
            double wave = 0.5 + 0.5 * Math.sin(ms / 200.0);
            int rCol = (int) (((accent >>> 16) & 0xFF) * (0.75 + 0.25 * wave));
            int gCol = (int) (((accent >>> 8) & 0xFF) * (0.75 + 0.25 * wave));
            int bCol = (int) ((accent & 0xFF) * (0.75 + 0.25 * wave));
            int mainFill = 0xFF000000 | (Math.min(255, rCol) << 16) | (Math.min(255, gCol) << 8) | Math.min(255, bCol);
            int topGlow = 0xFF000000 | (Math.min(255, rCol + 45) << 16) | (Math.min(255, gCol + 35) << 8) | Math.min(255, bCol + 45);
            g.fill(barX, barY, barX + fw, barY + barH, mainFill);
            g.fill(barX, barY, barX + fw, barY + 3, topGlow);
            g.fill(barX, barY + barH - 2, barX + fw, barY + barH, 0x55000000);

            // Flowing energy current streaks along the filled bar
            int numParticles = Math.max(4, fw / 18);
            for (int i = 0; i < numParticles; i++) {
                int px = barX + (int) ((ms / 10 + i * 31L) % Math.max(1, fw));
                int py = barY + 2 + ((i * 7) % (barH - 4));
                int pw = Math.min(4, barX + fw - px);
                if (pw > 0) {
                    g.fill(px, py, px + pw, py + 2, 0xDDE8B8FF);
                }
            }
            // Leading edge energy flare
            if (fw < barW) {
                g.fill(Math.max(barX, barX + fw - 2), barY - 1, barX + fw + 1, barY + barH + 1, 0xFFFFD8FF);
            }
        }

        // Perk milestone marks on the energy bar & connected Perk Cards below
        String[] order = CrystalCharge.ORDER[fam];
        int perks = order.length;
        String hoveredTooltipTitle = null;
        String hoveredTooltipDesc = null;
        String hoveredTooltipStatus = null;

        int invCrystals = countInventoryCrystals(minecraft.player);
        int cardsTopY = barY + barH + 12;
        boolean twoRows = perks > 4; // 4 cards for Tier 1/2, 8 cards (2 rows of 4) for Infinity
        int cardH = twoRows ? 48 : 96;

        for (int i = 0; i < perks; i++) {
            int req = CrystalCharge.crystalsRequired(fam, i);
            boolean unlocked = filled >= req;
            Perk perkI = Perk.set(fam, order[i]);
            boolean lit = unlocked && ClientPerks.enabled(perkI);   // M5: same state as the settings menu
            int needed = req - filled;
            boolean canAfford = !unlocked && (invCrystals >= needed);
            int perksGained = unlocked ? 0 : Math.max(1, unlockedCount(req, cap, perks) - unlockedCount(filled, cap, perks));
            int markX = barX + (i + 1) * barW / perks;

            // Milestone activation line on the energy bar
            int markCol = unlocked ? 0xFFFFB8FF : (canAfford ? 0xFFFF00AA : 0xFF6A3882);
            g.fill(markX - 1, barY - 3, markX + 1, barY + barH + 3, markCol);
            // Diamond/node indicator at the bottom of the mark on the bar
            g.fill(markX - 2, barY + barH + 1, markX + 2, barY + barH + 4, unlocked ? accent : (canAfford ? 0xFFFF00AA : 0xFF3A1848));
            if (unlocked || canAfford) {
                g.fill(markX - 1, barY + barH + 1, markX + 1, barY + barH + 3, 0xFFFFFFFF);
            }

            // Card position: left-to-right book reading order
            int cardW, cardX, cardY;
            if (!twoRows) {
                int gap = 6;
                cardW = (barW - gap * (perks - 1)) / perks;
                cardX = barX + i * (cardW + gap);
                cardY = cardsTopY;
            } else {
                int row = i / 4;
                int col = i % 4;
                int gap = 6;
                cardW = (barW - gap * 3) / 4;
                cardX = barX + col * (cardW + gap);
                cardY = cardsTopY + row * (cardH + 6);
            }

            boolean hoverCard = mouseX >= cardX && mouseX < cardX + cardW && mouseY >= cardY && mouseY < cardY + cardH;
            boolean hoverMark = Math.abs(mouseX - markX) <= 4 && mouseY >= barY - 4 && mouseY <= barY + barH + 6;
            boolean hovered = hoverCard || hoverMark;

            int borderCol = unlocked
                    ? (hovered ? 0xFFFFB0FF : accent)
                    : (canAfford ? (hovered ? 0xFFFFFFFF : 0xFFFF00AA) : (hovered ? 0xFF8A4A9E : 0xFF3E1C4C));
            int bgCol = unlocked ? 0xFF1A0A26 : (canAfford ? (hovered ? 0xFF321244 : 0xFF220C2E) : 0xFF100618);

            g.fill(cardX, cardY, cardX + cardW, cardY + cardH, borderCol);
            g.fill(cardX + 1, cardY + 1, cardX + cardW - 1, cardY + cardH - 1, bgCol);
            // Top header strip inside card
            g.fill(cardX + 1, cardY + 1, cardX + cardW - 1, cardY + 11, unlocked ? 0xFF2D1240 : (canAfford ? 0xFF421550 : 0xFF180A22));

            String badge = String.valueOf(req);
            boolean up = fam >= 2;   // tier 2/3: perks are already active, crystals upgrade them (tier 1 keeps "unlock")
            String status = unlocked ? (up ? "UPGRADED" : "UNLOCKED") : (canAfford ? (perksGained > 1 ? ("READY (+" + perksGained + ")") : "READY") : ("NEED " + needed));
            int statusCol = unlocked ? 0xFFFF7CD3 : (canAfford ? 0xFFFF00AA : 0xFF8E729E);

            float bScale = 0.72f;
            int bw = (int) (this.font.width(badge) * bScale);
            drawScaledText(g, badge, cardX + 4, cardY + 3, bScale, unlocked ? 0xFFFFD0FF : (canAfford ? 0xFFFFFFFF : 0xFFB494C4));
            drawCrystalIcon(g, cardX + 4 + bw + 2, cardY + 2, 0.5f);

            int sw = (int) (this.font.width(status) * 0.62f);
            drawScaledText(g, status, cardX + cardW - sw - 3, cardY + 3, 0.62f, statusCol);

            // Bottom action strip
            int btnH = twoRows ? 11 : 14;
            int btnY = cardY + cardH - btnH - 3;
            int btnX = cardX + 3;
            int btnW = cardW - 6;

            if (canAfford) {
                boolean btnHover = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
                int bBorder = btnHover || hovered ? 0xFFFFFFFF : 0xFFFF00AA;
                int bBg = btnHover ? 0xFF5A1C72 : 0xFF351046;
                g.fill(btnX, btnY, btnX + btnW, btnY + btnH, bBorder);
                g.fill(btnX + 1, btnY + 1, btnX + btnW - 1, btnY + btnH - 1, bBg);

                String verb = up ? "UPGRADE" : "UNLOCK";
                String bLabel = (perksGained > 1) ? (verb + " " + perksGained + " PERKS (-" + needed + ")") : (verb + " PERK (-" + needed + ")");
                int bCol = (btnHover || hovered) ? 0xFFFFFFFF : 0xFFFFB8E7;
                float bLabelScale = twoRows ? 0.58f : 0.68f;
                int blw = (int) (this.font.width(bLabel) * bLabelScale);
                drawScaledText(g, bLabel, btnX + (btnW - blw) / 2, btnY + (twoRows ? 2 : 3), bLabelScale, bCol);
            } else if (unlocked) {
                String aLabel = lit ? "ON · CLICK = LIGHTS OFF" : "LIGHTS OFF · CLICK = ON";
                float aScale = twoRows ? 0.50f : 0.56f;
                int aw = (int) (this.font.width(aLabel) * aScale);
                drawScaledText(g, aLabel, btnX + (btnW - aw) / 2, btnY + (twoRows ? 2 : 3), aScale, lit ? 0xFFFF7CD3 : 0xFF8A7A96);
            } else {
                String nLabel = "Need " + needed + " (Have " + invCrystals + ")";
                float nScale = twoRows ? 0.56f : 0.64f;
                int nw = (int) (this.font.width(nLabel) * nScale);
                drawScaledText(g, nLabel, btnX + (btnW - nw) / 2, btnY + (twoRows ? 2 : 3), nScale, 0xFF8A6E96);
            }

            // M5: perk logo. Lit + animated when unlocked & on, dimmed & still when "lights off", dark + padlock when locked.
            String shortDesc = PerkValues.shortPerkDesc(fam, order[i]);
            int state = !unlocked ? MatrixLogo.LOCKED : (lit ? MatrixLogo.LIT : MatrixLogo.OFF);
            int descColor = !unlocked ? (canAfford ? 0xFFFFFFFF : 0xFF9A82A8) : (lit ? 0xFFF2E2F8 : 0xFF8A7A96);
            if (!twoRows) {
                int ls = 30;
                MatrixLogo.draw(g, order[i], cardX + (cardW - ls) / 2, cardY + 14, ls, state, accent, i);
                drawWrappedCardText(g, shortDesc, cardX + 4, cardY + 14 + ls + 2, cardW - 8, cardH - (14 + ls + 2) - 18, 0.66f, descColor);
            } else {
                int ls = 18;
                MatrixLogo.draw(g, order[i], cardX + 3, cardY + 13, ls, state, accent, i);
                drawWrappedCardText(g, shortDesc, cardX + 3 + ls + 2, cardY + 13, cardW - ls - 8, cardH - 26, 0.6f, descColor);
            }

            if (hovered) {
                hoveredTooltipTitle = Perk.FAMILY_NAMES[fam] + " Milestone " + (i + 1) + " (" + req + " / " + cap + " Crystals)";
                hoveredTooltipDesc = fullPerkDescription(fam, order[i], shortDesc);
                hoveredTooltipStatus = unlocked ? (lit ? (up ? "Status: Upgraded & On. Click to turn the lights off (also in Infinium Perk Settings)."
                                : "Status: Unlocked & On. Click to turn the lights off (also in Infinium Perk Settings).")
                        : (up ? "Status: Upgraded, lights off. Click to turn it back on." : "Status: Unlocked, lights off. Click to turn it back on."))
                        : (canAfford ? (up ? ("Status: Ready to Upgrade! Click to spend " + needed + " Crystals and upgrade " + (perksGained > 1 ? (perksGained + " perks!") : "this perk."))
                                : ("Status: Ready to Unlock! Click to spend " + needed + " Crystals and unlock " + (perksGained > 1 ? (perksGained + " perks!") : "this perk.")))
                        : ("Status: Locked — Requires " + needed + " more Crystals (You have " + invCrystals + " in inventory)"));
            }
        }

        if (hoveredTooltipTitle == null && chipTip != null) {
            g.tooltip(this.font, List.of(ClientTooltipComponent.create(Component.literal(chipTip).getVisualOrderText())), mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null);
        }
        chipTip = null;
        if (hoveredTooltipTitle != null) {
            List<ClientTooltipComponent> tip = List.of(
                ClientTooltipComponent.create(Component.literal(hoveredTooltipTitle).getVisualOrderText()),
                ClientTooltipComponent.create(Component.literal(hoveredTooltipDesc).getVisualOrderText()),
                ClientTooltipComponent.create(Component.literal(hoveredTooltipStatus).getVisualOrderText())
            );
            String tn = dev.justin.infinium.Info.tierNote(look == null ? -1 : fam);
            if (tn != null) {
                tip = new java.util.ArrayList<>(tip);
                tip.add(ClientTooltipComponent.create(Component.literal(tn).getVisualOrderText()));
            }
            g.tooltip(this.font, tip, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null);
        }
    }

    private static int unlockedCount(int filled, int cap, int perks) {
        int n = 0;
        for (int i = 0; i < perks; i++) {
            int need = (int) Math.ceil((i + 1) * (cap / (double) perks));
            if (filled >= need) n++;
        }
        return n;
    }

    private String fullPerkDescription(int fam, String kind, String fallback) {
        Perk target = Perk.set(fam, kind);
        for (Object[] entry : Info.setLines(fam)) {
            if (entry[1] == target) return (String) entry[0];
        }
        return fallback;
    }

    private void drawScaledText(GuiGraphicsExtractor g, String text, int x, int y, float scale, int color) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        g.text(this.font, text, 0, 0, color, false);
        pose.popMatrix();
    }

    private void drawWrappedCardText(GuiGraphicsExtractor g, String text, int x, int y, int width, int height, float scale, int color) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        int wrap = Math.max(24, (int) (width / scale));
        int maxH = (int) (height / scale);
        int ly = 0;
        for (FormattedCharSequence line : this.font.split(FormattedText.of(text), wrap)) {
            if (ly + this.font.lineHeight > maxH + 2) break;
            g.text(this.font, line, 0, ly, color, false);
            ly += this.font.lineHeight + 1;
        }
        pose.popMatrix();
    }

    private static int familyAccent(int fam) {
        return switch (fam) {
            case 0 -> 0xFFC45AD4; // Astral
            case 1 -> 0xFFD24A8A; // Cybernetic
            case 2 -> 0xFFA14AD0; // Singularity
            case 3 -> 0xFFE0235A; // Cyber Psycho
            case 4 -> 0xFFD84CFF; // Infinity
            default -> 0xFFC45AD4;
        };
    }

    private static void drawBackgroundMotes(GuiGraphicsExtractor g, int x0, int y0, int w, int h) {
        if (!InfiniumClientConfig.uiEffects()) return;
        long ms = System.currentTimeMillis();
        for (int i = 0; i < 18; i++) {
            int hh = (i * 0x9E3779B9) ^ 0x5A17C3;
            int period = 6500 + (hh & 2047);
            float t = ((ms + ((hh >>> 5) & 4095)) % period) / (float) period;
            int mx = x0 + Math.floorMod(hh >>> 3, w);
            int my = y0 + h - 2 - (int) (t * (h - 4));
            int a = (int) (90 * Math.sin(Math.PI * t));
            if (a < 8) continue;
            g.fill(mx, my, mx + 2, my + 2, (a << 24) | ((i & 1) == 0 ? 0xC45AD4 : 0x8A3AA0));
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() == 0) {
            for (int[] c : abilityChips) {   // M6(a): ability chip toggle
                if (event.x() >= c[0] && event.x() < c[0] + 18 && event.y() >= c[1] && event.y() < c[1] + 18) {
                    Perk ap = Perk.values()[c[2]];
                    boolean nowOn = !ClientPerks.enabled(ap);
                    ClientPerks.set(ap, nowOn);
                    if (minecraft != null) minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, nowOn ? 1.25F : 0.7F));
                    return true;
                }
            }
            int fam = currentFamily();
            int cap = currentCap();
            int filled = currentFilled();
            if (fam >= 0 && cap > 0 && minecraft != null && minecraft.player != null) {
                int invCrystals = countInventoryCrystals(minecraft.player);
                String[] order = CrystalCharge.ORDER[fam];
                int perks = order.length;
                boolean twoRows = perks > 4;
                int cardH = twoRows ? 48 : 96;
                int cardsTopY = barY + barH + 12;

                for (int i = 0; i < perks; i++) {
                    int cardW, cardX, cardY;
                    if (!twoRows) {
                        int gap = 6;
                        cardW = (barW - gap * (perks - 1)) / perks;
                        cardX = barX + i * (cardW + gap);
                        cardY = cardsTopY;
                    } else {
                        int row = i / 4;
                        int col = i % 4;
                        int gap = 6;
                        cardW = (barW - gap * 3) / 4;
                        cardX = barX + col * (cardW + gap);
                        cardY = cardsTopY + row * (cardH + 6);
                    }

                    int markX = barX + (i + 1) * barW / perks;
                    boolean hoverCard = event.x() >= cardX && event.x() < cardX + cardW && event.y() >= cardY && event.y() < cardY + cardH;
                    boolean hoverMark = Math.abs(event.x() - markX) <= 4 && event.y() >= barY - 4 && event.y() <= barY + barH + 6;

                    if (hoverCard || hoverMark) {
                        int req = CrystalCharge.crystalsRequired(fam, i);
                        if (filled >= req) {   // M5: unlocked card click = toggle lights (shared toggle state)
                            Perk perk = Perk.set(fam, order[i]);
                            if (perk != null && ClientPerks.canSend()) {
                                boolean nowOn = !ClientPerks.enabled(perk);
                                ClientPerks.set(perk, nowOn);
                                minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, nowOn ? 1.25F : 0.7F));
                            }
                            return true;
                        }
                        int needed = req - filled;
                        if (invCrystals >= needed) {
                            if (ClientPlayNetworking.canSend(CrystalCharge.Insert.TYPE)) {
                                ClientPlayNetworking.send(new CrystalCharge.Insert(containerId, slotIndex, req));
                            }
                            return true;
                        } else {
                            minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 0.6F));
                            return true;
                        }
                    }
                }
            }
        }
        return super.mouseClicked(event, doubled);
    }
}
