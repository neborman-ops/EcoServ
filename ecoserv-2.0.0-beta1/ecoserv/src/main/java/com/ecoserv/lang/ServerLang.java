package com.ecoserv.lang;

import com.ecoserv.EcoServ;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Перевод серверных текстов мода (1.9.0): окна, сообщения в чат, строки над хотбаром.
 * <p>Тексты мода пишутся по-русски. Для игрока с другим языком (клиент сообщает его в настройках) строки
 * переводятся по словарю assets/ecoserv/lang/server/&lt;язык&gt;.json: русские куски (литералы из исходников,
 * разрезанные по цветовым кодам) заменяются переводом, а имена, числа и прочее, что подставлено в текст, остаются.</p>
 */
public final class ServerLang {

    /** Языки, для которых есть словари (остальным — английский, кроме близких к русскому). */
    private static final String[] AVAILABLE = {"en", "uk", "de", "es", "fr", "pl", "pt", "zh"};
    /** Те, кому привычнее русский, чем английский, если своего словаря нет. */
    private static final Set<String> RUSSIAN_FAMILY = new HashSet<String>(Arrays.asList("ru", "be", "kk", "uz", "ky", "tg", "hy", "az", "ka", "tt", "ba", "cv", "sah", "os"));
    private static final Map<String, Dict> DICTS = new HashMap<String, Dict>();
    /** Не переводить значения этих полей окна (ввод игрока, id). */
    private static final Set<String> RAW = new HashSet<String>(Arrays.asList("id", "t", "val", "value", "pick", "reset",
            "back", "item", "icon", "search", "win", "rev"));

    private static final class Dict {
        /** Первые 2 буквы ключа (в нижнем регистре) -> записи, длинные первыми. */
        final Map<String, List<String[]>> index = new HashMap<String, List<String[]>>();
        int size;
    }

    private ServerLang() {
    }

    public static synchronized void load() {
        DICTS.clear();
        Gson gson = new Gson();
        for (String lang : AVAILABLE) {
            String path = "/assets/ecoserv/lang/server/" + lang + ".json";
            try (InputStream in = ServerLang.class.getResourceAsStream(path)) {
                if (in == null) {
                    continue;
                }
                Map<String, String> map;
                try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    map = gson.fromJson(r, new TypeToken<HashMap<String, String>>() {
                    }.getType());
                }
                if (map == null || map.isEmpty()) {
                    continue;
                }
                Dict d = new Dict();
                for (Map.Entry<String, String> e : map.entrySet()) {
                    String k = e.getKey();
                    if (k == null || k.length() < 2 || e.getValue() == null || e.getValue().isEmpty()) {
                        continue;
                    }
                    String key = k.substring(0, 2).toLowerCase(Locale.ROOT);
                    d.index.computeIfAbsent(key, x -> new ArrayList<String[]>()).add(new String[]{k, e.getValue()});
                    d.size++;
                }
                for (List<String[]> l : d.index.values()) {
                    l.sort((a, b) -> Integer.compare(b[0].length(), a[0].length()));
                }
                DICTS.put(lang, d);
            } catch (Exception e) {
                EcoServ.LOG.warn("Словарь перевода {} не прочитан: {}", lang, e.toString());
            }
        }
        if (!DICTS.isEmpty()) {
            EcoServ.LOG.info("Переводы серверных текстов: {}", DICTS.keySet());
        }
    }

    /** Код словаря для языка клиента (en_us -> en), null — показывать по-русски. */
    @Nullable
    public static String dictFor(@Nullable String locale) {
        if (locale == null || locale.isEmpty()) {
            return null;
        }
        String lang = locale.toLowerCase(Locale.ROOT);
        int u = lang.indexOf('_');
        if (u > 0) {
            lang = lang.substring(0, u);
        }
        if ("ru".equals(lang)) {
            return null;
        }
        if (DICTS.containsKey(lang)) {
            return lang;
        }
        if (RUSSIAN_FAMILY.contains(lang)) {
            return null;
        }
        return DICTS.containsKey("en") ? "en" : null;
    }

    @Nullable
    public static String dictFor(@Nullable EntityPlayerMP player) {
        return player == null ? null : dictFor(com.ecoserv.prefs.PlayerPrefs.locale(player.getUniqueID()));
    }

    private static boolean letter(char c) {
        return Character.isLetter(c);
    }

    private static boolean cyr(char c) {
        return (c >= 'А' && c <= 'я') || c == 'Ё' || c == 'ё';
    }

    /** Перевести строку (русские куски по словарю). */
    public static String tr(@Nullable String text, @Nullable String dict) {
        if (text == null || dict == null || text.isEmpty()) {
            return text;
        }
        Dict d = DICTS.get(dict);
        if (d == null) {
            return text;
        }
        StringBuilder out = null;
        int n = text.length();
        int i = 0;
        int copied = 0;
        while (i < n) {
            char c = text.charAt(i);
            if (cyr(c) && (i == 0 || !letter(text.charAt(i - 1))) && i + 1 < n) {
                List<String[]> cands = d.index.get(text.substring(i, i + 2).toLowerCase(Locale.ROOT));
                if (cands != null) {
                    String[] hit = null;
                    for (String[] e : cands) {
                        String k = e[0];
                        if (text.regionMatches(i, k, 0, k.length())) {
                            int end = i + k.length();
                            if (end >= n || !letter(text.charAt(end)) || !letter(k.charAt(k.length() - 1))) {
                                hit = e;
                                break;
                            }
                        }
                    }
                    if (hit != null) {
                        if (out == null) {
                            out = new StringBuilder(n + 16);
                        }
                        out.append(text, copied, i).append(hit[1]);
                        i += hit[0].length();
                        copied = i;
                        continue;
                    }
                }
            }
            i++;
        }
        if (out == null) {
            return text;
        }
        out.append(text, copied, n);
        return out.toString();
    }

    public static String tr(@Nullable EntityPlayerMP player, @Nullable String text) {
        return tr(text, dictFor(player));
    }

    /** Перевести окно перед отправкой игроку. */
    public static void translate(NBTTagCompound tag, @Nullable String dict) {
        if (dict == null || tag == null) {
            return;
        }
        for (String key : new ArrayList<String>(tag.getKeySet())) {
            if (RAW.contains(key)) {
                continue;
            }
            NBTBase v = tag.getTag(key);
            if (v instanceof NBTTagString) {
                String s = ((NBTTagString) v).getString();
                String t = tr(s, dict);
                if (!t.equals(s)) {
                    tag.setString(key, t);
                }
            } else if (v instanceof NBTTagCompound) {
                translate((NBTTagCompound) v, dict);
            } else if (v instanceof NBTTagList) {
                NBTTagList list = (NBTTagList) v;
                for (int i = 0; i < list.tagCount(); i++) {
                    NBTBase e = list.get(i);
                    if (e instanceof NBTTagString) {
                        String s = ((NBTTagString) e).getString();
                        String t = tr(s, dict);
                        if (!t.equals(s)) {
                            list.set(i, new NBTTagString(t));
                        }
                    } else if (e instanceof NBTTagCompound) {
                        translate((NBTTagCompound) e, dict);
                    }
                }
            }
        }
    }
}
