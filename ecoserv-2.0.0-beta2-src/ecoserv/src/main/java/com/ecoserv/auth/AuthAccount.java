package com.ecoserv.auth;

/** Аккаунт: соль и хэш пароля (PBKDF2-SHA256), последний IP и вход. Пароль в открытом виде нигде не хранится. */
public class AuthAccount {
    public String name = "";
    public String salt = "";
    public String hash = "";
    public int iterations = 120000;
    public String lastIp = "";
    public long lastLogin;
    public long created;
    public String regIp = "";
}
