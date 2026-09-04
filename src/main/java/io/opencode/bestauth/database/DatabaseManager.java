package io.opencode.bestauth.database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {

    private final String tip;
    private Connection baglanti;
    private final File pluginKlasoru;
    private String host = "localhost";
    private int port = 3306;
    private String veritabani = "bestauth";
    private String kullanici = "root";
    private String sifre = "";

    public DatabaseManager(File pluginKlasoru, String tip) {
        this.pluginKlasoru = pluginKlasoru;
        this.tip = tip.toLowerCase();
    }

    public void setMysqlBilgileri(String host, int port, String veritabani, String kullanici, String sifre) {
        this.host = host;
        this.port = port;
        this.veritabani = veritabani;
        this.kullanici = kullanici;
        this.sifre = sifre;
    }

    public void baglan() throws SQLException {
        if (tip.equals("mysql")) {
            String url = "jdbc:mysql://" + host + ":" + port + "/" + veritabani + "?useSSL=false&serverTimezone=UTC&autoReconnect=true";
            baglanti = DriverManager.getConnection(url, kullanici, sifre);
        } else {
            File dbDosya = new File(pluginKlasoru, "bestauth.db");
            String url = "jdbc:sqlite:" + dbDosya.getAbsolutePath();
            baglanti = DriverManager.getConnection(url);
        }

        tabloOlustur();
    }

    private void tabloOlustur() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS oyuncular (" +
            "uuid VARCHAR(36) PRIMARY KEY, " +
            "isim VARCHAR(16) NOT NULL, " +
            "sifre VARCHAR(128) NOT NULL, " +
            "salt VARCHAR(64) NOT NULL, " +
            "ip VARCHAR(45), " +
            "son_giris BIGINT, " +
            "kayit_tarihi BIGINT, " +
            "ip_kilitli BOOLEAN DEFAULT FALSE" +
            ")";
        try (Statement stmt = baglanti.createStatement()) {
            stmt.execute(sql);
        }
    }

    public boolean kayitlimi(String uuid) {
        String sql = "SELECT COUNT(*) FROM oyuncular WHERE uuid = ?";
        try (PreparedStatement stmt = baglanti.prepareStatement(sql)) {
            stmt.setString(1, uuid);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public void kayitOl(String uuid, String isim, String hash, String salt, String ip) {
        String sql = "INSERT OR REPLACE INTO oyuncular (uuid, isim, sifre, salt, ip, kayit_tarihi) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = baglanti.prepareStatement(sql)) {
            stmt.setString(1, uuid);
            stmt.setString(2, isim);
            stmt.setString(3, hash);
            stmt.setString(4, salt);
            stmt.setString(5, ip);
            stmt.setLong(6, System.currentTimeMillis());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String sifreGetir(String uuid) {
        String sql = "SELECT sifre FROM oyuncular WHERE uuid = ?";
        try (PreparedStatement stmt = baglanti.prepareStatement(sql)) {
            stmt.setString(1, uuid);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getString("sifre");
            }
        } catch (SQLException e) {
            return null;
        }
        return null;
    }

    public String saltGetir(String uuid) {
        String sql = "SELECT salt FROM oyuncular WHERE uuid = ?";
        try (PreparedStatement stmt = baglanti.prepareStatement(sql)) {
            stmt.setString(1, uuid);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getString("salt");
            }
        } catch (SQLException e) {
            return null;
        }
        return null;
    }

    public String ipGetir(String uuid) {
        String sql = "SELECT ip FROM oyuncular WHERE uuid = ?";
        try (PreparedStatement stmt = baglanti.prepareStatement(sql)) {
            stmt.setString(1, uuid);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getString("ip");
            }
        } catch (SQLException e) {
            return null;
        }
        return null;
    }

    public void guncelle(String uuid, String alan, String deger) {
        String sql = "UPDATE oyuncular SET " + alan + " = ? WHERE uuid = ?";
        try (PreparedStatement stmt = baglanti.prepareStatement(sql)) {
            stmt.setString(1, deger);
            stmt.setString(2, uuid);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void sonGuncelle(String uuid) {
        guncelle(uuid, "son_giris", String.valueOf(System.currentTimeMillis()));
    }

    public int ipHesapSayisi(String ip) {
        if (ip == null || ip.isEmpty()) return 0;
        String sql = "SELECT COUNT(*) FROM oyuncular WHERE ip = ?";
        try (PreparedStatement stmt = baglanti.prepareStatement(sql)) {
            stmt.setString(1, ip);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            return 0;
        }
        return 0;
    }

    public void sil(String uuid) {
        String sql = "DELETE FROM oyuncular WHERE uuid = ?";
        try (PreparedStatement stmt = baglanti.prepareStatement(sql)) {
            stmt.setString(1, uuid);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<String> tumOyuncular() {
        List<String> liste = new ArrayList<>();
        String sql = "SELECT uuid FROM oyuncular";
        try (Statement stmt = baglanti.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                liste.add(rs.getString("uuid"));
            }
        } catch (SQLException e) {
            return liste;
        }
        return liste;
    }

    public void kapat() {
        try {
            if (baglanti != null && !baglanti.isClosed()) {
                baglanti.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}

