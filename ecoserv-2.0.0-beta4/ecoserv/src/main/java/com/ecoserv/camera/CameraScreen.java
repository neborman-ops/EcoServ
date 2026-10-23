package com.ecoserv.camera;

import com.ecoserv.npc.Npc;
import com.ecoserv.npc.NpcManager;
import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiError;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Perms;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;

/** Камеры сцены: создать, настроить, переключить на камеру игрока (или всех), вернуть обратно. */
public class CameraScreen extends UiScreen {

    @Nullable
    private String camera;

    public CameraScreen(EntityPlayerMP player, @Nullable String camera) {
        super(player);
        this.camera = camera;
    }

    @Override
    protected boolean canView() {
        return Perms.isAdmin(player);
    }

    @Override
    protected void build(Ui ui) {
        final SceneCamera c = camera == null ? null : CameraManager.get(camera);
        if (c == null) {
            camera = null;
            list(ui);
            return;
        }
        ui.back(() -> camera = null);
        ui.title("🎥 " + c.title()).subtitle(String.format("&7%.1f %.1f %.1f · FOV %.0f°", c.x, c.y, c.z, c.fov))
                .icon(new ItemStack(Items.ENDER_EYE));
        ui.sideButton("Посмотреть", new ItemStack(Items.ENDER_EYE), Ui.GREEN, () -> {
            close();
            CameraManager.view(player, c, 0);
        }).tip("Сам посмотришь через камеру; Shift — выйти");
        ui.sideButton("Камера = мой взгляд", new ItemStack(Items.COMPASS), Ui.BLUE, () -> {
            c.x = player.posX;
            c.y = player.posY + player.getEyeHeight();
            c.z = player.posZ;
            c.yaw = player.rotationYaw;
            c.pitch = player.rotationPitch;
            c.dim = player.dimension;
            CameraManager.changed(c);
            toast("Камера поставлена туда, куда ты смотришь.");
        });
        ui.sideButton("Удалить", new ItemStack(Blocks.TNT), Ui.RED, () -> {
            CameraManager.delete(c);
            camera = null;
        }).confirm("Удалить камеру «" + c.title() + "»?");

        ui.header("Переключить игроков на камеру");
        ui.note(Ui.BLUE, "Игрок увидит мир через эту камеру и не сможет ходить. Вернуть — кнопкой ниже, шагом &fcamera off&f"
                + " или Shift (если разрешено).");
        ui.field("Игрок", "Ник — Enter", "", v -> {
            List<EntityPlayerMP> who = CameraManager.targets(player, v, c);
            for (EntityPlayerMP p : who) {
                CameraManager.view(p, c, 0);
            }
            CameraManager.notifyView(player, c, who);
        }).picker("player");
        ui.buttons().add("Всех в этом мире", Ui.GREEN, () -> {
            List<EntityPlayerMP> who = CameraManager.targets(player, "@a", c);
            for (EntityPlayerMP p : who) {
                CameraManager.view(p, c, 0);
            }
            CameraManager.notifyView(player, c, who);
        }).confirm("Переключить всех игроков этого мира на камеру?");
        List<EntityPlayerMP> viewers = CameraManager.viewers(c);
        if (!viewers.isEmpty()) {
            ui.header("Сейчас смотрят", viewers.size() + "");
            for (final EntityPlayerMP p : viewers) {
                ui.row(new ItemStack(Items.SKULL, 1, 3), "&f" + p.getName()).button("Вернуть", Ui.YELLOW, () -> CameraManager.stop(p));
            }
            ui.buttons().add("Вернуть всех", Ui.YELLOW, () -> {
                for (EntityPlayerMP p : CameraManager.viewers(c)) {
                    CameraManager.stop(p);
                }
            });
        }

        ui.header("Кадр");
        ui.field("Название", "", c.name, v -> {
            c.name = v.trim();
            CameraManager.changed(c);
        }).clearable().max(40);
        ui.slider("Поле зрения (FOV)", "Меньше — «зум», больше — широкий угол", c.fov, 10, 150, 1, "°", v -> {
            c.fov = (float) v;
            CameraManager.changed(c);
        });
        ui.slider("Поворот", "Куда смотрит по горизонтали", c.yaw, -180, 180, 1, "°", v -> {
            c.yaw = (float) v;
            CameraManager.changed(c);
        });
        ui.slider("Наклон", "-90 — вверх, 90 — вниз", c.pitch, -90, 90, 1, "°", v -> {
            c.pitch = (float) v;
            CameraManager.changed(c);
        });
        ui.slider("Крен", "Наклон кадра вбок", c.roll, -180, 180, 1, "°", v -> {
            c.roll = (float) v;
            CameraManager.changed(c);
        });
        Ui.Select track = ui.select("Следить", "Камера сама поворачивается к цели", c.track, v -> {
            c.track = v;
            CameraManager.changed(c);
        }).option("", "Нет", "Смотрит, куда поставили").option("viewer", "За тем, кто смотрит", "Как камера наблюдения");
        for (Npc n : NpcManager.all()) {
            if (n.dim == c.dim) {
                track.option("npc:" + n.id, "NPC: " + ChatUtil.strip(ChatUtil.color(n.name)), n.id);
            }
        }
        ui.header("Как показывать");
        ui.toggle("Прятать интерфейс", "Хотбар, здоровье, прицел", c.hideHud, on -> {
            c.hideHud = on;
            CameraManager.changed(c);
        });
        ui.toggle("Кинополосы", "Чёрные полосы сверху и снизу", c.bars, on -> {
            c.bars = on;
            CameraManager.changed(c);
        });
        ui.toggle("Можно выйти (Shift)", "Иначе — только шагом или админом", c.allowExit, on -> {
            c.allowExit = on;
            CameraManager.changed(c);
        });
        ui.slider("Плавный переход", "0 — сразу", c.transition / 20.0, 0, 5, 0.25, " с", v -> {
            c.transition = (int) Math.round(v * 20);
            CameraManager.changed(c);
        });
        ui.header("В программах");
        ui.text("&7Шаг &fcamera " + c.id + " 5&7 — показать камеру 5 секунд, &fcamera " + c.id + "&7 — пока не выключат,"
                + " &fcamera off&7 — вернуть. Команда: &f/camera view " + c.id + " <ник|@a>&7.");
    }

    private void list(Ui ui) {
        ui.title("Камеры").icon(new ItemStack(Items.ENDER_EYE));
        ui.text("&7Камера — точка обзора в мире. Переключи на неё игрока (или всех) — он увидит мир её глазами: камера"
                + " наблюдения, показ арены, кадр сюжета. В редакторе сцены (F8) камера видна значком с пирамидой обзора —"
                + " её можно двигать и поворачивать мышью.");
        ui.buttons().add("+ Камера здесь (мой взгляд)", Ui.GREEN, () -> camera = CameraManager.create(player).id);
        SceneCamera mine = CameraManager.viewing(player);
        if (mine != null) {
            ui.buttons().add("Выйти из камеры", Ui.YELLOW, () -> CameraManager.stop(player));
        }
        for (final SceneCamera c : CameraManager.all()) {
            int n = CameraManager.viewers(c).size();
            ui.row(new ItemStack(Items.ENDER_EYE), "&f" + c.title() + (n > 0 ? " &a· смотрят: " + n : ""))
                    .line(String.format("&7%s %.0f %.0f %.0f · FOV %.0f°", com.ecoserv.world.WorldNames.nameOf(c.dim), c.x, c.y, c.z, c.fov))
                    .onClick(() -> camera = c.id)
                    .button("👁", Ui.GREEN, () -> {
                        if (c.dim != player.dimension) {
                            throw new UiError("Камера в другом мире.");
                        }
                        close();
                        CameraManager.view(player, c, 0);
                    });
        }
    }
}
