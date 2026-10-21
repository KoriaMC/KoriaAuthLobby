package fr.koriamc.authlobby.command;

import fr.koriamc.authlobby.KoriaAuthLobby;
import fr.koriamc.authlobby.command.impl.LobbyCommands;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Enregistre les commandes du plugin (/authlobby, /koriaauthlobby) sous les noms et
 * alias de config.json. Elles sont réenregistrées à chaque rechargement : changer un nom
 * ne demande pas de redémarrage.
 */
public final class CommandManager {

    /** Identifiant de la commande d'administration (/koriaauthlobby). */
    public static final String ADMIN = "koriaauthlobby";

    private final KoriaAuthLobby plugin;
    private final Map<String, RootCommand> byId = new LinkedHashMap<String, RootCommand>();
    private final List<BukkitCommand> registered = new ArrayList<BukkitCommand>();
    private CommandMap commandMap;

    public CommandManager(KoriaAuthLobby plugin) {
        this.plugin = plugin;
        register(new LobbyCommands.Screen(plugin));
        register(new LobbyCommands.Admin(plugin));
    }

    private void register(RootCommand root) {
        byId.put(root.getId(), root);
    }

    /**
     * @param c        config.json (section "commandes")
     * @param messages messages.json (aide des commandes)
     */
    public void load(ConfigurationSection c, ConfigurationSection messages) {
        PluginManager pm = Bukkit.getPluginManager();
        for (RootCommand root : byId.values()) {
            root.configure(c.getConfigurationSection("commandes." + root.getId()),
                    messages.getConfigurationSection("help.commands." + root.getId()));
            String node = root.getPermission();
            if (root.isEnabled() && !node.isEmpty()) {
                PermissionDefault def = root.isAdmin() ? PermissionDefault.OP : PermissionDefault.TRUE;
                Permission existing = pm.getPermission(node);
                if (existing == null) {
                    pm.addPermission(new Permission(node, def));
                } else if (existing.getDefault() != def) {
                    existing.setDefault(def);
                }
            }
        }
    }

    /** (Ré)enregistre les commandes actives dans le CommandMap du serveur. */
    public void registerBukkitCommands() {
        unregisterBukkitCommands();
        try {
            commandMap = findCommandMap();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Impossible d'accéder aux commandes du serveur", e);
            return;
        }
        String prefix = plugin.getName().toLowerCase(Locale.ROOT);
        for (RootCommand root : byId.values()) {
            if (!root.isEnabled()) {
                continue;
            }
            BukkitCommand command = new BukkitCommand(root, this);
            if (!commandMap.register(prefix, command)) {
                plugin.getLogger().warning("/" + root.getName() + " est déjà utilisée par un autre plugin : elle reste"
                        + " disponible avec /" + prefix + ":" + root.getName() + ".");
            }
            registered.add(command);
        }
    }

    private static CommandMap findCommandMap() throws Exception {
        Object server = Bukkit.getServer();
        try {
            Method getter = server.getClass().getMethod("getCommandMap");
            return (CommandMap) getter.invoke(server);
        } catch (NoSuchMethodException e) {
            Field field = server.getClass().getDeclaredField("commandMap");
            field.setAccessible(true);
            return (CommandMap) field.get(server);
        }
    }

    @SuppressWarnings("unchecked")
    public void unregisterBukkitCommands() {
        if (registered.isEmpty() || commandMap == null) {
            registered.clear();
            return;
        }
        try {
            Field field = findField(commandMap.getClass(), "knownCommands");
            field.setAccessible(true);
            Map<String, Command> known = (Map<String, Command>) field.get(commandMap);
            Iterator<Map.Entry<String, Command>> it = known.entrySet().iterator();
            while (it.hasNext()) {
                Command value = it.next().getValue();
                if (value instanceof BukkitCommand && registered.contains(value)) {
                    it.remove();
                }
            }
            for (BukkitCommand command : registered) {
                command.unregister(commandMap);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Impossible de désenregistrer les commandes", e);
        }
        registered.clear();
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    /** Nom de la commande d'administration (/koriaauthlobby), pour {label} dans les menus. */
    public String getLabel() {
        return label(ADMIN);
    }

    /** Nom configuré d'une commande. */
    public String label(String id) {
        RootCommand root = byId.get(id);
        return root == null ? id : root.getName();
    }

    public RootCommand get(String id) {
        return byId.get(id);
    }

    /** Commandes visibles dans l'aide pour cet expéditeur. */
    public List<RootCommand> visible(CommandSender sender) {
        List<RootCommand> list = new ArrayList<RootCommand>();
        for (RootCommand root : byId.values()) {
            if (root.isEnabled() && root.hasPermission(sender)) {
                list.add(root);
            }
        }
        return list;
    }

    // ============================================================= Exécution

    void dispatch(RootCommand root, CommandSender sender, String usedLabel, String[] args) {
        CommandContext ctx = new CommandContext(plugin, sender, usedLabel, args);
        if (!root.hasPermission(sender)) {
            ctx.msg("general.no-permission");
            return;
        }
        if (root.isPlayerOnly() && ctx.player == null) {
            ctx.msg("general.players-only");
            return;
        }
        if (args.length < root.getMinArgs()) {
            ctx.msg("general.usage", "usage", root.getUsage());
            return;
        }
        try {
            root.execute(ctx);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur pendant /" + usedLabel, e);
            ctx.msg("general.error");
        }
    }

    List<String> tabComplete(RootCommand root, CommandSender sender, String usedLabel, String[] args) {
        if (!root.hasPermission(sender) || (root.isPlayerOnly() && !(sender instanceof Player))) {
            return Collections.emptyList();
        }
        try {
            List<String> result = root.complete(new CommandContext(plugin, sender, usedLabel, args));
            return result == null ? Collections.<String>emptyList() : result;
        } catch (RuntimeException e) {
            return Collections.emptyList();
        }
    }
}
