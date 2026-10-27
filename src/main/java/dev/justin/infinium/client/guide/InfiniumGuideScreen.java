package dev.justin.infinium.client.guide;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.justin.infinium.Infinium;
import dev.justin.infinium.InfiniumStyle;
import dev.justin.infinium.client.UiInfection;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * Two-page Informational Crystal guide. Text comes from {@code assets/infinium/guide/pages.json} (written by gen_book.py).
 * Scaled ~2x larger than legacy v2, with 50/50 vertically centered Welcome pages, inline Spacetime Crystal icons ({C}),
 * vertically centered right-page armor notes, two-column Good to Know spreads, and deep-space void backdrop.
 */
public class InfiniumGuideScreen extends Screen {
    private static final int BOOK_W = 292;
    private static final int BOOK_H = 196;
    static final int FRAMES = 16;
    static final int FRAME = 128;
    private static final int SET_FRAMES = 8;
    private static final int SET_FRAME = 64;
    private static final Identifier BOOK = Identifier.fromNamespaceAndPath("infinium", "textures/gui/guide/book.png");
    private static final Identifier CRYSTAL_TEX = Identifier.fromNamespaceAndPath("infinium", "textures/item/spacetime_crystal.png");
    private static final int CRYSTAL_FRAMES = 80;
    /** Scaled ~2x larger than old 0.8f cap while keeping a comfortable screen margin. */
    private static final float MAX_SCALE = 1.55f;
    private static final float FILL = 0.90f;
    private static final int SLOT_EDGE = 0xFF6A4A78, SLOT_FILL = 0xFF3A3346, PANEL_BG = 0xCC2A2433, RESULT_EDGE = 0xFFC45AD4, ARROW = 0xFF8A5AA0;
    private static final String CRYSTAL_TOKEN = "\uE000";

    private final Screen back;
    private final List<Spread> spreads = new ArrayList<>();
    private int page;
    private boolean closing;

    public InfiniumGuideScreen(Screen back) {
        super(Component.translatable("item.infinium.infinium_guide"));
        this.back = back;
    }

    private float bookScale() {
        float sx = (this.width * FILL) / (float) BOOK_W;
        float sy = (this.height * FILL) / (float) BOOK_H;
        return Math.min(MAX_SCALE, Math.min(sx, sy));
    }

    private int bookLeft() {
        return (int) ((this.width - BOOK_W * bookScale()) / 2f);
    }

    private int bookTop() {
        return (int) ((this.height - BOOK_H * bookScale()) / 2f);
    }

    @Override
    protected void init() {
        super.init();
        sheets = null;
        clearWidgets();
        load();
        float s = bookScale();
        int left = bookLeft();
        int top = bookTop();
        int bw = Math.max(30, (int) (46 * s));
        int bh = Math.max(10, (int) (14 * s));
        addRenderableWidget(new GuideButton(left + (int) (16 * s), top + (int) ((BOOK_H - 24) * s), bw, bh, Component.literal("Back"), b -> turn(-1)));
        addRenderableWidget(new GuideButton(left + (int) ((BOOK_W - 62) * s), top + (int) ((BOOK_H - 24) * s), bw, bh, Component.literal("Next"), b -> turn(1)));
        int idxW = Math.max(28, (int) (42 * s));
        addRenderableWidget(new GuideButton(left + (int) (74 * s), top + (int) ((BOOK_H - 24) * s), idxW, bh, Component.literal("Index"), b -> jumpToContents()));
    }

    private void jumpToContents() {
        for (int i = 0; i < spreads.size(); i++) {
            if (isContents(spreads.get(i))) {
                page = i;
                return;
            }
        }
        page = 0;
    }

    private void turn(int delta) {
        page = Math.max(0, Math.min(spreads.size() - 1, page + delta));
    }

    private void load() {
        if (!spreads.isEmpty()) return;
        var id = Identifier.fromNamespaceAndPath("infinium", "guide/pages.json");
        var resource = Minecraft.getInstance().getResourceManager().getResource(id);
        if (resource.isEmpty()) {
            spreads.add(new Spread("Informational Crystal", "The page file is missing. Run gen_book.py.", "", ""));
            return;
        }
        try (var reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (var element : root.getAsJsonArray("spreads")) {
                JsonObject o = element.getAsJsonObject();
                spreads.add(new Spread(
                        o.get("title").getAsString(),
                        o.get("body").getAsString(),
                        o.has("art") && !o.get("art").isJsonNull() ? o.get("art").getAsString() : "",
                        o.has("recipe") && !o.get("recipe").isJsonNull() ? o.get("recipe").getAsString() : "",
                        o.has("note") && !o.get("note").isJsonNull() ? o.get("note").getAsString() : ""));
            }
        } catch (Exception e) {
            spreads.add(new Spread("Informational Crystal", "Could not read the guide pages.", "", ""));
        }
        if (spreads.isEmpty()) spreads.add(new Spread("Informational Crystal", "", "", ""));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        float s = bookScale();
        int left = bookLeft();
        int top = bookTop();
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(left, top);
        pose.scale(s, s);

        graphics.fill(0, 0, BOOK_W, BOOK_H, 0xFF6A2A78);
        graphics.fill(1, 1, BOOK_W - 1, BOOK_H - 1, 0xFF000000);
        drawPageAnim(graphics);
        graphics.fill(145, 8, 147, BOOK_H - 28, 0xFF6A2A78);
        Spread spread = spreads.get(Math.min(page, spreads.size() - 1));
        if (!"additional_info".equals(spread.art)) {
            graphics.text(this.font, spread.title, 16, 14, titleColor(spread.art), false);
        }

        int localMouseX = (int) ((mouseX - left) / s);
        int localMouseY = (int) ((mouseY - top) / s);
        ItemStack hovered = ItemStack.EMPTY;

        if (isContents(spread)) {
            drawContents(graphics, localMouseX, localMouseY);
        } else if (isWelcome()) {
            drawWelcome(graphics, spread.body);
        } else if ("flow".equals(spread.art)) {
            drawTwoColumnFlow(graphics, spread.body);
        } else if ("additional_info".equals(spread.art)) {
            drawAdditionalInfo(graphics, spread, localMouseX, localMouseY);
        } else {
            drawBody(graphics, spread.body, 16, 28, 124);
            if ("welcome".equals(spread.art)) {
                blitArt(graphics, spread.art, 168, 18, 100);
            } else if ("settings".equals(spread.art)) {
                drawSettings(graphics, 152, 16);
            } else if ("matrix".equals(spread.art)) {
                drawMatrixShowcase(graphics, 150, 16);
            } else if ("meteor".equals(spread.art)) {
                drawMeteorShowcase(graphics, 150, 16);
            } else if ("templates".equals(spread.art) && spread.note.isEmpty()) {
                // Bring both template images closer to the vertical center so the gap is balanced (#5)
                int topArtY = 24;
                if (!blitSheet(graphics, spread.art, 151, topArtY, 124, 62)) {
                    blitArt(graphics, spread.art, 158, topArtY, 64);
                }
                hovered = drawRecipes(graphics, spread.recipe, 150, 96, 56, localMouseX, localMouseY);
            } else if (!spread.note.isEmpty() && spread.art.isEmpty()) {
                hovered = drawRecipes(graphics, spread.recipe, 150, 22, 72, localMouseX, localMouseY);
                drawBody(graphics, spread.note, 152, 102, 124);
            } else {
                int topArtY = 8;
                int artH = 62;
                int recipeY = 116;
                int recipeH = spread.art.isEmpty() ? 156 : 52;
                if (!spread.art.isEmpty()) {
                    if (!blitSheet(graphics, spread.art, 151, topArtY, 124, artH)) {
                        blitArt(graphics, spread.art, 158, topArtY, 64);
                    }
                    if (!spread.note.isEmpty()) {
                        // Vertically center the piece text equally between the top render and bottom recipe (#14)
                        int gapTop = topArtY + artH;
                        int gapBottom = recipeY;
                        drawNoteBandCentered(graphics, spread.note, 152, gapTop, gapBottom, 124);
                    }
                }
                hovered = drawRecipes(graphics, spread.recipe, 150, spread.art.isEmpty() ? 8 : recipeY, recipeH, localMouseX, localMouseY);
            }
        }

        String count = (page + 1) + " / " + spreads.size();
        graphics.text(this.font, count, BOOK_W / 2 + 18 - this.font.width(count) / 2, BOOK_H - 20, 0xFFD24A8A, false);
        pose.popMatrix();

        drawEdgeParticles(graphics, left, top, BOOK_W * s, BOOK_H * s);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (!hovered.isEmpty()) {
            List<ClientTooltipComponent> lines = Screen.getTooltipFromItem(Minecraft.getInstance(), hovered).stream()
                    .map(Component::getVisualOrderText)
                    .map(ClientTooltipComponent::create)
                    .toList();
            graphics.tooltip(this.font, lines, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null);
        }
    }

    private boolean isContents(Spread sp) {
        return "Contents".equals(sp.title) || "contents".equals(sp.art);
    }

    private int getFirstAdditionalInfoIndex() {
        for (int i = 0; i < spreads.size(); i++) {
            if ("additional_info".equals(spreads.get(i).art)) return i;
        }
        return -1;
    }

    private void drawContents(GuiGraphicsExtractor g, int localMx, int localMy) {
        String sub = "Click any chapter to jump directly to its page:";
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(16, 26);
        pose.scale(0.68f, 0.68f);
        g.text(this.font, sub, 0, 0, 0xFFB898C8, false);
        pose.popMatrix();

        int firstAddIdx = getFirstAdditionalInfoIndex();
        int totalChapters = 0;
        for (int i = 0; i < spreads.size(); i++) {
            Spread sp = spreads.get(i);
            if (isContents(sp)) continue;
            if ("additional_info".equals(sp.art) && i != firstAddIdx) continue;
            totalChapters++;
        }
        int leftCap = totalChapters <= 14 ? 7 : 8;
        int rowIdx = 0;
        for (int i = 0; i < spreads.size(); i++) {
            Spread sp = spreads.get(i);
            if (isContents(sp)) continue;
            if ("additional_info".equals(sp.art) && i != firstAddIdx) continue;

            boolean isAdd = "additional_info".equals(sp.art);
            String title = isAdd ? "Additional Information" : sp.title;
            String pgNum = isAdd ? (i + 1) + "–" + spreads.size() : String.valueOf(i + 1);
            int accent = isAdd ? 0xFFFFD700 : titleColor(sp.art);

            boolean rightCol = rowIdx >= leftCap;
            int colRow = rightCol ? (rowIdx - leftCap) : rowIdx;
            int rx = rightCol ? 154 : 16;
            int ry = (rightCol ? 22 : 38) + colRow * 16;
            int rw = 122;
            int rh = 14;
            boolean hover = localMx >= rx && localMx < rx + rw && localMy >= ry && localMy < ry + rh;

            int bg = hover ? 0xFF3A1848 : 0xFF160820;
            int border = hover ? 0xFFD864FF : (isAdd ? 0xFF8A6020 : 0xFF5A246E);
            g.fill(rx, ry, rx + rw, ry + rh, border);
            g.fill(rx + 1, ry + 1, rx + rw - 1, ry + rh - 1, bg);

            pose.pushMatrix();
            pose.translate(rx + 5, ry + 3);
            pose.scale(0.75f, 0.75f);
            g.text(this.font, title, 0, 0, hover ? 0xFFFFFFFF : accent, false);
            pose.popMatrix();

            pose.pushMatrix();
            int pw = (int) (this.font.width("p." + pgNum) * 0.7f);
            pose.translate(rx + rw - pw - 4, ry + 3);
            pose.scale(0.7f, 0.7f);
            g.text(this.font, "p." + pgNum, 0, 0, 0xFFB898C8, false);
            pose.popMatrix();

            rowIdx++;
        }
    }

    private int hitContentsSpread(double mouseX, double mouseY) {
        if (spreads.isEmpty() || !isContents(spreads.get(Math.min(page, spreads.size() - 1)))) return -1;
        float s = bookScale();
        int localMx = (int) ((mouseX - bookLeft()) / s);
        int localMy = (int) ((mouseY - bookTop()) / s);
        int firstAddIdx = getFirstAdditionalInfoIndex();
        int totalChapters = 0;
        for (int i = 0; i < spreads.size(); i++) {
            Spread sp = spreads.get(i);
            if (isContents(sp)) continue;
            if ("additional_info".equals(sp.art) && i != firstAddIdx) continue;
            totalChapters++;
        }
        int leftCap = totalChapters <= 14 ? 7 : 8;
        int rowIdx = 0;
        for (int i = 0; i < spreads.size(); i++) {
            Spread sp = spreads.get(i);
            if (isContents(sp)) continue;
            if ("additional_info".equals(sp.art) && i != firstAddIdx) continue;
            boolean rightCol = rowIdx >= leftCap;
            int colRow = rightCol ? (rowIdx - leftCap) : rowIdx;
            int rx = rightCol ? 154 : 16;
            int ry = (rightCol ? 22 : 38) + colRow * 16;
            int rw = 122;
            int rh = 14;
            if (localMx >= rx && localMx < rx + rw && localMy >= ry && localMy < ry + rh) {
                return i;
            }
            rowIdx++;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() == 0) {
            float s = bookScale();
            int localMx = (int) ((event.x() - bookLeft()) / s);
            int localMy = (int) ((event.y() - bookTop()) / s);
            Spread spread = spreads.get(Math.min(page, spreads.size() - 1));
            if ("additional_info".equals(spread.art)) {
                int clickedSub = hitAdditionalInfoTab(localMx, localMy);
                if (clickedSub >= 0) {
                    int target = getAdditionalInfoIndex(clickedSub);
                    if (target >= 0 && target != page) {
                        float cVol = 0.8F * dev.justin.infinium.client.InfiniumClientConfig.soundVolumeMaster() * dev.justin.infinium.client.InfiniumClientConfig.soundVolumeCrystal();
                        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(dev.justin.infinium.ModSounds.crystal(0, dev.justin.infinium.ModSounds.INSERT).value(), 1.0F, cVol));
                        page = target;
                        return true;
                    }
                }
            }
            int target = hitContentsSpread(event.x(), event.y());
            if (target >= 0) {
                float cVol = 0.8F * dev.justin.infinium.client.InfiniumClientConfig.soundVolumeMaster() * dev.justin.infinium.client.InfiniumClientConfig.soundVolumeCrystal();
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(dev.justin.infinium.ModSounds.crystal(0, dev.justin.infinium.ModSounds.INSERT).value(), 1.0F, cVol));
                page = target;
                return true;
            }
        }
        return super.mouseClicked(event, doubled);
    }

    /** Darker, calmer deep-space backdrop matching the main menu scene (#13). */
    private void drawPageAnim(GuiGraphicsExtractor graphics) {
        if (!dev.justin.infinium.client.InfiniumClientConfig.uiEffects()) return;
        UiInfection.guideBackdrop(graphics, 2, 2, BOOK_W - 4, BOOK_H - 4);
    }

    private void drawEdgeParticles(GuiGraphicsExtractor graphics, float left, float top, float w, float h) {
        long ms = System.currentTimeMillis();
        int n = 28;
        for (int i = 0; i < n; i++) {
            float px, py, ox, oy;
            int edge = i % 4;
            float t = (i / 4) / (n / 4f);
            float wobble = (float) Math.sin(ms / 400.0 + i) * 3f;
            switch (edge) {
                case 0 -> { px = left + t * w; py = top; ox = 0; oy = -1; }
                case 1 -> { px = left + w; py = top + t * h; ox = 1; oy = 0; }
                case 2 -> { px = left + (1 - t) * w; py = top + h; ox = 0; oy = 1; }
                default -> { px = left; py = top + (1 - t) * h; ox = -1; oy = 0; }
            }
            float dist = 3f + (hash(ms / 200, i) & 7) + wobble * 0.2f;
            int a = 70 - (int) (dist * 3.5f);
            if (a < 8) continue;
            int x = (int) (px + ox * dist);
            int y = (int) (py + oy * dist);
            int col = (i & 1) == 0 ? 0xC45AD4 : 0xE070C0;
            graphics.fill(x, y, x + 2, y + 2, (a << 24) | col);
        }
    }

    /** Vertically centers the piece description lines between gapTop and gapBottom on the right page (#14). */
    private void drawNoteBandCentered(GuiGraphicsExtractor graphics, String note, int x, int gapTop, int gapBottom, int width) {
        float scale = 0.68f;
        int wrap = Math.max(20, (int) (width / scale));
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (String para : note.split("\n", -1)) {
            if (para.isEmpty()) continue;
            lines.addAll(this.font.split(FormattedText.of(para), wrap));
        }
        int lh = this.font.lineHeight + 1;
        float blockH = (lines.size() * lh - 1) * scale;
        float startY = gapTop + ((gapBottom - gapTop) - blockH) / 2f;

        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, startY);
        pose.scale(scale, scale);
        int lineY = 0;
        for (FormattedCharSequence line : lines) {
            graphics.text(this.font, line, 0, lineY, 0xFFE8D4EE, false);
            lineY += lh;
        }
        pose.popMatrix();
    }

    private void drawMatrixShowcase(GuiGraphicsExtractor graphics, int x, int y) {
        long ms = System.currentTimeMillis();
        var pose = graphics.pose();

        // 1. Total Matrix Progression Box (replica of HUD X / 80 Counter)
        int boxW = 124, boxH = 24;
        graphics.fill(x, y, x + boxW, y + boxH, 0xFF5A246E);
        graphics.fill(x + 1, y + 1, x + boxW - 1, y + boxH - 1, 0xEE160822);

        // Blit animated Spacetime Crystal icon
        int frame = (int) ((ms / 100L) % CRYSTAL_FRAMES);
        pose.pushMatrix();
        pose.translate(x + 4, y + 4);
        pose.scale(0.80f, 0.80f);
        graphics.blit(RenderPipelines.GUI_TEXTURED, CRYSTAL_TEX, 0, 0, 0, frame * 16, 16, 16, 16, 16 * CRYSTAL_FRAMES);
        pose.popMatrix();

        pose.pushMatrix();
        pose.translate(x + 20, y + 3);
        pose.scale(0.60f, 0.60f);
        graphics.text(this.font, "LIFETIME MATRIX PROGRESSION", 0, 0, 0xFFB898C8, false);
        pose.popMatrix();

        pose.pushMatrix();
        pose.translate(x + 20, y + 9);
        pose.scale(0.68f, 0.68f);
        graphics.text(this.font, "80 / 80 Crystals (MAX)", 0, 0, 0xFFFFD700, false);
        pose.popMatrix();

        // Mini Spacetime Energy Bar underneath
        int barX = x + 4, barY = y + 17, barW = 116, barH = 4;
        graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF3A1248);
        graphics.fill(barX + 1, barY + 1, barX + barW - 1, barY + barH - 1, 0xFF0D0312);
        int fillW = barW - 2;
        for (int px = 0; px < fillW; px++) {
            float u = (float) px / fillW;
            float wave = (float) (0.5 + 0.5 * Math.sin(u * 6.0 + ms / 350.0));
            int r = (int) (180 + 75 * wave);
            int g = (int) (40 + 50 * wave);
            int b = (int) (200 + 55 * wave);
            int col = 0xFF000000 | (r << 16) | (g << 8) | b;
            graphics.fill(barX + 1 + px, barY + 1, barX + 2 + px, barY + barH - 1, col);
        }

        // 2. Upgrade Milestone Cards (Tier 1, Tier 2, Tier 3)
        int startY = y + 27;
        drawMilestoneCard(graphics, x, startY, 124, 25,
                "Tier 1: Astral & Cybernetic", 0xFFC45AD4,
                "8 Crystals Max • 4 Perks (2/perk)", 8, 4, 0xFF00E5FF, ms);

        drawMilestoneCard(graphics, x, startY + 28, 124, 25,
                "Tier 2: Singularity & Cyber Psycho", 0xFFA14AD0,
                "16 Crystals Max • 4 Perks (4/perk)", 16, 4, 0xFFA14AD0, ms);

        drawMilestoneCard(graphics, x, startY + 56, 124, 26,
                "Tier 3: Infinity Armor", 0xFFFFD700,
                "32 Crystals Max • 8 Perks (4/perk)", 32, 8, 0xFFFFD700, ms);

        // 3. Footer Summary Box
        int footY = startY + 85;
        graphics.fill(x, footY, x + 124, footY + 31, 0xFF4A1A5A);
        graphics.fill(x + 1, footY + 1, x + 123, footY + 30, 0xEE12051A);

        pose.pushMatrix();
        pose.translate(x + 5, footY + 4);
        pose.scale(0.60f, 0.60f);
        graphics.text(this.font, "✦ Matrix Sockets: 80 Crystals Total", 0, 0, 0xFFFFAA44, false);
        pose.popMatrix();

        pose.pushMatrix();
        pose.translate(x + 5, footY + 12);
        pose.scale(0.60f, 0.60f);
        graphics.text(this.font, "✦ Crafting Ingots: 20 Crystals (4x/set)", 0, 0, 0xFFD2C0D4, false);
        pose.popMatrix();

        pose.pushMatrix();
        pose.translate(x + 5, footY + 20);
        pose.scale(0.60f, 0.60f);
        graphics.text(this.font, "✦ The Golden Total: 100 Crystals", 0, 0, 0xFF55FFFF, false);
        pose.popMatrix();
    }

    private void drawMilestoneCard(GuiGraphicsExtractor graphics, int cx, int cy, int cw, int ch,
                                   String title, int titleCol, String sub, int cap, int perks, int pipCol, long ms) {
        graphics.fill(cx, cy, cx + cw, cy + ch, 0xFF4A1A5A);
        graphics.fill(cx + 1, cy + 1, cx + cw - 1, cy + ch - 1, 0xDD12061A);

        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(cx + 4, cy + 2);
        pose.scale(0.65f, 0.65f);
        graphics.text(this.font, title, 0, 0, titleCol, false);
        pose.popMatrix();

        pose.pushMatrix();
        pose.translate(cx + 4, cy + 9);
        pose.scale(0.57f, 0.57f);
        graphics.text(this.font, sub, 0, 0, 0xFFB898C8, false);
        pose.popMatrix();

        // Draw milestone perk pips
        int pipGap = 2;
        int availW = cw - 8;
        int totalGap = (perks - 1) * pipGap;
        int pipW = Math.max(6, (availW - totalGap) / perks);
        int pipH = 4;
        int pipY = cy + ch - 6;
        for (int p = 0; p < perks; p++) {
            int px = cx + 4 + p * (pipW + pipGap);
            graphics.fill(px, pipY, px + pipW, pipY + pipH, 0xFF30103A);
            float wave = (float) (0.8 + 0.2 * Math.sin(ms / 250.0 + p * 0.8));
            int r = (int) (((pipCol >> 16) & 0xFF) * wave);
            int g = (int) (((pipCol >> 8) & 0xFF) * wave);
            int b = (int) ((pipCol & 0xFF) * wave);
            int col = 0xFF000000 | (r << 16) | (g << 8) | b;
            graphics.fill(px + 1, pipY + 1, px + pipW - 1, pipY + pipH - 1, col);
        }
    }

    private void drawMeteorShowcase(GuiGraphicsExtractor graphics, int x, int y) {
        long ms = System.currentTimeMillis();
        var pose = graphics.pose();

        // 1. Animated Night Sky Celestial Event Window (124 x 56)
        int skyW = 124, skyH = 56;
        graphics.fill(x, y, x + skyW, y + skyH, 0xFF8A3A10);
        graphics.fill(x + 1, y + 1, x + skyW - 1, y + skyH - 1, 0xFF06030A);

        // Twinkling stars
        for (int i = 0; i < 14; i++) {
            int sx = x + 6 + Math.abs(hash(i, 42) % 110);
            int sy = y + 4 + Math.abs(hash(i, 99) % 36);
            float twinkle = (float) (0.4 + 0.6 * Math.sin(ms / 300.0 + i * 1.7));
            int alpha = (int) (180 * twinkle);
            graphics.fill(sx, sy, sx + 1, sy + 1, (alpha << 24) | 0xFFE0E0);
        }

        // Animated falling meteors
        for (int m = 0; m < 2; m++) {
            float phase = ((ms + m * 900L) % 1800L) / 1800.0f;
            float mx = x + 110 - phase * 100;
            float my = y + 4 + phase * 38;
            if (my < y + skyH - 14) {
                for (int t = 1; t <= 5; t++) {
                    float tx = mx + t * 3.5f;
                    float ty = my - t * 1.5f;
                    int tailCol = t > 3 ? 0x66FF3300 : 0xAAFFAA00;
                    graphics.fill((int) tx, (int) ty, (int) tx + 2, (int) ty + 2, tailCol);
                }
                graphics.fill((int) mx, (int) my, (int) mx + 3, (int) my + 3, 0xFFFFFFAA);
            }
        }

        // Ground silhouette
        int groundY = y + skyH - 12;
        graphics.fill(x + 1, groundY, x + skyW - 1, y + skyH - 1, 0xFF140A18);
        graphics.fill(x + 1, groundY, x + skyW - 1, groundY + 1, 0xFF3A1A30);

        // Crater impact pulse
        int craterX = x + 38;
        int ringPhase = (int) ((ms / 50L) % 24);
        int ringAlpha = Math.max(0, 160 - ringPhase * 6);
        graphics.fill(craterX - ringPhase, groundY - ringPhase / 3, craterX + ringPhase, groundY + ringPhase / 3, (ringAlpha << 24) | 0xFF9900);
        graphics.fill(craterX - 3, groundY - 1, craterX + 3, groundY + 2, 0xFFFFCC00);

        // Floating fragment item above impact
        if (Infinium.STRANGE_FRAGMENTS != null) {
            pose.pushMatrix();
            pose.translate(craterX - 4, groundY - 14);
            pose.scale(0.55f, 0.55f);
            graphics.item(new ItemStack(Infinium.STRANGE_FRAGMENTS), 0, 0);
            pose.popMatrix();
        }

        pose.pushMatrix();
        pose.translate(x + 4, y + 4);
        pose.scale(0.60f, 0.60f);
        graphics.text(this.font, "CELESTIAL IMPACT EVENT", 0, 0, 0xFFFFCC66, false);
        pose.popMatrix();

        // 2. Event Intel & Survival Briefing Box (124 x 84)
        int boxY = y + 59, boxH = 84;
        graphics.fill(x, boxY, x + skyW, boxY + boxH, 0xFF6A2A10);
        graphics.fill(x + 1, boxY + 1, x + skyW - 1, boxY + boxH - 1, 0xEE140608);

        pose.pushMatrix();
        pose.translate(x + 5, boxY + 4);
        pose.scale(0.66f, 0.66f);
        graphics.text(this.font, "Event Intel & Hazards", 0, 0, 0xFFFFAA33, false);
        pose.popMatrix();

        String[] intel = {
                "• Timing: Night only (10–15 days)",
                "• Warning: 2m Alert & Countdown",
                "• Scope: 10-Chunk Player Bubble",
                "• Danger: 1 Heart (True Piercing!)",
                "• Yield: ~8–10 Spacetime Fragments",
                "• Bonus: Ultra-rare Template (<1%)",
        };
        int ly = boxY + 13;
        for (String line : intel) {
            pose.pushMatrix();
            pose.translate(x + 5, ly);
            pose.scale(0.58f, 0.58f);
            int col = line.contains("Danger") ? 0xFFFF6666 : 0xFFD8C4C0;
            graphics.text(this.font, line, 0, 0, col, false);
            pose.popMatrix();
            ly += 8;
        }

        int alertY = boxY + boxH - 17;
        graphics.fill(x + 4, alertY, x + skyW - 4, alertY + 13, 0x884A0A0A);
        pose.pushMatrix();
        pose.translate(x + 8, alertY + 3);
        pose.scale(0.60f, 0.60f);
        graphics.text(this.font, "⚠ Take cover under solid roofs!", 0, 0, 0xFFFF5555, false);
        pose.popMatrix();
    }

    private void drawSettings(GuiGraphicsExtractor graphics, int x, int y) {
        String[] files = {"settings_particles_on", "settings_particles_off", "settings_wings_on", "settings_wings_off"};
        String[] labels = {"Particles on", "Particles off", "Wings on", "Wings off"};
        int size = 52;
        int colW = 64;
        int rowH = 74;
        long ms = System.currentTimeMillis();
        for (int i = 0; i < 4; i++) {
            int cx = x + (i % 2) * colW;
            int cy = y + (i / 2) * rowH;
            if (!blitStrip(graphics, files[i], cx, cy, size, SET_FRAME, SET_FRAMES)) {
                drawProceduralSettingsBox(graphics, i, cx, cy, size, ms);
            }
            var pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(cx, cy + size + 3);
            pose.scale(0.8f, 0.8f);
            graphics.text(this.font, labels[i], 0, 0, 0xFFD2C0D4, false);
            pose.popMatrix();
        }
    }

    private void drawProceduralSettingsBox(GuiGraphicsExtractor g, int mode, int cx, int cy, int size, long ms) {
        g.fill(cx, cy, cx + size, cy + size, 0xFF6A2A78);
        g.fill(cx + 1, cy + 1, cx + size - 1, cy + size - 1, 0xFF100618);
        int mx = cx + size / 2;
        int my = cy + size / 2 + 2;

        if (mode == 2) {
            int pulse = (int) (25 * (0.5 + 0.5 * Math.sin(ms / 240.0)));
            int wingOuter = (0xFF << 24) | ((170 + pulse) << 16) | (50 << 8) | (210 + pulse);
            int wingInner = 0xFF5A1E7A;
            g.fill(mx - 20, my - 12, mx - 5, my + 8, wingOuter);
            g.fill(mx - 18, my - 10, mx - 6, my + 6, wingInner);
            g.fill(mx - 22, my - 8, mx - 18, my + 12, wingOuter);
            g.fill(mx + 5, my - 12, mx + 20, my + 8, wingOuter);
            g.fill(mx + 6, my - 10, mx + 18, my + 6, wingInner);
            g.fill(mx + 18, my - 8, mx + 22, my + 12, wingOuter);
        } else if (mode == 3) {
            g.fill(mx - 7, my - 6, mx + 7, my + 6, 0xFF3A1848);
        }

        g.fill(mx - 5, my - 16, mx + 5, my - 7, 0xFF3E1C4E);
        g.fill(mx - 4, my - 13, mx + 4, my - 10, 0xFFD84CFF);
        g.fill(mx - 9, my - 6, mx + 9, my + 6, 0xFF2C1438);
        g.fill(mx - 6, my - 5, mx + 6, my + 5, 0xFF4A2060);
        g.fill(mx - 2, my - 3, mx + 2, my + 2, 0xFFE070FF);
        g.fill(mx - 5, my + 6, mx - 1, my + 16, 0xFF2C1438);
        g.fill(mx + 1, my + 6, mx + 5, my + 16, 0xFF2C1438);

        if (mode == 0) {
            int[][] offsets = {
                {-16, -12}, {15, -14}, {-18, 2}, {16, 0},
                {-13, 12}, {14, 13}, {-9, -19}, {10, -18},
                {-19, -5}, {18, 7}
            };
            for (int i = 0; i < offsets.length; i++) {
                int dy = (int) Math.round(Math.sin(ms / 220.0 + i * 0.9) * 2.5);
                int px = mx + offsets[i][0];
                int py = my + offsets[i][1] + dy;
                int col = (i % 3 == 0) ? 0xFFFF90FF : (i % 3 == 1) ? 0xFFD84CFF : 0xFF9A44E0;
                g.fill(px, py, px + 2, py + 2, col);
            }
        }

        boolean on = (mode % 2 == 0);
        g.fill(cx + size - 15, cy + 3, cx + size - 3, cy + 9, on ? 0xFF285A38 : 0xFF482038);
        g.fill(cx + size - 14, cy + 4, cx + size - 4, cy + 8, on ? 0xFF66FF99 : 0xFF9A6888);
    }

    private void blitArt(GuiGraphicsExtractor graphics, String art, int x, int y, int size) {
        blitStrip(graphics, art, x, y, size, FRAME, FRAMES);
    }

    private record Sheet(Identifier id, int fw, int fh, int cols, int frames, int ms) {}
    private static java.util.Map<String, Sheet> sheets;
    private static java.util.Map<String, Sheet> sheets() {
        if (sheets != null) return sheets;
        java.util.Map<String, Sheet> m = new java.util.HashMap<>();
        var res = Minecraft.getInstance().getResourceManager().getResource(Identifier.fromNamespaceAndPath("infinium", "guide/strips.json"));
        if (res.isPresent()) {
            try (var reader = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                for (var e : root.entrySet()) {
                    JsonObject o = e.getValue().getAsJsonObject();
                    m.put(e.getKey(), new Sheet(Identifier.fromNamespaceAndPath("infinium", "textures/gui/guide/" + o.get("file").getAsString() + ".png"),
                            o.get("fw").getAsInt(), o.get("fh").getAsInt(), Math.max(1, o.get("cols").getAsInt()),
                            Math.max(1, o.get("frames").getAsInt()), Math.max(10, o.get("ms").getAsInt())));
                }
            } catch (Exception ignored) {}
        }
        return sheets = m;
    }

    private boolean blitSheet(GuiGraphicsExtractor graphics, String art, int x, int y, int w, int h) {
        Sheet sh = sheets().get(art);
        if (sh == null || Minecraft.getInstance().getResourceManager().getResource(sh.id()).isEmpty()) return false;
        int which = (int) ((System.currentTimeMillis() / sh.ms()) % sh.frames());
        int rows = (sh.frames() + sh.cols() - 1) / sh.cols();
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(w / (float) sh.fw(), h / (float) sh.fh());
        graphics.blit(RenderPipelines.GUI_TEXTURED, sh.id(), 0, 0, (which % sh.cols()) * sh.fw(), (which / sh.cols()) * sh.fh(),
                sh.fw(), sh.fh(), sh.cols() * sh.fw(), rows * sh.fh());
        pose.popMatrix();
        return true;
    }

    private boolean blitStrip(GuiGraphicsExtractor graphics, String art, int x, int y, int size, int frame, int frames) {
        Identifier id = Identifier.fromNamespaceAndPath("infinium", "textures/gui/guide/" + art + ".png");
        if (Minecraft.getInstance().getResourceManager().getResource(id).isEmpty()) return false;
        int which = (int) ((System.currentTimeMillis() / 120L) % frames);
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(size / (float) frame, size / (float) frame);
        graphics.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0, which * frame, frame, frame, frame, frame * frames);
        pose.popMatrix();
        return true;
    }

    private ItemStack drawRecipes(GuiGraphicsExtractor graphics, String recipe, int x, int y, int availH, int mouseX, int mouseY) {
        List<GuideStacks.Layout> layouts = GuideStacks.layouts(recipe);
        if (layouts.isEmpty()) return ItemStack.EMPTY;
        boolean stackAll = "tools".equals(recipe) || "dyes".equals(recipe);
        if (!stackAll) {
            int index = layouts.size() == 1 || !"templates".equals(recipe) ? 0
                    : (int) ((System.currentTimeMillis() / 2200L) % layouts.size());
            return drawStation(graphics, layouts.get(index), x, y, 124, availH, mouseX, mouseY);
        }
        int rows = layouts.size();
        int gap = 4;
        int rowH = Math.max(12, Math.min(22, (availH - gap * (rows - 1)) / rows));
        int groupH = rows * rowH + gap * (rows - 1);
        int y0 = y + Math.max(0, (availH - groupH) / 2);
        ItemStack hovered = ItemStack.EMPTY;
        for (int i = 0; i < rows; i++) {
            ItemStack hit = drawStation(graphics, layouts.get(i), x, y0 + i * (rowH + gap), 124, rowH, mouseX, mouseY);
            if (!hit.isEmpty()) hovered = hit;
        }
        return hovered;
    }

    private ItemStack drawStation(GuiGraphicsExtractor graphics, GuideStacks.Layout layout, int x, int y, int availW, int availH, int mouseX, int mouseY) {
        List<GuideStacks.Slot> slots = layout.slots();
        if (slots.isEmpty()) return ItemStack.EMPTY;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (GuideStacks.Slot sl : slots) {
            minX = Math.min(minX, sl.x() - 1); minY = Math.min(minY, sl.y() - 1);
            maxX = Math.max(maxX, sl.x() + 17); maxY = Math.max(maxY, sl.y() + 17);
        }
        int cw = maxX - minX, ch = maxY - minY;
        float gs = Math.min(1f, Math.min(availW / (float) cw, availH / (float) ch));
        if (gs <= 0.05f) return ItemStack.EMPTY;
        float ox = x + (availW - cw * gs) / 2f;
        float oy = y + (availH - ch * gs) / 2f;
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(ox, oy);
        pose.scale(gs, gs);
        pose.translate(-minX, -minY);
        GuideStacks.Slot result = slots.get(slots.size() - 1);
        graphics.fill(minX - 4, minY - 4, maxX + 4, maxY + 4, PANEL_BG);
        int inRight = Integer.MIN_VALUE;
        for (int i = 0; i < slots.size(); i++) {
            GuideStacks.Slot sl = slots.get(i);
            boolean isResult = i == slots.size() - 1;
            if (!isResult) inRight = Math.max(inRight, sl.x() + 17);
            int edge = isResult ? RESULT_EDGE : SLOT_EDGE;
            graphics.fill(sl.x() - 1, sl.y() - 1, sl.x() + 17, sl.y() + 17, edge);
            graphics.fill(sl.x(), sl.y(), sl.x() + 16, sl.y() + 16, SLOT_FILL);
        }
        int ax0 = inRight + 4, ax1 = result.x() - 5, ay = result.y() + 7;
        if (ax1 - ax0 >= 8) {
            graphics.fill(ax0, ay, ax1 - 4, ay + 2, ARROW);
            for (int k = 0; k < 4; k++) graphics.fill(ax1 - 4 + k, ay - 3 + k, ax1 - 3 + k, ay + 5 - k, ARROW);
        }
        float mx = (mouseX - ox) / gs + minX;
        float my = (mouseY - oy) / gs + minY;
        ItemStack hovered = drawSlots(graphics, slots, mx, my);
        pose.popMatrix();
        return hovered;
    }

    private ItemStack drawSlots(GuiGraphicsExtractor graphics, List<GuideStacks.Slot> slots, float mouseX, float mouseY) {
        ItemStack hovered = ItemStack.EMPTY;
        for (GuideStacks.Slot slot : slots) {
            if (!slot.stack().isEmpty()) graphics.item(slot.stack(), slot.x(), slot.y());
            if (mouseX >= slot.x() && mouseX < slot.x() + 16 && mouseY >= slot.y() && mouseY < slot.y() + 16) hovered = slot.stack();
        }
        return hovered;
    }

    /** Draws body text with support for inline {C} Spacetime Crystal icons (#11). */
    private void drawBody(GuiGraphicsExtractor graphics, String body, int x, int y, int width) {
        String processed = body.replace("{C}", CRYSTAL_TOKEN);
        String[] paras = processed.split("\n", -1);
        int avail = BOOK_H - 34 - y;
        float textScale = 0.75f;
        while (textScale > 0.42f && bodyHeight(paras, width, textScale) > avail) textScale -= 0.03f;
        int view = (int) (avail / textScale);
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(textScale, textScale);
        int wrap = (int) (width / textScale);
        int lineY = 0;
        for (String para : paras) {
            if (para.isEmpty()) {
                lineY += (this.font.lineHeight + 1) / 2;
                continue;
            }
            for (String line : splitLines(para, wrap)) {
                if (lineY >= 0 && lineY + this.font.lineHeight <= view + 2) {
                    drawLineWithCrystalIcons(graphics, line, 0, lineY, 0xFFD2C0D4);
                }
                lineY += this.font.lineHeight + 1;
            }
        }
        pose.popMatrix();
    }

    private List<String> splitLines(String para, int wrapWidth) {
        String measured = para.replace(CRYSTAL_TOKEN, "  ");
        List<String> out = new ArrayList<>();
        this.font.getSplitter().splitLines(measured, wrapWidth, Style.EMPTY, false, (style, start, end) -> {
            out.add(restoreTokens(para, measured, start, end));
        });
        if (out.isEmpty()) out.add(para);
        return out;
    }

    private static String restoreTokens(String originalWithTokens, String measuredWithSpaces, int start, int end) {
        // Walk both strings in lockstep: CRYSTAL_TOKEN (len 1) in original corresponds to "  " (len 2) in measured
        StringBuilder sb = new StringBuilder();
        int oi = 0, mi = 0;
        while (oi < originalWithTokens.length() && mi < end) {
            char oc = originalWithTokens.charAt(oi);
            int mLen = (oc == CRYSTAL_TOKEN.charAt(0)) ? 2 : 1;
            if (mi + mLen > start) {
                sb.append(oc);
            }
            mi += mLen;
            oi++;
        }
        return sb.toString();
    }

    private void drawLineWithCrystalIcons(GuiGraphicsExtractor g, String line, int x, int y, int color) {
        int idx = line.indexOf(CRYSTAL_TOKEN);
        if (idx < 0) {
            g.text(this.font, line, x, y, color, false);
            return;
        }
        int curX = x;
        int pos = 0;
        int frame = (int) ((System.currentTimeMillis() / 100L) % CRYSTAL_FRAMES);
        while (pos < line.length()) {
            int next = line.indexOf(CRYSTAL_TOKEN, pos);
            if (next < 0) {
                String rem = line.substring(pos);
                g.text(this.font, rem, curX, y, color, false);
                break;
            }
            if (next > pos) {
                String seg = line.substring(pos, next);
                g.text(this.font, seg, curX, y, color, false);
                curX += this.font.width(seg);
            }
            // Blit mini animated Spacetime Crystal icon (8x8) inline
            var pose = g.pose();
            pose.pushMatrix();
            pose.translate(curX + 0.5f, y - 0.5f);
            pose.scale(0.55f, 0.55f);
            g.blit(RenderPipelines.GUI_TEXTURED, CRYSTAL_TEX, 0, 0, 0, frame * 16, 16, 16, 16, 16 * CRYSTAL_FRAMES);
            pose.popMatrix();
            curX += 10;
            pos = next + CRYSTAL_TOKEN.length();
        }
    }

    /**
     * Welcome spread (#3): Splits the text 50/50 equally between the left and right pages
     * and centers both bodies of text vertically on their respective pages.
     */
    private void drawWelcome(GuiGraphicsExtractor graphics, String body) {
        final float textScale = 0.70f;
        final int width = 124, leftX = 16, rightX = 154;
        final int topMargin = 26, bottomMargin = 32;
        int availH = BOOK_H - topMargin - bottomMargin;
        int lh = this.font.lineHeight + 1;
        int wrap = (int) (width / textScale);

        List<FormattedCharSequence> lines = new ArrayList<>();
        for (String para : body.split("\n", -1)) {
            if (!para.isEmpty()) lines.addAll(this.font.split(FormattedText.of(para), wrap));
        }

        int half = (lines.size() + 1) / 2;
        List<FormattedCharSequence> leftLines = lines.subList(0, half);
        List<FormattedCharSequence> rightLines = lines.subList(half, lines.size());

        float leftBlockH = (leftLines.size() * lh - 1) * textScale;
        float rightBlockH = (rightLines.size() * lh - 1) * textScale;
        float leftStartY = topMargin + Math.max(0f, (availH - leftBlockH) / 2f);
        float rightStartY = topMargin + Math.max(0f, (availH - rightBlockH) / 2f);

        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(leftX, leftStartY);
        pose.scale(textScale, textScale);
        for (int i = 0; i < leftLines.size(); i++) {
            graphics.text(this.font, leftLines.get(i), 0, i * lh, 0xFFD2C0D4, false);
        }
        pose.popMatrix();

        pose.pushMatrix();
        pose.translate(rightX, rightStartY);
        pose.scale(textScale, textScale);
        for (int i = 0; i < rightLines.size(); i++) {
            graphics.text(this.font, rightLines.get(i), 0, i * lh, 0xFFD2C0D4, false);
        }
        pose.popMatrix();
    }

    private static final String[] ADD_TABS = {
        "1. Movement",
        "2. Vision & HUD",
        "3. Combat Arts",
        "4. World FX"
    };

    private int getAdditionalInfoIndex(int subIdx) {
        int count = 0;
        for (int i = 0; i < spreads.size(); i++) {
            if ("additional_info".equals(spreads.get(i).art)) {
                if (count == subIdx) return i;
                count++;
            }
        }
        return -1;
    }

    private int getCurrentAdditionalInfoSubIndex(Spread sp) {
        int count = 0;
        for (Spread s : spreads) {
            if ("additional_info".equals(s.art)) {
                if (s == sp) return count;
                count++;
            }
        }
        return 0;
    }

    private int hitAdditionalInfoTab(int localMx, int localMy) {
        if (localMy < 8 || localMy > 23) return -1;
        if (localMx >= 16 && localMx < 74) return 0;
        if (localMx >= 78 && localMx < 142) return 1;
        if (localMx >= 150 && localMx < 210) return 2;
        if (localMx >= 214 && localMx < 276) return 3;
        return -1;
    }

    private void drawAdditionalInfo(GuiGraphicsExtractor graphics, Spread spread, int localMx, int localMy) {
        int activeSub = getCurrentAdditionalInfoSubIndex(spread);

        int[] tabX = {16, 78, 150, 214};
        int[] tabW = {58, 64, 60, 62};
        int ty = 9;
        int th = 13;

        for (int i = 0; i < 4; i++) {
            int tx = tabX[i];
            int tw = tabW[i];
            boolean active = i == activeSub;
            boolean hovered = localMx >= tx && localMx < tx + tw && localMy >= ty && localMy < ty + th;

            int border = active ? 0xFFFFD700 : (hovered ? 0xFFD864FF : 0xFF5A246E);
            int bg = active ? 0xFF3A1848 : (hovered ? 0xFF2A1038 : 0xFF160820);
            int textCol = active ? 0xFFFFFFFF : (hovered ? 0xFFFFFFFF : 0xFFB898C8);

            graphics.fill(tx, ty, tx + tw, ty + th, border);
            graphics.fill(tx + 1, ty + 1, tx + tw - 1, ty + th - 1, bg);

            var pose = graphics.pose();
            pose.pushMatrix();
            float scale = 0.68f;
            int strW = (int) (this.font.width(ADD_TABS[i]) * scale);
            int textX = tx + (tw - strW) / 2;
            int textY = ty + 2;
            pose.translate(textX, textY);
            pose.scale(scale, scale);
            graphics.text(this.font, ADD_TABS[i], 0, 0, textCol, false);
            pose.popMatrix();

            if (active) {
                graphics.fill(tx + 1, ty + th - 2, tx + tw - 1, ty + th - 1, 0xFFFFD700);
            }
        }

        graphics.fill(16, 24, 142, 25, 0xFF5A246E);
        graphics.fill(150, 24, 276, 25, 0xFF5A246E);

        drawTwoColumnFlowOffset(graphics, spread.body, 28);
    }

    private void drawTwoColumnFlowOffset(GuiGraphicsExtractor graphics, String body, int startY) {
        String[] paras = body.split("\n\n", -1);
        int split = (paras.length + 1) / 2;
        StringBuilder leftSb = new StringBuilder();
        StringBuilder rightSb = new StringBuilder();
        for (int i = 0; i < paras.length; i++) {
            if (i < split) {
                if (leftSb.length() > 0) leftSb.append("\n\n");
                leftSb.append(paras[i]);
            } else {
                if (rightSb.length() > 0) rightSb.append("\n\n");
                rightSb.append(paras[i]);
            }
        }
        drawBody(graphics, leftSb.toString(), 16, startY, 124);
        drawBody(graphics, rightSb.toString(), 154, startY, 124);
    }

    /** Two-column flow across both left and right pages for Good to Know I & II (#9, #10, #23). */
    private void drawTwoColumnFlow(GuiGraphicsExtractor graphics, String body) {
        String[] paras = body.split("\n\n", -1);
        int split = (paras.length + 1) / 2;
        StringBuilder leftSb = new StringBuilder();
        StringBuilder rightSb = new StringBuilder();
        for (int i = 0; i < paras.length; i++) {
            if (i < split) {
                if (leftSb.length() > 0) leftSb.append("\n\n");
                leftSb.append(paras[i]);
            } else {
                if (rightSb.length() > 0) rightSb.append("\n\n");
                rightSb.append(paras[i]);
            }
        }
        drawBody(graphics, leftSb.toString(), 16, 28, 124);
        drawBody(graphics, rightSb.toString(), 154, 16, 124);
    }

    private boolean isWelcome() {
        if (spreads.isEmpty()) return false;
        String t = spreads.get(Math.min(page, spreads.size() - 1)).title;
        return "Intro".equals(t) || "Welcome".equals(t);
    }

    private int bodyHeight(String[] paras, int width, float textScale) {
        int wrap = Math.max(20, (int) (width / textScale));
        int lineY = 0;
        for (String para : paras) {
            if (para.isEmpty()) {
                lineY += (this.font.lineHeight + 1) / 2;
                continue;
            }
            String measured = para.replace(CRYSTAL_TOKEN, "  ");
            lineY += this.font.split(FormattedText.of(measured), wrap).size() * (this.font.lineHeight + 1);
        }
        return (int) (lineY * textScale);
    }

    private static int titleColor(String art) {
        return switch (art) {
            case "astral" -> 0xFFC45AD4;
            case "cybernetic" -> 0xFFD24A8A;
            case "singularity" -> 0xFFA14AD0;
            case "cyberpsycho" -> 0xFFE0235A;
            case "infinity" -> 0xFFC43A7A;
            case "matrix" -> 0xFFC45AD4;
            case "meteor" -> 0xFFFFAA44;
            case "additional_info" -> 0xFFFFD700;
            default -> 0xFFE070C0;
        };
    }

    private static int hash(long a, int b) {
        long h = a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        return (int) (h ^ (h >>> 32));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == 263 || key == 65) { turn(-1); return true; }
        if (key == 262 || key == 68) { turn(1); return true; }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (closing) return;
        closing = true;
        Minecraft.getInstance().gui.setScreen(back);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Spread(String title, String body, String art, String recipe, String note) {
        Spread(String title, String body, String art, String recipe) { this(title, body, art, recipe, ""); }
    }

    private static final class GuideButton extends AbstractButton {
        @Override protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput out) { defaultButtonNarrationText(out); }
        private final java.util.function.Consumer<InputWithModifiers> press;

        GuideButton(int x, int y, int w, int h, Component message, java.util.function.Consumer<InputWithModifiers> press) {
            super(x, y, w, h, message);
            this.press = press;
        }

        @Override
        public void onPress(InputWithModifiers input) {
            press.accept(input);
        }

        @Override
        public void playDownSound(net.minecraft.client.sounds.SoundManager soundManager) {
            float cVol = 0.8F * dev.justin.infinium.client.InfiniumClientConfig.soundVolumeMaster() * dev.justin.infinium.client.InfiniumClientConfig.soundVolumeCrystal();
            soundManager.play(SimpleSoundInstance.forUI(dev.justin.infinium.ModSounds.crystal(0, dev.justin.infinium.ModSounds.INSERT).value(), 1.0F, cVol));
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            int bg = isHoveredOrFocused() ? 0xFF6A2A78 : 0xFF3A1830;
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);
            var font = Minecraft.getInstance().font;
            int tx = getX() + (getWidth() - font.width(getMessage())) / 2;
            graphics.text(font, getMessage(), tx, getY() + 3, 0xFFE8C8DE, false);
        }
    }
}
