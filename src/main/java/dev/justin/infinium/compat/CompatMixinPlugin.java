package dev.justin.infinium.compat;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Soft compat: each mixin group applies only when its mod is installed (tb, spatial-gui and jei; the last two client only). */
public final class CompatMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String pkg) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        FabricLoader fl = FabricLoader.getInstance();
        if (mixin.contains(".tb.")) return fl.isModLoaded("travelersbackpack");
        boolean client = fl.getEnvironmentType() == net.fabricmc.api.EnvType.CLIENT;
        if (mixin.contains(".spatial.")) return client && fl.isModLoaded("spatial-gui");   // R5b overscan
        if (mixin.contains(".jei.")) return client && fl.isModLoaded("jei");               // R5b effect column
        if (mixin.contains(".punchy.")) return client && fl.isModLoaded("punchy");
        return true;
    }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String t, ClassNode c, String m, IMixinInfo i) {}
    @Override public void postApply(String t, ClassNode c, String m, IMixinInfo i) {}
}
