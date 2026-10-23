package com.ecoserv.dev;

import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.ui.UiUtil;
import com.ecoserv.util.Perms;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

/** Настройка блока-программы (2.0). */
public class LogicScreen extends UiScreen {

    private final BlockPos pos;
    private final StepsEditor.State steps = new StepsEditor.State();

    public LogicScreen(EntityPlayerMP player, BlockPos pos) {
        super(player);
        this.pos = pos;
    }

    @Override
    protected boolean canView() {
        return Perms.isAdmin(player);
    }

    @Override
    protected void build(Ui ui) {
        TileEntity te = player.world.getTileEntity(pos);
        if (!(te instanceof TileLogic)) {
            ui.title("Блок-программа").note(Ui.RED, "Блока здесь больше нет.");
            return;
        }
        final TileLogic t = (TileLogic) te;
        ui.title("Блок-программа").subtitle(t.name.isEmpty() ? "&8без имени" : t.name)
                .icon(new ItemStack(com.ecoserv.v2.ModRegistry2.LOGIC));
        ui.note(t.global() ? Ui.RED : Ui.BLUE, t.global()
                ? "Глобальный (красный): шаги выполняются для ВСЕХ игроков в этом мире и его копиях."
                : "Локальный (синий): шаги выполняются для того, кто запустил блок.");
        ui.field("Имя", "Для событий logic:имя и квестов", t.name, v -> {
            t.name = v.trim();
            t.changed();
        }).max(48);
        ui.toggle("Включён", "", t.enabled, on -> {
            t.enabled = on;
            t.changed();
        });
        ui.select("Область действия", "", t.scope, v -> {
            t.scope = v;
            t.changed();
        }).option(DevZone.LOCAL, "&9Локальный", "Для того, кто запустил").option(DevZone.GLOBAL, "&cГлобальный", "Для всех в мире");
        ui.select("Когда срабатывает", "", t.trigger, v -> {
            t.trigger = v;
            t.changed();
        }).option("click", "ПКМ по блоку", "Игрок нажимает на блок")
                .option("redstone", "Сигнал редстоуна", "Когда на блок подали сигнал")
                .option("near", "Игрок рядом", "Подошёл ближе радиуса")
                .option("timer", "Таймер", "Каждые N тиков")
                .option("event", "Событие", "join, quest_done:id, logic:имя, своё событие из шага event");
        if ("near".equals(t.trigger)) {
            ui.number("Радиус", "блоков", t.radius, 1, 64, v -> {
                t.radius = (int) v;
                t.changed();
            });
        }
        if ("timer".equals(t.trigger)) {
            ui.number("Каждые (тиков)", "20 тиков = 1 секунда", t.interval, 5, 72000, v -> {
                t.interval = (int) v;
                t.changed();
            });
        }
        if ("event".equals(t.trigger)) {
            ui.field("Событие", "Например join, zone_enter:арена, quest_done:iron, своё_событие", t.eventName, v -> {
                t.eventName = v.trim();
                t.changed();
            }).max(64);
        }
        ui.field("Условие", "Пусто — всегда. Например: role vip, var ключ = 1, item minecraft:diamond 3", t.condition, v -> {
            t.condition = v.trim();
            t.changed();
        }).clearable().max(200);
        ui.number("Сигнал редстоуна (тиков)", "0 — без сигнала", t.pulseTicks, 0, 1200, v -> {
            t.pulseTicks = (int) v;
            t.changed();
        });
        ui.number("Пауза между срабатываниями (тиков)", "", t.cooldown, 0, 72000, v -> {
            t.cooldown = (int) v;
            t.changed();
        });
        ui.field("Только роли", "id через запятую (пусто — всем)", UiUtil.join(t.roles), v -> {
            t.roles.clear();
            t.roles.addAll(UiUtil.splitList(v));
            t.changed();
        }).clearable().max(200);
        StepsEditor.build(ui, steps, "Что делает", t.steps, t::changed);
        ui.footer("Проверить на себе", Ui.BLUE, () -> {
            t.fire(player, "test");
            toast("Запущено.");
        });
    }
}
