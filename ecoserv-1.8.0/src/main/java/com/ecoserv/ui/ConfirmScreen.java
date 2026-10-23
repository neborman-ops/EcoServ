package com.ecoserv.ui;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

/** Окно «Да / Отмена» вместо ответа «да» в чат. */
public class ConfirmScreen extends UiScreen {

    private final String title;
    private final String text;
    private final String yes;
    private final ItemStack icon;
    private final Ui.Click action;

    public ConfirmScreen(EntityPlayerMP player, String title, String text, String yes, ItemStack icon, Ui.Click action) {
        super(player);
        this.title = title;
        this.text = text;
        this.yes = yes;
        this.icon = icon == null ? ItemStack.EMPTY : icon;
        this.action = action;
    }

    @Override
    protected void build(Ui ui) {
        ui.title(title).compact();
        if (!icon.isEmpty()) {
            ui.icon(icon);
        }
        ui.note(Ui.BLUE, text);
        ui.footer("Отмена", Ui.GREY, this::close);
        ui.footer(yes, Ui.GREEN, () -> {
            action.run();
            if (UiManager.isCurrent(this)) {
                close();
            }
        });
    }
}
