package com.ecoserv.dev;

import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiError;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.ui.UiUtil;
import com.ecoserv.util.Perms;
import com.ecoserv.util.Selection;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;

/** Программируемые зоны (2.0): список, создание по выделению, правка шагов входа/выхода/внутри. */
public class DevZoneScreen extends UiScreen {

    @Nullable
    private String editing;
    private BlockPos a;
    private BlockPos b;
    private final StepsEditor.State enter = new StepsEditor.State();
    private final StepsEditor.State leave = new StepsEditor.State();
    private final StepsEditor.State stay = new StepsEditor.State();

    public DevZoneScreen(EntityPlayerMP player) {
        super(player);
    }

    @Override
    protected boolean canView() {
        return Perms.isAdmin(player);
    }

    @Override
    protected void build(Ui ui) {
        DevZone z = editing == null ? null : DevManager.zone(editing);
        if (z != null) {
            edit(ui, z);
            return;
        }
        editing = null;
        ui.title("Зоны разработчика").icon(new ItemStack(Items.MAP));
        ui.back(() -> new DevScreen(player).open());
        ui.note(Ui.BLUE, "Зона запускает шаги, когда игрок входит, выходит и пока он внутри. &cКрасные&f — глобальные "
                + "(во всех копиях мира, например в локальных мирах команд), &9синие&f — только в этом мире.");
        ui.header("Новая зона");
        Selection.Sel sel = Selection.complete(player.getUniqueID(), "build", player.dimension);
        if (sel != null) {
            ui.text("&7Выделение строителя: &f" + sel.describe());
        } else {
            ui.text("&7Выдели область инструментом строителя (/dev → Инструменты) или отметь углы здесь.");
            ui.buttons().add(a == null ? "Угол 1 = здесь" : "Угол 1 ✔", Ui.GREY, () -> a = new BlockPos(player))
                    .tip("Где ты стоишь");
            ui.buttons().add(b == null ? "Угол 2 = здесь" : "Угол 2 ✔", Ui.GREY, () -> b = new BlockPos(player));
        }
        ui.field("Создать зону", "Имя — Enter", "", v -> {
            BlockPos p1 = sel != null ? sel.min() : a;
            BlockPos p2 = sel != null ? sel.max() : b;
            if (p1 == null || p2 == null) {
                throw new UiError("Сначала выдели область.");
            }
            DevZone nz = DevManager.createZone(v, player.dimension, p1, p2);
            if (nz == null) {
                throw new UiError("Имя занято или неверное.");
            }
            editing = nz.name;
            a = b = null;
        }).placeholder("вход_в_замок").max(48);
        ui.header("Зоны", DevManager.zones().size() + "");
        ui.search("поиск по имени");
        for (final DevZone d : DevManager.zones()) {
            ui.row(new ItemStack(Blocks.STAINED_GLASS, 1, d.global() ? 14 : 11), (d.global() ? "&c" : "&9") + d.name)
                    .line("&7" + com.ecoserv.world.WorldNames.displayName(d.dim) + " · " + d.minX + " " + d.minY + " " + d.minZ
                            + " → " + d.maxX + " " + d.maxY + " " + d.maxZ)
                    .line("&8вход " + d.onEnter.size() + ", выход " + d.onLeave.size() + ", внутри " + d.onStay.size()
                            + (d.enabled ? "" : " · выключена"))
                    .onClick(() -> editing = d.name);
        }
    }

    private void edit(Ui ui, final DevZone z) {
        ui.back(() -> editing = null);
        ui.title("Зона " + z.name).subtitle(z.global() ? "&cглобальная" : "&9локальная").icon(new ItemStack(Items.MAP));
        ui.sideButton("Телепорт", new ItemStack(Items.ENDER_PEARL), Ui.BLUE, () -> {
            close();
            player.connection.setPlayerLocation((z.minX + z.maxX) / 2.0D + 0.5D, z.minY, (z.minZ + z.maxZ) / 2.0D + 0.5D,
                    player.rotationYaw, player.rotationPitch);
        });
        ui.sideButton("Рамка = выделение", new ItemStack(Items.COMPASS), Ui.GREY, () -> {
            Selection.Sel sel = Selection.complete(player.getUniqueID(), "build", player.dimension);
            if (sel == null) {
                throw new UiError("Нет готового выделения строителя.");
            }
            z.minX = sel.min().getX();
            z.minY = sel.min().getY();
            z.minZ = sel.min().getZ();
            z.maxX = sel.max().getX();
            z.maxY = sel.max().getY();
            z.maxZ = sel.max().getZ();
            z.normalize();
            DevManager.saveZones();
            toast("Рамка обновлена.");
        });
        ui.sideButton("Удалить", new ItemStack(Blocks.TNT), Ui.RED, () -> {
            DevManager.removeZone(z);
            editing = null;
        }).confirm("Удалить зону «" + z.name + "»?");
        ui.toggle("Включена", "", z.enabled, on -> {
            z.enabled = on;
            DevManager.saveZones();
        });
        ui.select("Область", "", z.scope, v -> {
            z.scope = v;
            DevManager.saveZones();
        }).option(DevZone.GLOBAL, "&cГлобальная", "Во всех копиях мира (шаблон подмира → все локальные миры)")
                .option(DevZone.LOCAL, "&9Локальная", "Только в этом мире");
        ui.field("Описание", "", z.description, v -> {
            z.description = v.trim();
            DevManager.saveZones();
        }).clearable().max(200);
        ui.field("Только роли", "id через запятую", UiUtil.join(z.roles), v -> {
            z.roles.clear();
            z.roles.addAll(UiUtil.splitList(v));
            DevManager.saveZones();
        }).clearable().max(200);
        ui.number("«Внутри» каждые (тиков)", "", z.stayTicks, 5, 72000, v -> {
            z.stayTicks = (int) v;
            DevManager.saveZones();
        });
        StepsEditor.build(ui, enter, "При входе", z.onEnter, DevManager::saveZones);
        StepsEditor.build(ui, leave, "При выходе", z.onLeave, DevManager::saveZones);
        StepsEditor.build(ui, stay, "Пока внутри", z.onStay, DevManager::saveZones);
    }
}
