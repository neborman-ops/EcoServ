package com.ecoserv.role;

import com.ecoserv.EcoServ;
import com.ecoserv.config.EcoConfig;
import net.minecraft.command.CommandHandler;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandManager;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Оборачивает все команды сервера в {@link GatedCommand} после старта сервера
 * (когда все моды уже зарегистрировали свои команды). FTB Utilities делает то же самое
 * для рангов — наша обёртка ложится поверх и отдаёт ей всё, что не касается ролей и RCON.
 */
public final class CommandGate {

    private CommandGate() {
    }

    public static void wrapAll(MinecraftServer server) {
        if (!EcoConfig.wrapCommands || server == null) {
            return;
        }
        ICommandManager manager = server.getCommandManager();
        if (!(manager instanceof CommandHandler)) {
            EcoServ.LOG.warn("Менеджер команд нестандартный ({}), роли и RCON-обёртка не подключены",
                    manager == null ? "null" : manager.getClass().getName());
            return;
        }
        Map<String, ICommand> map = ((CommandHandler) manager).getCommands();
        Map<ICommand, GatedCommand> wrappers = new IdentityHashMap<ICommand, GatedCommand>();
        int count = 0;
        for (Map.Entry<String, ICommand> entry : map.entrySet()) {
            ICommand command = entry.getValue();
            if (command == null || command instanceof GatedCommand) {
                continue;
            }
            GatedCommand wrapped = wrappers.get(command);
            if (wrapped == null) {
                wrapped = new GatedCommand(command);
                wrappers.put(command, wrapped);
                count++;
            }
            entry.setValue(wrapped);
        }
        replaceInCommandSet((CommandHandler) manager, wrappers);
        EcoServ.LOG.info("Команды: {} обёрнуто (RCON в главном потоке: {}, доступ по ролям)", count,
                EcoConfig.rconMainThread ? "да" : "нет");
    }

    /** Список для /help — чтобы игрок с ролью видел разрешённые ему команды. */
    @SuppressWarnings("unchecked")
    private static void replaceInCommandSet(CommandHandler handler, Map<ICommand, GatedCommand> wrappers) {
        try {
            for (Field field : CommandHandler.class.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || !Set.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                Set<ICommand> set = (Set<ICommand>) field.get(handler);
                if (set == null) {
                    continue;
                }
                List<ICommand> replaced = new ArrayList<ICommand>();
                for (ICommand command : set) {
                    GatedCommand wrapped = wrappers.get(command);
                    replaced.add(wrapped != null ? wrapped : command);
                }
                set.clear();
                set.addAll(replaced);
            }
        } catch (Throwable t) {
            EcoServ.LOG.debug("Не удалось обновить список команд для /help: {}", t.toString());
        }
    }
}
