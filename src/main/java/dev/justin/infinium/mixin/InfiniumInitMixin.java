package dev.justin.infinium.mixin;

import dev.justin.infinium.InfiniumBook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Register in gen_data.py's common mixin list as "InfiniumInitMixin". */
@Mixin(dev.justin.infinium.Infinium.class)
public abstract class InfiniumInitMixin {
    @Inject(method = "onInitialize", at = @At("RETURN"), require = 1)
    private void infinium$book(CallbackInfo ci) {
        InfiniumBook.bootstrap();
    }
}
