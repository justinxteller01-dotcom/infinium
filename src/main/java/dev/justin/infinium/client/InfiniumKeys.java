package dev.justin.infinium.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.justin.infinium.FlightThrustPayload;
import dev.justin.infinium.WingsKeyPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Custom Infinium key bindings:
 * - Toggle Wings (unbound by default)
 * - Flight Thrust key (key.infinium.flight_thrust, default Left Control / 341):
 *   * While in active flight/gliding: triggers Chestplate directional forward propulsion.
 *   (M2: no longer triggers Spacetime Warp; warp has its own ability key, unbound by default.)
 */
public final class InfiniumKeys {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("infinium", "controls"));
    public static KeyMapping TOGGLE_WINGS;
    public static KeyMapping ABILITY_CONTROL;

    private static boolean lastThrustState = false;

    public static void register() {
        TOGGLE_WINGS = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.infinium.toggle_wings", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY));
        ABILITY_CONTROL = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.infinium.flight_thrust", InputConstants.Type.KEYSYM, 341 /* GLFW_KEY_LEFT_CONTROL */, CATEGORY));
        // M3: the old toggle_xray key (X) is removed; X-ray is the pulse ability with its own unbound key.

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (TOGGLE_WINGS.consumeClick()) {
                if (mc.gui.screen() == null && mc.player != null && ClientPlayNetworking.canSend(WingsKeyPayload.TYPE))
                    ClientPlayNetworking.send(new WingsKeyPayload());
            }


            if (mc.player != null) {
                boolean ctrlDown = isControlHeld();

                if (mc.player.isFallFlying()) {
                    // FLIGHT STATE: Chestplate directional forward propulsion
                    if (ctrlDown != lastThrustState) {
                        lastThrustState = ctrlDown;
                        if (ClientPlayNetworking.canSend(FlightThrustPayload.TYPE)) {
                            ClientPlayNetworking.send(new FlightThrustPayload(ctrlDown));
                        }
                    }
                } else {
                    // Not gliding: release thrust. (M2: the old Ctrl rising-edge warp trigger is removed;
                    // warp now fires only from its own ability key, see SpacetimeWarp.register / AbilityClient.)
                    if (lastThrustState) {
                        lastThrustState = false;
                        if (ClientPlayNetworking.canSend(FlightThrustPayload.TYPE)) {
                            ClientPlayNetworking.send(new FlightThrustPayload(false));
                        }
                    }
                }
            } else {
                lastThrustState = false;
            }
        });
    }

    /**
     * True only while Infinium's own bound key (key.infinium.flight_thrust, default Left Control) is held.
     * M1 fix (review 5.1): no keySprint fallback and no hard-coded GLFW 341/345, so Toggle Sprint can't latch it
     * and rebinding in Controls / Controlling is respected. Unbound = never held.
     */
    public static boolean isControlHeld() {
        return ABILITY_CONTROL != null && !ABILITY_CONTROL.isUnbound() && ABILITY_CONTROL.isDown();
    }

    public static boolean isThrustHeld() {
        return isControlHeld();
    }

    private InfiniumKeys() {}
}
