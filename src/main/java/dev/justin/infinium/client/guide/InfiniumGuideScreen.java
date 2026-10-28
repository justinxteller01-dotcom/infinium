package dev.justin.infinium.client.guide;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.justin.infinium.Infinium;
import dev.justin.infinium.client.UiInfection;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * Guidebook v2 (GUIDEBOOK_OVERHAUL_PLAN). Pages come from {@code assets/infinium/guide/pages.json} (gen_guide_v2.py):
 * id, chapter, title, subtitle, keywords, body, visual, caption, numbers. No card system.
 * Left page: centered title, subtitle, short centered paragraphs. Right page: one centered visual
 * (recipe / live armor render / picture), a caption, then the "Numbers" paragraph. Text never shrinks: anything
 * that does not fit continues on a "(cont.)" spread (pagination in {@link #paginate()}).
 * Numbers are {n:key} tokens resolved by {@link GuideNumbers}. Layout is in fixed book units (BOOK_W x BOOK_H) and the
 * whole book is scaled to the screen, so it is identical at every GUI scale; nav buttons are drawn in book space.
 * Navigation: clickable contents + keyword search, chapter tabs on the right edge, Back / Next, Return (history),
 * last page remembered across sessions (config/infinium_guide_last.txt).
 */
public class InfiniumGuideScreen extends Screen {
    private static final int BOOK_W = 292;
    private static final int BOOK_H = 196;
    private static final int TAB_W = 18;
    static final int FRAMES = 16;
    static final int FRAME = 128;
    private static final int SET_FRAMES = 8;
    private static final int SET_FRAME = 64;
    private static final Identifier CRYSTAL_TEX = Identifier.fromNamespaceAndPath("infinium", "textures/item/spacetime_crystal.png");
    private static final int CRYSTAL_FRAMES = 80;
    private static final float MAX_SCALE = 1.55f;
    private static final float FILL = 0.90f;
    private static final int SLOT_EDGE = 0xFF6A4A78, SLOT_FILL = 0xFF3A3346, PANEL_BG = 0xCC2A2433, RESULT_EDGE = 0xFFC45AD4, ARROW = 0xFF8A5AA0;
    private static final String CRYSTAL_TOKEN = "\uE000";

    // page geometry (book units)
    private static final int LX = 14, RX = 152, PW = 126, TOP = 12, BOTTOM = 166, NAV_Y = 172, NAV_H = 13;
    private static final float TS = 0.70f;               // body text scale (never changes)
    private static final float SUB_S = 0.62f;
    private static final int LINE = 7;                   // (font 9 + 1) * 0.7
    private static final int GAP = 3;                    // paragraph gap
    private static final int ROW = 6;                    // contents row height
    private static final int TEXT = 0xFFD2C0D4, SUB = 0xFFB898C8, PINK = 0xFFFF66CC, HOT = 0xFFFF00AA, PURPLE = 0xFFC45AD4,
            EDGE = 0xFF6A2A78, NUM = 0xFFE8C8E8;

    private final Screen back;
    private final List<Page> pages = new ArrayList<>();
    private final List<String> chapters = new ArrayList<>();
    private final List<View> views = new ArrayList<>();
    private final Deque<Integer> history = new ArrayDeque<>();
    private int view;
    private boolean closing;
    private boolean dragging;
    private EditBox search;
    private String query = "";
    private static String lastPageId;
    private static int lastPageCont;

    public InfiniumGuideScreen(Screen back) {
        super(Component.translatable("item.infinium.infinium_guide"));
        this.back = back;
    }

    // ------------------------------------------------------------------ data

    private record Page(String id, int chapter, String title, String subtitle, List<String> keywords, String body,
                        String visual, String caption, String numbers) {}

    /** One rendered spread: page index, continuation number, line ranges for each side. */
    private record View(int page, int cont, int lFrom, int lTo, int rFrom, int rTo, boolean visual) {}

    /** Wrapped line; "" = paragraph gap. */
    private record Ln(String text, int color) {}

    private void load() {
        if (!pages.isEmpty()) return;
        var res = Minecraft.getInstance().getResourceManager().getResource(Identifier.fromNamespaceAndPath("infinium", "guide/pages.json"));
        try {
            if (res.isEmpty()) throw new IllegalStateException();
            try (var reader = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                for (var c : root.getAsJsonArray("chapters")) chapters.add(c.getAsString());
                for (var e : root.getAsJsonArray("pages")) {
                    JsonObject o = e.getAsJsonObject();
                    List<String> kw = new ArrayList<>();
                    if (o.has("keywords")) for (var k : o.getAsJsonArray("keywords")) kw.add(k.getAsString().toLowerCase(Locale.ROOT));
                    pages.add(new Page(str(o, "id"), o.has("chapter") ? o.get("chapter").getAsInt() : 0, str(o, "title"), str(o, "subtitle"),
                            kw, str(o, "body"), str(o, "visual"), str(o, "caption"), str(o, "numbers")));
                }
            }
        } catch (Exception ex) {
            pages.clear();
            pages.add(new Page("error", 0, "Informational Crystal", "", List.of(), "Could not read the guide pages.", "none", "", ""));
        }
        if (chapters.isEmpty()) chapters.add("Start");
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    // ------------------------------------------------------------------ layout

    private float bookScale() {
        float sx = (this.width * FILL) / (float) (BOOK_W + TAB_W);
        float sy = (this.height * FILL) / (float) BOOK_H;
        return Math.min(MAX_SCALE, Math.min(sx, sy));
    }

    private int bookLeft() { return (int) ((this.width - (BOOK_W + TAB_W) * bookScale()) / 2f); }
    private int bookTop() { return (int) ((this.height - BOOK_H * bookScale()) / 2f); }

    /** Search box band height in book units (EditBox needs >= 12 screen px). */
    private int searchH() { return Math.max(10, (int) Math.ceil(12 / bookScale())); }
    private int contentsTopLeft() { return TOP + 22 + searchH() + 4; }

    @Override
    protected void init() {
        super.init();
        sheets = null;
        clearWidgets();
        load();
        if (views.isEmpty() && lastPageId == null) readLast();
        String keepId = lastPageId;
        int keepCont = lastPageCont;
        if (!views.isEmpty()) { View v = cur(); keepId = pages.get(v.page()).id(); keepCont = v.cont(); }
        paginate();
        view = 0;
        if (keepId != null) {
            for (int i = 0; i < views.size(); i++) {
                View v = views.get(i);
                if (pages.get(v.page()).id().equals(keepId) && v.cont() <= keepCont) view = i;
            }
        }
        float s = bookScale();
        int bx = bookLeft() + (int) ((LX + 8) * s), by = bookTop() + (int) ((TOP + 22) * s);
        search = new EditBox(this.font, bx, by, (int) ((PW - 16) * s), (int) (searchH() * s), Component.literal("Search"));
        search.setHint(Component.literal("Search...").withColor(0xFF8A6A98));
        search.setTextColor(0xFFF0D8F0);
        search.setValue(query);
        search.setResponder(q -> query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT));
        addRenderableWidget(search);
        syncSearch();
    }

    private void syncSearch() {
        if (search == null) return;
        boolean on = !views.isEmpty() && isContents(cur()) && cur().cont() == 0;
        search.visible = on;
        search.active = on;
        if (!on && search.isFocused()) { search.setFocused(false); setFocused(null); }
    }

    private View cur() { return views.get(Math.max(0, Math.min(view, views.size() - 1))); }
    private boolean isContents(View v) { return "contents".equals(pages.get(v.page()).visual()); }
    private boolean isIntro(View v) { return "intro".equals(pages.get(v.page()).visual()); }

    private List<Ln> wrap(String text, float scale, int color, int width) {
        List<Ln> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        String t = GuideNumbers.resolve(text).replace("{C}", CRYSTAL_TOKEN);
        int wrapW = (int) (width / scale);
        boolean first = true;
        for (String para : t.split("\n")) {
            if (para.isBlank()) continue;
            if (!first) out.add(new Ln("", 0));
            first = false;
            for (String l : splitLines(para, wrapW)) out.add(new Ln(l, color));
        }
        return out;
    }

    private static int h(Ln l) { return l.text().isEmpty() ? GAP : LINE; }

    private List<Ln> leftLines(Page p) { return wrap(p.body(), TS, TEXT, PW - 4); }

    private List<Ln> rightLines(Page p) {
        List<Ln> r = new ArrayList<>();
        if (!p.numbers().isEmpty()) r.addAll(wrap(p.numbers(), TS, NUM, PW - 4));
        return r;
    }

    private int visualH(Page p) {
        String v = p.visual();
        if (v.startsWith("armor:")) return v.equals("armor:5") ? 104 : 112;
        if (v.equals("recipe:tools") || v.equals("recipe:dyes")) return 104;
        if (v.startsWith("recipe:")) return 66;
        if (v.startsWith("items:")) return 48;
        return switch (v) {
            case "matrix" -> 108;
            case "meteor" -> 50;
            case "settings" -> 104;
            case "flow" -> 108;
            default -> 0;
        };
    }

    /** y where right-page text starts on a spread that shows the visual. */
    private int rightTextTop(Page p) {
        int vh = visualH(p);
        if (vh == 0) return TOP + 2;
        return TOP + vh + 4 + (p.caption().isEmpty() ? 0 : 9) + 3;
    }

    private static int fit(List<Ln> lines, int from, int avail) {
        int used = 0, i = from;
        while (i < lines.size()) {
            int hh = h(lines.get(i));
            if (used + hh > avail) break;
            used += hh;
            i++;
        }
        while (i > from && i < lines.size() && lines.get(i - 1).text().isEmpty()) i--;   // no gap at the bottom of a page
        if (i == from && from < lines.size()) i = from + 1;   // always progress
        return i;
    }

    private void paginate() {
        views.clear();
        for (int pi = 0; pi < pages.size(); pi++) {
            Page p = pages.get(pi);
            if ("contents".equals(p.visual())) {
                int rows = contentsRows("").size();
                int cap = (BOTTOM - contentsTopLeft()) / ROW + (BOTTOM - TOP) / ROW;
                int n = Math.max(1, (rows + cap - 1) / cap);
                for (int c = 0; c < n; c++) views.add(new View(pi, c, 0, 0, 0, 0, false));
                continue;
            }
            if ("intro".equals(p.visual())) { views.add(new View(pi, 0, 0, 0, 0, 0, false)); continue; }
            List<Ln> l = leftLines(p), r = rightLines(p);
            int li = 0, ri = 0, cont = 0;
            boolean hasVisual = visualH(p) > 0 || !"none".equals(p.visual()) && !p.visual().isEmpty();
            do {
                int lTop = cont == 0 ? TOP + 12 + (p.subtitle().isEmpty() ? 0 : 9) + 6 : TOP + 16;
                int lEnd = l.isEmpty() ? 0 : fit(l, li, BOTTOM - lTop);
                if (li >= l.size()) lEnd = li;
                int rTop = cont == 0 && hasVisual ? rightTextTop(p) : TOP + 4;
                int rEnd = ri >= r.size() ? ri : fit(r, ri, BOTTOM - rTop);
                if (cont == 0 && hasVisual && BOTTOM - rTop < LINE) rEnd = ri;   // visual fills the page
                views.add(new View(pi, cont, li, lEnd, ri, rEnd, cont == 0 && hasVisual));
                li = lEnd; ri = rEnd; cont++;
            } while ((li < l.size() || ri < r.size()) && cont < 20);
        }
        if (views.isEmpty()) views.add(new View(0, 0, 0, 0, 0, 0, false));
    }

    /** Contents rows: {chapter header (-1 - chapter)} or {page index}. Filtered by query. */
    private List<Integer> contentsRows(String q) {
        List<Integer> rows = new ArrayList<>();
        int lastCh = -1;
        for (int i = 0; i < pages.size(); i++) {
            Page p = pages.get(i);
            if ("contents".equals(p.visual())) continue;
            if (!q.isEmpty() && !matches(p, q)) continue;
            if (p.chapter() != lastCh) { rows.add(-1 - p.chapter()); lastCh = p.chapter(); }
            rows.add(i);
        }
        return rows;
    }

    private boolean matches(Page p, String q) {
        if (p.title().toLowerCase(Locale.ROOT).contains(q)) return true;
        if (p.chapter() < chapters.size() && chapters.get(p.chapter()).toLowerCase(Locale.ROOT).contains(q)) return true;
        for (String k : p.keywords()) if (k.contains(q)) return true;
        return false;
    }

    private int firstView(int pageIdx) {
        for (int i = 0; i < views.size(); i++) if (views.get(i).page() == pageIdx) return i;
        return 0;
    }

    private int chapterOf(View v) { return pages.get(v.page()).chapter(); }

    // ------------------------------------------------------------------ navigation

    private void go(int target, boolean remember) {
        target = Math.max(0, Math.min(views.size() - 1, target));
        if (target == view) return;
        if (remember) { history.push(view); while (history.size() > 32) history.removeLast(); }
        view = target;
        dragging = false;
        saveLast();
        syncSearch();
    }

    private void turn(int d) { go(view + d, false); }

    private void returnBack() {
        if (!history.isEmpty()) { int t = history.pop(); view = Math.max(0, Math.min(views.size() - 1, t)); saveLast(); syncSearch(); }
    }

    private void click() {
        float vol = 0.8F * dev.justin.infinium.client.InfiniumClientConfig.soundVolumeMaster() * dev.justin.infinium.client.InfiniumClientConfig.soundVolumeCrystal();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(dev.justin.infinium.ModSounds.crystal(0, dev.justin.infinium.ModSounds.INSERT).value(), 1.0F, vol));
    }

    private static java.nio.file.Path lastFile() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("infinium_guide_last.txt");
    }

    private void saveLast() {
        if (views.isEmpty()) return;
        View v = cur();
        lastPageId = pages.get(v.page()).id();
        lastPageCont = v.cont();
        try { java.nio.file.Files.writeString(lastFile(), lastPageId + "\n" + lastPageCont + "\n"); } catch (Exception ignored) {}
    }

    private static void readLast() {
        try {
            var f = lastFile();
            if (!java.nio.file.Files.exists(f)) return;
            List<String> ls = java.nio.file.Files.readAllLines(f);
            if (!ls.isEmpty()) lastPageId = ls.get(0).trim();
            lastPageCont = ls.size() > 1 ? Integer.parseInt(ls.get(1).trim()) : 0;
        } catch (Exception ignored) {}
    }

    // nav buttons in book units: {x, w}
    private static final int[] B_BACK = {LX, 38}, B_CONTENTS = {LX + 42, 46}, B_RETURN = {LX + 92, 34}, B_NEXT = {BOOK_W - 14 - 38, 38};

    private boolean inRect(int mx, int my, int x, int y, int w, int hh) { return mx >= x && mx < x + w && my >= y && my < y + hh; }

    private int tabY(int i) { return 8 + i * 25; }

    // armor render box (book units) for the current view, or null
    private int[] armorBox() {
        View v = cur();
        Page p = pages.get(v.page());
        if (!v.visual() || !p.visual().startsWith("armor:")) return null;
        int vh = visualH(p);
        boolean toggle = !p.visual().equals("armor:5");
        return new int[]{RX + 10, TOP, PW - 20, vh - (toggle ? 12 : 0), toggle ? 1 : 0};
    }

    private int[] wingsBtn() {
        int[] b = armorBox();
        if (b == null || b[4] == 0) return null;
        return new int[]{RX + PW / 2 - 24, TOP + b[3] + 2, 48, 9};
    }

    // ------------------------------------------------------------------ render

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        float s = bookScale();
        int left = bookLeft(), top = bookTop();
        int mx = (int) Math.floor((mouseX - left) / s), my = (int) Math.floor((mouseY - top) / s);
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(left, top);
        pose.scale(s, s);

        g.fill(0, 0, BOOK_W, BOOK_H, EDGE);
        g.fill(1, 1, BOOK_W - 1, BOOK_H - 1, 0xFF000000);
        if (dev.justin.infinium.client.InfiniumClientConfig.uiEffects()) UiInfection.guideBackdrop(g, 2, 2, BOOK_W - 4, BOOK_H - 4);
        g.fill(145, 8, 147, BOTTOM + 2, EDGE);

        View v = cur();
        Page p = pages.get(v.page());
        ItemStack hovered = ItemStack.EMPTY;
        int titleCol = titleColor(p);

        if (isContents(v)) {
            title(g, v.cont() == 0 ? p.title() : p.title() + " (cont.)", LX, titleCol);
            if (v.cont() == 0) sub(g, query.isEmpty() ? p.subtitle() : "Results for \"" + query + "\"", LX, TOP + 12);
            drawContents(g, v, mx, my);
        } else if (isIntro(v)) {
            title(g, p.title(), LX, titleCol);
            drawWelcome(g, p.body());
        } else {
            title(g, v.cont() == 0 ? p.title() : p.title() + " (cont.)", LX, titleCol);
            int lTop;
            if (v.cont() == 0) {
                if (!p.subtitle().isEmpty()) sub(g, p.subtitle(), LX, TOP + 12);
                lTop = TOP + 12 + (p.subtitle().isEmpty() ? 0 : 9) + 6;
            } else lTop = TOP + 16;
            List<Ln> l = leftLines(p);
            drawLines(g, l, v.lFrom(), v.lTo(), LX, lTop, BOTTOM, true);
            if (v.visual()) hovered = drawVisual(g, p, mx, my, mouseX, mouseY, left, top, s);
            List<Ln> r = rightLines(p);
            if (v.rTo() > v.rFrom()) {
                int rTop = v.visual() ? rightTextTop(p) : TOP + 4;
                drawLines(g, r, v.rFrom(), v.rTo(), RX, rTop, BOTTOM, v.visual() || v.lTo() > v.lFrom());
            } else if (!v.visual() && v.cont() > 0 && v.lTo() > v.lFrom()) {
                // continuation spread with nothing left for the right page: keep it calm
                centerText(g, "~", RX, (TOP + BOTTOM) / 2, 1f, 0xFF5A246E);
            }
        }

        drawNav(g, mx, my);
        drawTabs(g, mx, my, chapterOf(v));
        pose.popMatrix();

        drawEdgeParticles(g, left, top, BOOK_W * s, BOOK_H * s);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        if (!hovered.isEmpty()) {
            List<ClientTooltipComponent> lines = Screen.getTooltipFromItem(Minecraft.getInstance(), hovered).stream()
                    .map(Component::getVisualOrderText).map(ClientTooltipComponent::create).toList();
            g.tooltip(this.font, lines, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null);
        }
    }

    private int titleColor(Page p) {
        return switch (p.visual()) {
            case "armor:0" -> 0xFFC45AD4;
            case "armor:1" -> 0xFFD24A8A;
            case "armor:2" -> 0xFFA14AD0;
            case "armor:3" -> 0xFFE0235A;
            case "armor:4" -> 0xFFC43A7A;
            default -> 0xFFE070C0;
        };
    }

    private void title(GuiGraphicsExtractor g, String t, int x, int col) {
        // never shrink below 0.8: long titles are short by design; clamp only if a title would overflow the page
        float sc = Math.min(1f, (PW - 2) / (float) Math.max(1, this.font.width(t)));
        sc = Math.max(0.8f, sc);
        centerText(g, t, x, TOP, sc, col);
    }

    private void sub(GuiGraphicsExtractor g, String t, int x, int y) { centerText(g, t, x, y, SUB_S, SUB); }

    private void centerText(GuiGraphicsExtractor g, String t, int pageX, int y, float sc, int col) {
        var pose = g.pose();
        float w = this.font.width(t.replace(CRYSTAL_TOKEN, "  ")) * sc;
        pose.pushMatrix();
        pose.translate(pageX + (PW - w) / 2f, y);
        pose.scale(sc, sc);
        drawLineWithCrystalIcons(g, t, 0, 0, col);
        pose.popMatrix();
    }

    /** Draws lines [from, to) centred horizontally; block vertically centred in [y0, y1] when center is true. */
    private void drawLines(GuiGraphicsExtractor g, List<Ln> lines, int from, int to, int pageX, int y0, int y1, boolean center) {
        int total = 0;
        for (int i = from; i < to; i++) total += h(lines.get(i));
        int y = center ? y0 + Math.max(0, (y1 - y0 - total) / 2) : y0;
        for (int i = from; i < to; i++) {
            Ln l = lines.get(i);
            if (!l.text().isEmpty()) centerText(g, l.text(), pageX, y, TS, l.color());
            y += h(l);
        }
    }

    private void drawNav(GuiGraphicsExtractor g, int mx, int my) {
        navButton(g, B_BACK, "< Back", view > 0, mx, my);
        navButton(g, B_CONTENTS, "Contents", true, mx, my);
        if (!history.isEmpty()) navButton(g, B_RETURN, "Return", true, mx, my);
        navButton(g, B_NEXT, "Next >", view < views.size() - 1, mx, my);
        String count = (view + 1) + " / " + views.size();
        centerText(g, count, RX, NAV_Y + 3, 0.75f, 0xFFD24A8A);
    }

    private void navButton(GuiGraphicsExtractor g, int[] b, String label, boolean enabled, int mx, int my) {
        boolean hover = enabled && inRect(mx, my, b[0], NAV_Y, b[1], NAV_H);
        g.fill(b[0], NAV_Y, b[0] + b[1], NAV_Y + NAV_H, hover ? 0xFFD864FF : 0xFF5A246E);
        g.fill(b[0] + 1, NAV_Y + 1, b[0] + b[1] - 1, NAV_Y + NAV_H - 1, hover ? 0xFF4A1A5A : 0xFF1A0A22);
        var pose = g.pose();
        float sc = 0.75f;
        float w = this.font.width(label) * sc;
        pose.pushMatrix();
        pose.translate(b[0] + (b[1] - w) / 2f, NAV_Y + 3.5f);
        pose.scale(sc, sc);
        g.text(this.font, label, 0, 0, enabled ? (hover ? 0xFFFFFFFF : 0xFFE8C8DE) : 0xFF6A4A78, false);
        pose.popMatrix();
    }

    private void drawTabs(GuiGraphicsExtractor g, int mx, int my, int active) {
        var pose = g.pose();
        for (int i = 0; i < chapters.size(); i++) {
            int y = tabY(i);
            boolean on = i == active;
            boolean hover = inRect(mx, my, BOOK_W, y, TAB_W, 23);
            int x0 = BOOK_W - 1, x1 = BOOK_W + (on ? TAB_W : TAB_W - 3);
            g.fill(x0, y, x1, y + 23, on ? HOT : hover ? 0xFFD864FF : 0xFF5A246E);
            g.fill(x0, y + 1, x1 - 1, y + 22, on ? 0xFF3A1848 : hover ? 0xFF2A1038 : 0xFF12061A);
            String lab = chapters.get(i);
            if (lab.length() > 5) lab = lab.substring(0, 4) + ".";
            float sc = 0.5f;
            float w = this.font.width(lab) * sc;
            pose.pushMatrix();
            pose.translate(x0 + ((x1 - x0) - w) / 2f, y + 9);
            pose.scale(sc, sc);
            g.text(this.font, lab, 0, 0, on ? 0xFFFFFFFF : 0xFFB898C8, false);
            pose.popMatrix();
        }
    }

    private void drawContents(GuiGraphicsExtractor g, View v, int mx, int my) {
        List<Integer> rows = contentsRows(query);
        int capL = (BOTTOM - contentsTopLeft()) / ROW, capR = (BOTTOM - TOP) / ROW;
        int per = capL + capR;
        int start = v.cont() * per;
        if (rows.isEmpty() && v.cont() == 0) {
            centerText(g, "No pages match.", LX, contentsTopLeft() + 6, TS, SUB);
            return;
        }
        if (start >= rows.size()) {
            centerText(g, "End of results.", LX, contentsTopLeft() + 6, TS, SUB);
            return;
        }
        for (int k = start; k < Math.min(rows.size(), start + per); k++) {
            int rel = k - start;
            boolean right = rel >= capL;
            int px = right ? RX : LX;
            int y = right ? TOP + (rel - capL) * ROW : contentsTopLeft() + rel * ROW;
            int r = rows.get(k);
            if (r < 0) {
                int ch = -1 - r;
                String name = "- " + (ch < chapters.size() ? chapters.get(ch) : "?") + " -";
                centerText(g, name, px, y + 1, 0.62f, HOT);
            } else {
                Page p = pages.get(r);
                boolean hover = inRect(mx, my, px + 4, y, PW - 8, ROW);
                if (hover) g.fill(px + 4, y, px + PW - 4, y + ROW - 1, 0xFF3A1848);
                centerText(g, p.title(), px, y + 1, 0.62f, hover ? 0xFFFFFFFF : TEXT);
                String n = String.valueOf(firstView(r) + 1);
                var pose = g.pose();
                pose.pushMatrix();
                pose.translate(px + PW - 4 - this.font.width(n) * 0.5f, y + 1.5f);
                pose.scale(0.5f, 0.5f);
                g.text(this.font, n, 0, 0, SUB, false);
                pose.popMatrix();
            }
        }
    }

    private int hitContents(int mx, int my) {
        View v = cur();
        if (!isContents(v)) return -1;
        List<Integer> rows = contentsRows(query);
        int capL = (BOTTOM - contentsTopLeft()) / ROW, capR = (BOTTOM - TOP) / ROW;
        int per = capL + capR, start = v.cont() * per;
        for (int k = start; k < Math.min(rows.size(), start + per); k++) {
            int rel = k - start;
            boolean right = rel >= capL;
            int px = right ? RX : LX;
            int y = right ? TOP + (rel - capL) * ROW : contentsTopLeft() + rel * ROW;
            if (rows.get(k) >= 0 && inRect(mx, my, px + 4, y, PW - 8, ROW)) return rows.get(k);
        }
        return -1;
    }

    private ItemStack drawVisual(GuiGraphicsExtractor g, Page p, int mx, int my, int mouseX, int mouseY, int left, int top, float s) {
        String v = p.visual();
        int vh = visualH(p);
        ItemStack hovered = ItemStack.EMPTY;
        if (v.startsWith("armor:")) {
            int fam = Integer.parseInt(v.substring(6));
            int[] b = armorBox();
            g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], 0x66160822);
            GuideArmorRender.drawSparkles(g, b[0], b[1], b[2], b[3]);
            int sx0 = left + Math.round(b[0] * s), sy0 = top + Math.round(b[1] * s);
            int sx1 = left + Math.round((b[0] + b[2]) * s), sy1 = top + Math.round((b[1] + b[3]) * s);
            if (!GuideArmorRender.draw(g, fam, sx0, sy0, sx1, sy1)) {
                centerText(g, "[ armor render ]", RX, b[1] + b[3] / 2 - 3, TS, SUB);
            }
            int[] w = wingsBtn();
            if (w != null) {
                boolean hov = inRect(mx, my, w[0], w[1], w[2], w[3]);
                g.fill(w[0], w[1], w[0] + w[2], w[1] + w[3], hov ? 0xFFD864FF : 0xFF5A246E);
                g.fill(w[0] + 1, w[1] + 1, w[0] + w[2] - 1, w[1] + w[3] - 1, hov ? 0xFF4A1A5A : 0xFF12061A);
                centerText(g, "Wings: " + (GuideArmorRender.wings ? "On" : "Off"), RX, w[1] + 2, 0.55f, 0xFFE8C8DE);
            }
            // drag hint
        } else if (v.startsWith("recipe:")) {
            hovered = drawRecipes(g, v.substring(7), RX + 1, TOP, vh, mx, my);
        } else if (v.startsWith("items:")) {
            String[] ids = v.substring(6).split(",");
            int n = ids.length, size = 40, gap = 6;
            int x = RX + (PW - (n * size + (n - 1) * gap)) / 2;
            for (String id : ids) {
                var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id.trim()));
                ItemStack st = new ItemStack(item);
                float bob = (float) Math.sin(System.currentTimeMillis() / 600.0) * 1.5f;
                g.fill(x, TOP + 2, x + size, TOP + 2 + size, 0x55160822);
                var pose = g.pose();
                pose.pushMatrix();
                pose.translate(x + 4, TOP + 6 + bob);
                pose.scale(2f, 2f);
                g.item(st, 0, 0);
                pose.popMatrix();
                if (inRect(mx, my, x, TOP + 2, size, size)) hovered = st;
                x += size + gap;
            }
            GuideArmorRender.drawSparkles(g, RX + 20, TOP, PW - 40, vh);
        } else switch (v) {
            case "matrix" -> drawMatrixShowcase(g, RX + 1, TOP);
            case "meteor" -> drawMeteorShowcase(g, RX + 1, TOP + 2);
            case "settings" -> drawSettings(g, RX + 3, TOP);
            case "flow" -> hovered = drawFlow(g, RX, TOP, mx, my);
            default -> {}
        }
        if (!p.caption().isEmpty()) sub(g, p.caption(), RX, TOP + vh + 4);
        return hovered;
    }

    /** Roadmap: fragments -> crystal -> ingot + template -> tier 1 -> tier 2 -> Infinity, zig-zag with arrows. */
    private ItemStack drawFlow(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
        ItemStack[] st = {
                new ItemStack(Infinium.SPACETIME_FRAGMENTS), new ItemStack(Infinium.SPACETIME_CRYSTAL),
                new ItemStack(Infinium.INGOT), GuideStacks.template(dev.justin.infinium.InfiniumStyle.ASTRAL),
                GuideStacks.armor(Infinium.CHESTPLATE, dev.justin.infinium.InfiniumStyle.ASTRAL),
                GuideStacks.armor(Infinium.CHESTPLATE, dev.justin.infinium.InfiniumStyle.SINGULARITY),
                new ItemStack(Infinium.INF_CHESTPLATE)};
        String[] lab = {"Fragments", "Crystal", "Ingot", "Template", "Tier 1", "Tier 2", "Infinity"};
        // 4 rows: (0,1) (2,3) (4,5) (6)
        int[][] pos = {{x + 22, y + 2}, {x + 82, y + 2}, {x + 82, y + 30}, {x + 22, y + 30}, {x + 22, y + 58}, {x + 82, y + 58}, {x + 52, y + 84}};
        ItemStack hovered = ItemStack.EMPTY;
        long ms = System.currentTimeMillis();
        for (int i = 0; i < st.length - 1; i++) {
            int ax = pos[i][0] + 11, ay = pos[i][1] + 9, bx = pos[i + 1][0] + 11, by = pos[i + 1][1] + 9;
            int steps = 14;
            for (int k = 1; k < steps; k++) {
                float t = k / (float) steps;
                float pulse = (float) ((ms / 900.0 + i * 0.3) % 1.0);
                int a = Math.abs(t - pulse) < 0.12f ? 0xFF : 0x88;
                int col = (a << 24) | ((i & 1) == 0 ? 0xD864FF : 0xFF66CC);
                int px = (int) (ax + (bx - ax) * t), py = (int) (ay + (by - ay) * t);
                g.fill(px, py, px + 1, py + 1, col);
            }
        }
        var pose = g.pose();
        for (int i = 0; i < st.length; i++) {
            int px = pos[i][0], py = pos[i][1];
            g.fill(px, py, px + 22, py + 18, i == st.length - 1 ? 0xFFC45AD4 : 0xFF5A246E);
            g.fill(px + 1, py + 1, px + 21, py + 17, 0xFF12061A);
            if (!st[i].isEmpty()) g.item(st[i], px + 3, py + 1);
            if (inRect(mx, my, px, py, 22, 18)) hovered = st[i];
            float w = this.font.width(lab[i]) * 0.5f;
            pose.pushMatrix();
            pose.translate(px + 11 - w / 2f, py + 19);
            pose.scale(0.5f, 0.5f);
            g.text(this.font, lab[i], 0, 0, SUB, false);
            pose.popMatrix();
        }
        return hovered;
    }

    private void drawMatrixShowcase(GuiGraphicsExtractor graphics, int x, int y) {
        long ms = System.currentTimeMillis();
        var pose = graphics.pose();
        int boxW = 124, boxH = 24;
        graphics.fill(x, y, x + boxW, y + boxH, 0xFF5A246E);
        graphics.fill(x + 1, y + 1, x + boxW - 1, y + boxH - 1, 0xEE160822);
        int frame = (int) ((ms / 100L) % CRYSTAL_FRAMES);
        pose.pushMatrix();
        pose.translate(x + 4, y + 4);
        pose.scale(0.80f, 0.80f);
        graphics.blit(RenderPipelines.GUI_TEXTURED, CRYSTAL_TEX, 0, 0, 0, frame * 16, 16, 16, 16, 16 * CRYSTAL_FRAMES);
        pose.popMatrix();
        small(graphics, "LIFETIME CRYSTALS", x + 20, y + 3, 0.60f, SUB);
        String tot = GuideNumbers.get("crystals_total");
        small(graphics, tot + " / " + tot + " (max)", x + 20, y + 9, 0.68f, HOT);
        int barX = x + 4, barY = y + 17, barW = 116, barH = 4;
        graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF3A1248);
        graphics.fill(barX + 1, barY + 1, barX + barW - 1, barY + barH - 1, 0xFF0D0312);
        int fillW = barW - 2;
        for (int px = 0; px < fillW; px++) {
            float u = (float) px / fillW;
            float wave = (float) (0.5 + 0.5 * Math.sin(u * 6.0 + ms / 350.0));
            int col = 0xFF000000 | ((int) (180 + 75 * wave) << 16) | ((int) (40 + 50 * wave) << 8) | (int) (200 + 55 * wave);
            graphics.fill(barX + 1 + px, barY + 1, barX + 2 + px, barY + barH - 1, col);
        }
        int startY = y + 27;
        drawMilestoneCard(graphics, x, startY, 124, 25, "Tier 1: Astral & Cybernetic", 0xFFC45AD4,
                GuideNumbers.get("crystals_t1") + " crystals, 4 perks", 8, 4, 0xFFFF00AA, ms);
        drawMilestoneCard(graphics, x, startY + 28, 124, 25, "Tier 2: Singularity & Cyber Psycho", 0xFFA14AD0,
                GuideNumbers.get("crystals_t2") + " crystals, 4 upgrades", 16, 4, 0xFFA14AD0, ms);
        drawMilestoneCard(graphics, x, startY + 56, 124, 25, "Tier 3: Infinity", 0xFFFF00AA,
                GuideNumbers.get("crystals_t3") + " crystals, 8 upgrades", 32, 8, 0xFFFF00AA, ms);
    }

    private void small(GuiGraphicsExtractor g, String t, float x, float y, float sc, int col) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(sc, sc);
        g.text(this.font, t, 0, 0, col, false);
        pose.popMatrix();
    }

    /** Animated night sky window (the old fact cards are gone; facts live in the Numbers paragraph). */
    private void drawMeteorShowcase(GuiGraphicsExtractor graphics, int x, int y) {
        long ms = System.currentTimeMillis();
        var pose = graphics.pose();
        int skyW = 124, skyH = 46;
        graphics.fill(x, y, x + skyW, y + skyH, 0xFF8A1061);
        graphics.fill(x + 1, y + 1, x + skyW - 1, y + skyH - 1, 0xFF06030A);
        for (int i = 0; i < 14; i++) {
            int sx = x + 6 + Math.abs(hash(i, 42) % 110);
            int sy = y + 4 + Math.abs(hash(i, 99) % 26);
            float tw = (float) (0.4 + 0.6 * Math.sin(ms / 300.0 + i * 1.7));
            graphics.fill(sx, sy, sx + 1, sy + 1, ((int) (180 * tw) << 24) | 0xFFD8F0);
        }
        for (int m = 0; m < 2; m++) {
            float phase = ((ms + m * 900L) % 1800L) / 1800.0f;
            float mx = x + 110 - phase * 100;
            float my = y + 4 + phase * 28;
            if (my < y + skyH - 14) {
                for (int t = 1; t <= 5; t++) {
                    float tx = mx + t * 3.5f, ty = my - t * 1.5f;
                    graphics.fill((int) tx, (int) ty, (int) tx + 2, (int) ty + 2, t > 3 ? 0x66FF00AA : 0xAAFF00AA);
                }
                graphics.fill((int) mx, (int) my, (int) mx + 3, (int) my + 3, 0xFFFFAAE3);
            }
        }
        int groundY = y + skyH - 12;
        graphics.fill(x + 1, groundY, x + skyW - 1, y + skyH - 1, 0xFF140A18);
        graphics.fill(x + 1, groundY, x + skyW - 1, groundY + 1, 0xFF3A1A30);
        int craterX = x + 38;
        int ring = (int) ((ms / 50L) % 24);
        int ra = Math.max(0, 160 - ring * 6);
        graphics.fill(craterX - ring, groundY - ring / 3, craterX + ring, groundY + ring / 3, (ra << 24) | 0xFF00AA);
        graphics.fill(craterX - 3, groundY - 1, craterX + 3, groundY + 2, 0xFFFF00AA);
        if (Infinium.SPACETIME_FRAGMENTS != null) {
            pose.pushMatrix();
            pose.translate(craterX - 4, groundY - 14);
            pose.scale(0.55f, 0.55f);
            graphics.item(new ItemStack(Infinium.SPACETIME_FRAGMENTS), 0, 0);
            pose.popMatrix();
        }
    }

    private void drawSettings(GuiGraphicsExtractor graphics, int x, int y) {
        String[] files = {"settings_particles_on", "settings_particles_off", "settings_wings_on", "settings_wings_off"};
        String[] labels = {"Particles on", "Particles off", "Wings on", "Wings off"};
        int size = 42, colW = 62, rowH = 52;
        long ms = System.currentTimeMillis();
        for (int i = 0; i < 4; i++) {
            int cx = x + (i % 2) * colW + (colW - size) / 2;
            int cy = y + (i / 2) * rowH;
            if (!blitStrip(graphics, files[i], cx, cy, size, SET_FRAME, SET_FRAMES)) drawProceduralSettingsBox(graphics, i, cx, cy, size, ms);
            float w = this.font.width(labels[i]) * 0.6f;
            small(graphics, labels[i], cx + (size - w) / 2f, cy + size + 2, 0.6f, 0xFFD2C0D4);
        }
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
            int index = layouts.size() == 1 ? 0 : (int) ((System.currentTimeMillis() / 2200L) % layouts.size());
            return drawStation(graphics, layouts.get(index), x, y, 124, availH, mouseX, mouseY);
        }
        int rows = layouts.size(), gap = 3;
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

    private void drawLineWithCrystalIcons(GuiGraphicsExtractor g, String line, int x, int y, int color) {
        int idx = line.indexOf(CRYSTAL_TOKEN);
        if (idx < 0) { g.text(this.font, line, x, y, color, false); return; }
        int curX = x, pos = 0;
        int frame = (int) ((System.currentTimeMillis() / 100L) % CRYSTAL_FRAMES);
        while (pos < line.length()) {
            int next = line.indexOf(CRYSTAL_TOKEN, pos);
            if (next < 0) { g.text(this.font, line.substring(pos), curX, y, color, false); break; }
            if (next > pos) {
                String seg = line.substring(pos, next);
                g.text(this.font, seg, curX, y, color, false);
                curX += this.font.width(seg);
            }
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

    // ------------------------------------------------------------------ input

    private int lmx(double x) { return (int) Math.floor((x - bookLeft()) / bookScale()); }
    private int lmy(double y) { return (int) Math.floor((y - bookTop()) / bookScale()); }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (search != null && search.visible && search.isMouseOver(event.x(), event.y())) return super.mouseClicked(event, doubled);
        if (event.button() == 0) {
            int mx = lmx(event.x()), my = lmy(event.y());
            if (inRect(mx, my, B_BACK[0], NAV_Y, B_BACK[1], NAV_H) && view > 0) { click(); turn(-1); return true; }
            if (inRect(mx, my, B_NEXT[0], NAV_Y, B_NEXT[1], NAV_H) && view < views.size() - 1) { click(); turn(1); return true; }
            if (inRect(mx, my, B_CONTENTS[0], NAV_Y, B_CONTENTS[1], NAV_H)) { click(); go(0, true); return true; }
            if (!history.isEmpty() && inRect(mx, my, B_RETURN[0], NAV_Y, B_RETURN[1], NAV_H)) { click(); returnBack(); return true; }
            for (int i = 0; i < chapters.size(); i++) {
                if (inRect(mx, my, BOOK_W, tabY(i), TAB_W, 23)) {
                    for (int pi = 0; pi < pages.size(); pi++) {
                        if (pages.get(pi).chapter() == i && !"contents".equals(pages.get(pi).visual())) { click(); go(firstView(pi), true); return true; }
                    }
                }
            }
            int hit = hitContents(mx, my);
            if (hit >= 0) { click(); go(firstView(hit), true); return true; }
            int[] w = wingsBtn();
            if (w != null && inRect(mx, my, w[0], w[1], w[2], w[3])) { click(); GuideArmorRender.wings = !GuideArmorRender.wings; return true; }
            int[] b = armorBox();
            if (b != null && inRect(mx, my, b[0], b[1], b[2], b[3])) { dragging = true; return true; }
            if (search != null && search.isFocused()) { search.setFocused(false); setFocused(null); }
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging) { GuideArmorRender.addDrag(dx / Math.max(0.5f, bookScale())); return true; }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (sy > 0) { turn(-1); return true; }
        if (sy < 0) { turn(1); return true; }
        return super.mouseScrolled(x, y, sx, sy);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (search != null && search.visible && search.isFocused()) {
            if (key == 256) return super.keyPressed(event);   // Esc closes
            if (key == 257 || key == 335) {   // Enter: open first result
                for (int r : contentsRows(query)) if (r >= 0) { go(firstView(r), true); return true; }
                return true;
            }
            return super.keyPressed(event);
        }
        if (key == 263 || key == 65 || key == 266) { turn(-1); return true; }
        if (key == 262 || key == 68 || key == 267) { turn(1); return true; }
        if (key == 259) { returnBack(); return true; }    // Backspace = Return
        if (key == 268) { go(0, true); return true; }     // Home = Contents
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (closing) return;
        closing = true;
        saveLast();
        Minecraft.getInstance().gui.setScreen(back);
    }

    @Override
    public boolean isPauseScreen() { return false; }

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
        g.fill(cx + size - 15, cy + 3, cx + size - 3, cy + 9, on ? 0xFF5A2849 : 0xFF482038);
        g.fill(cx + size - 14, cy + 4, cx + size - 4, cy + 8, on ? 0xFFFF66CC : 0xFF9A6888);
    }


    private record ArtSheet(Identifier id, int fw, int fh, int cols, int frames, int ms) {}
    private static java.util.Map<String, ArtSheet> sheets;
    private static java.util.Map<String, ArtSheet> sheets() {
        if (sheets != null) return sheets;
        java.util.Map<String, ArtSheet> m = new java.util.HashMap<>();
        var res = Minecraft.getInstance().getResourceManager().getResource(Identifier.fromNamespaceAndPath("infinium", "guide/strips.json"));
        if (res.isPresent()) {
            try (var reader = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                for (var e : root.entrySet()) {
                    JsonObject o = e.getValue().getAsJsonObject();
                    m.put(e.getKey(), new ArtSheet(Identifier.fromNamespaceAndPath("infinium", "textures/gui/guide/" + o.get("file").getAsString() + ".png"),
                            o.get("fw").getAsInt(), o.get("fh").getAsInt(), Math.max(1, o.get("cols").getAsInt()),
                            Math.max(1, o.get("frames").getAsInt()), Math.max(10, o.get("ms").getAsInt())));
                }
            } catch (Exception ignored) {}
        }
        return sheets = m;
    }

    private boolean blitSheet(GuiGraphicsExtractor graphics, String art, int x, int y, int w, int h) {
        ArtSheet sh = sheets().get(art);
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


    private static int hash(long a, int b) {
        long h = a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        return (int) (h ^ (h >>> 32));
    }


}
