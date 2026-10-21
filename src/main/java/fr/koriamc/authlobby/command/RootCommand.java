package fr.koriamc.authlobby.command;

import fr.koriamc.authlobby.KoriaAuthLobby;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Commande du plugin (/authlobby, /koriaauthlobby). Nom, alias et permission viennent de
 * config.json (section "commandes"), usage et description de messages.json (help.commands).
 */
public abstract class RootCommand {

    protected final KoriaAuthLobby plugin;
    private final String id;
    private final boolean playerOnly;
    private final int minArgs;
    private final boolean admin;

    private boolean enabled = true;
    private String name;
    private List<String> aliases = Collections.emptyList();
    private String permission = "";
    private String usage = "";
    private String description = "";

    protected RootCommand(KoriaAuthLobby plugin, String id, boolean playerOnly, int minArgs, boolean admin) {
        this.plugin = plugin;
        this.id = id;
        this.name = id;
        this.playerOnly = playerOnly;
        this.minArgs = minArgs;
        this.admin = admin;
    }

    /**
     * @param s    section "commandes.&lt;id&gt;" de config.json (peut être nulle)
     * @param help section "help.commands.&lt;id&gt;" de messages.json (peut être nulle)
     */
    public void configure(ConfigurationSection s, ConfigurationSection help) {
        usage = help == null || help.getString("usage") == null ? "" : help.getString("usage");
        description = help == null || help.getString("description") == null ? "" : help.getString("description");
        name = id;
        aliases = new ArrayList<String>();
        permission = "";
        enabled = true;
        if (s == null) {
            return;
        }
        enabled = !s.contains("actif") || s.getBoolean("actif");
        String configured = s.getString("nom");
        if (configured != null && !configured.trim().isEmpty()) {
            name = configured.trim().toLowerCase(Locale.ROOT);
        }
        for (String alias : s.getStringList("alias")) {
            if (!alias.trim().isEmpty()) {
                aliases.add(alias.trim().toLowerCase(Locale.ROOT));
            }
        }
        permission = s.getString("permission") == null ? "" : s.getString("permission").trim();
    }

    public abstract void execute(CommandContext ctx);

    public List<String> complete(CommandContext ctx) {
        return Collections.emptyList();
    }

    /** Identifiant fixe (clé de config.json). */
    public String getId() {
        return id;
    }

    /** Nom de la commande (config.json : commandes.&lt;id&gt;.nom). */
    public String getName() {
        return name;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isPlayerOnly() {
        return playerOnly;
    }

    public int getMinArgs() {
        return minArgs;
    }

    public boolean isAdmin() {
        return admin;
    }

    public String getPermission() {
        return permission;
    }

    public String getUsage() {
        return usage;
    }

    public String getDescription() {
        return description;
    }

    public boolean hasPermission(CommandSender sender) {
        return permission.isEmpty() || sender.hasPermission(permission);
    }
}
