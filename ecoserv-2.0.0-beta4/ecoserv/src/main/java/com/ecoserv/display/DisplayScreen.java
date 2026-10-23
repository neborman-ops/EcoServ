package com.ecoserv.display;

import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiError;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.util.Perms;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;

/** Блок-дисплеи: список и свойства (что показывать, поворот по трём осям, размер, свечение). */
public class DisplayScreen extends UiScreen {

    @Nullable
    private String display;

    public DisplayScreen(EntityPlayerMP player, @Nullable String display) {
        super(player);
        this.display = display;
    }

    @Override
    protected boolean canView() {
        return Perms.isAdmin(player);
    }

    static ItemStack stack(String key) {
        try {
            String[] p = key.split("@");
            Item it = net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.getValue(new ResourceLocation(p[0]));
            if (it == null) {
                return new ItemStack(Blocks.BARRIER);
            }
            return new ItemStack(it, 1, p.length > 1 ? Integer.parseInt(p[1]) : 0);
        } catch (Exception e) {
            return new ItemStack(Blocks.BARRIER);
        }
    }

    @Override
    protected void build(Ui ui) {
        final Display d = display == null ? null : DisplayManager.get(display);
        if (d == null) {
            display = null;
            ui.title("Блок-дисплеи").icon(new ItemStack(Items.ARMOR_STAND));
            ui.text("&7Блок или предмет в любой точке: свободное перемещение, поворот по трём осям и размер по каждой оси —"
                    + " как block_display в новых версиях. Двигать и вращать удобнее всего в редакторе сцены (F8).");
            ui.buttons().add("+ Из руки перед тобой", Ui.GREEN, () -> {
                ItemStack h = player.getHeldItemMainhand();
                if (h.isEmpty()) {
                    throw new UiError("Возьми блок или предмет в руку.");
                }
                net.minecraft.util.math.Vec3d look = player.getLookVec();
                display = DisplayManager.create(player.dimension, player.posX + look.x * 2.5, player.posY + 1.5 + look.y * 2.5,
                        player.posZ + look.z * 2.5, DisplayManager.key(h)).id;
            });
            for (final Display x : DisplayManager.all()) {
                ui.row(stack(x.item), "&f" + (x.name.isEmpty() ? x.id : x.name))
                        .line(String.format("&7%s %.1f %.1f %.1f · ×%.2f", com.ecoserv.world.WorldNames.nameOf(x.dim), x.x, x.y, x.z,
                                Math.max(x.sx, Math.max(x.sy, x.sz))))
                        .onClick(() -> display = x.id);
            }
            return;
        }
        ui.back(() -> display = null);
        ui.title(d.name.isEmpty() ? d.id : d.name).subtitle("&7блок-дисплей").icon(stack(d.item));
        ui.sideButton("Ко мне", new ItemStack(Items.ENDER_PEARL), Ui.BLUE, () -> {
            d.x = player.posX;
            d.y = player.posY + 1.5;
            d.z = player.posZ;
            DisplayManager.changed(d);
        });
        ui.sideButton("Удалить", new ItemStack(Blocks.TNT), Ui.RED, () -> {
            DisplayManager.delete(d);
            display = null;
        }).confirm("Удалить дисплей?");
        ui.header("Что показывать");
        ui.field("Блок или предмет", "Выбери из списка", d.item, v -> {
            d.item = v.trim();
            DisplayManager.changed(d);
        }).picker("item");
        ui.buttons().add("Взять из руки", Ui.GREY, () -> {
            ItemStack h = player.getHeldItemMainhand();
            if (h.isEmpty()) {
                throw new UiError("Рука пустая.");
            }
            d.item = DisplayManager.key(h);
            DisplayManager.changed(d);
        });
        ui.select("Как показывать", "", d.mode, v -> {
            d.mode = v;
            DisplayManager.changed(d);
        }).option("auto", "Авто", "Блок — как блок, остальное — как предмет")
                .option("block", "Блок (block_display)", "Модель блока 1×1×1")
                .option("item", "Предмет (item_display)", "Как предмет в рамке");
        ui.field("Название", "Для списка и редактора", d.name, v -> {
            d.name = v.trim();
            DisplayManager.changed(d);
        }).clearable().max(48);
        ui.header("Поворот (свободный, по трём осям)");
        final double[] e = Display.toEuler(d.qx, d.qy, d.qz, d.qw);
        ui.slider("Рыскание (Y)", "Вокруг вертикали", round(e[0]), -180, 180, 1, "°", v -> setEuler(d, v, e[1], e[2]));
        ui.slider("Наклон (X)", "Вперёд-назад", round(e[1]), -90, 90, 1, "°", v -> setEuler(d, e[0], v, e[2]));
        ui.slider("Крен (Z)", "Вбок", round(e[2]), -180, 180, 1, "°", v -> setEuler(d, e[0], e[1], v));
        ui.buttons().add("Сбросить поворот", Ui.GREY, () -> {
            d.qx = d.qy = d.qz = 0;
            d.qw = 1;
            DisplayManager.changed(d);
        });
        ui.slider("Вращение", "Само крутится вокруг вертикали (0 — нет)", d.spin, -180, 180, 5, "°/с", v -> {
            d.spin = v;
            DisplayManager.changed(d);
        });
        ui.header("Размер");
        ui.slider("Ширина (X)", "", d.sx, 0.05, 16, 0.05, "", v -> {
            d.sx = v;
            d.normalize();
            DisplayManager.changed(d);
        });
        ui.slider("Высота (Y)", "", d.sy, 0.05, 16, 0.05, "", v -> {
            d.sy = v;
            d.normalize();
            DisplayManager.changed(d);
        });
        ui.slider("Глубина (Z)", "", d.sz, 0.05, 16, 0.05, "", v -> {
            d.sz = v;
            d.normalize();
            DisplayManager.changed(d);
        });
        ui.header("Вид");
        ui.toggle("Светится", "Полная яркость даже в темноте (со свечением Bloom — сияет)", d.glow, on -> {
            d.glow = on;
            DisplayManager.changed(d);
        });
        ui.slider("Видно с расстояния", "", d.viewDistance, 8, 512, 8, " бл.", v -> {
            d.viewDistance = v;
            DisplayManager.changed(d);
        });
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static void setEuler(Display d, double yaw, double pitch, double roll) {
        double[] q = Display.fromEuler(yaw, pitch, roll);
        d.qx = q[0];
        d.qy = q[1];
        d.qz = q[2];
        d.qw = q[3];
        DisplayManager.changed(d);
    }
}
