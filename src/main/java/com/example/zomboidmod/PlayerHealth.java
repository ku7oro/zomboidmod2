package com.example.zomboidmod;

import net.minecraft.nbt.NbtCompound;

public class PlayerHealth {
    public final PartState[] parts = new PartState[BodyPart.values().length];
    public boolean zombieInfected = false; // вирус Нокс (смертелен)
    public float zombieHours = 0;          // игровых часов с момента заражения
    public float zombieDeathAt = 72;       // через сколько часов наступит смерть
    public float painLeft = 0;             // часов действия обезболивающего
    public float antibioticLeft = 0;       // часов действия антибиотиков

    // не сохраняются
    public int capPercent = 100;           // максимум здоровья из-за травм, %
    public int painValue = 0;              // боль 0-100
    public int bleedLevel = 0;             // 0 нет, 1 слабое, 2 кровотечение, 3 обильная кровопотеря

    public PlayerHealth() {
        for (int i = 0; i < parts.length; i++) parts[i] = new PartState();
    }

    public NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.putBoolean("zi", zombieInfected);
        n.putFloat("zh", zombieHours);
        n.putFloat("zd", zombieDeathAt);
        n.putFloat("pain", painLeft);
        n.putFloat("abx", antibioticLeft);
        NbtCompound pp = new NbtCompound();
        for (int i = 0; i < parts.length; i++) pp.put("p" + i, parts[i].toNbt());
        n.put("parts", pp);
        return n;
    }

    public static PlayerHealth fromNbt(NbtCompound n) {
        PlayerHealth h = new PlayerHealth();
        h.zombieInfected = n.getBoolean("zi");
        h.zombieHours = n.getFloat("zh");
        h.zombieDeathAt = n.getFloat("zd") > 0 ? n.getFloat("zd") : 72f;
        h.painLeft = n.getFloat("pain");
        h.antibioticLeft = n.getFloat("abx");
        NbtCompound pp = n.getCompound("parts");
        for (int i = 0; i < h.parts.length; i++) h.parts[i] = PartState.fromNbt(pp.getCompound("p" + i));
        return h;
    }
}
