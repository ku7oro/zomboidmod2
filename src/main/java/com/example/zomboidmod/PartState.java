package com.example.zomboidmod;

import net.minecraft.nbt.NbtCompound;

/** Состояние одной части тела. */
public class PartState {
    public int type;                 // порядковый номер WoundType
    public boolean bandaged, dirty, disinfected, stitched, infected;
    public boolean fracture, splinted;
    public int bandageAge;           // секунд с наложения бинта
    public int bleedLeft;            // -1 пока не вылечат, 0 не кровит
    public int heal;                 // секунд до заживления
    public int fractureHeal;         // секунд до сращения перелома (идёт только в шине)

    public WoundType wound() {
        return WoundType.values()[type];
    }

    public void clearWound() {
        type = 0;
        bandaged = false;
        dirty = false;
        disinfected = false;
        stitched = false;
        infected = false;
        bandageAge = 0;
        bleedLeft = 0;
        heal = 0;
    }

    public boolean isBleeding() {
        WoundType t = wound();
        if (t.bleedSeconds == 0 || bleedLeft == 0 || stitched) return false;
        if (bandaged && t != WoundType.DEEP) return false; // глубокая рана кровит под бинтом, пока не зашита
        return true;
    }

    public boolean hasAnything() {
        return type != 0 || fracture;
    }

    public NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.putInt("type", type);
        n.putBoolean("bandaged", bandaged);
        n.putBoolean("dirty", dirty);
        n.putBoolean("disinfected", disinfected);
        n.putBoolean("stitched", stitched);
        n.putBoolean("infected", infected);
        n.putBoolean("fracture", fracture);
        n.putBoolean("splinted", splinted);
        n.putInt("bandageAge", bandageAge);
        n.putInt("bleedLeft", bleedLeft);
        n.putInt("heal", heal);
        n.putInt("fractureHeal", fractureHeal);
        return n;
    }

    public static PartState fromNbt(NbtCompound n) {
        PartState s = new PartState();
        s.type = Math.max(0, Math.min(WoundType.values().length - 1, n.getInt("type")));
        s.bandaged = n.getBoolean("bandaged");
        s.dirty = n.getBoolean("dirty");
        s.disinfected = n.getBoolean("disinfected");
        s.stitched = n.getBoolean("stitched");
        s.infected = n.getBoolean("infected");
        s.fracture = n.getBoolean("fracture");
        s.splinted = n.getBoolean("splinted");
        s.bandageAge = n.getInt("bandageAge");
        s.bleedLeft = n.getInt("bleedLeft");
        s.heal = n.getInt("heal");
        s.fractureHeal = n.getInt("fractureHeal");
        return s;
    }
}
