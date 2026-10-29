package com.example.zomboidmod;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Медицинский предмет: бинт, антисептик, швы, шина, обезболивающее, антибиотики. */
public class MedItem extends Item {
    public enum Kind {
        BANDAGE(false), ANTISEPTIC(true), SUTURE(false), SPLINT(false), PAINKILLERS(true), ANTIBIOTICS(true);

        final boolean drinkSound;

        Kind(boolean drinkSound) {
            this.drinkSound = drinkSound;
        }
    }

    private final Kind kind;

    public MedItem(Settings settings, Kind kind) {
        super(settings);
        this.kind = kind;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.pass(stack);

        if (user instanceof ServerPlayerEntity p && Wounds.treat(p, kind)) {
            if (kind.drinkSound) {
                world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_GENERIC_DRINK,
                        SoundCategory.PLAYERS, 0.8f, 1.0f);
            }
            stack.decrementUnlessCreative(1, user);
            return TypedActionResult.success(stack);
        }
        return TypedActionResult.fail(stack);
    }
}
