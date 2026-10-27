package dev.justin.infinium.client.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Optional Iris hook (reflection only, no compile dependency). Before submitting the glow overlay we set Iris'
 * currentRenderedItemId to the shaderpack's id for minecraft:glowstone_dust; Iris captures it per submit, and
 * Complementary (IPBR) then renders the overlay as an unconditional emissive. No-op without Iris / shaderpack /
 * item.properties; any failure disables the hook permanently. Never throws.
 */
public final class IrisGlowCompat {
    private static final int NONE = Integer.MIN_VALUE;
    private static volatile boolean disabled;
    private static boolean init;
    private static Object crs, wrs;
    private static MethodHandle getItem, setItem, getIds, getInt;
    private static Object glowKey;
    private static Object cachedIds;
    private static int cachedId;

    private IrisGlowCompat() {}

    private static boolean ensure() {
        if (init) return true;
        try {
            ClassLoader cl = IrisGlowCompat.class.getClassLoader();
            Class<?> c = Class.forName("net.irisshaders.iris.uniforms.CapturedRenderingState", true, cl);
            Class<?> w = Class.forName("net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings", true, cl);
            Class<?> n = Class.forName("net.irisshaders.iris.shaderpack.materialmap.NamespacedId", true, cl);
            Class<?> f = Class.forName("it.unimi.dsi.fastutil.objects.Object2IntFunction", true, cl);
            MethodHandles.Lookup l = MethodHandles.publicLookup();
            crs = c.getField("INSTANCE").get(null);
            wrs = w.getField("INSTANCE").get(null);
            getItem = l.findVirtual(c, "getCurrentRenderedItem", MethodType.methodType(int.class));
            setItem = l.findVirtual(c, "setCurrentRenderedItem", MethodType.methodType(void.class, int.class));
            getIds = l.findVirtual(w, "getItemIds", MethodType.methodType(f));
            getInt = l.findVirtual(f, "getInt", MethodType.methodType(int.class, Object.class));
            glowKey = l.findConstructor(n, MethodType.methodType(void.class, String.class, String.class)).invoke("minecraft", "glowstone_dust");
            init = true;
            return true;
        } catch (Throwable t) {
            disabled = true;   // Iris absent or internals changed
            return false;
        }
    }

    /** Sets the glow item id; returns the previous id to pass to {@link #pop}, or NONE if nothing changed. */
    public static int pushGlowId() {
        if (disabled) return NONE;
        try {
            if (!ensure()) return NONE;
            Object ids = getIds.invoke(wrs);
            if (ids == null) return NONE;                       // no shaderpack / no item.properties
            if (ids != cachedIds) { cachedId = (int) getInt.invoke(ids, glowKey); cachedIds = ids; }
            if (cachedId <= 0) return NONE;                      // pack doesn't map glowstone_dust
            int prev = (int) getItem.invoke(crs);
            setItem.invoke(crs, cachedId);
            return prev;
        } catch (Throwable t) {
            disabled = true;
            return NONE;
        }
    }

    public static void pop(int prev) {
        if (prev == NONE || disabled) return;
        try { setItem.invoke(crs, prev); } catch (Throwable t) { disabled = true; }
    }
}
