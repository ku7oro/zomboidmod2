package com.example.zomboidmod;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Панель здоровья как в Project Zomboid: куколка из 17 частей, ЛКМ/ПКМ по части тела открывает меню лечения. */
public class HealthScreen extends Screen {
    // Прямоугольники частей тела (x1, y1, x2, y2) в порядке BodyPart. Куколка стоит лицом к нам.
    private static final int[][] RECTS = {
            {22, 0, 34, 11},     // HEAD
            {25, 12, 31, 16},    // NECK
            {16, 17, 40, 35},    // UPPER_TORSO
            {17, 36, 39, 50},    // LOWER_TORSO
            {21, 51, 35, 60},    // GROIN
            {41, 17, 47, 34},    // L_UPPER_ARM
            {9, 17, 15, 34},     // R_UPPER_ARM
            {42, 35, 48, 51},    // L_FOREARM
            {8, 35, 14, 51},     // R_FOREARM
            {42, 52, 49, 60},    // L_HAND
            {7, 52, 14, 60},     // R_HAND
            {29, 61, 38, 82},    // L_THIGH
            {18, 61, 27, 82},    // R_THIGH
            {30, 83, 38, 102},   // L_SHIN
            {18, 83, 26, 102},   // R_SHIN
            {29, 103, 39, 110},  // L_FOOT
            {17, 103, 27, 110},  // R_FOOT
    };

    private record MenuEntry(String label, int part, int action, boolean enabled, int x, int y, int w) {}

    private int selected = -1;
    private final List<MenuEntry> menu = new ArrayList<>();
    private int menuX, menuY, menuW, menuH;
    // кнопки общих действий
    private int btnX, btnW, painY, abY;

    public HealthScreen() {
        super(Text.literal("Здоровье"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ----- статус здоровья по таблице из Project Zomboid -----
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

    public static int colorOf(float pct) {
        if (pct >= 70f) return 0x55FF55;
        if (pct >= 40f) return 0xFFFF55;
        if (pct >= 20f) return 0xFFAA33;
        return 0xFF5555;
    }

    private int scale() {
        return Math.max(1, Math.min(2, (this.height - 60) / 115));
    }

    private int originX() {
        return this.width / 2 - 140;
    }

    private int originY(int sc) {
        return (this.height - 112 * sc) / 2 + 10;
    }

    private int partAt(double mx, double my) {
        int sc = scale(), ox = originX(), oy = originY(sc);
        for (int i = 0; i < RECTS.length; i++) {
            int[] r = RECTS[i];
            if (mx >= ox + r[0] * sc && mx < ox + (r[2] + 1) * sc
                    && my >= oy + r[1] * sc && my < oy + (r[3] + 1) * sc) return i;
        }
        return -1;
    }

    private static int partColor(int code) {
        int color = 0xFF3C8C50;
        switch (code & 15) {
            case 1 -> color = 0xFFE6D83C;
            case 2 -> color = 0xFFF0A030;
            case 3 -> color = 0xFFE03030;
            case 4 -> color = 0xFFA01818;
            case 5 -> color = 0xFFE07830;
            default -> { }
        }
        if ((code & (1 << 8)) != 0) color = 0xFFB060E0;
        return color;
    }

    private static int count(Item item) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null) return 0;
        if (c.player.isCreative()) return 99;
        return c.player.getInventory().count(item);
    }

    private static String describe(int idx, int code) {
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

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null) return;

        int sc = scale(), ox = originX(), oy = originY(sc);
        int[] codes = ZomboidClient.codes();
        int infoX = ox + 60 * sc + 14;

        float pct = c.player.getHealth() / c.player.getMaxHealth() * 100f;
        ctx.drawTextWithShadow(this.textRenderer, "Здоровье", ox, oy - 16, 0xFFFFFF);
        ctx.drawTextWithShadow(this.textRenderer,
                "Общее состояние: " + statusOf(pct, ZomboidClient.capPercent()) + " (" + Math.round(pct) + "%)",
                infoX, oy, colorOf(pct));
        ctx.drawTextWithShadow(this.textRenderer,
                "Максимум из-за травм: " + ZomboidClient.capPercent() + "%", infoX, oy + 11, 0xAAAAAA);

        // куколка
        int hover = partAt(mx, my);
        for (int i = 0; i < RECTS.length; i++) {
            int[] r = RECTS[i];
            int x1 = ox + r[0] * sc, y1 = oy + r[1] * sc, x2 = ox + (r[2] + 1) * sc, y2 = oy + (r[3] + 1) * sc;
            int border = (i == hover || i == selected) ? 0xFFFFFFFF : 0xFF101010;
            ctx.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, border);
            ctx.fill(x1, y1, x2, y2, partColor(codes[i]));
            int code = codes[i];
            if ((code & (1 << 4)) != 0) { // бинт
                int bc = (code & (1 << 5)) != 0 ? 0xFFB08040 : 0xFFFFFFFF;
                ctx.fill(x1 + sc, y1 + sc, x2 - sc, y1 + sc * 2, bc);
            }
            if ((code & (1 << 11)) != 0) ctx.fill(x2 - 3 * sc, y1 + sc, x2 - sc, y1 + 3 * sc, 0xFFFF2020);
            if ((code & (1 << 7)) != 0) ctx.fill(x1 + sc, y1 + sc, x1 + 3 * sc, y1 + 3 * sc, 0xFFA0FF30);
        }

        // сведения о части тела под курсором (или выбранной)
        int idx = hover >= 0 ? hover : selected;
        int ty = oy + 28;
        if (idx >= 0) {
            ctx.drawTextWithShadow(this.textRenderer, BodyPart.values()[idx].title, infoX, ty, 0xFFFFFF);
            ctx.drawTextWithShadow(this.textRenderer, describe(idx, codes[idx]), infoX, ty + 11, 0xCCCCCC);
        } else {
            ctx.drawTextWithShadow(this.textRenderer, "Нажми на часть тела для лечения", infoX, ty, 0x888888);
        }

        // общие статусы и действия
        int by = oy + 62;
        if (ZomboidClient.virus()) {
            ctx.drawTextWithShadow(this.textRenderer, "Заражение вирусом: да (неизлечимо)", infoX, by, 0xCC55FF);
            by += 11;
        }
        if (ZomboidClient.painkiller()) {
            ctx.drawTextWithShadow(this.textRenderer, "Обезболивающее действует", infoX, by, 0x55AAFF);
            by += 11;
        }
        by += 6;
        btnX = infoX;
        btnW = 150;
        painY = by;
        abY = by + 14;
        drawButton(ctx, mx, my, btnX, painY, btnW,
                "Принять обезболивающее (" + count(ZomboidMod.PAINKILLERS) + ")",
                count(ZomboidMod.PAINKILLERS) > 0);
        drawButton(ctx, mx, my, btnX, abY, btnW,
                "Принять антибиотики (" + count(ZomboidMod.ANTIBIOTICS) + ")",
                count(ZomboidMod.ANTIBIOTICS) > 0);

        // меню лечения
        if (!menu.isEmpty()) {
            ctx.fill(menuX - 1, menuY - 1, menuX + menuW + 1, menuY + menuH + 1, 0xFFFFFFFF);
            ctx.fill(menuX, menuY, menuX + menuW, menuY + menuH, 0xFF202020);
            for (MenuEntry e : menu) {
                boolean over = mx >= e.x() && mx < e.x() + e.w() && my >= e.y() && my < e.y() + 12;
                if (over && e.enabled()) ctx.fill(e.x(), e.y(), e.x() + e.w(), e.y() + 12, 0xFF404040);
                ctx.drawTextWithShadow(this.textRenderer, e.label(), e.x() + 3, e.y() + 2,
                        e.enabled() ? 0xFFFFFF : 0x777777);
            }
        }
    }

    private void drawButton(DrawContext ctx, int mx, int my, int x, int y, int w, String label, boolean enabled) {
        boolean over = mx >= x && mx < x + w && my >= y && my < y + 12;
        ctx.fill(x, y, x + w, y + 12, over && enabled ? 0xFF404040 : 0xFF202020);
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
        if (fracture && !splinted) {
            int n = count(ZomboidMod.SPLINT);
            labels.add("Наложить шину (" + n + ")");
            actions.add(MedItem.Kind.SPLINT.ordinal());
            enabled.add(n > 0);
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
