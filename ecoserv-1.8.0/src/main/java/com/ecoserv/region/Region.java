package com.ecoserv.region;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Область, срабатывающая при входе игрока: зона-триггер (п. 14) или портал (п. 4).
 * Хранится в world/ecoserv/regions.json.
 */
public class Region {

    public static final String TRIGGER = "trigger";
    public static final String PORTAL = "portal";

    /** Уникальное имя: латиница/цифры/_ (и кириллица), без пробелов. */
    public String name = "";
    /** trigger или portal. */
    public String kind = TRIGGER;
    public int dim;
    public int minX;
    public int minY;
    public int minZ;
    public int maxX;
    public int maxY;
    public int maxZ;

    // --- действия ---------------------------------------------------------------
    /** Сообщение в чат игроку. */
    public String message = "";
    /** «Заголовок|подзаголовок» на экране. */
    public String title = "";
    /** id звука, например minecraft:entity.player.levelup */
    public String sound = "";
    /** Команды от имени сервера (%player% — ник). */
    public List<String> commands = new ArrayList<String>();
    /** Запустить сценарий (/script). */
    public String script = "";
    /** Засчитать «Активацию» с именем области (условие в квестах, скрытые квесты). */
    public boolean activation = true;

    // --- портал -----------------------------------------------------------------
    /** Куда ведёт портал: имя мира (псевдоним/номер) или подмира. */
    public String target = "";
    /**
     * spawn — на спавн мира, last — туда, где игрок был, point — в заданную точку,
     * portal — к другому порталу (targetPortal), в том числе в подмире и в локальных мирах команд.
     */
    public String targetMode = "spawn";
    public double tx;
    public double ty;
    public double tz;
    public float tyaw;
    public float tpitch;
    /** Для режима portal: имя портала, к которому ведёт этот. */
    public String targetPortal = "";
    /** Точка выхода у ЭТОГО портала (куда попадает пришедший через связанный портал). */
    public boolean exitSet;
    public double ex;
    public double ey;
    public double ez;
    public float eyaw;
    public float epitch;

    // --- условия ----------------------------------------------------------------
    /** enter — каждый вход, once — один раз на игрока, cooldown — не чаще раза в cooldownSeconds. */
    public String mode = "enter";
    public int cooldownSeconds = 30;
    /** Нужна одна из ролей (пусто — всем). */
    public List<String> roles = new ArrayList<String>();
    /** Фильтр возраста (пусто — всем), см. /birthday. */
    public String age = "";
    /** Сообщение тем, кому нельзя (пусто — молча). */
    public String denyMessage = "";
    public boolean enabled = true;

    public String creator = "";
    public long created;

    public void normalize() {
        if (commands == null) {
            commands = new ArrayList<String>();
        }
        if (roles == null) {
            roles = new ArrayList<String>();
        }
        if (message == null) {
            message = "";
        }
        if (title == null) {
            title = "";
        }
        if (sound == null) {
            sound = "";
        }
        if (script == null) {
            script = "";
        }
        if (target == null) {
            target = "";
        }
        if (targetMode == null || targetMode.isEmpty()) {
            targetMode = "spawn";
        }
        if (targetPortal == null) {
            targetPortal = "";
        }
        if (mode == null || mode.isEmpty()) {
            mode = "enter";
        }
        if (age == null) {
            age = "";
        }
        if (denyMessage == null) {
            denyMessage = "";
        }
        if (kind == null || (!kind.equals(PORTAL) && !kind.equals(TRIGGER))) {
            kind = TRIGGER;
        }
        cooldownSeconds = Math.max(0, cooldownSeconds);
        int x1 = Math.min(minX, maxX);
        int y1 = Math.min(minY, maxY);
        int z1 = Math.min(minZ, maxZ);
        int x2 = Math.max(minX, maxX);
        int y2 = Math.max(minY, maxY);
        int z2 = Math.max(minZ, maxZ);
        minX = x1;
        minY = y1;
        minZ = z1;
        maxX = x2;
        maxY = y2;
        maxZ = z2;
    }

    public boolean isPortal() {
        return PORTAL.equals(kind);
    }

    public void setArea(int dim, BlockPos a, BlockPos b) {
        this.dim = dim;
        minX = Math.min(a.getX(), b.getX());
        minY = Math.min(a.getY(), b.getY());
        minZ = Math.min(a.getZ(), b.getZ());
        maxX = Math.max(a.getX(), b.getX());
        maxY = Math.max(a.getY(), b.getY());
        maxZ = Math.max(a.getZ(), b.getZ());
    }

    /** Точка внутри (блоки min..max включительно). */
    public boolean contains(double x, double y, double z) {
        return x >= minX && x < maxX + 1 && y >= minY && y < maxY + 1 && z >= minZ && z < maxZ + 1;
    }

    /** Игрок «в области», если в ней ноги или голова. */
    public boolean containsPlayer(double x, double y, double z) {
        return contains(x, y, z) || contains(x, y + 1.0D, z);
    }

    public BlockPos min() {
        return new BlockPos(minX, minY, minZ);
    }

    public BlockPos max() {
        return new BlockPos(maxX, maxY, maxZ);
    }

    public BlockPos center() {
        return new BlockPos((minX + maxX) / 2, minY, (minZ + maxZ) / 2);
    }

    public String describeArea() {
        return minX + " " + minY + " " + minZ + " → " + maxX + " " + maxY + " " + maxZ
                + " (" + (maxX - minX + 1) + "×" + (maxY - minY + 1) + "×" + (maxZ - minZ + 1) + ")";
    }

    /** Куда ведёт — для людей. */
    public String targetTitle() {
        if ("portal".equals(targetMode)) {
            return targetPortal.isEmpty() ? "к порталу (не выбран)" : "к порталу «" + targetPortal + "»";
        }
        if (target.isEmpty()) {
            return "не настроен";
        }
        String where = "point".equals(targetMode) ? " в точку " + (int) tx + " " + (int) ty + " " + (int) tz
                : "last".equals(targetMode) ? ", где игрок был" : ", на спавн";
        return target + where;
    }

    public String modeTitle() {
        if ("once".equals(mode)) {
            return "один раз на игрока";
        }
        if ("cooldown".equals(mode)) {
            return "не чаще раза в " + cooldownSeconds + " с";
        }
        return "при каждом входе";
    }
}
