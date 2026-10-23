package com.ecoserv.role;

import com.ecoserv.config.EcoConfig;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.TeleportRules;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.CommandException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Телепорт по карте Xaero (Minimap / World Map) для игроков с ролью, где он разрешён.
 * Карта отправляет в чат «/tp @p x y z» (или «/tp @s …», «/tp ник …», «/tp x y z»,
 * вместо высоты бывает «~»). Разрешён только телепорт себя по координатам в своём мире —
 * телепортировать других или к другим игрокам роль не даёт.
 * Вместо /tp в настройках карты можно поставить «/xtp {x} {y} {z}».
 */
public final class XaeroTp {

    private static final Map<UUID, Long> LAST = new HashMap<UUID, Long>();

    private XaeroTp() {
    }

    public static boolean isTeleportCommand(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return n.equals("tp") || n.equals("teleport") || n.equals("minecraft:tp");
    }

    /** @p / @s → ник игрока (селекторы без OP не работают). */
    public static String[] replaceSelf(String[] args, EntityPlayerMP player) {
        String[] out = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            out[i] = "@p".equals(a) || "@s".equals(a) ? player.getName() : a;
        }
        return out;
    }

    private static boolean isSelf(String arg, EntityPlayerMP player) {
        return "@p".equals(arg) || "@s".equals(arg) || player.getName().equalsIgnoreCase(arg);
    }

    /** Разбор аргументов /tp от карты и сам телепорт. */
    public static void handle(EntityPlayerMP player, String[] args) throws CommandException {
        String[] coords;
        if (args.length == 3) {
            coords = args;
        } else if (args.length >= 4 && isSelf(args[0], player)) {
            coords = new String[]{args[1], args[2], args[3]};
        } else {
            throw new CommandException("commands.generic.permission");
        }
        teleport(player, coords[0], coords[1], coords[2]);
    }

    /** /xtp x y z — то же самое напрямую. */
    public static void teleport(EntityPlayerMP player, String sx, String sy, String sz) throws CommandException {
        if (!Perms_isAdmin(player)) {
            Long last = LAST.get(player.getUniqueID());
            long wait = last == null ? 0L
                    : last + EcoConfig.rolesXaeroCooldownSeconds * 1000L - System.currentTimeMillis();
            if (wait > 0L) {
                ChatUtil.actionBar(player, "&eТелепорт по карте: подожди " + ((wait + 999L) / 1000L) + " с");
                return;
            }
        }
        double x = coordinate(sx, player.posX, true);
        double z = coordinate(sz, player.posZ, true);
        double y;
        if (sy == null || sy.equals("~") || sy.isEmpty()) {
            y = topY(player.world, (int) Math.floor(x), (int) Math.floor(z), player.posY);
        } else {
            y = coordinate(sy, player.posY, false);
        }
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)) {
            throw new CommandException("commands.generic.num.invalid", sx + " " + sy + " " + sz);
        }
        if (Math.abs(x) > 29_999_000D || Math.abs(z) > 29_999_000D || y < -64D || y > 1024D) {
            throw new CommandException("commands.generic.num.invalid", sx + " " + sy + " " + sz);
        }
        String reason = TeleportRules.check(player, player.dimension, x, y, z);
        if (reason != null) {
            ChatUtil.send(player, "&c" + reason);
            return;
        }
        if (player.isRiding()) {
            player.dismountRidingEntity();
        }
        player.connection.setPlayerLocation(x, y, z, player.rotationYaw, player.rotationPitch);
        player.fallDistance = 0.0F;
        LAST.put(player.getUniqueID(), System.currentTimeMillis());
        ChatUtil.actionBar(player, "&a➜ &f" + (int) Math.floor(x) + " " + (int) Math.floor(y) + " " + (int) Math.floor(z));
    }

    private static boolean Perms_isAdmin(EntityPlayerMP player) {
        return com.ecoserv.util.Perms.isAdmin(player);
    }

    /** Число, «~» или «~5». Целые X/Z — в центр блока, как ванильный /tp. */
    private static double coordinate(String text, double base, boolean center) {
        if (text == null) {
            return Double.NaN;
        }
        String s = text.trim().replace(',', '.');
        boolean relative = s.startsWith("~");
        if (relative) {
            s = s.substring(1);
        }
        double value;
        if (s.isEmpty()) {
            value = 0.0D;
        } else {
            try {
                value = Double.parseDouble(s);
            } catch (NumberFormatException e) {
                return Double.NaN;
            }
        }
        if (relative) {
            return base + value;
        }
        if (center && s.indexOf('.') < 0) {
            value += 0.5D;
        }
        return value;
    }

    /** Высота для «~»: первый твёрдый блок сверху (в аду — ниже крыши). */
    private static double topY(World world, int x, int z, double fallback) {
        BlockPos column = new BlockPos(x, 0, z);
        int top = world.provider.isNether() ? 120 : world.getHeight(column).getY() + 1;
        top = Math.min(top, world.getHeight() - 2);
        for (int y = top; y > 1; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            IBlockState below = world.getBlockState(pos.down());
            if (below.getMaterial().isSolid() && below.getMaterial() != Material.LAVA
                    && world.isAirBlock(pos) && world.isAirBlock(pos.up())) {
                return y;
            }
        }
        return fallback;
    }

    public static void forget(UUID player) {
        LAST.remove(player);
    }
}
