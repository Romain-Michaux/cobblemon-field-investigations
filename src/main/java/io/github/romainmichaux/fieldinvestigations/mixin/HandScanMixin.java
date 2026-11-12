package io.github.romainmichaux.fieldinvestigations.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.romainmichaux.fieldinvestigations.client.FieldInvestigationsClient;
import net.minecraft.client.renderer.ItemInHandRenderer;

// While scanning, you look through the kit: the hand and held item are not drawn (like a spyglass).
@Mixin(ItemInHandRenderer.class)
public class HandScanMixin {
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void fieldinvestigations$hideHand(CallbackInfo ci) {
        if (FieldInvestigationsClient.scanActive()) ci.cancel();
    }
}
