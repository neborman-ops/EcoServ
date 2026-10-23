package com.ecoserv.content;

import com.ecoserv.Ref;
import net.minecraft.block.material.Material;
import net.minecraftforge.fluids.BlockFluidClassic;
import net.minecraftforge.fluids.Fluid;

/** Блок своей жидкости. */
public class ContentFluidBlock extends BlockFluidClassic {

    public final int slot;

    public ContentFluidBlock(Fluid fluid, int slot) {
        super(fluid, slot < ContentPack.WATER_FLUIDS ? Material.WATER : Material.LAVA);
        this.slot = slot;
        setRegistryName(Ref.MODID, "cfluid_" + slot);
        setTranslationKey(Ref.MODID + ".cfluid");
    }
}
