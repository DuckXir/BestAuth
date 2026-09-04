package io.opencode.bestauth.lang;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class MessageManager {

    private final File pluginKlasoru;
    private String aktifDil;
    private FileConfiguration dilConfig;
    private final Map<String, FileConfiguration> dilCache = new HashMap<>();

    public MessageManager(File pluginKlasoru, String varsayilanDil) {
        this.pluginKlasoru = pluginKlasoru;
        this.aktifDil = (varsayilanDil != null && !varsayilanDil.isEmpty()) ? varsayilanDil.toLowerCase() : "tr";
        dilleriYukle();
    }

    private void dilleriYukle() {
        String[] desteklenenDiller = {"tr", "en", "de", "es"};
        File langFolder = new File(pluginKlasoru, "lang");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }

        for (String dil : desteklenenDiller) {
            File dilDosyasi = new File(langFolder, dil + ".yml");
            if (!dilDosyasi.exists()) {
                try (InputStream is = getClass().getClassLoader().getResourceAsStream("lang/" + dil + ".yml")) {
                    if (is != null) {
                        byte[] buffer = is.readAllBytes();
                        java.nio.file.Files.write(dilDosyasi.toPath(), buffer);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        dilCache.clear();
        dilConfig = dilDosyasiniYukle(aktifDil);
    }

    public FileConfiguration dilDosyasiniYukle(String dil) {
        if (dilCache.containsKey(dil)) {
            return dilCache.get(dil);
        }
        File dilDosyasi = new File(pluginKlasoru, "lang/" + dil + ".yml");
        FileConfiguration config;
        if (dilDosyasi.exists()) {
            try (java.io.Reader reader = new java.io.InputStreamReader(new java.io.FileInputStream(dilDosyasi), StandardCharsets.UTF_8)) {
                config = YamlConfiguration.loadConfiguration(reader);
            } catch (Exception e) {
                config = YamlConfiguration.loadConfiguration(dilDosyasi);
            }
        } else {
            InputStream is = getClass().getClassLoader().getResourceAsStream("lang/" + dil + ".yml");
            if (is != null) {
                config = YamlConfiguration.loadConfiguration(new InputStreamReader(is, StandardCharsets.UTF_8));
            } else {
                config = new YamlConfiguration();
            }
        }

        InputStream defaultIs = getClass().getClassLoader().getResourceAsStream("lang/tr.yml");
        if (defaultIs != null) {
            FileConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultIs, StandardCharsets.UTF_8));
            config.setDefaults(defaults);
        }

        dilCache.put(dil, config);
        return config;
    }

    public void dilDegistir(String yeniDil) {
        this.aktifDil = yeniDil.toLowerCase();
        this.dilConfig = dilDosyasiniYukle(aktifDil);
    }

    public void yenidenYukle(String varsayilanDil) {
        this.aktifDil = (varsayilanDil != null && !varsayilanDil.isEmpty()) ? varsayilanDil.toLowerCase() : "tr";
        dilleriYukle();
    }

    public String getMesaj(String yol) {
        return getMesaj(yol, (String[]) null);
    }

    public String getMesaj(String yol, String... degerler) {
        if (dilConfig == null) {
            dilConfig = dilDosyasiniYukle(aktifDil);
        }

        String mesaj = dilConfig != null ? dilConfig.getString(yol) : null;
        if (mesaj == null) {
            return "&cMesaj bulunamadi: " + yol;
        }

        if (degerler != null) {
            for (int i = 0; i < degerler.length - 1; i += 2) {
                mesaj = mesaj.replace(degerler[i], degerler[i + 1] != null ? degerler[i + 1] : "");
            }
        }

        return mesaj;
    }

    public String renklendir(String mesaj) {
        return ChatColor.translateAlternateColorCodes('&', mesaj);
    }

    public String getRenkliMesaj(String yol, String... degerler) {
        return renklendir(getMesaj(yol, degerler));
    }

    public String getAktifDil() {
        return aktifDil;
    }

    public FileConfiguration getDilConfig() {
        return dilConfig;
    }
}

