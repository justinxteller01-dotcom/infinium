package dev.justin.infinium.compat.punchy;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import punchy.client.animation.PunchyAnimationManager;
import punchy.client.animation.data.AnimationClip;
import punchy.config.PunchyTuningConfig;

/**
 * Direct client bridge to the Punchy animation mod.
 * Allows Infinium to resolve Punchy animation clips matching the sword's combo sequence,
 * and trigger the hand-spin flourish after the stab.
 */
public final class PunchyBridge {
    private PunchyBridge() {}

    public static boolean isPunchyLoaded() {
        return FabricLoader.getInstance().isModLoaded("punchy");
    }

    private static final String[] RESOLVER_CLASS_NAMES = new String[] {
        "punchy.client.animation.CustomAnimationResolver",
        "punchy.client.animation.resolver.CustomAnimationResolver",
        "punchy.client.CustomAnimationResolver",
        "punchy.config.CustomAnimationResolver",
        "punchy.client.state.CustomAnimationResolver",
        "punchy.animation.CustomAnimationResolver"
    };

    /**
     * Looks up the custom animation entry for the given stack with action "attack".
     * Checks known resolver classes (e.g. CustomAnimationResolver.findEntries(stack, "attack"))
     * and PunchyTuningConfig. Returns null if not found.
     */
    public static PunchyTuningConfig.CustomAnimationEntry findAttackEntry(ItemStack stack) {
        if (!isPunchyLoaded() || stack == null || stack.isEmpty()) return null;
        ClassLoader cl = PunchyBridge.class.getClassLoader();
        for (String className : RESOLVER_CLASS_NAMES) {
            try {
                Class<?> clazz = Class.forName(className, true, cl);
                for (java.lang.reflect.Method m : clazz.getMethods()) {
                    if (m.getName().equals("findEntries") && m.getParameterCount() == 2) {
                        Object target = java.lang.reflect.Modifier.isStatic(m.getModifiers()) ? null : clazz.getDeclaredConstructor().newInstance();
                        Object result = m.invoke(target, stack, "attack");
                        PunchyTuningConfig.CustomAnimationEntry entry = extractEntry(result);
                        if (entry != null) return entry;
                    }
                }
            } catch (Throwable ignored) {}
        }
        try {
            for (java.lang.reflect.Method m : PunchyTuningConfig.class.getMethods()) {
                if (m.getName().equals("findEntries") && m.getParameterCount() == 2) {
                    Object result = m.invoke(null, stack, "attack");
                    PunchyTuningConfig.CustomAnimationEntry entry = extractEntry(result);
                    if (entry != null) return entry;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static PunchyTuningConfig.CustomAnimationEntry extractEntry(Object result) {
        if (result == null) return null;
        if (result instanceof PunchyTuningConfig.CustomAnimationEntry entry) {
            return entry;
        }
        if (result instanceof java.util.List<?> list) {
            if (!list.isEmpty()) {
                Object first = list.get(0);
                if (first instanceof PunchyTuningConfig.CustomAnimationEntry entry) {
                    return entry;
                }
            }
            return null;
        }
        if (result instanceof Object[] arr) {
            if (arr.length > 0 && arr[0] instanceof PunchyTuningConfig.CustomAnimationEntry entry) {
                return entry;
            }
            return null;
        }
        if (result instanceof java.util.Optional<?> opt) {
            if (opt.isPresent() && opt.get() instanceof PunchyTuningConfig.CustomAnimationEntry entry) {
                return entry;
            }
            return null;
        }
        if (result instanceof Iterable<?> iterable) {
            java.util.Iterator<?> it = iterable.iterator();
            if (it.hasNext()) {
                Object first = it.next();
                if (first instanceof PunchyTuningConfig.CustomAnimationEntry entry) {
                    return entry;
                }
            }
        }
        return null;
    }

    public static AnimationClip resolveNamedClip(
        String clipName,
        Minecraft mc,
        InteractionHand hand,
        PunchyTuningConfig.CustomAnimationEntry entry,
        ItemStack stack
    ) {
        if (!isPunchyLoaded()) return null;
        try {
            String option = entry != null ? PunchyTuningConfig.getMixPreferredOptionForEntry(stack, entry) : null;
            return PunchyAnimationManager.resolveNamedClip(clipName, mc, hand, option);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String getCritClipNameFromEntry(PunchyTuningConfig.CustomAnimationEntry entry) {
        if (entry == null) return null;
        Class<?> clazz = entry.getClass();
        while (clazz != null && clazz != Object.class) {
            try {
                java.lang.reflect.Field f = clazz.getDeclaredField("critical_hit");
                f.setAccessible(true);
                Object val = f.get(entry);
                if (val instanceof String s && !s.isEmpty()) return s;
            } catch (Throwable ignored) {}
            try {
                java.lang.reflect.Method m = clazz.getDeclaredMethod("critical_hit");
                m.setAccessible(true);
                Object val = m.invoke(entry);
                if (val instanceof String s && !s.isEmpty()) return s;
            } catch (Throwable ignored) {}
            try {
                java.lang.reflect.Method m = clazz.getDeclaredMethod("getCriticalHit");
                m.setAccessible(true);
                Object val = m.invoke(entry);
                if (val instanceof String s && !s.isEmpty()) return s;
            } catch (Throwable ignored) {}
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    private static AnimationClip resolveWithOptionFallback(
        String clipName,
        Minecraft mc,
        InteractionHand hand,
        String option
    ) {
        if (clipName == null || clipName.isEmpty() || mc == null) return null;
        try {
            AnimationClip clip = null;
            if (option != null && !option.isEmpty()) {
                clip = PunchyAnimationManager.resolveNamedClip(clipName, mc, hand, option);
            }
            if (clip == null) {
                clip = PunchyAnimationManager.resolveNamedClip(clipName, mc, hand, null);
            }
            return clip;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Resolves Punchy's critical attack clip by reading the clip name from the entry's
     * "critical_hit" field, then resolving it with the player's chosen Punchy animation option
     * (via PunchyTuningConfig.getMixPreferredOptionForEntry).
     * Fallback chain: critical_hit value -> "attack_critical".
     * If nothing resolves, returns null (no crit path, no beam, no crash).
     */
    public static AnimationClip resolveCritClip(
        Minecraft mc,
        ItemStack stack,
        PunchyTuningConfig.CustomAnimationEntry entry
    ) {
        if (!isPunchyLoaded() || mc == null) return null;
        try {
            String option = entry != null ? PunchyTuningConfig.getMixPreferredOptionForEntry(stack, entry) : null;
            String critClipName = getCritClipNameFromEntry(entry);

            AnimationClip clip = null;
            // Fallback chain: critical_hit value -> "attack_critical"
            if (critClipName != null && !critClipName.isEmpty()) {
                clip = resolveWithOptionFallback(critClipName, mc, InteractionHand.MAIN_HAND, option);
            }
            if (clip == null) {
                clip = resolveWithOptionFallback("attack_critical", mc, InteractionHand.MAIN_HAND, option);
            }
            return clip;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Neutralized: TriggerableStateMachine.trigger(MAIN_HAND) triggered the 19s sword_inspect_rework animation. */
    public static void triggerFlourishTwirl() {
        // No-op: flourish now plays only sword_spin SFX and returns sword straight to idle hold
    }
}
