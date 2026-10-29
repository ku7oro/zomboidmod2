package com.example.zomboidmod;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

/** Жажда из капель, сводка здоровья (статус, боль, кровотечение) и клавиша панели здоровья (H). */
public class ZomboidClient implements ClientModInitializer {
    private static final Identifier FULL = Identifier.of(ZomboidMod.MOD_ID, "textures/gui/drop_full.png");
    private static final Identifier HALF = Identifier.of(ZomboidMod.MOD_ID, "textures/gui/drop_half.png");
    private static final Identifier EMPTY = Identifier.of(ZomboidMod.MOD_ID, "textures/gui/drop_empty.png");

    public static KeyBinding healthKey;

    private static int thirst = 100;
    private static int[] codes = new int[BodyPart.values().length];
    private static boolean virus = false;
    private static boolean painkiller = false;
    private static int capPercent = 100;
    private static int pain = 0;
    private static int bleedLevel = 0;

    public static int[] codes() {
        return codes;
    }

    public static boolean virus() {
        return virus;
    }

    public static boolean painkiller() {
        return painkiller;
    }

    public static int capPercent() {
        return capPercent;
    }

    public static int pain() {
        return pain;
    }

    public static int bleedLevel() {
        return bleedLevel;
    }

    @Override
    public void onInitializeClient() {
        healthKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.zomboidmod.health", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, "category.zomboidmod"));

        ClientPlayNetworking.registerGlobalReceiver(ThirstPayload.ID,
                (payload, context) -> thirst = payload.thirst());
        ClientPlayNetworking.registerGlobalReceiver(WoundsPayload.ID,
                (payload, context) -> parseWounds(payload.data()));
        HudRenderCallback.EVENT.register(ZomboidClient::render);
        ClientTickEvents.END_CLIENT_TICK.register(ZomboidClient::tick);
    }

    private static void parseWounds(String data) {
        try {
            String[] a = data.split(",");
            int n = BodyPart.values().length;
            int[] c = new int[n];
            for (int i = 0; i < n; i++) c[i] = Integer.parseInt(a[i].trim());
            boolean v = a[n].trim().equals("1");
            boolean pk = a[n + 1].trim().equals("1");
            int cap = Integer.parseInt(a[n + 2].trim());
            int pn = Integer.parseInt(a[n + 3].trim());
            int bl = Integer.parseInt(a[n + 4].trim());
            codes = c;
            virus = v;
            painkiller = pk;
            capPercent = cap;
            pain = pn;
            bleedLevel = bl;
        } catch (Exception ignored) {
        }
    }

    private static void tick(MinecraftClient c) {
        if (c.player == null || c.world == null) return;

        // H открывает панель здоровья
        while (healthKey.wasPressed()) {
            if (c.currentScreen == null) c.setScreen(new HealthScreen());
        }

        // Shift + зажатая ПКМ с пустой рукой, глядя на воду: просим сервер дать попить
        if (c.currentScreen != null) return;
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
        renderOverview(ctx, c);
    }

    // ===== Жажда =====
    private static void renderThirst(DrawContext ctx, MinecraftClient c) {
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        int y = h - 49;
        if (c.player.getAir() < c.player.getMaxAir()) y -= 10;

        RenderSystem.enableBlend();
        for (int j = 0; j < 10; j++) {
            int x = w / 2 + 10 + j * 8;
            int full = (10 - j) * 10; // левые капли пустеют первыми
            Identifier tex = thirst >= full ? FULL : (thirst >= full - 5 ? HALF : EMPTY);
            ctx.drawTexture(tex, x, y, 0f, 0f, 9, 9, 9, 9);
        }
        RenderSystem.disableBlend();
    }

    // ===== Мини-куколка и сводка здоровья вместо сердец (слева сверху) =====
    private static void renderOverview(DrawContext ctx, MinecraftClient c) {
        float pct = c.player.getHealth() / c.player.getMaxHealth() * 100f;
        int x = 6, y = 6;
        int dollW = 28, dollH = 56;

        // подложка
        ctx.fill(x - 3, y - 3, x + dollW + 22, y + dollH + 3, 0x66000000);

        // куколка
        RenderSystem.enableBlend();
        GuiUtil.mask(ctx, GuiUtil.gui("mini_body_fill"), x, y, dollW, dollH, dollW, dollH, 0x88000000);
        for (int i = 0; i < codes.length; i++) {
            int color = GuiUtil.woundColor(codes[i]);
            if (color != 0) {
                GuiUtil.mask(ctx, GuiUtil.part(i, true), x, y, dollW, dollH, dollW, dollH,
                        (color & 0x00FFFFFF) | 0xDD000000);
            }
        }
        GuiUtil.mask(ctx, GuiUtil.gui("mini_body_outline"), x, y, dollW, dollH, dollW, dollH, 0xFFF0F0DC);

        // вертикальная шкала здоровья и сердечко
        int barX = x + dollW + 4;
        int fh = Math.round(dollH * Math.max(0f, Math.min(100f, pct)) / 100f);
        ctx.fill(barX - 1, y - 1, barX + 4, y + dollH + 1, 0xFF3A3A3A);
        ctx.fill(barX, y, barX + 3, y + dollH, 0xFF141414);
        if (fh > 0) ctx.fillGradient(barX, y + dollH - fh, barX + 3, y + dollH, 0xFFFFFFFF, 0xFF7A7A7A);
        int hy = Math.max(y, Math.min(y + dollH - 6, y + dollH - fh - 3));
        ctx.drawTexture(GuiUtil.HEART, barX + 6, hy, 7, 6, 0f, 0f, 9, 8, 9, 8);
        RenderSystem.disableBlend();

        // мелкие статы справа
        int tx = x + dollW + 26;
        int ty = y;
        GuiUtil.smallText(ctx, c.textRenderer,
                Math.round(pct) + "% " + HealthScreen.statusOf(pct, capPercent), tx, ty, HealthScreen.colorOf(pct));
        ty += 8;
        if (capPercent < 100) {
            GuiUtil.smallText(ctx, c.textRenderer, "Макс. " + capPercent + "%", tx, ty, 0xAAAAAA);
            ty += 8;
        }
        String injured = HealthScreen.injuredName(pct);
        if (!injured.isEmpty()) {
            GuiUtil.smallText(ctx, c.textRenderer, injured, tx, ty, 0xFFAA33);
            ty += 8;
        }
        String painName = HealthScreen.painName(pain);
        if (!painName.isEmpty()) {
            GuiUtil.smallText(ctx, c.textRenderer, painName, tx, ty, 0xFF8888);
            ty += 8;
        }
        String bleed = HealthScreen.bleedName(bleedLevel);
        if (!bleed.isEmpty()) {
            GuiUtil.smallText(ctx, c.textRenderer, bleed, tx, ty, 0xFF5555);
            ty += 8;
        }
        boolean infected = false;
        for (int code : codes) {
            if ((code & (1 << 7)) != 0) infected = true;
        }
        if (infected) {
            GuiUtil.smallText(ctx, c.textRenderer, "Инфекция в ране", tx, ty, 0xA0FF30);
            ty += 8;
        }
        if (virus) {
            GuiUtil.smallText(ctx, c.textRenderer, "Заражение (укус)", tx, ty, 0xCC55FF);
            ty += 8;
        }
        if (painkiller) {
            GuiUtil.smallText(ctx, c.textRenderer, "Обезболивающее", tx, ty, 0x55AAFF);
            ty += 8;
        }
        GuiUtil.smallText(ctx, c.textRenderer,
                "[" + healthKey.getBoundKeyLocalizedText().getString() + "] здоровье", tx, ty, 0x888888);
    }
}
