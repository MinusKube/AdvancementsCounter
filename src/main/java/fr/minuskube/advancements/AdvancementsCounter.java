package fr.minuskube.advancements;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import fr.minuskube.netherboard.Netherboard;
import fr.minuskube.netherboard.bukkit.BPlayerBoard;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.advancement.Advancement;
import org.bukkit.craftbukkit.v1_14_R1.advancement.CraftAdvancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.bukkit.ChatColor.AQUA;
import static org.bukkit.ChatColor.BOLD;
import static org.bukkit.ChatColor.DARK_AQUA;
import static org.bukkit.ChatColor.GOLD;
import static org.bukkit.ChatColor.GRAY;
import static org.bukkit.ChatColor.UNDERLINE;
import static org.bukkit.ChatColor.WHITE;
import static org.bukkit.ChatColor.YELLOW;

public final class AdvancementsCounter extends JavaPlugin implements Listener {

    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("00.0");

    private File dataFile;
    private Gson gson;

    private Map<UUID, Integer> doneAdvancements;
    private Map<UUID, BukkitRunnable> runningTasks = new HashMap<>();

    private int totalAdvancements;

    @Override
    public void onEnable() {
        for(Iterator<Advancement> iter = Bukkit.advancementIterator(); iter.hasNext(); ) {
            Advancement advancement = iter.next();

            if(!this.isAdvancementValid(advancement)) {
                continue;
            }

            this.totalAdvancements++;
        }

        this.dataFile = new File(this.getDataFolder(), "data.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        this.loadAvancements();

        Bukkit.getOnlinePlayers().forEach(this::updateScoreboard);

        Bukkit.getPluginManager().registerEvents(this, this);

        Bukkit.getScheduler().runTaskTimer(
                this, this::saveAdvancements,
                20 * 5 * 60, 20 * 5 * 60
        );

        CriteriaCommand command = new CriteriaCommand(this);
        this.getCommand("criteria").setExecutor(command);
        this.getCommand("criteria").setTabCompleter(command);

        this.getLogger().info("Loaded " + this.totalAdvancements + " advancements!");
    }

    @Override
    public void onDisable() {
        Bukkit.getOnlinePlayers().forEach(this::deleteScoreboard);

        this.saveAdvancements();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        this.doneAdvancements.put(uuid, this.calculateAdvancementCount(player));

        Bukkit.getOnlinePlayers().forEach(this::updateScoreboard);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        this.doneAdvancements.put(uuid, this.calculateAdvancementCount(player));

        Bukkit.getOnlinePlayers().forEach(this::updateScoreboard);
        this.deleteScoreboard(player);

        this.saveAdvancements();
    }

    @EventHandler
    public void onPlayerAdvancementDone(PlayerAdvancementDoneEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        Advancement advancement = event.getAdvancement();

        if(!this.isAdvancementValid(advancement)) {
            return;
        }

        this.getLogger().info("Advancement " + advancement.getKey() + " done for player " + player.getName());

        int advancementCount = this.calculateAdvancementCount(player);
        this.doneAdvancements.put(uuid, advancementCount);

        float percent = this.getAdvancementPercent(uuid);
        String percentStr = PERCENT_FORMAT.format(percent);

        Bukkit.getOnlinePlayers().forEach(this::updateScoreboard);

        if(this.runningTasks.get(uuid) != null) {
            this.runningTasks.get(uuid).cancel();
        }

        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                Bukkit.broadcastMessage(
                        GRAY + "[" + GOLD + "Advancements" + GRAY + "] " +
                                AQUA + player.getName() + WHITE + " -> " +
                                AQUA + advancementCount + GRAY + "/" + AQUA + AdvancementsCounter.this.totalAdvancements +
                                GRAY + " (" + DARK_AQUA + percentStr + WHITE + "%)"
                );
            }
        };
        runnable.runTaskLater(this, 20);

        this.runningTasks.put(uuid, runnable);
    }

    private void loadAvancements() {
        if(this.dataFile.exists()) {
            Type type = new TypeToken<Map<UUID, Integer>>() {}.getType();

            try(FileReader reader = new FileReader(this.dataFile)) {
                this.doneAdvancements = this.gson.fromJson(reader, type);
            } catch(IOException e) {
                e.printStackTrace();

                this.doneAdvancements = new HashMap<>();
            }
        }
        else {
            this.doneAdvancements = new HashMap<>();
        }
    }

    private void saveAdvancements() {
        String json = this.gson.toJson(this.doneAdvancements);

        if(!this.dataFile.exists()) {
            this.dataFile.getParentFile().mkdirs();
        }

        try(FileWriter writer = new FileWriter(this.dataFile)) {
            writer.write(json);
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    private void updateScoreboard(Player player) {
        BPlayerBoard board = Netherboard.instance().getBoard(player);

        if(board == null) {
            board = Netherboard.instance().createBoard(player,
                    GOLD + "- " + DARK_AQUA + "Advancements" + GOLD + " -");
        }

        List<Map.Entry<UUID, Integer>> sortedEntries = new ArrayList<>(this.doneAdvancements.entrySet());
        sortedEntries.sort(Map.Entry.<UUID, Integer>comparingByValue().reversed());

        if(sortedEntries.size() < board.getLines().size()) {
            board.getLines().keySet()
                    .forEach(board::remove);
        }

        for(int i = 0; i < sortedEntries.size(); i++) {
            Map.Entry<UUID, Integer> entry = sortedEntries.get(i);
            String playerName = Bukkit.getOfflinePlayer(entry.getKey()).getName();

            if(player.getUniqueId().equals(entry.getKey())) {
                playerName = UNDERLINE.toString() + BOLD + playerName;
            }

            if(i == 0) {
                playerName = YELLOW + playerName;
            }

            float percent = this.getAdvancementPercent(entry.getKey());
            String percentStr = PERCENT_FORMAT.format(percent);


            board.set(
                    GOLD + StringUtils.leftPad(String.valueOf(i + 1), 2) + GRAY
                            + " (" + GOLD + percentStr + GRAY + "%)" + " | " + WHITE + playerName,
                    16 - i
            );
        }
    }

    private void deleteScoreboard(Player player) {
        Netherboard.instance().deleteBoard(player);
    }

    public boolean isAdvancementValid(Advancement advancement) {
        net.minecraft.server.v1_14_R1.Advancement nmsAdvancement = ((CraftAdvancement) advancement).getHandle();

        return nmsAdvancement.c() != null;
    }

    public int calculateAdvancementCount(Player player) {
        int doneAdvancements = 0;

        for(Iterator<Advancement> iter = Bukkit.advancementIterator(); iter.hasNext(); ) {
            Advancement advancement = iter.next();

            if(!this.isAdvancementValid(advancement)) {
                continue;
            }

            if(player.getAdvancementProgress(advancement).isDone()) {
                doneAdvancements++;
            }
        }

        return doneAdvancements;
    }

    public float getAdvancementPercent(UUID uuid) {
        return 100f * this.doneAdvancements.get(uuid) / totalAdvancements;
    }

}
