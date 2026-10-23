package com.ecoserv.auth;

/** Настройки входа по паролю (2.0): world/ecoserv/auth_settings.json. */
public class AuthSettings {
    /** auto — только на сервере без проверки лицензии (online-mode=false); on — всегда; off — выключено. */
    public String mode = "auto";
    /** Повторный вход с того же IP в течение N минут — без пароля. 0 — пароль каждый раз. */
    public int sessionMinutes = 30;
    /** Сколько секунд даётся на вход, потом кик. */
    public int timeoutSeconds = 120;
    /** Неверных паролей подряд до кика. */
    public int maxAttempts = 5;
    public int minLength = 6;
    /** Сколько аккаунтов можно зарегистрировать с одного IP (0 — без ограничения). */
    public int maxPerIp = 3;
}
