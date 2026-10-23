package com.ecoserv.lighting;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Освещение мира (2.0 beta3) — по образцу свойств Lighting в Roblox Studio: Appearance, Data, Exposure, Fog и
 * дочерние эффекты Atmosphere, Sky, Bloom, Blur, ColorCorrection, DepthOfField, SunRays, Clouds.
 * Цвета — 0xRRGGBB.
 */
public class LightingProfile {

    /** 2.0 beta4: включено по умолчанию — базовые «шейдеры», чтобы все видели свечение и цветной свет. */
    public boolean enabled = true;
    /** Формат файла: 2 — beta4 (базовое освещение по умолчанию). */
    public int format = 2;
    /** Compatibility / Voxel / ShadowMap / Future — сколько эффектов включено. */
    public String technology = "ShadowMap";

    // Appearance
    public int ambient = 0x464646;
    public double brightness = 2.0D;
    public int colorShiftBottom = 0x000000;
    public int colorShiftTop = 0x000000;
    public double environmentDiffuseScale = 1.0D;
    public double environmentSpecularScale = 1.0D;
    public boolean globalShadows = true;
    public int outdoorAmbient = 0x464646;
    public double shadowSoftness = 0.2D;

    // Data
    public boolean lockTime;
    public double clockTime = 14.5D;
    public double geographicLatitude = 41.733D;

    // Exposure
    public double exposureCompensation = 0.0D;

    // Fog
    public boolean customFog;
    public int fogColor = 0xC0C0C0;
    public double fogStart = 0.0D;
    public double fogEnd = 100000.0D;

    // Atmosphere
    public boolean atmosphere;
    public double atmDensity = 0.3D;
    public double atmOffset = 0.25D;
    public int atmColor = 0xC7C7C7;
    public int atmDecay = 0x5C3C0D;
    public double atmGlare = 0.0D;
    public double atmHaze = 0.0D;

    // Sky
    public boolean customSky;
    public boolean celestialBodiesShown = true;
    public double sunAngularSize = 21.0D;
    public double moonAngularSize = 11.0D;
    public int starCount = 3000;
    public int skyTop = 0x5A8CFF;
    public int skyHorizon = 0xB4D2FF;

    // Bloom
    public boolean bloom = true;
    public double bloomIntensity = 0.35D;
    public double bloomSize = 18.0D;
    public double bloomThreshold = 2.6D;

    // Blur
    public boolean blur;
    public double blurSize = 24.0D;

    // ColorCorrection
    public boolean colorCorrection = true;
    public double ccBrightness = 0.0D;
    public double ccContrast = 0.05D;
    public double ccSaturation = 0.1D;
    public int ccTint = 0xFFFFFF;

    // DepthOfField
    public boolean depthOfField;
    public double dofFarIntensity = 0.75D;
    public double dofFocusDistance = 0.05D;
    public double dofInFocusRadius = 10.0D;
    public double dofNearIntensity = 0.75D;

    // SunRays
    public boolean sunRays;
    public double sunRaysIntensity = 0.25D;
    public double sunRaysSpread = 1.0D;

    // Clouds
    public boolean clouds;
    public double cloudCover = 0.5D;
    public double cloudDensity = 0.7D;
    public int cloudColor = 0xFFFFFF;
    public boolean hideVanillaClouds = true;

    /** Выключенное освещение (клиент до пакета сервера). */
    public static LightingProfile off() {
        LightingProfile p = new LightingProfile();
        p.enabled = false;
        return p;
    }

    public NBTTagCompound write() {
        NBTTagCompound t = new NBTTagCompound();
        t.setString("json", com.ecoserv.util.JsonUtil.GSON.toJson(this));
        return t;
    }

    public static LightingProfile read(NBTTagCompound t) {
        try {
            LightingProfile p = com.ecoserv.util.JsonUtil.GSON.fromJson(t.getString("json"), LightingProfile.class);
            return p == null ? off() : p;
        } catch (Throwable e) {
            return off();
        }
    }

    public LightingProfile copy() {
        return com.ecoserv.util.JsonUtil.GSON.fromJson(com.ecoserv.util.JsonUtil.GSON.toJson(this), LightingProfile.class);
    }
}
