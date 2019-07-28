package fr.minuskube.advancements;

import org.bukkit.Bukkit;
import org.bukkit.advancement.Advancement;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import static org.bukkit.ChatColor.DARK_RED;
import static org.bukkit.ChatColor.GOLD;
import static org.bukkit.ChatColor.GRAY;
import static org.bukkit.ChatColor.GREEN;
import static org.bukkit.ChatColor.RED;
import static org.bukkit.ChatColor.YELLOW;

public class CriteriaCommand implements CommandExecutor, TabCompleter {

    private AdvancementsCounter plugin;

    public CriteriaCommand(AdvancementsCounter plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if(args.length != 1) {
            sender.sendMessage(DARK_RED + "Wrong syntax. " + RED + "/" + label + " <advancement>");
            return true;
        }

        Advancement foundAdvancement = null;

        for(Iterator<Advancement> iter = Bukkit.advancementIterator(); iter.hasNext(); ) {
            Advancement advancement = iter.next();

            if(!this.plugin.isAdvancementValid(advancement)) {
                continue;
            }

            if(args[0].equalsIgnoreCase(advancement.getKey().toString()) ||
                    args[0].equalsIgnoreCase(advancement.getKey().getKey())) {

                foundAdvancement = advancement;
                break;
            }
        }

        if(foundAdvancement == null) {
            sender.sendMessage(RED + "No advancement was found with the given name.");
        }
        else {
            sender.sendMessage(
                    YELLOW + "Criteria for advancement " + GOLD +
                            foundAdvancement.getKey().getKey() + YELLOW + ":"
            );

            Collection<String> doneCriteria;

            if (sender instanceof Player) {
                Player player = (Player) sender;
                doneCriteria = player.getAdvancementProgress(foundAdvancement).getAwardedCriteria();
            }
            else {
                doneCriteria = Collections.emptyList();
            }

            foundAdvancement.getCriteria().stream()
                    .map(crit -> GRAY + "- " + (doneCriteria.contains(crit) ? GREEN : RED) + crit)
                    .forEach(sender::sendMessage);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if(args.length != 1) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();
        String toComplete = args[0].toLowerCase(Locale.ENGLISH);

        for(Iterator<Advancement> iter = Bukkit.advancementIterator(); iter.hasNext(); ) {
            Advancement advancement = iter.next();

            if(!this.plugin.isAdvancementValid(advancement)) {
                continue;
            }

            String name = advancement.getKey().toString();
            String key = advancement.getKey().getKey();

            if(StringUtil.startsWithIgnoreCase(name, toComplete) ||
                StringUtil.startsWithIgnoreCase(key, toComplete)) {

                completions.add(name);
            }
        }

        return completions;
    }

}
