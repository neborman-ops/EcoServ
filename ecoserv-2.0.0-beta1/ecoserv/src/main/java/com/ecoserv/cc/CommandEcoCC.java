package com.ecoserv.cc;

import com.ecoserv.tile.TileMarket;
import com.ecoserv.tile.TileVendor;
import com.ecoserv.tile.TileVending;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * /ecocc confirm|deny &lt;id&gt; — ответ на запрос компьютера (кнопки в чате);
 * /ecocc key &lt;секрет&gt; — смотря на свой терминал/склад/автомат: ключ для программ ComputerCraft
 * (/ecocc key - — убрать ключ).
 */
public class CommandEcoCC extends CommandBase {

    @Override
    public String getName() {
        return "ecocc";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("компьютер");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/ecocc key <секрет> | /ecocc confirm|deny <номер>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        if (args.length >= 2 && ("confirm".equalsIgnoreCase(args[0]) || "deny".equalsIgnoreCase(args[0]))) {
            int id;
            try {
                id = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                ChatUtil.send(player, "&cНомер запроса — число.");
                return;
            }
            CcRequests.answer(player, id, "confirm".equalsIgnoreCase(args[0]));
            return;
        }
        if (args.length >= 2 && "key".equalsIgnoreCase(args[0])) {
            BlockPos pos = PlayerUtil.lookingAt(player, 6.0D);
            TileEntity te = pos == null ? null : player.world.getTileEntity(pos);
            UUID owner = te instanceof TileMarket ? ((TileMarket) te).getOwner()
                    : te instanceof TileVendor ? ((TileVendor) te).getOwner()
                    : te instanceof TileVending ? ((TileVending) te).getOwner() : null;
            if (te == null || !(te instanceof TileMarket || te instanceof TileVendor || te instanceof TileVending)) {
                ChatUtil.send(player, "&cСмотри на свой торговый терминал, склад продавца или торговый автомат.");
                return;
            }
            if (!Perms.isAdmin(player) && (owner == null || !owner.equals(player.getUniqueID()))) {
                ChatUtil.send(player, "&cЭто не твой блок.");
                return;
            }
            String secret = "-".equals(args[1]) ? "" : args[1];
            CcKeys.set(CcKeys.blockKey(player.dimension, pos), secret);
            ChatUtil.send(player, secret.isEmpty() ? "&eКлюч убран: компьютерам доступно только чтение и запросы игрокам."
                    : "&aКлюч задан. В программе: &fperipheral.wrap(...).auth(\"" + secret.charAt(0) + "…\")"
                    + " &7— после этого компьютеру доступны действия владельца.");
            return;
        }
        ChatUtil.send(player, "&7" + getUsage(sender));
    }
}
