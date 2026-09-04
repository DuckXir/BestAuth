package io.opencode.bestauth.update;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateChecker {

    private final JavaPlugin plugin;
    private final String githubUrl;
    private String yeniSurum;
    private boolean guncellemeVar;

    public UpdateChecker(JavaPlugin plugin, String githubUrl) {
        this.plugin = plugin;
        this.githubUrl = githubUrl;
        this.guncellemeVar = false;
    }

    public void kontrol() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URL url = new URL(githubUrl);
                HttpURLConnection baglanti = (HttpURLConnection) url.openConnection();
                baglanti.setRequestMethod("GET");
                baglanti.setConnectTimeout(5000);
                baglanti.setReadTimeout(5000);

                int responseCode = baglanti.getResponseCode();
                if (responseCode == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(baglanti.getInputStream()));
                    StringBuilder yanit = new StringBuilder();
                    String satir;
                    while ((satir = reader.readLine()) != null) {
                        yanit.append(satir);
                    }
                    reader.close();

                    String icerik = yanit.toString();
                    int surumBaslangici = icerik.indexOf("\"tag_name\":\"");
                    if (surumBaslangici != -1) {
                        surumBaslangici += 12;
                        int surumBitisi = icerik.indexOf("\"", surumBaslangici);
                        if (surumBitisi != -1) {
                            yeniSurum = icerik.substring(surumBaslangici, surumBitisi);
                            String mevcutSurum = plugin.getDescription().getVersion();

                            if (!mevcutSurum.equals(yeniSurum)) {
                                guncellemeVar = true;
                                plugin.getLogger().info("Yeni guncelleme mevcut: " + yeniSurum);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Guncelleme kontrolu basarisiz: " + e.getMessage());
            }
        });
    }

    public void bildirimGonder(Player oyuncu) {
        if (guncellemeVar && yeniSurum != null) {
            oyuncu.sendMessage(ChatColor.YELLOW + "----------------------------------------");
            oyuncu.sendMessage(ChatColor.GREEN + "BestAuth icin yeni guncelleme mevcut!");
            oyuncu.sendMessage(ChatColor.YELLOW + "Mevcut: " + ChatColor.RED + plugin.getDescription().getVersion());
            oyuncu.sendMessage(ChatColor.YELLOW + "Yeni: " + ChatColor.GREEN + yeniSurum);
            oyuncu.sendMessage(ChatColor.YELLOW + "----------------------------------------");
        }
    }

    public boolean guncellemeVarMi() {
        return guncellemeVar;
    }

    public String getYeniSurum() {
        return yeniSurum;
    }
}

