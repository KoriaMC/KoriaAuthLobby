package fr.koriamc.authlobby;

import fr.koriamc.authlobby.auth.LobbyAuth;
import fr.koriamc.authlobby.command.CommandManager;
import fr.koriamc.authlobby.config.ConfigDocs;
import fr.koriamc.authlobby.config.JsonFile;
import fr.koriamc.authlobby.config.Messages;
import fr.koriamc.authlobby.config.Settings;
import fr.koriamc.authlobby.gui.Menu;
import fr.koriamc.authlobby.gui.MenuManager;
import fr.koriamc.authlobby.hook.GuiHook;
import fr.koriamc.authlobby.listener.RestrictionListener;
import org.bukkit.Bukkit;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * KoriaAuthLobby : écrans d'inscription et de connexion KoriaMC sur le serveur lobby. Les comptes et la vérification
 * des mots de passe sont sur le proxy (plugin KoriaAuth) ; ce plugin affiche les écrans, fige le joueur en attendant
 * et transmet au proxy ce qu'il tape (canal koriamc:auth). Il ne stocke aucun mot de passe.
 */
public final class KoriaAuthLobby extends JavaPlugin {

    private static KoriaAuthLobby instance;

    // Fichiers
    private JsonFile configFile;
    private JsonFile messagesFile;
    private JsonFile menusFile;
    private Configuration config = new MemoryConfiguration();
    private Configuration messagesConfig = new MemoryConfiguration();
    private Configuration menusConfig = new MemoryConfiguration();

    private final Settings settings = new Settings();
    private final Messages messages = new Messages();
    private final ConfigDocs configDocs = new ConfigDocs();

    private LobbyAuth auth;
    private MenuManager menus;
    private CommandManager commands;
    private GuiHook gui;

    public static KoriaAuthLobby get() {
        return instance;
    }

    @Override
    public void onEnable() {
        long start = System.currentTimeMillis();
        instance = this;

        configFile = new JsonFile(this, "config.json");
        messagesFile = new JsonFile(this, "messages.json");
        menusFile = new JsonFile(this, "menus.json", "editeur.fichiers");

        gui = new GuiHook(getLogger());
        auth = new LobbyAuth(this);
        menus = new MenuManager(this);
        commands = new CommandManager(this);

        for (String problem : loadConfigs()) {
            getLogger().warning(problem);
        }
        configDocs.load(this);
        configDocs.writeReadme(this, configFiles());
        commands.load(config, messagesConfig);

        if (!gui.isAvailable()) {
            getLogger().severe("Ce serveur n'a pas le système de GUI KoriaMC : les écrans ne peuvent pas s'afficher"
                    + " (les joueurs utiliseront /login et /register dans le chat).");
        } else if (menus.installLayout()) {
            gui.reloadLayouts();
        }

        getServer().getMessenger().registerOutgoingPluginChannel(this, LobbyAuth.CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, LobbyAuth.CHANNEL, auth);

        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new RestrictionListener(this), this);
        pm.registerEvents(menus, this);
        pm.registerEvents(menus.prompt(), this);
        gui.onClose(this, new GuiHook.CloseCallback() {
            @Override
            public void closed(Player player, String layoutId) {
                auth.onScreenClosed(player, layoutId);
            }
        });
        commands.registerBukkitCommands();
        auth.start();
        auth.adoptOnlinePlayers();

        getLogger().info("KoriaAuthLobby activé en " + (System.currentTimeMillis() - start) + " ms.");
    }

    @Override
    public void onDisable() {
        if (auth != null) {
            auth.stop();
        }
        if (commands != null) {
            commands.unregisterBukkitCommands();
        }
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu) {
                player.closeInventory();
            }
        }
        instance = null;
    }

    /** @return les problèmes à signaler (erreurs JSON) */
    private List<String> loadConfigs() {
        List<String> problems = new ArrayList<String>();
        for (JsonFile file : configFiles()) {
            String error = file.load();
            if (error != null) {
                problems.add(file.getName() + " : " + error);
            }
        }
        config = configFile.toConfiguration();
        messagesConfig = messagesFile.toConfiguration();
        menusConfig = menusFile.toConfiguration();
        settings.load(config, getLogger());
        messages.load(messagesConfig, settings);
        return problems;
    }

    /**
     * Recharge toute la configuration (/koriaauthlobby reload, éditeur) et réenregistre les commandes.
     *
     * @return les problèmes détectés, à afficher à l'auteur de la commande
     */
    public List<String> reload() {
        List<String> problems = loadConfigs();
        if (commands != null) {
            commands.load(config, messagesConfig);
            commands.registerBukkitCommands();
        }
        if (menus != null && menus.installLayout()) {
            gui.reloadLayouts();
        }
        return problems;
    }

    // ============================================================ Accesseurs

    public Settings settings() {
        return settings;
    }

    public Messages msg() {
        return messages;
    }

    /** Explication des options (éditeur /koriaauthlobby config et LISEZMOI.txt). */
    public ConfigDocs configDocs() {
        return configDocs;
    }

    public Configuration config() {
        return config;
    }

    public Configuration menusConfig() {
        return menusConfig;
    }

    /** Fichiers de configuration, dans l'ordre de l'éditeur. */
    public List<JsonFile> configFiles() {
        return Arrays.asList(configFile, messagesFile, menusFile);
    }

    public LobbyAuth auth() {
        return auth;
    }

    public MenuManager menus() {
        return menus;
    }

    public CommandManager commands() {
        return commands;
    }

    /** Pont vers les GUI custom KoriaMC. */
    public GuiHook gui() {
        return gui;
    }
}
