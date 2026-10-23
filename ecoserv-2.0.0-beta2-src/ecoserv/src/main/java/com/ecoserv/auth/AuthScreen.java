package com.ecoserv.auth;

import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiError;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.ui.UiUtil;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

/** Окно входа / регистрации (2.0): пароль звёздочками, без ввода в чат. */
public class AuthScreen extends UiScreen {

    private String pass = "";
    private String again = "";

    public AuthScreen(EntityPlayerMP player) {
        super(player);
    }

    public static void open(EntityPlayerMP player) {
        if (com.ecoserv.ui.UiManager.clientHasMod(player)) {
            new AuthScreen(player).open();
        }
    }

    @Override
    protected void build(Ui ui) {
        boolean reg = AuthManager.registered(player.getUniqueID());
        ui.title(reg ? "Вход" : "Регистрация").icon(new ItemStack(Items.NAME_TAG)).compact();
        if (!AuthManager.isPending(player)) {
            ui.note(Ui.GREEN, "Ты уже вошёл.");
            ui.footer("Закрыть", Ui.GREY, this::close);
            return;
        }
        if (reg) {
            ui.text("&7Это сервер без проверки лицензии: аккаунт защищён паролем. Введи свой пароль.");
            ui.password("Пароль", "Enter — войти", v -> pass = v).onEnter(this::submit).max(64);
        } else {
            ui.text("&7Придумай пароль — его будут спрашивать при входе, чтобы никто не зашёл под твоим ником."
                    + " Минимум " + AuthManager.settings().minLength + " символов.");
            ui.password("Пароль", "", v -> pass = v).max(64);
            ui.password("Ещё раз", "Enter — зарегистрироваться", v -> again = v).onEnter(this::submit).max(64);
        }
        ui.footer(reg ? "Войти" : "Зарегистрироваться", Ui.GREEN, this::submit);
    }

    private void submit() {
        boolean reg = AuthManager.registered(player.getUniqueID());
        String err = reg ? AuthManager.login(player, pass) : AuthManager.register(player, pass, again);
        if (err != null) {
            throw new UiError(UiUtil.plain(err));
        }
    }

    @Override
    protected void onClosed() {
        // окно закрыли, а вход не сделан — напомнить про команду
        if (AuthManager.isPending(player)) {
            AuthManager.hint(player);
        }
    }
}
