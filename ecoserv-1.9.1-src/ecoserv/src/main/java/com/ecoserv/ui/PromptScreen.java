package com.ecoserv.ui;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Маленькое окно «введи значение» вместо ввода в чат: вопрос, поле, «Готово» / «Отмена».
 * Обработчик может бросить UiError — окно останется открытым с подсказкой.
 */
public class PromptScreen extends UiScreen {

    public interface Done {
        void accept(EntityPlayerMP player, String value);
    }

    private final String title;
    private final String question;
    private final String label;
    private final String placeholder;
    private final ItemStack icon;
    private final Done done;
    @Nullable
    private final Runnable cancel;
    private final int maxLength;
    private String value;
    private boolean finished;

    public PromptScreen(EntityPlayerMP player, String title, String question, String label, String initial,
                        String placeholder, ItemStack icon, int maxLength, Done done, @Nullable Runnable cancel) {
        super(player);
        this.title = title;
        this.question = question;
        this.label = label;
        this.value = initial == null ? "" : initial;
        this.placeholder = placeholder == null ? "" : placeholder;
        this.icon = icon == null ? ItemStack.EMPTY : icon;
        this.maxLength = maxLength;
        this.done = done;
        this.cancel = cancel;
    }

    /** Короткая форма: заголовок, вопрос, начальное значение и что сделать. */
    public static void ask(EntityPlayerMP player, String title, String question, String initial, Done done) {
        new PromptScreen(player, title, question, "Значение", initial, "", ItemStack.EMPTY, 256, done, null).open();
    }

    @Override
    protected void build(Ui ui) {
        ui.title(title).compact();
        if (!icon.isEmpty()) {
            ui.icon(icon);
        }
        if (!question.isEmpty()) {
            ui.text(question);
        }
        ui.field(label, "Enter — готово", value, v -> value = v).placeholder(placeholder).max(maxLength)
                .onEnter(this::submit);
        ui.footer("Отмена", Ui.GREY, () -> {
            finished = true;
            if (cancel != null) {
                cancel.run();
            } else {
                close();
            }
        });
        ui.footer("Готово", Ui.GREEN, this::submit);
    }

    private void submit() {
        done.accept(player, value);
        finished = true;
        if (UiManager.isCurrent(this)) {
            close();
        }
    }

    public boolean finished() {
        return finished;
    }
}
