package dev.justin.infinium.compat.mixin.tb;
import dev.justin.infinium.compat.tb.*;

import com.tiviacz.travelersbackpack.inventory.BackpackWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The backpack storage handler is BackpackWrapper's first anonymous ItemStackHandler. */
@Mixin(targets = "com.tiviacz.travelersbackpack.inventory.BackpackWrapper$1", remap = false)
public interface StorageOwnerAccessor {
    @Accessor("this$0") BackpackWrapper infinium$wrapper();
}
