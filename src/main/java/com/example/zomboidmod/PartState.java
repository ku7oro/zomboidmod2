package com.example.zomboidmod;

import net.minecraft.nbt.NbtCompound;

/** Состояние одной части тела. Все «left» - в игровых часах PZ. */
public class PartState {
    public int type;                 // порядковый номер WoundType
    public boolean bandaged, dirty, disinfected, stitched, infected;
    public boolean fracture, splinted;
    public float healLeft;           // часов до заживления раны
    public float fractureLeft;       // часов до сращения перелома
    public float bandageLeft;        // часов до того, как бинт станет грязным

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
        healLeft = 0;
        bandageLeft = 0;
    }

    /** Кровит, пока не перевязана (глубокая рана - пока не зашита, под бинтом кровит слабее). */
    public boolean isBleeding() {
        WoundType t = wound();
        if (!t.bleeds || stitched) return false;
        if (bandaged && t != WoundType.DEEP) return false;
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
        n.putFloat("healLeft", healLeft);
        n.putFloat("fractureLeft", fractureLeft);
        n.putFloat("bandageLeft", bandageLeft);
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
        s.healLeft = n.getFloat("healLeft");
        s.fractureLeft = n.getFloat("fractureLeft");
        s.bandageLeft = n.getFloat("bandageLeft");
        return s;
    }
}
