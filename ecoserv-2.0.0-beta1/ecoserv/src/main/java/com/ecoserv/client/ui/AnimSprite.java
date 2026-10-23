package com.ecoserv.client.ui;

import com.ecoserv.gui.GuiUtil;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Анимированные иконки окна магазина: PNG-лента 16×(16·кадров) в textures/icons. */
@SideOnly(Side.CLIENT)
public final class AnimSprite {
   // до констант: их конструкторы кладут себя сюда
   private static final java.util.Map<String, AnimSprite> BY_NAME = new java.util.HashMap<>();
   public static final AnimSprite AUCTION = new AnimSprite("anim_auction", 8, 120);
   public static final AnimSprite PROFILE = new AnimSprite("anim_profile", 8, 140);
   public static final AnimSprite BANK = new AnimSprite("anim_bank", 8, 110);
   public static final AnimSprite HISTORY = new AnimSprite("anim_history", 8, 180);
   public static final AnimSprite PLAYERS = new AnimSprite("anim_players", 8, 160);
   public static final AnimSprite PRICES = new AnimSprite("anim_prices", 8, 130);
   public static final AnimSprite SERVER = new AnimSprite("anim_server", 8, 90);
   public static final AnimSprite MONEY = new AnimSprite("anim_money", 8, 120);
   public static final AnimSprite ROLE = new AnimSprite("anim_role", 8, 130);
   public static final AnimSprite HOURGLASS = new AnimSprite("anim_hourglass", 8, 200);
   public static final AnimSprite TRADE = new AnimSprite("anim_trade", 8, 110);
   public static final AnimSprite PORTAL = new AnimSprite("anim_portal", 8, 120);
   public static final AnimSprite EXPAND = new AnimSprite("anim_expand", 8, 120);
   public static final AnimSprite HIDDEN = new AnimSprite("anim_hidden", 8, 150);
   public static final AnimSprite HIDDEN_OPEN = new AnimSprite("anim_hidden_open", 8, 150);
   public static final AnimSprite PLANET = new AnimSprite("anim_planet", 8, 160);
   public static final AnimSprite TEAM = new AnimSprite("anim_team", 8, 140);
   public static final AnimSprite CREATE = new AnimSprite("anim_create", 8, 140);
   public static final AnimSprite COMPASS = new AnimSprite("anim_compass", 8, 150);
   public static final AnimSprite ADMIN = new AnimSprite("anim_admin", 8, 130);
   public static final AnimSprite BOOK = new AnimSprite("anim_book", 8, 170);
   public static final AnimSprite QUEST_BOOK = new AnimSprite("anim_quest_book", 8, 170);
   public static final AnimSprite KEY = new AnimSprite("anim_key", 8, 120);
   public static final AnimSprite MAP = new AnimSprite("anim_map", 8, 220);
   public static final AnimSprite FEATHER = new AnimSprite("anim_feather", 8, 150);
   public static final AnimSprite SKY = new AnimSprite("anim_sky", 8, 220);
   public static final AnimSprite GLITCH = new AnimSprite("anim_glitch", 8, 110);
   public static final AnimSprite GEAR = new AnimSprite("anim_gear", 8, 110);
   public static final AnimSprite CAKE = new AnimSprite("anim_cake", 8, 140);
   public static final AnimSprite RACE = new AnimSprite("anim_race", 8, 140);
   public static final AnimSprite ORDERS = new AnimSprite("anim_orders", 8, 160);
   public static final AnimSprite MARKET = new AnimSprite("anim_market", 8, 120);
   public static final AnimSprite VENDING = new AnimSprite("anim_vending", 8, 140);
   public static final AnimSprite BACKPACK = new AnimSprite("anim_backpack", 8, 160);
   public static final AnimSprite BAN = new AnimSprite("anim_ban", 8, 120);
   public static final AnimSprite BOMB = new AnimSprite("anim_bomb", 8, 110);
   public static final AnimSprite PICKAXE = new AnimSprite("anim_pickaxe", 8, 110);
   public static final AnimSprite WAND = new AnimSprite("anim_wand", 8, 120);
   public static final AnimSprite CUBE = new AnimSprite("anim_cube", 8, 130);
   public static final AnimSprite ANVIL = new AnimSprite("anim_anvil", 8, 110);
   public static final AnimSprite DISK = new AnimSprite("anim_disk", 8, 130);
   public static final AnimSprite POWER = new AnimSprite("anim_power", 8, 120);
   public static final AnimSprite MEGAPHONE = new AnimSprite("anim_megaphone", 8, 150);
   public static final AnimSprite TERMINAL = new AnimSprite("anim_terminal", 8, 160);
   public static final AnimSprite RELOAD = new AnimSprite("anim_reload", 8, 100);
   public static final AnimSprite SAVE = new AnimSprite("anim_save", 8, 160);
   private final ResourceLocation texture;
   private final int frames;
   private final int frameMs;

   public AnimSprite(String name, int frames, int frameMs) {
      this.texture = new ResourceLocation("ecoserv", "textures/icons/" + name + ".png");
      this.frames = Math.max(1, frames);
      this.frameMs = Math.max(30, frameMs);
      BY_NAME.put(name, this);
   }

   /** Иконка по имени из метки {@code ecoAnim} (только те, что лежат в моде), иначе null. */
   @javax.annotation.Nullable
   public static AnimSprite byName(String name) {
      return BY_NAME.get(name);
   }

   /** Нарисовать 16×16; animate=false — первый кадр (неактивная вкладка). */
   public void draw(int x, int y, boolean animate) {
      int frame = animate ? (int)(System.currentTimeMillis() / (long)this.frameMs % (long)this.frames) : 0;
      GuiUtil.bind(this.texture);
      GlStateManager.enableBlend();
      Gui.drawModalRectWithCustomSizedTexture(x, y, 0.0F, (float)(frame * 16), 16, 16, 16.0F, (float)(16 * this.frames));
   }
}
