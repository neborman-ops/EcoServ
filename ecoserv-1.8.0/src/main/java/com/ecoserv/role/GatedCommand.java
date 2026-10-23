package com.ecoserv.role;

import com.ecoserv.config.EcoConfig;
import com.ecoserv.util.ChatUtil;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Обёртка вокруг любой команды сервера (ставится при старте, см. {@link CommandGate}).
 * <ul>
 * <li>RCON: ванильный 1.12 выполняет команды RCON в своём потоке — это гонки и падения модов.
 * Обёртка переносит выполнение в главный поток сервера и ждёт результат, поэтому ответ
 * команды всё равно попадает в RCON.</li>
 * <li>Роли: игрок с ролью, где команда в списке, может её выполнить без OP.</li>
 * <li>Карта Xaero: роль с телепортом по карте разрешает «/tp @p x y z» только для себя.</li>
 * </ul>
 * Всё остальное без изменений передаётся исходной команде.
 */
public class GatedCommand implements ICommand {

    private final ICommand inner;

    public GatedCommand(ICommand inner) {
        this.inner = inner;
    }

    public ICommand inner() {
        return inner;
    }

    @Override
    public String getName() {
        return inner.getName();
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return inner.getUsage(sender);
    }

    @Override
    public List<String> getAliases() {
        return inner.getAliases();
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        if (inner.checkPermission(server, sender)) {
            return true;
        }
        if (!(sender instanceof EntityPlayerMP)) {
            return false;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        return RoleManager.allowsCommand(player.getUniqueID(), inner.getName())
                || XaeroTp.isTeleportCommand(inner.getName()) && RoleManager.xaeroTp(player.getUniqueID());
    }

    @Override
    public void execute(final MinecraftServer server, final ICommandSender sender, final String[] args)
            throws CommandException {
        if (EcoConfig.rconMainThread && server != null && !server.isCallingFromMinecraftThread()) {
            runOnMainThread(server, sender, args);
            return;
        }
        executeNow(server, sender, args);
    }

    private void executeNow(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (sender instanceof EntityPlayerMP && !inner.checkPermission(server, sender)) {
            EntityPlayerMP player = (EntityPlayerMP) sender;
            if (RoleManager.allowsCommand(player.getUniqueID(), inner.getName())) {
                // селекторы @p/@s без OP не работают — подставляем ник самого игрока
                inner.execute(server, sender, XaeroTp.replaceSelf(args, player));
                return;
            }
            if (XaeroTp.isTeleportCommand(inner.getName()) && RoleManager.xaeroTp(player.getUniqueID())) {
                XaeroTp.handle(player, args);
                return;
            }
            throw new CommandException("commands.generic.permission");
        }
        inner.execute(server, sender, args);
    }

    /** Выполнить в главном потоке сервера и дождаться (для RCON). */
    private void runOnMainThread(final MinecraftServer server, final ICommandSender sender, final String[] args)
            throws CommandException {
        Future<Object> future = server.callFromMainThread(new java.util.concurrent.Callable<Object>() {
            @Override
            public Object call() throws Exception {
                executeNow(server, sender, args);
                return null;
            }
        });
        try {
            future.get(Math.max(1, EcoConfig.rconTimeoutSeconds), TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof CommandException) {
                throw (CommandException) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new CommandException("commands.generic.exception");
        } catch (TimeoutException e) {
            ChatUtil.sendRaw(sender, "Команда поставлена в очередь сервера, но не успела выполниться за "
                    + EcoConfig.rconTimeoutSeconds + " с (сервер занят).");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                          @Nullable BlockPos targetPos) {
        return inner.getTabCompletions(server, sender, args, targetPos);
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        return inner.isUsernameIndex(args, index);
    }

    @Override
    public int compareTo(ICommand other) {
        return inner.compareTo(other instanceof GatedCommand ? ((GatedCommand) other).inner : other);
    }
}
