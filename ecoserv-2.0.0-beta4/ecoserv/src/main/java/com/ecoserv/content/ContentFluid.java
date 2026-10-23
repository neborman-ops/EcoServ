package com.ecoserv.content;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

/** Слот своей жидкости: текстура воды/лавы, окрашенная в цвет из набора. */
public class ContentFluid extends Fluid {

    public final int slot;

    public ContentFluid(int slot) {
        super("ecoserv_fluid_" + slot,
                new ResourceLocation(slot < ContentPack.WATER_FLUIDS ? "minecraft:blocks/water_still" : "minecraft:blocks/lava_still"),
                new ResourceLocation(slot < ContentPack.WATER_FLUIDS ? "minecraft:blocks/water_flow" : "minecraft:blocks/lava_flow"));
        this.slot = slot;
        if (slot >= ContentPack.WATER_FLUIDS) {
            setTemperature(1300);
            setViscosity(6000);
            setDensity(3000);
            setLuminosity(15);
        }
    }

    @Override
    public int getColor() {
        ContentPack.FluidDef d = CustomContent.fluidDef(slot);
        if (d == null) {
            return 0xFFFFFFFF;
        }
        return 0xFF000000 | d.color;
    }

    @Override
    public String getLocalizedName(FluidStack stack) {
        ContentPack.FluidDef d = CustomContent.fluidDef(slot);
        return d == null ? "Жидкость " + slot : com.ecoserv.util.ChatUtil.color(d.name);
    }
}
