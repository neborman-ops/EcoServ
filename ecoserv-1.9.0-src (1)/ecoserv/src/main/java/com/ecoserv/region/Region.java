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
    /** Вид портала (1.8.0): обычный или «Чёрная дыра» (вид на место назначения, искажения, притяжение). */
    public static final String STYLE_NORMAL = "normal";
    public static final String STYLE_BLACKHOLE = "blackhole";
    /** 1.9.0: плоский портал-картинка (выделение толщиной в 1 блок): вид на место назначения, почти без искажений. */
    public static final String STYLE_MONOLITH = "monolith";

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
    /** normal или blackhole (только у порталов). */
    public String style = STYLE_NORMAL;
    /** 1.9.0: свои настройки эффектов (HoleParams), null — как «для всех». */
    @javax.annotation.Nullable
    public java.util.Map<String, Double> fx;

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
        if (style == null || !(STYLE_BLACKHOLE.equals(style) || STYLE_MONOLITH.equals(style))) {
            style = STYLE_NORMAL;
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

    public boolean isBlackHole() {
        return isPortal() && STYLE_BLACKHOLE.equals(style);
    }

    public boolean isMonolith() {
        return isPortal() && STYLE_MONOLITH.equals(style);
    }

    /** Чёрная дыра или монолит: есть окно в место назначения. */
    public boolean isSpecial() {
        return isBlackHole() || isMonolith();
    }

    /**
     * Ось-нормаль монолита: 0 — X (стена «восток-запад»), 1 — Y (пол / потолок), 2 — Z. Это ось, по которой
     * выделение толщиной в 1 блок. -1 — область не плоская.
     */
    public int planeAxis() {
        return planeAxis(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
    }

    public static int planeAxis(int sx, int sy, int sz) {
        if (sx == 1 && sz != 1) {
            return 0;
        }
        if (sz == 1 && sx != 1) {
            return 2;
        }
        if (sy == 1) {
            return 1;
        }
        if (sx == 1) {
            return 0;
        }
        return -1;
    }

    /** Полуразмеры картинки монолита по двум осям плоскости (u, v) в блоках. */
    public double[] planeHalf() {
        int axis = planeAxis();
        double sx = (maxX - minX + 1) / 2.0D;
        double sy = (maxY - minY + 1) / 2.0D;
        double sz = (maxZ - minZ + 1) / 2.0D;
        if (axis == 0) {
            return new double[]{sz, sy};
        }
        if (axis == 1) {
            return new double[]{sx, sz};
        }
        return new double[]{sx, sy};
    }

    /** Центр области (для «Чёрной дыры» — центр сферы). */
    public double centerX() {
        return (minX + maxX + 1) / 2.0D;
    }

    public double centerY() {
        return (minY + maxY + 1) / 2.0D;
    }

    public double centerZ() {
        return (minZ + maxZ + 1) / 2.0D;
    }

    /** Радиус «Чёрной дыры»: половина самой большой стороны области, не меньше блока. */
    public double holeRadius() {
        int s = Math.max(maxX - minX + 1, Math.max(maxY - minY + 1, maxZ - minZ + 1));
        return Math.max(1.0D, s / 2.0D);
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
