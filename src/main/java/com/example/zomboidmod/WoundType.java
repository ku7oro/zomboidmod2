package com.example.zomboidmod;

/** Тип раны. rank - тяжесть (она же боль), bleedSeconds: -1 кровит пока не вылечат, 0 не кровит. */
public enum WoundType {
    NONE("", 0, 0, 0),
    SCRATCH("царапина", 1, 25, 240),
    CUT("порез", 2, 120, 600),
    DEEP("глубокая рана", 3, -1, 1200),
    BITE("укус", 4, -1, 1500),
    BURN("ожог", 2, 0, 900);

    public final String title;
    public final int rank;
    public final int bleedSeconds;
    public final int healSeconds;

    WoundType(String title, int rank, int bleedSeconds, int healSeconds) {
        this.title = title;
        this.rank = rank;
        this.bleedSeconds = bleedSeconds;
        this.healSeconds = healSeconds;
    }
}
