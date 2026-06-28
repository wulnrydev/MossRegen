package dev.wulnry.mossregen.command;

import dev.wulnry.mossregen.MossRegen;
import dev.wulnry.mossregen.config.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * /mossregen komutunu yonetir (reload, info, forceregen).
 */
public final class MossRegenCommand implements TabExecutor {

    private static final String PERMISSION = "mossregen.admin";
    private static final List<String> SUB_COMMANDS = List.of("reload", "info", "forceregen");

    private final MossRegen plugin;
    private final MessageManager messages;

    public MossRegenCommand(MossRegen plugin) {
        this.plugin = plugin;
        this.messages = plugin.getMessageManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            messages.send(sender, "general.no-permission");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> handleReload(sender);
            case "info" -> handleInfo(sender);
            case "forceregen" -> handleForceRegen(sender);
            default -> messages.send(sender, "general.unknown-command");
        }
        return true;
    }

    private void handleReload(CommandSender sender) {
        try {
            plugin.reloadEverything();
            messages.send(sender, "command.reload-success");
        } catch (Exception ex) {
            messages.send(sender, "command.reload-fail");
            plugin.getLogger().warning("Reload hatasi: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void handleInfo(CommandSender sender) {
        messages.send(sender, "command.info", "version", plugin.getDescription().getVersion());
    }

    private void handleForceRegen(CommandSender sender) {
        int individual = plugin.getRegenManager().forceRegenAll();
        // Global gruplari da senkron bosalt (genelde az sayida olur).
        plugin.getGlobalRegenManager().flushAllSync();
        messages.send(sender, "command.forceregen-done", "count", String.valueOf(individual));
    }

    private void sendHelp(CommandSender sender) {
        messages.send(sender, "command.help-header");
        messages.send(sender, "command.help-reload");
        messages.send(sender, "command.help-info");
        messages.send(sender, "command.help-forceregen");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            return SUB_COMMANDS.stream()
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
