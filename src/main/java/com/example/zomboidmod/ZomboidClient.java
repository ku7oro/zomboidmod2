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

/** Жажда из капель, краткая сводка здоровья и клавиша панели здоровья (H). */
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
            codes = c;
            virus = v;
            painkiller = pk;
            capPercent = cap;
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

    // ===== Краткая сводка здоровья вместо сердец (слева сверху) =====
    private static void renderOverview(DrawContext ctx, MinecraftClient c) {
        float pct = c.player.getHealth() / c.player.getMaxHealth() * 100f;
        int x = 8, y = 8;
        ctx.drawTextWithShadow(c.textRenderer,
                "Здоровье: " + Math.round(pct) + "% (" + HealthScreen.statusOf(pct, capPercent) + ")",
                x, y, HealthScreen.colorOf(pct));
        int ty = y + 11;

        boolean bleeding = false, infected = false;
        for (int code : codes) {
            if ((code & (1 << 11)) != 0) bleeding = true;
            if ((code & (1 << 7)) != 0) infected = true;
        }
        if (bleeding) {
            ctx.drawTextWithShadow(c.textRenderer, "Кровотечение!", x, ty, 0xFF5555);
            ty += 10;
        }
        if (infected) {
            ctx.drawTextWithShadow(c.textRenderer, "Инфекция в ране", x, ty, 0xA0FF30);
            ty += 10;
        }
        if (virus) {
            ctx.drawTextWithShadow(c.textRenderer, "Заражение (укус)!", x, ty, 0xCC55FF);
            ty += 10;
        }
        if (painkiller) {
            ctx.drawTextWithShadow(c.textRenderer, "Обезболивающее действует", x, ty, 0x55AAFF);
            ty += 10;
        }
        ctx.drawTextWithShadow(c.textRenderer,
                "[" + healthKey.getBoundKeyLocalizedText().getString() + "] панель здоровья", x, ty, 0x888888);
    }
}
