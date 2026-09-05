package io.opencode.bestauth;

import io.opencode.bestauth.database.DatabaseManager;
import io.opencode.bestauth.lang.MessageManager;
import io.opencode.bestauth.hooks.VaultManager;
import io.opencode.bestauth.metrics.MetricsManager;
import io.opencode.bestauth.update.UpdateChecker;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.server.TabCompleteEvent;
import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

public final class Main extends JavaPlugin implements Listener, TabCompleter {

    private int girisSuresi;
    private boolean ipLogla;
    private int sifreMin, sifreMax;
    private int maksHesapSayisi;
    private int maksDeneme, hataGecikme;
    private boolean sifreFiltre;
    private boolean tabEngel;
    private boolean ipGirisAktif;
    private int ipEslesmeOktet;
    private List<String> karaListe;
    private boolean joinEnabled, leaveEnabled;
    private boolean rateLimitAktif;
    private int rateLimitDakika, rateLimitMaks;
    private boolean vaultOdulAktif;
    private double vaultOdulMiktari;
    private boolean sandboxModu;
    private boolean animsatmaAktif;
    private int animsatmaAralik;

    private File kayitDosya;
    private FileConfiguration kayitConfig;
    private DatabaseManager dbManager;
    private String veritabaniTipi;
    private MessageManager messageManager;
    private VaultManager vaultManager;
    private MetricsManager metricsManager;
    private UpdateChecker updateChecker;
    private final Set<UUID> dogrulanan = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> denemeSayisi = new ConcurrentHashMap<>();
    private final Map<UUID, Long> hataBekleme = new ConcurrentHashMap<>();
    private final Map<String, List<Long>> rateLimitMap = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        yukleConfig();

        if (!veritabaniTipi.equals("yaml")) {
            try {
                dbManager = new DatabaseManager(getDataFolder(), veritabaniTipi);
                if (veritabaniTipi.equals("mysql")) {
                    dbManager.setMysqlBilgileri(
                        getConfig().getString("mysql.host", "localhost"),
                        getConfig().getInt("mysql.port", 3306),
                        getConfig().getString("mysql.database", getConfig().getString("mysql.veritabani", "bestauth")),
                        getConfig().getString("mysql.username", getConfig().getString("mysql.kullanici", "root")),
                        getConfig().getString("mysql.password", getConfig().getString("mysql.sifre", ""))
                    );
                }
                dbManager.baglan();
                getLogger().info(veritabaniTipi.toUpperCase() + " database connected successfully!");
            } catch (Exception e) {
                getLogger().severe("Failed to connect to " + veritabaniTipi + " database! Falling back to YAML.");
                veritabaniTipi = "yaml";
                dbManager = null;
            }
        }

        if (veritabaniTipi.equals("yaml")) {
            kayitDosya = new File(getDataFolder(), "kayitli-oyuncular.yml");
            if (!kayitDosya.exists()) {
                try {
                    kayitDosya.createNewFile();
                } catch (IOException e) {
                    getLogger().severe("Could not create kayitli-oyuncular.yml!");
                }
            }
            kayitConfig = YamlConfiguration.loadConfiguration(kayitDosya);
            boolean guncellendi = false;
            for (String uid : tumUuidler()) {
                if (!kayitConfig.contains(uid + ".isim")) {
                    try {
                        OfflinePlayer off = Bukkit.getOfflinePlayer(UUID.fromString(uid));
                        String ad = off.getName();
                        if (ad != null) {
                            kayitConfig.set(uid + ".isim", ad);
                            guncellendi = true;
                        }
                    } catch (Exception ignored) {}
                }
            }
            if (guncellendi) kaydet();
        }

        getServer().getPluginManager().registerEvents(this, this);
        org.bukkit.command.PluginCommand bestAuthCmd = getCommand("bestauth");
        if (bestAuthCmd != null) bestAuthCmd.setTabCompleter(this);

        org.bukkit.command.PluginCommand resetCmd = getCommand("resetpassword");
        if (resetCmd != null) resetCmd.setTabCompleter(this);

        getLogger().info("BestAuth enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (dbManager != null) {
            dbManager.kapat();
        }
        getLogger().info("BestAuth disabled!");
    }

    public boolean dogrulanmis(String uuid) {
        try {
            return dogrulanan.contains(UUID.fromString(uuid));
        } catch (Exception e) {
            return false;
        }
    }

    public String kayitConfigGetString(String yol) {
        return kayitConfig != null ? kayitConfig.getString(yol) : null;
    }

    public long kayitConfigGetLong(String yol) {
        return kayitConfig != null ? kayitConfig.getLong(yol) : 0L;
    }

    public String dbManagerIpGetir(String uuid) {
        if (dbManager != null) {
            return dbManager.ipGetir(uuid);
        }
        return null;
    }

    public String messageManagerGetAktifDil() {
        return messageManager != null ? messageManager.getAktifDil() : "tr";
    }

    private void yukleConfig() {
        reloadConfig();
        girisSuresi = getConfig().getInt("login-timeout", getConfig().getInt("giris-suresi", 60));
        ipLogla = getConfig().getBoolean("log-ip", getConfig().getBoolean("ip-logla", true));
        sifreMin = getConfig().getInt("password-min-length", getConfig().getInt("sifre-min", 4));
        sifreMax = getConfig().getInt("password-max-length", getConfig().getInt("sifre-max", 32));
        maksHesapSayisi = getConfig().getInt("max-accounts-per-ip", getConfig().getInt("maks-hesap-sayisi", 1));
        maksDeneme = getConfig().getInt("max-login-attempts", getConfig().getInt("maks-deneme", 3));
        hataGecikme = getConfig().getInt("failed-attempt-delay", getConfig().getInt("hata-gecikme", 2));
        sifreFiltre = getConfig().getBoolean("chat-filter-passwords", getConfig().getBoolean("sifre-filtre", true));
        tabEngel = getConfig().getBoolean("block-tab-completion", getConfig().getBoolean("tab-engel", true));
        ipGirisAktif = getConfig().getBoolean("auto-login-by-ip", getConfig().getBoolean("ip-giris-aktif", false));
        ipEslesmeOktet = getConfig().getInt("ip-match-octets", getConfig().getInt("ip-eslesme-oktet", 3));
        
        List<String> bl = getConfig().getStringList("password-blacklist");
        if (bl.isEmpty()) bl = getConfig().getStringList("sifre-kara-listesi");
        karaListe = bl;

        rateLimitAktif = getConfig().getBoolean("rate-limit-enabled", getConfig().getBoolean("rate-limit-aktif", true));
        rateLimitDakika = getConfig().getInt("rate-limit-minutes", getConfig().getInt("rate-limit-dakika", 5));
        rateLimitMaks = getConfig().getInt("rate-limit-max-requests", getConfig().getInt("rate-limit-maks", 10));
        
        vaultOdulAktif = getConfig().getBoolean("vault.reward-enabled", getConfig().getBoolean("vault.odul-aktif", true));
        vaultOdulMiktari = getConfig().getDouble("vault.reward-amount", getConfig().getDouble("vault.odul-miktari", 100.0));
        
        sandboxModu = getConfig().getBoolean("sandbox-mode", getConfig().getBoolean("sandbox-modu", false));
        animsatmaAktif = getConfig().getBoolean("reminders-enabled", getConfig().getBoolean("animsatma-aktif", true));
        animsatmaAralik = getConfig().getInt("reminder-interval-seconds", getConfig().getInt("animsatma-aralik", 30));
        
        veritabaniTipi = getConfig().getString("database-type", getConfig().getString("veritabani-tipi", "yaml"));
        String dil = getConfig().getString("language", getConfig().getString("dil", "en"));

        if (messageManager == null) {
            messageManager = new MessageManager(getDataFolder(), dil);
        } else {
            messageManager.yenidenYukle(dil);
        }

        if (vaultManager == null) {
            vaultManager = new VaultManager();
        }
        if (metricsManager == null) {
            metricsManager = new MetricsManager(this);
            metricsManager.baslat();
        }
        if (updateChecker == null) {
            updateChecker = new UpdateChecker(this, "https://api.github.com/repos/OpenCode/BestAuth/releases/latest");
            updateChecker.kontrol();
        }

        joinEnabled = getConfig().getBoolean("chat.join_enabled", true);
        leaveEnabled = getConfig().getBoolean("chat.leave_enabled", true);
    }

    private void kaydet() {
        if (!veritabaniTipi.equals("yaml") && dbManager != null) {
            return;
        }
        try {
            kayitConfig.save(kayitDosya);
        } catch (IOException e) {
            getLogger().severe("Could not save kayitli-oyuncular.yml!");
        }
    }

    private String uretSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    private String hashSifre(String sifre, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(Base64.getDecoder().decode(salt));
            byte[] hash = md.digest(sifre.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return sifre;
        }
    }

    private boolean eskiHashMi(String hash) {
        return hash != null && hash.length() == 64 && !hash.contains(".");
    }

    private String hashSifreEski(String sifre) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(sifre.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return sifre;
        }
    }

    private boolean girisYap(Player p, String sifre) {
        String uuid = p.getUniqueId().toString();
        if (!kayitlimi(uuid)) return false;

        String kayitliHash;
        String salt;

        if (!veritabaniTipi.equals("yaml") && dbManager != null) {
            kayitliHash = dbManager.sifreGetir(uuid);
            salt = dbManager.saltGetir(uuid);
        } else {
            kayitliHash = kayitConfig.getString(uuid + ".sifre");
            salt = kayitConfig.getString(uuid + ".salt");
        }

        if (salt == null || salt.isEmpty()) {
            if (eskiHashMi(kayitliHash)) {
                return kayitliHash.equals(hashSifreEski(sifre));
            }
            salt = uretSalt();
            if (!veritabaniTipi.equals("yaml") && dbManager != null) {
                final String s = salt;
                Bukkit.getScheduler().runTaskAsynchronously(this, () -> dbManager.guncelle(uuid, "salt", s));
            } else {
                kayitConfig.set(uuid + ".salt", salt);
                kaydet();
            }
        }

        String yeniHash = hashSifre(sifre, salt);
        if (kayitliHash != null && kayitliHash.equals(yeniHash)) {
            if (eskiHashMi(kayitliHash)) {
                if (!veritabaniTipi.equals("yaml") && dbManager != null) {
                    final String h = yeniHash;
                    Bukkit.getScheduler().runTaskAsynchronously(this, () -> dbManager.guncelle(uuid, "sifre", h));
                } else {
                    kayitConfig.set(uuid + ".sifre", yeniHash);
                    kaydet();
                }
                getLogger().info("Password hash updated with new salt for UUID: " + uuid);
            }
            return true;
        }

        if (eskiHashMi(kayitliHash) && kayitliHash != null && kayitliHash.equals(hashSifreEski(sifre))) {
            if (!veritabaniTipi.equals("yaml") && dbManager != null) {
                final String h = yeniHash;
                final String s = salt;
                Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                    dbManager.guncelle(uuid, "sifre", h);
                    dbManager.guncelle(uuid, "salt", s);
                });
            } else {
                kayitConfig.set(uuid + ".sifre", yeniHash);
                kayitConfig.set(uuid + ".salt", salt);
                kaydet();
            }
            getLogger().info(uuid + " icin eski hash yeni salt ile guncellendi.");
            return true;
        }

        return false;
    }

    private void dogrulandi(Player player) {
        UUID uuid = player.getUniqueId();
        dogrulanan.add(uuid);
        denemeSayisi.remove(uuid);
        hataBekleme.remove(uuid);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
    }

    private void kayitOduluVer(Player player) {
        if (vaultOdulAktif && vaultManager != null && vaultManager.vaultVarMi()) {
            vaultManager.paraEkle(player, vaultOdulMiktari);
            player.sendMessage(messageManager.getRenkliMesaj("login.vault-reward", "%amount%", String.valueOf((int) vaultOdulMiktari)));
        }
    }

    private boolean kayitlimi(String uuid) {
        if (!veritabaniTipi.equals("yaml") && dbManager != null) {
            return dbManager.kayitlimi(uuid);
        }
        return kayitConfig != null && kayitConfig.contains(uuid + ".sifre");
    }

    private boolean bekliyor(Player p) {
        return !dogrulanan.contains(p.getUniqueId());
    }

    private boolean rateLimitKontrol(Player p) {
        if (!rateLimitAktif) return true;
        String ip = p.getAddress() != null && p.getAddress().getAddress() != null
            ? p.getAddress().getAddress().getHostAddress() : "";
        if (ip.isEmpty()) return true;

        long simdi = System.currentTimeMillis();
        long pencere = rateLimitDakika * 60 * 1000L;

        List<Long> zamanlar = rateLimitMap.computeIfAbsent(ip, k -> new java.util.concurrent.CopyOnWriteArrayList<>());
        zamanlar.removeIf(zaman -> simdi - zaman > pencere);

        if (zamanlar.size() >= rateLimitMaks) {
            return false;
        }

        zamanlar.add(simdi);
        return true;
    }

    private int ipdekiHesapSayisi(String ip) {
        if (ip == null || ip.isEmpty()) return 0;
        if (!veritabaniTipi.equals("yaml") && dbManager != null) {
            return dbManager.ipHesapSayisi(ip);
        }
        if (kayitConfig == null) return 0;
        int sayi = 0;
        for (String uid : tumUuidler()) {
            if (kayitConfig.contains(uid + ".sifre")) {
                String kayitliIp = kayitConfig.getString(uid + ".ip");
                if (ip.equals(kayitliIp)) {
                    sayi++;
                }
            }
        }
        return sayi;
    }

    private boolean ipTamEslesiyor(Player p, UUID uuid) {
        String kayitliIp;
        if (!veritabaniTipi.equals("yaml") && dbManager != null) {
            kayitliIp = dbManager.ipGetir(uuid.toString());
        } else {
            kayitliIp = kayitConfig != null ? kayitConfig.getString(uuid.toString() + ".ip") : null;
        }
        if (kayitliIp == null || kayitliIp.isEmpty()) return true;
        String simdikiIp = p.getAddress() != null && p.getAddress().getAddress() != null
            ? p.getAddress().getAddress().getHostAddress() : "";
        if (simdikiIp.isEmpty()) return true;
        return kayitliIp.equalsIgnoreCase(simdikiIp);
    }

    private boolean ipOktetEslesiyor(Player p, UUID uuid) {
        String kayitliIp;
        if (!veritabaniTipi.equals("yaml") && dbManager != null) {
            kayitliIp = dbManager.ipGetir(uuid.toString());
        } else {
            kayitliIp = kayitConfig != null ? kayitConfig.getString(uuid.toString() + ".ip") : null;
        }
        if (kayitliIp == null || kayitliIp.isEmpty()) return true;
        String simdikiIp = p.getAddress() != null && p.getAddress().getAddress() != null
            ? p.getAddress().getAddress().getHostAddress() : "";
        if (simdikiIp.isEmpty()) return true;
        String[] kayitli = kayitliIp.split("\\.");
        String[] simdiki = simdikiIp.split("\\.");
        int eslenecek = Math.min(ipEslesmeOktet, Math.min(kayitli.length, simdiki.length));
        for (int i = 0; i < eslenecek; i++) {
            if (!kayitli[i].equals(simdiki[i])) return false;
        }
        return true;
    }

    private Set<String> tumUuidler() {
        if (!veritabaniTipi.equals("yaml") && dbManager != null) {
            return new HashSet<>(dbManager.tumOyuncular());
        }
        Set<String> uuids = new HashSet<>();
        if (kayitConfig == null) return uuids;
        for (String key : kayitConfig.getKeys(false)) {
            int dot = key.indexOf('.');
            uuids.add(dot > 0 ? key.substring(0, dot) : key);
        }
        return uuids;
    }

    private String uuidBul(String isim) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().equalsIgnoreCase(isim)) return p.getUniqueId().toString();
        }

        if (!veritabaniTipi.equals("yaml") && dbManager != null) {
            for (String uid : dbManager.tumOyuncular()) {
                String offline = UUID.nameUUIDFromBytes(("OfflinePlayer:" + isim).getBytes(StandardCharsets.UTF_8)).toString();
                if (uid.equals(offline)) return uid;
            }
            return UUID.nameUUIDFromBytes(("OfflinePlayer:" + isim).getBytes(StandardCharsets.UTF_8)).toString();
        }

        if (kayitConfig != null) {
            for (String uid : tumUuidler()) {
                String a = kayitConfig.getString(uid + ".isim");
                if (a != null && a.equalsIgnoreCase(isim)) return uid;
            }
            for (String uid : tumUuidler()) {
                if (kayitConfig.contains(uid + ".isim")) continue;
                try {
                    OfflinePlayer off = Bukkit.getOfflinePlayer(UUID.fromString(uid));
                    String ad = off.getName();
                    if (ad != null) {
                        kayitConfig.set(uid + ".isim", ad);
                        kaydet();
                        if (ad.equalsIgnoreCase(isim)) return uid;
                    }
                } catch (Exception ignored) {}
            }
        }
        String offline = UUID.nameUUIDFromBytes(("OfflinePlayer:" + isim).getBytes(StandardCharsets.UTF_8)).toString();
        if (kayitlimi(offline)) {
            if (kayitConfig != null) {
                kayitConfig.set(offline + ".isim", isim);
                kaydet();
            }
            return offline;
        }
        return offline;
    }

    private String sifreKontrol(String sifre) {
        if (sifre.length() < sifreMin) {
            return messageManager.getRenkliMesaj("register.password-short", "%min%", String.valueOf(sifreMin));
        }
        if (sifre.length() > sifreMax) {
            return messageManager.getRenkliMesaj("register.password-long", "%max%", String.valueOf(sifreMax));
        }
        for (String yasak : karaListe) {
            if (sifre.equalsIgnoreCase(yasak)) {
                return messageManager.getRenkliMesaj("register.password-blacklisted");
            }
        }
        return null;
    }

    private static final String[] IZINLI_KOMUTLAR = {
        "/login", "/register",
        "/bestauth",
        "/resetpassword", "/auth-reload"
    };

    private boolean izinliKomut(String msg) {
        String dusuk = msg.toLowerCase().trim();
        for (String k : IZINLI_KOMUTLAR) {
            if (dusuk.equals(k) || dusuk.startsWith(k + " ")) return true;
        }
        return false;
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        Player p = event.getPlayer();
        UUID uuid = p.getUniqueId();
        String uuidStr = uuid.toString();

        if (kayitlimi(uuidStr)) {
            if (!veritabaniTipi.equals("yaml") && dbManager != null) {
                if (ipLogla) {
                    String ip = event.getAddress() != null ? event.getAddress().getHostAddress() : "";
                    if (!ip.isEmpty()) {
                        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                            dbManager.guncelle(uuidStr, "ip", ip);
                            dbManager.sonGuncelle(uuidStr);
                        });
                    }
                }
                return;
            }

            if (kayitConfig != null) {
                kayitConfig.set(uuidStr + ".isim", p.getName());

                if (ipLogla) {
                    String ip = event.getAddress() != null ? event.getAddress().getHostAddress() : "";
                    if (!ip.isEmpty()) {
                        kayitConfig.set(uuidStr + ".ip", ip);
                        kayitConfig.set(uuidStr + ".son-giris", System.currentTimeMillis());
                    }
                }
                kaydet();
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        UUID uuid = p.getUniqueId();
        boolean kayitli = kayitlimi(uuid.toString());

        if (sandboxModu) {
            dogrulandi(p);
            p.sendMessage(messageManager.getRenkliMesaj("join.sandbox"));
            if (joinEnabled) {
                event.setJoinMessage(messageManager.getRenkliMesaj("chat.join", "%player%", p.getName()));
            } else {
                event.setJoinMessage(null);
            }
            return;
        }

        if (kayitli && ipGirisAktif && !ipOktetEslesiyor(p, uuid)) {
            p.kickPlayer(messageManager.getRenkliMesaj("kick.ip-mismatch"));
            return;
        }

        if (joinEnabled) {
            event.setJoinMessage(messageManager.getRenkliMesaj("chat.join", "%player%", p.getName()));
        } else {
            event.setJoinMessage(null);
        }

        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 999999, 1, false, false));

        if (kayitli) {
            if (!ipTamEslesiyor(p, uuid)) {
                p.sendMessage(messageManager.getRenkliMesaj("join.different-ip"));
            } else {
                p.sendMessage(messageManager.getRenkliMesaj("join.login"));
            }
            p.sendTitle(messageManager.getRenkliMesaj("title.login"), messageManager.getRenkliMesaj("title.login-sub"), 10, 70, 20);
        } else {
            p.sendMessage(messageManager.getRenkliMesaj("join.register"));
            p.sendTitle(messageManager.getRenkliMesaj("title.register"), messageManager.getRenkliMesaj("title.register-sub"), 10, 70, 20);
        }

        new BukkitRunnable() {
            int saniye = girisSuresi;
            @Override
            public void run() {
                if (!p.isOnline() || dogrulanan.contains(uuid)) {
                    cancel();
                    return;
                }
                saniye--;
                if (saniye <= 0) {
                    String kickMsg = kayitli
                        ? messageManager.getRenkliMesaj("kick.no-auth-login")
                        : messageManager.getRenkliMesaj("kick.no-auth-register");
                    kickMsg = kickMsg.replace("%n%", "\n").replace("%n", "\n");
                    p.kickPlayer(kickMsg);
                    cancel();
                } else if (saniye % 15 == 0 || saniye <= 5) {
                    p.sendMessage(messageManager.getRenkliMesaj("time.remaining", "%seconds%", String.valueOf(saniye)));
                }
            }
        }.runTaskTimer(this, 20L, 20L);

        if (animsatmaAktif) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!p.isOnline() || dogrulanan.contains(uuid)) {
                        cancel();
                        return;
                    }
                    String reminder = kayitli
                        ? messageManager.getRenkliMesaj("title.reminder-login")
                        : messageManager.getRenkliMesaj("title.reminder-register");
                    p.sendTitle("", reminder, 5, 40, 10);
                    p.sendMessage(reminder);
                }
            }.runTaskTimer(this, animsatmaAralik * 20L, animsatmaAralik * 20L);
        }

        if (updateChecker != null && p.hasPermission("bestauth.admin")) {
            updateChecker.bildirimGonder(p);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!bekliyor(event.getPlayer())) return;
        Player p = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getBlockX() != to.getBlockX() ||
            from.getBlockY() != to.getBlockY() ||
            from.getBlockZ() != to.getBlockZ()) {
            event.setTo(from);
            boolean kayitli = kayitlimi(p.getUniqueId().toString());
            String title = kayitli ? messageManager.getRenkliMesaj("title.login") : messageManager.getRenkliMesaj("title.register");
            String sub = kayitli ? messageManager.getRenkliMesaj("title.login-sub") : messageManager.getRenkliMesaj("title.register-sub");
            p.sendTitle(title, sub, 5, 40, 10);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (bekliyor(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (bekliyor(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (bekliyor(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (bekliyor(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player && bekliyor((Player) event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player && bekliyor((Player) event.getWhoClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player && bekliyor((Player) event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        if (bekliyor(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player && bekliyor((Player) event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player && bekliyor((Player) event.getDamager())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player && bekliyor((Player) event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        Player p = event.getPlayer();
        if (bekliyor(p)) {
            event.setCancelled(true);
            return;
        }
        if (sifreFiltre) {
            String uuid = p.getUniqueId().toString();
            if (kayitlimi(uuid)) {
                String hash;
                String salt;
                if (!veritabaniTipi.equals("yaml") && dbManager != null) {
                    hash = dbManager.sifreGetir(uuid);
                    salt = dbManager.saltGetir(uuid);
                } else {
                    hash = kayitConfig != null ? kayitConfig.getString(uuid + ".sifre") : null;
                    salt = kayitConfig != null ? kayitConfig.getString(uuid + ".salt") : null;
                }
                if (hash != null && salt != null) {
                    String mesaj = event.getMessage();
                    for (String kelime : mesaj.split(" ")) {
                        if (hashSifre(kelime, salt).equals(hash)) {
                            event.setMessage(mesaj.replace(kelime, "*****"));
                            break;
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (bekliyor(event.getPlayer()) && !izinliKomut(event.getMessage())) {
            event.setCancelled(true);
            Player p = event.getPlayer();
            String msg = kayitlimi(p.getUniqueId().toString())
                ? messageManager.getRenkliMesaj("join.login")
                : messageManager.getRenkliMesaj("join.register");
            p.sendMessage(msg);
        }
    }

    @EventHandler
    public void onTabComplete(TabCompleteEvent event) {
        if (!tabEngel) return;
        if (event.getSender() instanceof Player && bekliyor((Player) event.getSender())) {
            event.setCancelled(true);
        }
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        String name = cmd.getName().toLowerCase();
        if (name.equals("bestauth") && sender.hasPermission("bestauth.admin")) {
            if (args.length == 1) {
                java.util.List<String> subCommands = java.util.Arrays.asList("view", "list");
                String current = args[0].toLowerCase();
                java.util.List<String> filtered = new java.util.ArrayList<>();
                for (String s : subCommands) {
                    if (s.toLowerCase().startsWith(current)) filtered.add(s);
                }
                return filtered;
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("view")) {
                String current = args[1].toLowerCase();
                java.util.List<String> list = new java.util.ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(current)) {
                        list.add(p.getName());
                    }
                }
                for (String uid : tumUuidler()) {
                    String ad = null;
                    if (kayitConfig != null) {
                        ad = kayitConfig.getString(uid + ".isim");
                    }
                    if (ad != null && ad.toLowerCase().startsWith(current) && !list.contains(ad)) {
                        list.add(ad);
                    }
                }
                return list;
            }
            return java.util.Collections.emptyList();
        }

        if (name.equals("resetpassword") && sender.hasPermission("bestauth.admin")) {
            if (args.length == 1) {
                String current = args[0].toLowerCase();
                java.util.List<String> list = new java.util.ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(current)) list.add(p.getName());
                }
                for (String uid : tumUuidler()) {
                    String ad = null;
                    if (kayitConfig != null) ad = kayitConfig.getString(uid + ".isim");
                    if (ad != null && ad.toLowerCase().startsWith(current) && !list.contains(ad)) {
                        list.add(ad);
                    }
                }
                return list;
            }
            return java.util.Collections.emptyList();
        }

        return super.onTabComplete(sender, cmd, label, args);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        UUID uuid = p.getUniqueId();
        dogrulanan.remove(uuid);
        denemeSayisi.remove(uuid);
        hataBekleme.remove(uuid);
        if (leaveEnabled) {
            event.setQuitMessage(messageManager.getRenkliMesaj("chat.leave", "%player%", p.getName()));
        } else {
            event.setQuitMessage(null);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String cmdName = cmd.getName().toLowerCase();

        if (cmdName.equals("auth-reload")) {
            if (!sender.hasPermission("bestauth.admin")) {
                sender.sendMessage(messageManager.getRenkliMesaj("general.no-permission"));
                return true;
            }
            yukleConfig();
            sender.sendMessage(messageManager.getRenkliMesaj("admin.reload-success"));
            return true;
        }

        if (cmdName.equals("resetpassword")) {
            if (!sender.hasPermission("bestauth.admin")) {
                sender.sendMessage(messageManager.getRenkliMesaj("general.no-permission"));
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage(messageManager.getRenkliMesaj("password-reset.usage"));
                return true;
            }

            String hedefIsim = args[0];
            String hedefUuid = uuidBul(hedefIsim);
            Player hedef = Bukkit.getPlayer(hedefIsim);

            if (!kayitlimi(hedefUuid)) {
                sender.sendMessage(messageManager.getRenkliMesaj("password-reset.not-registered"));
                return true;
            }

            if (!veritabaniTipi.equals("yaml") && dbManager != null) {
                Bukkit.getScheduler().runTaskAsynchronously(this, () -> dbManager.sil(hedefUuid));
            } else if (kayitConfig != null) {
                kayitConfig.set(hedefUuid + ".sifre", null);
                kaydet();
            }

            if (hedef == null) {
                sender.sendMessage(messageManager.getRenkliMesaj("password-reset.reset-offline", "%player%", hedefIsim));
                return true;
            }

            dogrulanan.remove(hedef.getUniqueId());
            denemeSayisi.remove(hedef.getUniqueId());
            hataBekleme.remove(hedef.getUniqueId());

            hedef.kickPlayer(messageManager.getRenkliMesaj("password-reset.reset-online"));
            sender.sendMessage(messageManager.getRenkliMesaj("password-reset.reset-offline", "%player%", hedefIsim));
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(messageManager.getRenkliMesaj("general.player-only"));
            return true;
        }

        Player p = (Player) sender;
        String uuid = p.getUniqueId().toString();

        if (cmdName.equals("register")) {
            if (sandboxModu) {
                dogrulandi(p);
                p.sendMessage(messageManager.getRenkliMesaj("join.sandbox"));
                return true;
            }
            if (args.length < 1) {
                p.sendMessage(messageManager.getRenkliMesaj("register.usage"));
                return true;
            }
            if (kayitlimi(uuid)) {
                p.sendMessage(messageManager.getRenkliMesaj("login.already-registered"));
                return true;
            }
            if (!rateLimitKontrol(p)) {
                p.sendMessage(messageManager.getRenkliMesaj("login.rate-limit"));
                return true;
            }
            String ip = p.getAddress() != null && p.getAddress().getAddress() != null
                ? p.getAddress().getAddress().getHostAddress() : "";
            if (maksHesapSayisi > 0 && ipdekiHesapSayisi(ip) >= maksHesapSayisi) {
                p.sendMessage(messageManager.getRenkliMesaj("register.max-accounts", "%max%", String.valueOf(maksHesapSayisi)));
                return true;
            }
            String hata = sifreKontrol(args[0]);
            if (hata != null) {
                p.sendMessage(hata);
                return true;
            }
            String salt = uretSalt();
            String hash = hashSifre(args[0], salt);

            if (!veritabaniTipi.equals("yaml") && dbManager != null) {
                Bukkit.getScheduler().runTaskAsynchronously(this, () -> dbManager.kayitOl(uuid, p.getName(), hash, salt, ip));
            } else if (kayitConfig != null) {
                kayitConfig.set(uuid + ".sifre", hash);
                kayitConfig.set(uuid + ".salt", salt);
                kayitConfig.set(uuid + ".isim", p.getName());
                kayitConfig.set(uuid + ".ip", ip);
                kaydet();
            }

            dogrulandi(p);
            kayitOduluVer(p);
            p.sendMessage(messageManager.getRenkliMesaj("login.register"));
            return true;
        }

        if (cmdName.equals("bestauth")) {
            if (!sender.hasPermission("bestauth.admin")) {
                sender.sendMessage(messageManager.getRenkliMesaj("general.no-permission"));
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage(messageManager.getRenkliMesaj("admin.ipgiris-usage"));
                return true;
            }
            if (args[0].equalsIgnoreCase("list")) {
                sender.sendMessage(messageManager.getRenkliMesaj("admin.registered-players"));
                for (String uid : tumUuidler()) {
                    String ad = null;
                    if (kayitConfig != null) {
                        ad = kayitConfig.getString(uid + ".isim");
                    }
                    if (ad == null) {
                        try {
                            OfflinePlayer off = Bukkit.getOfflinePlayer(UUID.fromString(uid));
                            ad = off.getName();
                        } catch (Exception ignored) {}
                    }
                    if (ad == null) ad = uid;
                    sender.sendMessage(renklendir(" &7- &e" + ad));
                }
                return true;
            }
            if (!args[0].equalsIgnoreCase("view") || args.length < 2) {
                sender.sendMessage(messageManager.getRenkliMesaj("admin.ipgiris-usage"));
                return true;
            }
            String isim = args[1];
            String hid = uuidBul(isim);
            if (!kayitlimi(hid)) {
                sender.sendMessage(messageManager.getRenkliMesaj("password-reset.not-registered"));
                return true;
            }
            String isimGoster;
            String ip;
            long son;
            if (!veritabaniTipi.equals("yaml") && dbManager != null) {
                isimGoster = isim;
                ip = dbManager.ipGetir(hid);
                if (ip == null) ip = messageManager.getMesaj("admin.player-unknown");
                son = 0;
            } else {
                isimGoster = kayitConfig != null ? kayitConfig.getString(hid + ".isim", isim) : isim;
                ip = kayitConfig != null ? kayitConfig.getString(hid + ".ip", messageManager.getMesaj("admin.player-unknown")) : messageManager.getMesaj("admin.player-unknown");
                son = kayitConfig != null ? kayitConfig.getLong(hid + ".son-giris", 0) : 0;
            }
            String bilinmiyor = messageManager.getMesaj("admin.player-unknown");
            String tarih = son > 0 ? new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm").format(new java.util.Date(son)) : bilinmiyor;
            sender.sendMessage(messageManager.getRenkliMesaj("admin.player-info", "%player%", isimGoster));
            sender.sendMessage(messageManager.getRenkliMesaj("admin.player-ip", "%ip%", ip));
            sender.sendMessage(messageManager.getRenkliMesaj("admin.player-last-login", "%date%", tarih));
            return true;
        }

        if (cmdName.equals("login")) {
            if (sandboxModu) {
                dogrulandi(p);
                p.sendMessage(messageManager.getRenkliMesaj("join.sandbox"));
                return true;
            }
            if (args.length < 1) {
                p.sendMessage(messageManager.getRenkliMesaj("title.login"));
                return true;
            }
            if (!kayitlimi(uuid)) {
                p.sendMessage(messageManager.getRenkliMesaj("login.not-registered"));
                return true;
            }
            if (dogrulanan.contains(p.getUniqueId())) {
                p.sendMessage(messageManager.getRenkliMesaj("login.already-logged-in"));
                return true;
            }
            if (!rateLimitKontrol(p)) {
                p.sendMessage(messageManager.getRenkliMesaj("login.rate-limit"));
                return true;
            }

            UUID puid = p.getUniqueId();

            if (hataGecikme > 0 && hataBekleme.containsKey(puid)) {
                long kalan = (hataBekleme.get(puid) - System.currentTimeMillis()) / 1000;
                if (kalan > 0) {
                    p.sendMessage(messageManager.getRenkliMesaj("login.wait", "%seconds%", String.valueOf(kalan)));
                    return true;
                }
                hataBekleme.remove(puid);
            }

            int deneme = denemeSayisi.getOrDefault(puid, 0);
            if (deneme >= maksDeneme) {
                p.kickPlayer(messageManager.getRenkliMesaj("login.too-many-attempts"));
                return true;
            }

            if (girisYap(p, args[0])) {
                dogrulandi(p);
                p.sendMessage(messageManager.getRenkliMesaj("login.success"));
            } else {
                denemeSayisi.put(puid, deneme + 1);
                p.sendMessage(messageManager.getRenkliMesaj("login.wrong-password"));
                if (hataGecikme > 0) {
                    hataBekleme.put(puid, System.currentTimeMillis() + (hataGecikme * 1000L));
                    p.sendMessage(messageManager.getRenkliMesaj("login.remaining-attempts", "%count%", String.valueOf(maksDeneme - deneme - 1)));
                }
            }
            return true;
        }

        return false;
    }

    private String renklendir(String mesaj) {
        return ChatColor.translateAlternateColorCodes('&', mesaj);
    }
}

