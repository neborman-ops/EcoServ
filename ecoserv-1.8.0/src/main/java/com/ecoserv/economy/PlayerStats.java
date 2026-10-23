package com.ecoserv.economy;

/** Профиль игрока: онлайн, ежедневки, статистика по магазину. */
public class PlayerStats {

    public long playtimeMillis;
    public long lastSeen;
    public long lastDailyDay = -1L;
    public int dailyStreak;
    public long totalEarned;
    public long totalSpent;
    public long totalSold;
    public long rewardProgressMillis;
    public boolean shopHintShown;
    /** Где игрок был в каждом мире в последний раз: x, y, z, yaw, pitch (для /world). */
    public java.util.Map<Integer, double[]> lastPositions = new java.util.HashMap<Integer, double[]>();

    public PlayerStats() {
    }
}
