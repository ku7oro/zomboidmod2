package com.example.zomboidmod;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Панель здоровья в стиле Project Zomboid: окно со вкладками, силуэт из 17 частей, шкала здоровья с сердцем. */
public class HealthScreen extends Screen {
    // Рамки частей тела (x1, y1, x2, y2) в координатах текстуры 64x128, в порядке BodyPart.
    private static final int[][] RECTS = {
            {23, 0, 40, 20},   // 0,
            {28, 20, 34, 25},   // 1,
            {17, 26, 46, 45},   // 2,
            {21, 46, 42, 61},   // 3,
            {22, 62, 41, 74},   // 4,
            {47, 27, 55, 47},   // 5,
            {8, 27, 16, 47},   // 6,
            {47, 48, 55, 67},   // 7,
            {8, 48, 16, 67},   // 8,
            {48, 68, 56, 79},   // 9,
            {7, 68, 15, 79},   // 10,
            {33, 71, 42, 97},   // 11,
            {21, 71, 30, 97},   // 12,
            {34, 98, 41, 117},   // 13,
            {22, 98, 29, 117},   // 14,
            {33, 118, 44, 126},   // 15,
            {19, 118, 30, 126},   // 16
    };

    private record MenuEntry(String label, int part, int action, boolean enabled, int x, int y, int w) {}

    private int selected = -1;
    private final List<MenuEntry> menu = new ArrayList<>();
    private int menuX, menuY, menuW, menuH;
    private int btnX, btnW, painY, abY;

    public HealthScreen() {
        super(Text.literal("Здоровье"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ----- статусы (таблица и моодлы из PZ) -----
    public static String statusOf(float pct, int cap) {
        if (pct <= 0f) return "Мёртвый";
        if (pct >= 99.9f && cap >= 100) return "Отличное";
        if (pct >= 90f) return "Очень незначительный урон";
        if (pct >= 80f) return "Незначительный урон";
        if (pct >= 70f) return "Лёгкий урон";
        if (pct >= 60f) return "Умеренный урон";
        if (pct >= 50f) return "Тяжёлый урон";
        if (pct >= 40f) return "Очень тяжёлый урон";
        if (pct >= 20f) return "Критический урон";
        if (pct >= 10f) return "Весьма критический урон";
        return "Смертельный урон";
    }

    /** Моодл "Травмирован": пороги 80 / 60 / 40 / 25. */
    public static String injuredName(float pct) {
        if (pct >= 80f) return "";
        if (pct >= 60f) return "Дискомфорт";
        if (pct >= 40f) return "Травмирован";
        if (pct >= 25f) return "Тяжёлые травмы";
        return "Критические травмы";
    }

    /** Моодл "Боль": пороги 10 / 20 / 50 / 75. */
    public static String painName(int pain) {
        if (pain >= 75) return "Агония";
        if (pain >= 50) return "Сильная боль";
        if (pain >= 20) return "Боль";
        if (pain >= 10) return "Слабая боль";
        return "";
    }

    public static String bleedName(int level) {
        return switch (level) {
            case 1 -> "Лёгкое кровотечение";
            case 2 -> "Кровотечение";
            case 3 -> "Обильная кровопотеря";
            default -> "";
        };
    }

    public static int colorOf(float pct) {
        if (pct >= 70f) return 0x55FF55;
        if (pct >= 40f) return 0xFFFF55;
        if (pct >= 20f) return 0xFFAA33;
        return 0xFF5555;
    }

    // ----- раскладка окна -----
    /** {sc, winW, winH, wx, wy, bx, by} */
    private int[] lay() {
        int sc = this.height >= 330 ? 2 : 1;
        int infoW = 230;
        int winW = 12 + 64 * sc + 8 + 6 + 14 + infoW + 12;
        int winH = Math.max(128 * sc + 52, 230);
        int wx = (this.width - winW) / 2;
        int wy = (this.height - winH) / 2;
        return new int[]{sc, winW, winH, wx, wy, wx + 12, wy + 26};
    }

    private int partAt(double mx, double my) {
        int[] L = lay();
        int sc = L[0], bx = L[5], by = L[6];
        for (int i = 0; i < RECTS.length; i++) {
            int[] r = RECTS[i];
            if (mx >= bx + r[0] * sc && mx < bx + (r[2] + 1) * sc
                    && my >= by + r[1] * sc && my < by + (r[3] + 1) * sc) return i;
        }
        return -1;
    }

    private static int count(Item item) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null) return 0;
        if (c.player.isCreative()) return 99;
        return c.player.getInventory().count(item);
    }

    private static String describe(int code) {
        List<String> parts = new ArrayList<>();
        int type = code & 15;
        if (type != 0) parts.add(WoundType.values()[type].title);
        if ((code & (1 << 11)) != 0) parts.add("кровотечение");
        if ((code & (1 << 4)) != 0) parts.add((code & (1 << 5)) != 0 ? "грязный бинт" : "бинт");
        if ((code & (1 << 10)) != 0) parts.add("обработана");
        if ((code & (1 << 6)) != 0) parts.add("швы");
        if ((code & (1 << 7)) != 0) parts.add("инфекция");
        if ((code & (1 << 8)) != 0) parts.add((code & (1 << 9)) != 0 ? "перелом (шина)" : "перелом");
        return parts.isEmpty() ? "здорова" : String.join(", ", parts);
    }

    private int drawTab(DrawContext ctx, int x, int y, String label, boolean active) {
        int w = this.textRenderer.getWidth(label) + 10;
        ctx.fill(x, y, x + w, y + 13, active ? 0xFF3C3C3C : 0xFF1B1B1B);
        ctx.drawTextWithShadow(this.textRenderer, label, x + 5, y + 3, active ? 0xFFFFFF : 0x777777);
        return x + w + 2;
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null) return;

        int[] L = lay();
        int sc = L[0], winW = L[1], winH = L[2], wx = L[3], wy = L[4], bx = L[5], by = L[6];
        int[] codes = ZomboidClient.codes();
        float pct = c.player.getHealth() / c.player.getMaxHealth() * 100f;

        // окно и заголовок
        ctx.fill(wx - 1, wy - 1, wx + winW + 1, wy + winH + 1, 0xFF6E6E6E);
        ctx.fill(wx, wy, wx + winW, wy + winH, 0xF0121212);
        ctx.fill(wx, wy, wx + winW, wy + 16, 0xFF262626);
        ctx.fill(wx + 3, wy + 3, wx + 13, wy + 13, 0xFF505050);
        ctx.drawTextWithShadow(this.textRenderer, "x", wx + 6, wy + 3, 0xFFFFFF);
        int tx = wx + 20;
        tx = drawTab(ctx, tx, wy + 3, "Инфо", false);
        tx = drawTab(ctx, tx, wy + 3, "Навыки", false);
        drawTab(ctx, tx, wy + 3, "Здоровье", true);

        // силуэт
        int bw = 64 * sc, bh = 128 * sc;
        ctx.fill(bx - 3, by - 3, bx + bw + 3, by + bh + 3, 0xFF2E2E2E);
        ctx.fill(bx - 2, by - 2, bx + bw + 2, by + bh + 2, 0xFF0A0A0A);
        int hover = partAt(mx, my);

        RenderSystem.enableBlend();
        GuiUtil.mask(ctx, GuiUtil.gui("body_fill"), bx, by, bw, bh, 64, 128, 0x66101010);
        for (int i = 0; i < codes.length; i++) {
            int color = GuiUtil.woundColor(codes[i]);
            if (color != 0) {
                GuiUtil.mask(ctx, GuiUtil.part(i, false), bx, by, bw, bh, 64, 128, (color & 0x00FFFFFF) | 0xC0000000);
            }
        }
        if (hover >= 0) GuiUtil.mask(ctx, GuiUtil.part(hover, false), bx, by, bw, bh, 64, 128, 0x40FFFFFF);
        if (selected >= 0) GuiUtil.mask(ctx, GuiUtil.part(selected, false), bx, by, bw, bh, 64, 128, 0x50FFFFFF);
        GuiUtil.mask(ctx, GuiUtil.gui("body_outline"), bx, by, bw, bh, 64, 128, 0xFFF0F0DC);
        RenderSystem.disableBlend();

        // значки состояния на частях тела
        for (int i = 0; i < codes.length; i++) {
            int code = codes[i];
            int[] r = RECTS[i];
            int cx = bx + (r[0] + r[2] + 1) * sc / 2;
            int cy = by + (r[1] + r[3] + 1) * sc / 2;
            if ((code & (1 << 11)) != 0) ctx.fill(cx - sc, cy - sc, cx + sc, cy + sc, 0xFFFF2020);
            if ((code & (1 << 4)) != 0) {
                int bc = (code & (1 << 5)) != 0 ? 0xFFB08040 : 0xFFFFFFFF;
                ctx.fill(cx - 2 * sc, cy + sc, cx + 2 * sc, cy + 2 * sc, bc);
            }
            if ((code & (1 << 7)) != 0) ctx.fill(cx + sc, cy - 3 * sc, cx + 3 * sc, cy - sc, 0xFFA0FF30);
            if ((code & (1 << 6)) != 0) ctx.fill(cx - 2 * sc, cy - 3 * sc, cx, cy - 2 * sc, 0xFFDDDDDD);
        }

        // вертикальная шкала здоровья с сердцем
        int barX = bx + bw + 10, barW = 6;
        ctx.fill(barX - 1, by - 1, barX + barW + 1, by + bh + 1, 0xFF404040);
        ctx.fill(barX, by, barX + barW, by + bh, 0xFF141414);
        int fh = Math.round(bh * Math.max(0f, Math.min(100f, pct)) / 100f);
        if (fh > 0) ctx.fillGradient(barX, by + bh - fh, barX + barW, by + bh, 0xFFFFFFFF, 0xFF7A7A7A);
        if (ZomboidClient.capPercent() < 100) {
            int capY = by + Math.round(bh * (1f - ZomboidClient.capPercent() / 100f));
            ctx.fill(barX - 2, capY, barX + barW + 2, capY + 1, 0xFFFF5555);
        }
        int hy = Math.max(by, Math.min(by + bh - 8, by + bh - fh - 4));
        RenderSystem.enableBlend();
        ctx.drawTexture(GuiUtil.HEART, barX + barW + 4, hy, 0f, 0f, 9, 8, 9, 8);
        RenderSystem.disableBlend();

        // текстовая часть
        int ix = barX + barW + 20;
        int ty = by - 2;
        ctx.drawTextWithShadow(this.textRenderer, "Нажмите на часть тела:", ix, ty, 0xAAAAAA);
        ctx.drawTextWithShadow(this.textRenderer, "откроется меню лечения", ix, ty + 10, 0xAAAAAA);
        ty += 26;
        ctx.drawTextWithShadow(this.textRenderer, "Общее состояние тела", ix, ty, 0xFFFFFF);
        ty += 11;
        ctx.drawTextWithShadow(this.textRenderer,
                statusOf(pct, ZomboidClient.capPercent()) + " (" + Math.round(pct) + "%)", ix, ty, colorOf(pct));
        ty += 11;
        if (ZomboidClient.capPercent() < 100) {
            ctx.drawTextWithShadow(this.textRenderer,
                    "Максимум из-за травм: " + ZomboidClient.capPercent() + "%", ix, ty, 0xAAAAAA);
            ty += 11;
        }
        String injured = injuredName(pct);
        if (!injured.isEmpty()) {
            ctx.drawTextWithShadow(this.textRenderer, injured, ix, ty, 0xFFAA33);
            ty += 11;
        }
        String painLine = painName(ZomboidClient.pain());
        if (!painLine.isEmpty()) {
            ctx.drawTextWithShadow(this.textRenderer, painLine + " (" + ZomboidClient.pain() + "/100)", ix, ty, 0xFF8888);
            ty += 11;
        }
        String bleedLine = bleedName(ZomboidClient.bleedLevel());
        if (!bleedLine.isEmpty()) {
            ctx.drawTextWithShadow(this.textRenderer, bleedLine, ix, ty, 0xFF5555);
            ty += 11;
        }
        if (ZomboidClient.virus()) {
            ctx.drawTextWithShadow(this.textRenderer, "Заражение вирусом (неизлечимо)", ix, ty, 0xCC55FF);
            ty += 11;
        }
        if (ZomboidClient.painkiller()) {
            ctx.drawTextWithShadow(this.textRenderer, "Обезболивающее действует", ix, ty, 0x55AAFF);
            ty += 11;
        }

        // выбранная или наведённая часть тела
        ty += 4;
        int idx = hover >= 0 ? hover : selected;
        if (idx >= 0) {
            ctx.drawTextWithShadow(this.textRenderer, BodyPart.values()[idx].title, ix, ty, 0xFFFFFF);
            ctx.drawTextWithShadow(this.textRenderer, describe(codes[idx]), ix, ty + 11, 0xCCCCCC);
        }

        // общие действия
        btnX = ix;
        btnW = 170;
        abY = wy + winH - 20;
        painY = abY - 14;
        drawButton(ctx, mx, my, btnX, painY, btnW,
                "Принять обезболивающее (" + count(ZomboidMod.PAINKILLERS) + ")",
                count(ZomboidMod.PAINKILLERS) > 0);
        drawButton(ctx, mx, my, btnX, abY, btnW,
                "Принять антибиотики (" + count(ZomboidMod.ANTIBIOTICS) + ")",
                count(ZomboidMod.ANTIBIOTICS) > 0);

        // меню лечения
        if (!menu.isEmpty()) {
            ctx.fill(menuX - 1, menuY - 1, menuX + menuW + 1, menuY + menuH + 1, 0xFF8A8A8A);
            ctx.fill(menuX, menuY, menuX + menuW, menuY + menuH, 0xFF1A1A1A);
            for (MenuEntry e : menu) {
                boolean over = mx >= e.x() && mx < e.x() + e.w() && my >= e.y() && my < e.y() + 12;
                if (over && e.enabled()) ctx.fill(e.x(), e.y(), e.x() + e.w(), e.y() + 12, 0xFF3C3C3C);
                ctx.drawTextWithShadow(this.textRenderer, e.label(), e.x() + 3, e.y() + 2,
                        e.enabled() ? 0xFFFFFF : 0x777777);
            }
        }
    }

    private void drawButton(DrawContext ctx, int mx, int my, int x, int y, int w, String label, boolean enabled) {
        boolean over = mx >= x && mx < x + w && my >= y && my < y + 12;
        ctx.fill(x, y, x + w, y + 12, over && enabled ? 0xFF3C3C3C : 0xFF1E1E1E);
        ctx.drawTextWithShadow(this.textRenderer, label, x + 3, y + 2, enabled ? 0xFFFFFF : 0x777777);
    }

    private void openMenu(int part, int mx, int my) {
        selected = part;
        menu.clear();
        int code = ZomboidClient.codes()[part];
        int type = code & 15;
        boolean bandaged = (code & (1 << 4)) != 0, dirty = (code & (1 << 5)) != 0;
        boolean stitched = (code & (1 << 6)) != 0, disinfected = (code & (1 << 10)) != 0;
        boolean fracture = (code & (1 << 8)) != 0, splinted = (code & (1 << 9)) != 0;

        List<String> labels = new ArrayList<>();
        List<Integer> actions = new ArrayList<>();
        List<Boolean> enabled = new ArrayList<>();

        if (type != 0 && (!bandaged || dirty)) {
            int n = count(ZomboidMod.BANDAGE);
            labels.add((dirty ? "Заменить бинт" : "Наложить бинт") + " (" + n + ")");
            actions.add(MedItem.Kind.BANDAGE.ordinal());
            enabled.add(n > 0);
        }
        if (type != 0 && !disinfected) {
            int n = count(ZomboidMod.ANTISEPTIC);
            labels.add("Продезинфицировать (" + n + ")");
            actions.add(MedItem.Kind.ANTISEPTIC.ordinal());
            enabled.add(n > 0);
        }
        if (type == WoundType.DEEP.ordinal() && !stitched) {
            int n = count(ZomboidMod.SUTURE_KIT);
            labels.add("Наложить швы (" + n + ")");
            actions.add(MedItem.Kind.SUTURE.ordinal());
            enabled.add(n > 0);
        }
        if (fracture && !splinted && BodyPart.values()[part].canSplint()) {
            int n = count(ZomboidMod.SPLINT);
            labels.add("Наложить шину (" + n + ")");
            actions.add(MedItem.Kind.SPLINT.ordinal());
            enabled.add(n > 0);
        }
        if (labels.isEmpty() && fracture && !BodyPart.values()[part].canSplint()) {
            labels.add("Перелом торса не зашинировать");
            actions.add(-1);
            enabled.add(false);
        }
        if (labels.isEmpty()) {
            labels.add("Лечить нечего");
            actions.add(-1);
            enabled.add(false);
        }

        int w = 0;
        for (String l : labels) w = Math.max(w, this.textRenderer.getWidth(l));
        w += 8;
        menuW = w;
        menuH = labels.size() * 12;
        menuX = Math.min(mx, this.width - menuW - 4);
        menuY = Math.min(my, this.height - menuH - 4);
        for (int i = 0; i < labels.size(); i++) {
            menu.add(new MenuEntry(labels.get(i), part, actions.get(i), enabled.get(i),
                    menuX, menuY + i * 12, menuW));
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int[] L = lay();
        // кнопка закрытия
        if (mx >= L[3] + 3 && mx < L[3] + 13 && my >= L[4] + 3 && my < L[4] + 13) {
            this.close();
            return true;
        }
        // пункты меню
        for (MenuEntry e : menu) {
            if (mx >= e.x() && mx < e.x() + e.w() && my >= e.y() && my < e.y() + 12) {
                if (e.enabled() && e.action() >= 0) {
                    ClientPlayNetworking.send(new TreatPayload(e.part(), e.action()));
                }
                menu.clear();
                return true;
            }
        }
        // общие действия
        if (mx >= btnX && mx < btnX + btnW) {
            if (my >= painY && my < painY + 12) {
                if (count(ZomboidMod.PAINKILLERS) > 0) {
                    ClientPlayNetworking.send(new TreatPayload(0, MedItem.Kind.PAINKILLERS.ordinal()));
                }
                return true;
            }
            if (my >= abY && my < abY + 12) {
                if (count(ZomboidMod.ANTIBIOTICS) > 0) {
                    ClientPlayNetworking.send(new TreatPayload(0, MedItem.Kind.ANTIBIOTICS.ordinal()));
                }
                return true;
            }
        }
        // часть тела
        int hit = partAt(mx, my);
        if (hit >= 0) {
            openMenu(hit, (int) mx, (int) my);
            return true;
        }
        menu.clear();
        selected = -1;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ZomboidClient.healthKey != null && ZomboidClient.healthKey.matchesKey(keyCode, scanCode)) {
            this.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
