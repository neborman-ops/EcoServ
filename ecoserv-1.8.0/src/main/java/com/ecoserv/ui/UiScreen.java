package com.ecoserv.ui;

import net.minecraft.entity.player.EntityPlayerMP;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Окно на стороне сервера: build() описывает содержимое, обработчики живут здесь же.
 * После каждого действия окно пересобирается и отправляется заново (UiManager).
 */
public abstract class UiScreen {

    /** Сколько последних версий окна помним (действие могло прийти по чуть устаревшему виду). */
    private static final int KEEP_REVISIONS = 4;

    protected final EntityPlayerMP player;
    int windowId;
    int revision;
    /** Окно, из которого перешли в это (-1 — открыто с нуля). */
    int from = -1;
    /** Последний отправленный вид (без номера версии) — автообновление не шлёт одно и то же. */
    net.minecraft.nbt.NBTTagCompound lastSent;
    final Map<Integer, List<Ui.ClickEx>> recent = new LinkedHashMap<Integer, List<Ui.ClickEx>>() {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, List<Ui.ClickEx>> eldest) {
            return size() > KEEP_REVISIONS;
        }
    };
    private String toast;
    private boolean toastError;

    protected UiScreen(EntityPlayerMP player) {
        this.player = player;
    }

    /** Описать окно. Вызывается при каждом обновлении. */
    protected abstract void build(Ui ui);

    /** Можно ли игроку видеть это окно (проверяется при каждом действии, например «только админ»). */
    protected boolean canView() {
        return true;
    }

    /** Окно обновляется само каждые N секунд (0 — только после действий). */
    protected int refreshSeconds() {
        return 0;
    }

    /** Окно закрыто (игрок закрыл или открыл другое). */
    protected void onClosed() {
    }

    public EntityPlayerMP player() {
        return player;
    }

    public final void open() {
        UiManager.open(player, this);
    }

    /** Пересобрать и отправить (если окно всё ещё открыто). */
    public final void refresh() {
        UiManager.refresh(this);
    }

    public final void close() {
        UiManager.close(player);
    }

    /** Открыть с зелёным уведомлением (например, после перехода из другого окна). */
    public final UiScreen withToast(String text) {
        toast(text);
        return this;
    }

    /** Зелёное уведомление в окне. */
    protected final void toast(String text) {
        toast = text;
        toastError = false;
    }

    /** Красное уведомление в окне. */
    protected final void error(String text) {
        toast = text;
        toastError = true;
    }

    @Nullable
    final String takeToast() {
        String t = toast;
        toast = null;
        return t;
    }

    final boolean toastIsError() {
        return toastError;
    }

    final void setToast(String text, boolean isError) {
        toast = text;
        toastError = isError;
    }
}
