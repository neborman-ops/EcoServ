package com.ecoserv;

/** Константы мода. */
public final class Ref {

    public static final String MODID = "ecoserv";
    public static final String NAME = "EcoServ";
    /** Подставляется при сборке из gradle.properties (1.5.0 или 1.5.0-quests, см. build.gradle). */
    public static final String VERSION = "@VERSION@";
    public static final String MC_VERSIONS = "[1.12.2]";

    /** Все интеграции опциональны и загружаются через рефлексию. */
    public static final String DEPENDENCIES = "after:ftblib;after:ftbmoney;after:ftbquests;after:ftbutilities";

    public static final String CLIENT_PROXY = "com.ecoserv.proxy.ClientProxy";
    public static final String COMMON_PROXY = "com.ecoserv.proxy.CommonProxy";

    public static final String CHANNEL = "ecoserv";

    private Ref() {
    }
}
