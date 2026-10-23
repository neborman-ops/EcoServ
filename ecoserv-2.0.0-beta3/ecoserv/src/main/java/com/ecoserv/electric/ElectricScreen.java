package com.ecoserv.electric;

import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

/** Окно электро-блока: ток, настройки, провода. */
public class ElectricScreen extends UiScreen {

    private final BlockPos pos;

    public ElectricScreen(EntityPlayerMP player, BlockPos pos) {
        super(player);
        this.pos = pos;
    }

    @Override
    protected int refreshSeconds() {
        return 1;
    }

    private TileElectric tile() {
        TileEntity te = player.world.getTileEntity(pos);
        return te instanceof TileElectric ? (TileElectric) te : null;
    }

    @Override
    protected void build(Ui ui) {
        final TileElectric t = tile();
        if (t == null) {
            ui.title("Электричество").note(Ui.GREY, "Блока больше нет.");
            return;
        }
        int type = t.type();
        ui.title("⚡ " + ElectricTypes.TITLES[type]).subtitle("&7" + ElectricTypes.HINTS[type])
                .icon(new ItemStack(com.ecoserv.v2.ModRegistry2.ELECTRIC, 1, type));
        ui.sideButton("Взять провод", new ItemStack(Items.STRING), Ui.GREEN, () -> PlayerUtil.giveOrDrop(player, ElectricFeature.wire()));
        ui.header("Сейчас");
        int use = ElectricTypes.USE[type];
        if (ElectricTypes.source(type)) {
            ui.bar("Отдаёт", t.out, type == ElectricTypes.GENERATOR ? 50 : type == ElectricTypes.SOLAR ? 20 : Math.max(1, t.rate), Ui.YELLOW);
        } else {
            ui.bar("Приходит", t.in, Math.max(1, Math.max(t.in, t.demand)), t.in >= use ? Ui.GREEN : Ui.RED);
            ui.text("&7Нужно этому блоку: &f" + use + "&7 · вся ветка дальше просит: &f" + t.demand + "&7 · уходит дальше: &f" + t.out);
        }
        ui.note(t.lit ? Ui.GREEN : Ui.GREY, t.lit ? "Работает" : ElectricTypes.source(type) ? "Не отдаёт ток (нет спроса или нечем)"
                : t.in < use ? "Мало тока: нужно " + use + ", приходит " + t.in : "Выключено");
        switch (type) {
            case ElectricTypes.SWITCH:
                ui.toggle("Включён", "Можно щёлкать ПКМ по блоку", t.on, on -> {
                    t.on = on;
                    t.changed();
                });
                break;
            case ElectricTypes.BATTERY:
                ui.bar("Заряд", t.energy, t.capacity, Ui.GREEN);
                ui.select("Отдача", "Сколько ед. отдаёт и берёт за раз", Integer.toString(t.rate), v -> {
                    t.rate = Integer.parseInt(v);
                    t.changed();
                }).option("10", "10 ед. — маленькая").option("25", "25 ед. — средняя").option("50", "50 ед. — большая")
                        .option("100", "100 ед. — промышленная");
                ui.select("Ёмкость", "", Integer.toString(t.capacity), v -> {
                    t.capacity = Integer.parseInt(v);
                    t.energy = Math.min(t.energy, t.capacity);
                    t.changed();
                }).option("20000", "20 000").option("100000", "100 000").option("500000", "500 000");
                break;
            case ElectricTypes.GENERATOR:
                ui.text("&7Топливо: &f" + t.fuel / 20 + " сек &8(уголь — 90 с, бревно — 30 с, огненный стержень — 120 с;"
                        + " ПКМ топливом по блоку)");
                break;
            case ElectricTypes.TIMER:
                ui.select("Период", "Сколько секунд вкл. и столько же выкл.", Integer.toString(t.interval), v -> {
                    t.interval = Integer.parseInt(v);
                    t.changed();
                }).option("1", "1 сек").option("2", "2 сек").option("5", "5 сек").option("10", "10 сек").option("30", "30 сек")
                        .option("60", "1 мин");
                break;
            case ElectricTypes.SENSOR:
            case ElectricTypes.DOOR:
                ui.number(type == ElectricTypes.SENSOR ? "Радиус датчика" : "Радиус поиска дверей", "блоков", t.range, 1,
                        type == ElectricTypes.SENSOR ? 16 : 6, v -> {
                            t.range = (int) v;
                            t.changed();
                        });
                break;
            default:
        }
        ui.field("Подпись", "Видна над блоком в очках разработчика", t.label, v -> {
            t.label = com.ecoserv.ui.UiUtil.cut(v.trim(), 32);
            t.changed();
        }).clearable().max(32);
        ui.header("Провода", t.outputs.size() + "/" + ElectricTypes.OUTS[type]);
        ui.text("&7Провод: &fПКМ&7 по этому блоку, затем по тому, куда вести ток. &fShift+ПКМ&7 проводом — снять все провода.");
        for (final BlockPos o : new java.util.ArrayList<BlockPos>(t.outputs)) {
            TileEntity te = player.world.getTileEntity(o);
            String name = te instanceof TileElectric ? ElectricTypes.TITLES[((TileElectric) te).type()] : "&cпусто";
            ui.row(new ItemStack(Items.STRING), "&f→ " + name).line("&8" + o.getX() + " " + o.getY() + " " + o.getZ())
                    .button("✕", Ui.RED, () -> {
                        t.outputs.remove(o);
                        t.changed();
                    });
        }
    }
}
