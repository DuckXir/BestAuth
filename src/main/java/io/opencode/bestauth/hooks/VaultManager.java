package io.opencode.bestauth.hooks;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class VaultManager {

    private final boolean vaultAktif;
    private Object economyProvider;

    public VaultManager() {
        this.vaultAktif = Bukkit.getPluginManager().getPlugin("Vault") != null;
        if (vaultAktif) {
            try {
                Class<?> vaultClass = Class.forName("net.milkbowl.vault.economy.Economy");
                Class<?> registrationClass = Class.forName("org.bukkit.Bukkit");
                Class<?> servicesManagerClass = Class.forName("org.bukkit.services.ServicesManager");
                Class<?> serviceRegistrationClass = Class.forName("org.bukkit.plugin.RegisteredServiceProvider");

                Object servicesManager = registrationClass.getMethod("getServicesManager").invoke(null);
                Object registration = servicesManagerClass.getMethod("getRegistration", Class.class).invoke(servicesManager, vaultClass);

                if (registration != null) {
                    economyProvider = serviceRegistrationClass.getMethod("getProvider").invoke(registration);
                }
            } catch (Exception e) {
                // Vault bulunamadi veya basarisiz
            }
        }
    }

    public boolean paraEkle(Player oyuncu, double miktar) {
        if (!vaultAktif || economyProvider == null) return false;
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            economyClass.getMethod("depositPlayer", Player.class, double.class).invoke(economyProvider, oyuncu, miktar);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean paraVer(Player oyuncu, double miktar) {
        if (!vaultAktif || economyProvider == null) return false;
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            economyClass.getMethod("withdrawPlayer", Player.class, double.class).invoke(economyProvider, oyuncu, miktar);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public double paraMiktari(Player oyuncu) {
        if (!vaultAktif || economyProvider == null) return 0;
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            return (double) economyClass.getMethod("getBalance", Player.class).invoke(economyProvider, oyuncu);
        } catch (Exception e) {
            return 0;
        }
    }

    public boolean vaultVarMi() {
        return vaultAktif && economyProvider != null;
    }
}

