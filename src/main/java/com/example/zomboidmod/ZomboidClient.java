package com.example.zomboidmod;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

/** Шкала жажды из капель, куколка с ранами и питьё из водоёма по Shift + ПКМ. */
public class ZomboidClient implements ClientModInitializer {
    private static final Identifier FULL = Identifier.of(ZomboidMod.MOD_ID, "textures/gui/drop_full.png");
    private static final Identifier HALF = Identifier.of(ZomboidMod.MOD_ID, "textures/gui/drop_half.png");
    private static final Identifier EMPTY = Identifier.of(ZomboidMod.MOD_ID, "textures/gui/drop_empty.png");

    private static int thirst = 100;
    private static int[] codes = new int[6];
    private static boolean virus = false;
    private static boolean painkiller = false;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(ThirstPayload.ID,
                (payload, context) -> thirst = payload.thirst());
        ClientPlayNetworking.registerGlobalReceiver(WoundsPayload.ID,
                (payload, context) -> parseWounds(payload.data()));
        HudRenderCallback.EVENT.register(ZomboidClient::render);
        ClientTickEvents.END_CLIENT_TICK.register(ZomboidClient::tickDrink);
    }

    private static void parseWounds(String data) {
        try {
            String[] a = data.split(",");
            int[] c = new int[6];
            for (int i = 0; i < 6; i++) c[i] = Integer.parseInt(a[i].trim());
            boolean v = a[6].trim().equals("1");
            boolean pk = a[7].trim().equals("1");
            codes = c;
            virus = v;
            painkiller = pk;
        } catch (Exception ignored) {
        }
    }

    /** Shift + зажатая ПКМ с пустой рукой, глядя на воду: просим сервер дать попить. */
    private static void tickDrink(MinecraftClient c) {
        if (c.player == null || c.world == null || c.currentScreen != null) return;
        if (c.player.isCreative() || c.player.isSpectator()) return;
        if (!c.options.useKey.isPressed() || !c.player.isSneaking()) return;
        if (!c.player.getMainHandStack().isEmpty()) return;

        HitResult hit = c.player.raycast(4.0, 1.0f, true);
        if (hit.getType() == HitResult.Type.BLOCK
                && c.world.getFluidState(((BlockHitResult) hit).getBlockPos()).isIn(FluidTags.WATER)) {
            ClientPlayNetworking.send(new DrinkPayload());
        }
    }

    private static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null || c.options.hudHidden) return;
        if (c.player.isCreative() || c.player.isSpectator()) return;

        renderThirst(ctx, c);
        renderWounds(ctx, c);
    }

    // ===== Жажда =====
    private static void renderThirst(DrawContext ctx, MinecraftClient c) {
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        int y = h - 49;                                        // строка над шкалой голода
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

    // ===== Куколка с ранами (слева сверху) =====
    private static void renderWounds(DrawContext ctx, MinecraftClient c) {
        boolean any = virus || painkiller;
        for (int code : codes) {
            if ((code & 15) != 0 || (code & (1 << 8)) != 0) any = true;
        }
        if (!any) return;

        int x = 8, y = 8;
        // Куколка стоит лицом к нам: левая рука персонажа справа на экране
        drawPart(ctx, codes[0], x + 8, y, x + 16, y + 8);            // голова
        drawPart(ctx, codes[1], x + 6, y + 9, x + 18, y + 25);       // торс
        drawPart(ctx, codes[3], x, y + 9, x + 5, y + 23);            // правая рука
        drawPart(ctx, codes[2], x + 19, y + 9, x + 24, y + 23);      // левая рука
        drawPart(ctx, codes[5], x + 6, y + 26, x + 11, y + 40);      // правая нога
        drawPart(ctx, codes[4], x + 13, y + 26, x + 18, y + 40);     // левая нога

        // Подписи рядом
        int ty = y;
        for (int i = 0; i < 6; i++) {
            int code = codes[i];
            int type = code & 15;
            boolean fracture = (code & (1 << 8)) != 0;
            if (type == 0 && !fracture) continue;

            StringBuilder sb = new StringBuilder(BodyPart.values()[i].title).append(": ");
            if (type != 0) {
                sb.append(WoundType.values()[type].title);
                if ((code & (1 << 11)) != 0) sb.append(", кровь");
                if ((code & (1 << 4)) != 0) sb.append((code & (1 << 5)) != 0 ? ", грязный бинт" : ", бинт");
                if ((code & (1 << 6)) != 0) sb.append(", швы");
                if ((code & (1 << 7)) != 0) sb.append(", инфекция");
            }
            if (fracture) sb.append(type != 0 ? "; " : "").append((code & (1 << 9)) != 0 ? "перелом (шина)" : "перелом");
            ctx.drawTextWithShadow(c.textRenderer, sb.toString(), x + 30, ty, 0xFFFFFF);
            ty += 10;
        }
        if (virus) {
            ctx.drawTextWithShadow(c.textRenderer, "Заражение (укус)!", x + 30, ty, 0xCC55FF);
            ty += 10;
        }
        if (painkiller) {
            ctx.drawTextWithShadow(c.textRenderer, "Обезболивающее действует", x + 30, ty, 0x55AAFF);
        }
    }

    private static void drawPart(DrawContext ctx, int code, int x1, int y1, int x2, int y2) {
        int type = code & 15;
        int color = 0xFF3C8C50; // здорова
        switch (type) {
            case 1 -> color = 0xFFE6D83C; // царапина
            case 2 -> color = 0xFFF0A030; // порез
            case 3 -> color = 0xFFE03030; // глубокая рана
            case 4 -> color = 0xFFA01818; // укус
            case 5 -> color = 0xFFE07830; // ожог
            default -> { }
        }
        if ((code & (1 << 8)) != 0) color = 0xFFB060E0; // перелом
        ctx.fill(x1, y1, x2, y2, color);

        if ((code & (1 << 4)) != 0) { // бинт: белая рамка (грязный - коричневая)
            int bc = (code & (1 << 5)) != 0 ? 0xFFB08040 : 0xFFFFFFFF;
            ctx.fill(x1, y1, x2, y1 + 1, bc);
            ctx.fill(x1, y2 - 1, x2, y2, bc);
            ctx.fill(x1, y1, x1 + 1, y2, bc);
            ctx.fill(x2 - 1, y1, x2, y2, bc);
        }
        if ((code & (1 << 11)) != 0) ctx.fill(x2 - 3, y1 + 1, x2 - 1, y1 + 3, 0xFFFF2020); // кровь
        if ((code & (1 << 7)) != 0) ctx.fill(x1 + 1, y1 + 1, x1 + 3, y1 + 3, 0xFFA0FF30);  // инфекция
    }
}
