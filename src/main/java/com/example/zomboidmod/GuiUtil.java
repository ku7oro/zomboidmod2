package com.example.zomboidmod;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** Общие помощники для рисования интерфейса здоровья. */
public final class GuiUtil {
    public static final Identifier HEART = Identifier.of(ZomboidMod.MOD_ID, "textures/gui/heart.png");

    private static final int PARTS = BodyPart.values().length;
    private static final Identifier[] BIG = new Identifier[PARTS];
    private static final Identifier[] MINI = new Identifier[PARTS];

    static {
        for (int i = 0; i < PARTS; i++) {
            String n = String.format("%02d", i);
            BIG[i] = Identifier.of(ZomboidMod.MOD_ID, "textures/gui/part_" + n + ".png");
            MINI[i] = Identifier.of(ZomboidMod.MOD_ID, "textures/gui/mini_part_" + n + ".png");
        }
    }

    private GuiUtil() {
    }

    public static Identifier part(int i, boolean mini) {
        return mini ? MINI[i] : BIG[i];
    }

    public static Identifier gui(String name) {
        return Identifier.of(ZomboidMod.MOD_ID, "textures/gui/" + name + ".png");
    }

    /** Рисует маску-текстуру, окрашенную в цвет argb (прозрачность берётся из argb). */
    public static void mask(DrawContext ctx, Identifier tex, int x, int y, int w, int h, int tw, int th, int argb) {
        RenderSystem.setShaderColor(((argb >> 16) & 255) / 255f, ((argb >> 8) & 255) / 255f,
                (argb & 255) / 255f, ((argb >>> 24) & 255) / 255f);
        ctx.drawTexture(tex, x, y, w, h, 0f, 0f, tw, th, tw, th);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /** Цвет части тела по состоянию (0 - здорова). */
    public static int woundColor(int code) {
        int color = 0;
        switch (code & 15) {
            case 1 -> color = 0xFFFFDC3C; // царапина
            case 2 -> color = 0xFFFF9628; // рваная рана
            case 3 -> color = 0xFFE62828; // глубокая рана
            case 4 -> color = 0xFFAA1414; // укус
            case 5 -> color = 0xFFDC6E28; // ожог
            default -> { }
        }
        if ((code & (1 << 8)) != 0) color = 0xFFB45AE6; // перелом
        return color;
    }

    /** Мелкий текст (в 0.75 раза меньше обычного). */
    public static void smallText(DrawContext ctx, TextRenderer tr, String text, int x, int y, int color) {
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.scale(0.75f, 0.75f, 1.0f);
        ctx.drawTextWithShadow(tr, text, Math.round(x / 0.75f), Math.round(y / 0.75f), color);
        m.pop();
    }
}
