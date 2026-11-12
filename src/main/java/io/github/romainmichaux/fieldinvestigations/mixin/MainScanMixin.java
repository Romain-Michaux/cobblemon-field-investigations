package io.github.romainmichaux.fieldinvestigations.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.romainmichaux.fieldinvestigations.client.FieldInvestigationsClient;
import net.minecraft.client.renderer.ItemInHandRenderer;

// Pendant le scan, on regarde à travers le kit : la main et l'objet tenu ne sont plus dessinés (comme une longue-vue).
@Mixin(ItemInHandRenderer.class)
public class MainScanMixin {
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void fieldinvestigations$hideHand(CallbackInfo ci) {
        if (FieldInvestigationsClient.scanActive()) ci.cancel();
    }
}
