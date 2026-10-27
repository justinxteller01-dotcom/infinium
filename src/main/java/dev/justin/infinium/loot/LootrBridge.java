package dev.justin.infinium.loot;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Optional Lootr (checked against lootr 1.24.41.124 Fabric 26.2 public API by javap). No compile dependency.
 * Per-container, per-player clear: LootrAPI.INSTANCE.getData(ILootrContainerInstance) -> ILootrInventoryStore,
 * then clearInventories(UUID) and remove the player from the actual/visual opener sets, markChanged().
 * Deliberately NOT LootrAPI.clearPlayerLoot(UUID): that wipes the player's Lootr loot in every chest in the world.
 */
public final class LootrBridge {
    private static boolean looked, warned;
    private static Object api;
    private static Class<?> instanceType;
    private static Method getData;

    public static boolean loaded() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("lootr");
    }

    public static boolean clearPlayerChest(ServerLevel level, UUID player, BlockPos pos) {
        if (!find()) return false;
        Object be = level.getBlockEntity(pos);
        if (be == null || !instanceType.isInstance(be)) return false;
        try {
            Object store = getData.invoke(api, be);
            if (store == null) return false;
            store.getClass().getMethod("clearInventories", UUID.class).invoke(store, player);
            for (String m : new String[]{"getActualOpeners", "getVisualOpeners"}) {
                Object set = store.getClass().getMethod(m).invoke(store);
                if (set instanceof Set<?> s) s.remove(player);
            }
            store.getClass().getMethod("markChanged").invoke(store);
            for (String m : new String[]{"getVisualOpeners", "getActualOpeners"}) {
                try { Object set = be.getClass().getMethod(m).invoke(be); if (set instanceof Set<?> s) s.remove(player); } catch (NoSuchMethodException ignored) {}
            }
            return true;
        } catch (Exception e) {
            if (!warned) { warned = true; dev.justin.infinium.Infinium.LOG.warn("infinium: Lootr per-chest clear failed: {}", e.toString()); }
            return false;
        }
    }

    public static boolean isLootr(Object be) {
        return be != null && be.getClass().getName().startsWith("noobanidus.mods.lootr");
    }

    /** javap lootr 1.24.41.124: ILootrInventoryStore.getInventory(UUID) -> ILootrInventory (extends Container). Null if none yet. */
    public static net.minecraft.world.Container playerInventory(Object be, UUID player) {
        if (!find() || !instanceType.isInstance(be)) return null;
        try {
            Object store = getData.invoke(api, be);
            if (store == null) return null;
            Object inv = store.getClass().getMethod("getInventory", UUID.class).invoke(store, player);
            return inv instanceof net.minecraft.world.Container c ? c : null;
        } catch (Exception e) {
            if (!warned) { warned = true; dev.justin.infinium.Infinium.LOG.warn("infinium: Lootr inventory lookup failed: {}", e.toString()); }
            return null;
        }
    }

    public static void markChanged(Object be) {
        if (!isLootr(be) || !find() || !instanceType.isInstance(be)) return;
        try { Object store = getData.invoke(api, be); if (store != null) store.getClass().getMethod("markChanged").invoke(store); } catch (Exception ignored) {}
    }

    private static boolean find() {
        if (looked) return getData != null;
        looked = true;
        if (!loaded()) return false;
        try {
            Class<?> apiC = Class.forName("noobanidus.mods.lootr.common.api.LootrAPI");
            api = apiC.getField("INSTANCE").get(null);
            instanceType = Class.forName("noobanidus.mods.lootr.common.api.data.ILootrContainerInstance");
            Class<?> iface = Class.forName("noobanidus.mods.lootr.common.api.interfaces.lootr.ILootrAPI");
            getData = iface.getMethod("getData", instanceType);
        } catch (Exception e) {
            getData = null;
            dev.justin.infinium.Infinium.LOG.warn("infinium: Lootr API not found ({}); per-player Lootr chests will not be cleared on dungeon loot refresh", e.toString());
        }
        return getData != null;
    }

    private LootrBridge() {}
}
