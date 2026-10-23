package com.ecoserv.client.ui;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Всплывающая панель поверх окна: подтверждение, выпадающий список, выбор звука/предмета. */
@SideOnly(Side.CLIENT)
abstract class Overlay {

    abstract void draw(GuiWindow g, int mx, int my, float partialTicks);

    /** true — нажатие обработано; false — нажали мимо, панель закрывается. */
    abstract boolean click(GuiWindow g, int mx, int my, int button);

    void key(GuiWindow g, char c, int code) {
    }

    void wheel(int dir) {
    }

    void tick() {
    }
}
