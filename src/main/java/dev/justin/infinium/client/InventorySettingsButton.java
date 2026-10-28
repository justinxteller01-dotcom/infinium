package dev.justin.infinium.client;

import dev.justin.infinium.CrystalCharge;
import dev.justin.infinium.Infinium;
import dev.justin.infinium.Perk;
import dev.justin.infinium.SetLook;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Inventory widgets:
 * 1) 12 x 12 Settings Gear button to the right of the Recipe Book button (survival +128,+63; creative Inventory tab +130,+14).
 *    Opens InfiniumPerksScreen directly to the player's worn armor set tab.
 * 2) 47 x 6 Spacetime Energy Progress Bar located directly across the base of the player portrait box
 *    (survival +28,+71; creative Inventory tab +75,+81). Visually fills with the armor set's cosmic color and
 *    opens the Spacetime Crystal Matrix on click. Only visible when wearing a full matching Infinium armor set.
 * 3) Crystal counter (G4): small Spacetime Crystal icon + "x/80" directly right of the gear, display only.
 *    x = CrystalCharge.totalLifetimeCrystals(player).
 * batch1 visibility: gear appears the first time ANY Infinium weapon/tool/armor is acquired and stays forever (A1);
 *    the counter appears after the first full-set equip and stays forever (A3); both via the synced UiUnlocks flags.
 *    The energy bar shows only while a full matching set is worn (A2).
 *    The old portrait-top "X / 80" counter box was removed (G4); the bottom energy bar is kept.
 */
public final class InventorySettingsButton extends AbstractButton {
    private static final Identifier TEX = Infinium.id("textures/gui/settings_button.png");
    private static final Identifier TEX_HOVER = Infinium.id("textures/gui/settings_button_hover.png");

    private InventorySettingsButton() {
        super(0, 0, 12, 12, Component.translatable("gui.infinium.settings"));
        setTooltip(Tooltip.create(Component.translatable("gui.infinium.settings")));
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (!(screen instanceof InventoryScreen) && !(screen instanceof CreativeModeInventoryScreen)) return;
            AbstractContainerScreen<?> cs = (AbstractContainerScreen<?>) screen;
            InventorySettingsButton gear = new InventorySettingsButton();
            InventoryCrystalCounter counter = new InventoryCrystalCounter();
            Screens.getWidgets(screen).add(gear);
            InventoryCrystalEnergyBar energyBar = new InventoryCrystalEnergyBar(cs);
            Screens.getWidgets(screen).add(counter);
            Screens.getWidgets(screen).add(energyBar);
            update(gear, counter, energyBar, cs);
            ScreenEvents.afterTick(screen).register(s -> update(gear, counter, energyBar, (AbstractContainerScreen<?>) s));
        });
    }

    static void update(InventorySettingsButton gear, InventoryCrystalCounter counter, InventoryCrystalEnergyBar energyBar, AbstractContainerScreen<?> s) {
        var acc = (dev.justin.infinium.client.mixin.ContainerScreenPosAccessor) s;
        boolean creative = s instanceof CreativeModeInventoryScreen;
        boolean invOpen = !creative || ((CreativeModeInventoryScreen) s).isInventoryOpen();
        if (creative) {
            gear.setPosition(acc.infinium$leftPos() + 130, acc.infinium$topPos() + 14);
            energyBar.setPosition(acc.infinium$leftPos() + 75, acc.infinium$topPos() + 81);
        } else {
            gear.setPosition(acc.infinium$leftPos() + 128, acc.infinium$topPos() + 63);
            energyBar.setPosition(acc.infinium$leftPos() + 28, acc.infinium$topPos() + 71);
        }
        // G4: counter sits immediately right of the gear (gear is 12 wide, 2 px gap).
        counter.setPosition(gear.getX() + gear.getWidth() + 2, gear.getY() + 2);
        Player p = Minecraft.getInstance().player;
        // batch1: gear = forever once ANY Infinium gear was acquired (A1); counter = forever after the first full-set
        // equip (A3); energy bar = only while a full matching set is worn right now (A2). Flags: dev.justin.infinium.UiUnlocks.
        boolean setEquipped = p != null && Infinium.setWorn(p) && invOpen;
        boolean gearUnlocked = p != null && invOpen && (dev.justin.infinium.UiUnlocks.has(p, dev.justin.infinium.UiUnlocks.GEAR) || hasGear(p) || setEquipped);
        boolean counterUnlocked = p != null && invOpen && (dev.justin.infinium.UiUnlocks.has(p, dev.justin.infinium.UiUnlocks.FULL_SET) || setEquipped);
        gear.visible = gearUnlocked;
        gear.active = gear.visible;

        counter.visible = counterUnlocked;
        counter.active = false; // display only, never clickable

        energyBar.visible = setEquipped;
        energyBar.active = setEquipped;
        if (energyBar.visible) {
            energyBar.updateTooltip(p);
        }
    }

    static boolean hasGear(Player p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var s = inv.getItem(i);
            if (!s.isEmpty() && (Infinium.isArmor(s) || Infinium.isTool(s))) return true;
        }
        return false;
    }

    @Override
    public void onPress(InputWithModifiers in) {
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        SetLook look = mc.player == null ? null : SetLook.of(mc.player);
        if (look == null && mc.player != null) look = HolderStyleProperty.wornLook(mc.player);
        mc.gui.setScreen(new InfiniumPerksScreen(mc.gui.screen(), look != null ? look.ordinal() : 0));
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float a) {
        g.blit(RenderPipelines.GUI_TEXTURED, isHoveredOrFocused() ? TEX_HOVER : TEX, getX() - 2, getY() - 2, 0F, 0F, 16, 16, 16, 16);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }

    static final class InventoryCrystalEnergyBar extends AbstractButton {
        private final AbstractContainerScreen<?> parentScreen;
        private int lastFilled = -1;
        private int lastCap = -1;

        InventoryCrystalEnergyBar(AbstractContainerScreen<?> parentScreen) {
            super(0, 0, 47, 6, Component.literal("Spacetime Crystal Matrix"));
            this.parentScreen = parentScreen;
        }

        void updateTooltip(Player p) {
            SetLook l = SetLook.of(p);
            if (l == null) return;
            int filled = CrystalCharge.setFilled(p);
            int cap = CrystalCharge.setSlots(p);
            if (filled != lastFilled || cap != lastCap) {
                lastFilled = filled;
                lastCap = cap;
                String name = Perk.FAMILY_NAMES[l.ordinal()];
                setTooltip(Tooltip.create(Component.literal(name + " Energy: " + filled + "/" + cap + " Crystals\nClick to open Matrix Screen")));
            }
        }

        @Override
        public void onPress(InputWithModifiers in) {
            Minecraft mc = Minecraft.getInstance();
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            mc.gui.setScreen(new CrystalScreen(mc.gui.screen(), parentScreen.getMenu().containerId, -1));
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float a) {
            Player p = Minecraft.getInstance().player;
            if (p == null) return;
            SetLook look = SetLook.of(p);
            if (look == null) return;
            int filled = CrystalCharge.setFilled(p);
            int cap = Math.max(1, CrystalCharge.setSlots(p));
            float ratio = Math.min(1.0f, Math.max(0.0f, filled / (float) cap));

            int fam = look.ordinal();
            int accent = switch (fam) {
                case 0 -> 0xFFC45AD4;
                case 1 -> 0xFFD24A8A;
                case 2 -> 0xFFA14AD0;
                case 3 -> 0xFFE0235A;
                case 4 -> 0xFFD84CFF;
                default -> 0xFFC45AD4;
            };

            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            int border = isHoveredOrFocused() ? 0xFFFF70E0 : 0xFF7A3290;
            g.fill(x, y, x + w, y + h, border);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF12061A);

            int fw = Math.round(ratio * (w - 2));
            if (fw > 0) {
                g.fill(x + 1, y + 1, x + 1 + fw, y + h - 1, accent);
                g.fill(x + 1, y + 1, x + 1 + fw, y + 2, 0xAAFFFFFF);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }

    /** G4: display-only "icon x/80" readout right of the gear. */
    static final class InventoryCrystalCounter extends AbstractButton {
        private ItemStack crystalStack;

        InventoryCrystalCounter() {
            super(0, 0, 30, 8, Component.literal("Spacetime Crystals"));
        }

        private ItemStack getCrystalStack() {
            if (crystalStack == null && Infinium.SPACETIME_CRYSTAL != null) {
                crystalStack = new ItemStack(Infinium.SPACETIME_CRYSTAL);
            }
            return crystalStack;
        }

        @Override
        public void onPress(InputWithModifiers in) {}

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float a) {
            Player p = Minecraft.getInstance().player;
            if (p == null) return;
            int total = CrystalCharge.totalLifetimeCrystals(p);
            int x = getX(), y = getY();

            // Spacetime Crystal item icon, scaled 16 -> 8 px
            ItemStack stack = getCrystalStack();
            if (stack != null && !stack.isEmpty()) {
                var pose = g.pose();
                pose.pushMatrix();
                pose.translate(x, y);
                pose.scale(0.5f, 0.5f);
                g.item(stack, 0, 0);
                pose.popMatrix();
            }

            String txt = total + "/80";
            var font = Minecraft.getInstance().font;
            float scale = 0.70f;
            var pose = g.pose();
            pose.pushMatrix();
            pose.translate(x + 9f, y + 1f);
            pose.scale(scale, scale);
            g.text(font, txt, 0, 0, total >= 80 ? 0xFFFF00AA : 0xFFE8D4EE, true);
            pose.popMatrix();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }
}
