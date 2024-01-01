package com.github.kumo0621.paperplus;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Paperplus extends JavaPlugin implements TabCompleter {
    private final List<Advertisement> adList = new ArrayList<>();
    private BossBar currentBossBar = null;
    private final Random random = new Random();
    private static Economy econ = null;
    private FileConfiguration config;

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().severe(String.format("[%s] - Disabled due to no Vault dependency found!", getDescription().getName()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Plugin startup logic
        saveDefaultConfig();

        // コンフィグファイルを取得
        config = getConfig();

        reloadConfig();
        scheduleAdDisplay(); // 宣伝表示のスケジュールを設定
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
                player.sendMessage("/ad <時間帯(時間)> <価格(1時間あたり)> <内容>");
                // 現在の宣伝金額を確認する
                double totalCost = adList.stream().mapToDouble(Advertisement::getCost).sum();
                player.sendMessage("現在の宣伝枠で積まれている合計金額: " + totalCost + "円");
                return true;
            }

            try {
                int durationInHours = Integer.parseInt(args[0]);
                double pricePerHour = Double.parseDouble(args[1]);
                String message = args[2];
                double totalCost = durationInHours * pricePerHour;

                // コンフィグファイルに保存されている message の値を取得
                List<String> nclist = config.getStringList("ngWords");

                // messageにNGワードが含まれているかどうかをチェック
                boolean containsNgWord = false;
                for (String ngWord : nclist) {
                    if (message.contains(ngWord)) {
                        containsNgWord = true;
                        break;
                    }
                }

                // NGワードが含まれていたら、処理を終了する
                if (containsNgWord) {
                    player.sendMessage("NGワードが検出されました。宣伝はできません。");
                    return true;
                }

                if (getEconomy().getBalance(player) < totalCost) {
                    player.sendMessage("所持金が不足しています。必要金額: " + totalCost + "円");
                    return true;
                }

                getEconomy().withdrawPlayer(player, totalCost);
                adList.add(new Advertisement(player.getName(), message, totalCost));

                player.sendMessage("宣伝が予約されました。料金: " + totalCost + "円");
            } catch (NumberFormatException e) {
                player.sendMessage("無効な数値が入力されました。");
            }
            return true;
        }
        return false;
    }

    private void scheduleAdDisplay() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!adList.isEmpty()) {
                    showRandomAdByChance();
                }
            }
        }.runTaskTimer(this, 1200L, 1200L); // 1分ごとに実行
    }

    private void showRandomAdByChance() {
        if (currentBossBar != null) {
            currentBossBar.setVisible(false);
        }

        double totalCost = adList.stream().mapToDouble(Advertisement::getCost).sum();
        double randomValue = random.nextDouble() * totalCost;

        double cumulativeCost = 0;
        for (Advertisement ad : adList) {
            cumulativeCost += ad.getCost();
            if (cumulativeCost >= randomValue) {
                currentBossBar = Bukkit.createBossBar(ad.getMessage(), BarColor.PURPLE, BarStyle.SOLID);
                currentBossBar.setVisible(true);
                Bukkit.getOnlinePlayers().forEach(currentBossBar::addPlayer);
                break;
            }
        }
    }

    private class Advertisement {
        private final String player;
        private final String message;
        private final double cost;

        public Advertisement(String player, String message, double cost) {
            this.player = player;
            this.message = message;
            this.cost = cost;
        }

        public String getPlayer() {
            return player;
        }

        public String getMessage() {
            return message;
        }

        public double getCost() {
            return cost;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("ad")) {
            List<String> completions = new ArrayList<>();
            if (args.length == 1) {
                // 第一引数の候補を追加
                completions.add("時間");
            } else if (args.length == 2) {
                // 第二引数の候補を追加
                completions.add("価格");
            } else if (args.length == 3) {
                // 第二引数の候補を追加
                completions.add("宣伝メッセージ");
            }
            return completions;
        }
        return null;
    }

}



