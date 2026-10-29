package com.example.zomboidmod.mixin;

import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Прячет ванильные сердца: здоровье теперь показывается в процентах и на панели здоровья. */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Inject(method = "renderHealthBar", at = @At("HEAD"), cancellable = true)
    private void zomboid$hideHearts(CallbackInfo ci) {
        ci.cancel();
    }
}
