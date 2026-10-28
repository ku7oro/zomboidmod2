package com.example.zomboidmod;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;

/** Шкала жажды из капель, как шкала голода (над ней). */
public class ZomboidClient implements ClientModInitializer {
    private static final Identifier FULL = Identifier.of(ZomboidMod.MOD_ID, "textures/gui/drop_full.png");
    private static final Identifier HALF = Identifier.of(ZomboidMod.MOD_ID, "textures/gui/drop_half.png");
    private static final Identifier EMPTY = Identifier.of(ZomboidMod.MOD_ID, "textures/gui/drop_empty.png");

    private static int thirst = 100;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(ThirstPayload.ID,
                (payload, context) -> thirst = payload.thirst());
        HudRenderCallback.EVENT.register(ZomboidClient::render);
    }

    private static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null || c.options.hudHidden) return;
        if (c.player.isCreative() || c.player.isSpectator()) return;

        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        int y = h - 49;                                   // строка над шкалой голода
        if (c.player.getAir() < c.player.getMaxAir()) y -= 10; // под водой пузырьки занимают эту строку

        RenderSystem.enableBlend();
        for (int j = 0; j < 10; j++) {
            int x = w / 2 + 10 + j * 8;
            int full = (10 - j) * 10; // левые капли пустеют первыми
            Identifier tex = thirst >= full ? FULL : (thirst >= full - 5 ? HALF : EMPTY);
            ctx.drawTexture(tex, x, y, 0f, 0f, 9, 9, 9, 9);
        }
        RenderSystem.disableBlend();
    }
}
