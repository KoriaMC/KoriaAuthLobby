package fr.koriamc.authlobby.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * Commande Bukkit enregistrée dynamiquement (nom et alias depuis config.json).
 */
final class BukkitCommand extends Command {

    private final CommandManager manager;
    final RootCommand root;

    BukkitCommand(RootCommand root, CommandManager manager) {
        super(root.getName(), root.getDescription(), "/" + root.getName(), root.getAliases());
        this.root = root;
        this.manager = manager;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        manager.dispatch(root, sender, label, args);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        return manager.tabComplete(root, sender, alias, args);
    }
}
