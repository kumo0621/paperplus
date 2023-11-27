package com.github.kumo0621.paperplus;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public final class Paperplus extends JavaPlugin {
    private Queue<Advertisement> adQueue = new LinkedList<>();
    private BossBar currentBossBar = null;
    private static Economy econ = null;
    private List<Advertisement> adList = new ArrayList<>();
    private Random random = new Random();
    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().severe(String.format("[%s] - Disabled due to no Vault dependency found!", getDescription().getName()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        saveDefaultConfig();
        reloadConfig();
        // 他の初期化コード...
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().severe("Vault plugin not found!");
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().severe("No economy plugin found!");
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    public static Economy getEconomy() {
        return econ;
    }
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("ad") && sender instanceof Player) {
            Player player = (Player) sender;

            if (args.length < 3) {
                player.sendMessage("/ad 時間 価格(1時間あたり) 内容");
                return true;
            }

            try {
                int duration = Integer.parseInt(args[0]) * 60; // 分を秒に変換
                double pricePerHour = Double.parseDouble(args[1]);
                String message = args[2];

                double minPricePerHour = getConfig().getDouble("min-price-per-hour");
                if (pricePerHour < minPricePerHour) {
                    player.sendMessage("入力された価格が最低価格以下です。最低価格: " + minPricePerHour);
                    return true;
                }

                double totalCost = (pricePerHour / 60) * duration;

                if (getEconomy().getBalance(player) < totalCost) {
                    player.sendMessage("所持金が不足しています。必要金額: " + totalCost);
                    return true;
                }

                // 所持金から料金を引く
                getEconomy().withdrawPlayer(player, totalCost);

                // 宣伝リストに追加
                adList.add(new Advertisement(message, duration, totalCost));
                if (currentBossBar == null) {
                    showRandomAd();
                }

                player.sendMessage("宣伝が予約されました。料金: " + totalCost);
            } catch (NumberFormatException e) {
                player.sendMessage("無効な数値が入力されました。");
            }
            return true;
        }
        return false;
    }

    private void showRandomAd() {
        if (currentBossBar != null || adList.isEmpty()) {
            return;
        }

        // 金額が高い宣伝を優先して選択
        Advertisement ad = adList.stream()
                .max(Comparator.comparingDouble(Advertisement::getCost))
                .orElseThrow();

        adList.remove(ad);
        currentBossBar = Bukkit.createBossBar(ad.getMessage(), BarColor.PURPLE, BarStyle.SOLID);
        currentBossBar.setVisible(true);
        Bukkit.getOnlinePlayers().forEach(currentBossBar::addPlayer);

        // 1分後に宣伝を切り替える
        new BukkitRunnable() {
            @Override
            public void run() {
                currentBossBar.setVisible(false);
                currentBossBar = null;
                if (!adList.isEmpty()) {
                    showRandomAd(); // 次の宣伝を表示
                }
            }
        }.runTaskLater(this, 1200L); // 1分 = 1200ティック
    }

    private class Advertisement {
        private final String message;
        private final int duration; // 秒単位
        private final double cost; // この宣伝に対する支払い金額

        public Advertisement(String message, int duration, double cost) {
            this.message = message;
            this.duration = duration;
            this.cost = cost;
        }

        public String getMessage() {
            return message;
        }

        public int getDuration() {
            return duration;
        }

        public double getCost() {
            return cost;
        }
    }
}

