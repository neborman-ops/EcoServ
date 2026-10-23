package com.ecoserv.auth;

import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /login, /register, /changepassword и админская /auth (2.0). */
public class CommandAuth extends CommandBase {

    public static final int LOGIN = 0;
    public static final int REGISTER = 1;
    public static final int CHANGE = 2;
    public static final int ADMIN = 3;

    private final int kind;

    public CommandAuth(int kind) {
        this.kind = kind;
    }

    @Override
    public String getName() {
        switch (kind) {
            case LOGIN:
                return "login";
            case REGISTER:
                return "register";
            case CHANGE:
                return "changepassword";
            default:
                return "auth";
        }
    }

    @Override
    public List<String> getAliases() {
        switch (kind) {
            case LOGIN:
                return Arrays.asList("l", "логин", "войти");
            case REGISTER:
                return Arrays.asList("reg", "регистрация");
            case CHANGE:
                return Collections.singletonList("сменитьпароль");
            default:
                return Collections.emptyList();
        }
    }

    @Override
    public String getUsage(ICommandSender sender) {
        switch (kind) {
            case LOGIN:
                return "/login <пароль>";
            case REGISTER:
                return "/register <пароль> <пароль ещё раз>";
            case CHANGE:
                return "/changepassword <старый> <новый>";
            default:
                return "/auth reset <ник> | /auth info <ник> | /auth mode <auto|on|off>";
        }
    }

    @Override
    public int getRequiredPermissionLevel() {
        return kind == ADMIN ? 3 : 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return kind == ADMIN ? Perms.isAdmin(sender) : sender instanceof EntityPlayerMP;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (kind == ADMIN) {
            admin(sender, args);
            return;
        }
        EntityPlayerMP p = getCommandSenderAsPlayer(sender);
        if (!AuthManager.enabled()) {
            ChatUtil.send(p, "&7Вход по паролю на этом сервере выключен.");
            return;
        }
        String err;
        if (kind == LOGIN) {
            if (args.length < 1) {
                ChatUtil.send(p, "&c" + getUsage(p));
                return;
            }
            err = AuthManager.login(p, args[0]);
        } else if (kind == REGISTER) {
            if (args.length < 2) {
                ChatUtil.send(p, "&c" + getUsage(p));
                return;
            }
            err = AuthManager.register(p, args[0], args[1]);
        } else {
            if (args.length < 2) {
                ChatUtil.send(p, "&c" + getUsage(p));
                return;
            }
            err = AuthManager.change(p, args[0], args[1]);
            if (err == null) {
                ChatUtil.send(p, "&aПароль изменён.");
            }
        }
        if (err != null) {
            ChatUtil.send(p, err);
        }
    }

    private void admin(ICommandSender sender, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (sub.equals("mode") && args.length > 1) {
            AuthManager.settings().mode = args[1].toLowerCase(Locale.ROOT);
            AuthManager.saveSettings();
            ChatUtil.send(sender, "&aРежим входа: " + args[1] + " (сейчас " + (AuthManager.enabled() ? "&aвключён" : "&7выключен") + "&a)");
            return;
        }
        if ((sub.equals("reset") || sub.equals("info")) && args.length > 1) {
            UUID id = PlayerUtil.uuidOf(args[1]);
            if (id == null) {
                ChatUtil.send(sender, "&cИгрок не найден.");
                return;
            }
            if (sub.equals("reset")) {
                ChatUtil.send(sender, AuthManager.reset(id) ? "&aАккаунт сброшен — при входе " + args[1] + " придумает новый пароль."
                        : "&7У игрока нет аккаунта.");
                return;
            }
            AuthAccount a = AuthManager.get(id);
            ChatUtil.send(sender, a == null ? "&7Не зарегистрирован." : "&7" + a.name + ": создан "
                    + com.ecoserv.util.Fmt.timestamp(a.created) + ", вход " + com.ecoserv.util.Fmt.timestamp(a.lastLogin));
            return;
        }
        ChatUtil.send(sender, "&7Аккаунтов: " + AuthManager.accountCount() + ", вход по паролю "
                + (AuthManager.enabled() ? "&aвключён" : "&7выключен") + "&7. " + getUsage(sender));
    }
}
