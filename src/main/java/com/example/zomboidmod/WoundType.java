package com.example.zomboidmod;

import net.minecraft.util.math.random.Random;

/**
 * Тип раны. Часы заживления (minHours-maxHours) взяты с вики Project Zomboid (в игровых часах PZ),
 * боль и штраф к максимуму здоровья - оценки.
 */
public enum WoundType {
    NONE("", 0, 0, 0, 0, 0, false),
    SCRATCH("царапина", 1, 7, 15, 10, 3, true),
    CUT("рваная рана", 2, 10, 20, 15, 6, true),
    DEEP("глубокая рана", 3, 15, 20, 25, 12, true),
    BITE("укус", 4, 50, 80, 20, 10, true),
    BURN("ожог", 3, 50, 100, 35, 15, false);

    public final String title;
    public final int rank;
    public final int minHours;
    public final int maxHours;
    public final int pain;        // боль от раны (0-100)
    public final int capPenalty;  // насколько рана ограничивает максимум здоровья, %
    public final boolean bleeds;

    WoundType(String title, int rank, int minHours, int maxHours, int pain, int capPenalty, boolean bleeds) {
        this.title = title;
        this.rank = rank;
        this.minHours = minHours;
        this.maxHours = maxHours;
        this.pain = pain;
        this.capPenalty = capPenalty;
        this.bleeds = bleeds;
    }

    public float rollHours(Random r) {
        return minHours + r.nextFloat() * (maxHours - minHours);
    }

    /** Потеря здоровья от кровотечения, % максимума в секунду. */
    public double bleedPerSecond() {
        return switch (this) {
            case SCRATCH -> 0.04;
            case CUT -> 0.08;
            case DEEP -> 0.12;
            case BITE -> 0.08;
            default -> 0.0;
        };
    }
}
