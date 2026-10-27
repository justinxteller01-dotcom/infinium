package dev.justin.infinium.client;

import dev.justin.infinium.ArmorRemovePayload;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** R4: confirm removing armor that would end the stack-limit buff. "Remove" asks the server to perform the exact action. */
public final class ArmorRemoveWarningScreen extends Screen {
    private static final String MSG = "Removing this piece ends your bigger-stacks set bonus. Every item that can no longer be held "
        + "will fall on the ground, including items in backpacks, equipped or not.";
    private final Screen parent; private final ArmorRemovePayload action;
    public ArmorRemoveWarningScreen(Screen parent, ArmorRemovePayload action) {
        super(Component.literal("Remove armor?")); this.parent = parent; this.action = action;
    }
    @Override protected void init() {
        int cx = width / 2, y = height / 2 + 24;
        addRenderableWidget(Button.builder(Component.literal("Remove"), b -> { ClientPlayNetworking.send(action); onClose(); }).bounds(cx - 104, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Leave On"), b -> onClose()).bounds(cx + 4, y, 100, 20).build());
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        int cx = width / 2, top = height / 2 - 52;
        g.fill(cx - 124, top - 8, cx + 124, height / 2 + 52, 0xF00E0612);
        String t = "Remove armor?";
        g.text(font, t, cx - font.width(t) / 2, top, 0xFFD24A8A, false);
        int y = top + 16;
        for (String line : wrap(MSG, 232)) { g.text(font, line, cx - font.width(line) / 2, y, 0xFFE07ACB, false); y += 10; }
        super.extractRenderState(g, mx, my, pt);
    }
    private List<String> wrap(String s, int w) {
        List<String> out = new ArrayList<>(); StringBuilder cur = new StringBuilder();
        for (String word : s.split(" ")) {
            String next = cur.length() == 0 ? word : cur + " " + word;
            if (font.width(next) > w && cur.length() > 0) { out.add(cur.toString()); cur = new StringBuilder(word); } else cur = new StringBuilder(next);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }
}
