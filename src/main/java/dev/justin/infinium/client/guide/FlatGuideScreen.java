package dev.justin.infinium.client.guide;

import net.minecraft.client.gui.screens.Screen;

/** R6e: the guide opened from the settings screen. Plain 2D: excluded from Spatial GUI by compat.mixin.spatial.ShouldHookScreenMixin.
 *  Closing returns to the screen that opened it (InfiniumGuideScreen.onClose -> back). */
public final class FlatGuideScreen extends InfiniumGuideScreen {
    public FlatGuideScreen(Screen back) { super(back); }
}
