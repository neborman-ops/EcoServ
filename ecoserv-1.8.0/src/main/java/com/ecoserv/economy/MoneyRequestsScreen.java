package com.ecoserv.economy;

import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiIcons;
import com.ecoserv.ui.UiError;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.ui.UiUtil;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.UUID;

/** Просьбы о деньгах: входящие (дать / отказать), свои (отменить) и новая просьба. */
public class MoneyRequestsScreen extends UiScreen {

    private String who = "";
    private long amount;
    private String reason = "";

    public MoneyRequestsScreen(EntityPlayerMP player) {
        super(player);
    }

    @Override
    protected int refreshSeconds() {
        return 5;
    }

    @Override
    protected void build(Ui ui) {
        ui.title("Просьбы о деньгах").icon(UiIcons.money()).compact();
        ui.status("&7Баланс: &6" + Fmt.money(EconomyManager.get(player.getUniqueID())));
        List<MoneyRequests.Request> in = MoneyRequests.incoming(player.getUniqueID());
        ui.header("Просят у тебя", Integer.toString(in.size()));
        if (in.isEmpty()) {
            ui.text("&8Никто ничего не просит.");
        }
        for (final MoneyRequests.Request r : in) {
            ui.row(new ItemStack(Items.SKULL, 1, 3), "&e" + r.fromName + " &fпросит &6" + Fmt.money(r.amount))
                    .line(r.reason.isEmpty() ? "&8без пояснения" : "&7«" + r.reason + "»")
                    .line("&8осталось " + Fmt.duration(Math.max(0, r.left())))
                    .button("Дать", Ui.GREEN, () -> {
                        String err = MoneyRequests.accept(player, r);
                        if (err != null) {
                            throw new UiError(UiUtil.plain(err));
                        }
                        toast("Переведено " + Fmt.money(r.amount) + " игроку " + r.fromName);
                    }).confirm("Дать &e" + r.fromName + " &6" + Fmt.money(r.amount) + "&f?")
                    .button("Отказать", Ui.RED, () -> {
                        MoneyRequests.deny(player, r);
                        toast("Отказано.");
                    });
        }
        List<MoneyRequests.Request> out = MoneyRequests.outgoing(player.getUniqueID());
        if (!out.isEmpty()) {
            ui.header("Ты просишь", Integer.toString(out.size()));
            for (final MoneyRequests.Request r : out) {
                ui.row(new ItemStack(Items.PAPER), "&fу &e" + r.toName + " &6" + Fmt.money(r.amount))
                        .line("&8ждёт ответа · осталось " + Fmt.duration(Math.max(0, r.left())))
                        .button("Отменить", Ui.GREY, () -> MoneyRequests.cancel(player.getUniqueID(), r.id));
            }
        }
        ui.header("Попросить денег");
        ui.field("У кого", "Игрок должен быть в сети", who, v -> who = v.trim()).picker(Ui.PICK_PLAYER)
                .placeholder("ник").max(16);
        ui.money("Сколько", "Понимает 500, 1.5k, 2m", amount, v -> amount = v);
        ui.field("Зачем", "Необязательно — увидит тот, у кого просишь", reason, v -> reason = v.trim())
                .placeholder("на кирку").max(80);
        ui.footer("Попросить", Ui.GREEN, () -> {
            if (who.isEmpty()) {
                throw new UiError("Укажи, у кого просишь.");
            }
            UUID to = PlayerUtil.knownUuid(who);
            if (to == null) {
                throw new UiError("Игрок " + who + " не найден.");
            }
            String err = MoneyRequests.ask(player, to, amount, reason);
            if (err != null) {
                throw new UiError(UiUtil.plain(err));
            }
            toast("Просьба отправлена: " + PlayerUtil.nameOf(to));
            who = "";
            amount = 0;
            reason = "";
        });
    }
}
