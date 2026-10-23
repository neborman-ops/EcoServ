package com.ecoserv.client.v2;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** 2.0 beta4 на клиенте: свечение источников, блок-дисплеи, свои окна и дизайнер. */
@SideOnly(Side.CLIENT)
public final class Beta4Client {

    private Beta4Client() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.register(new LightGlow());
        DisplayRender.register();
        MinecraftForge.EVENT_BUS.register(new DisplayRender());
        GuiCustom.register();
        GuiDesigner.register();
    }

    public static void reset() {
        DisplayRender.reset();
    }
}
