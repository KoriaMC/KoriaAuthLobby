package fr.koriamc.authlobby.command.impl;

import fr.koriamc.authlobby.KoriaAuthLobby;
import fr.koriamc.authlobby.command.CommandContext;
import fr.koriamc.authlobby.command.RootCommand;
import fr.koriamc.authlobby.config.JsonFile;
import fr.koriamc.authlobby.util.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Commandes du lobby : /authlobby (boutons des écrans, jamais tapée par les joueurs : un /login tapé dans le chat est
 * traité par le proxy) et /koriaauthlobby (administration : config, reload).
 */
public final class LobbyCommands {

    public static final String PERM_CONFIG = "koriaauthlobby.admin.config";
    public static final String PERM_RELOAD = "koriaauthlobby.admin.reload";

    private LobbyCommands() {
    }

    /** Valeur d'un champ : vide si absent ou si le client a laissé %local_...% tel quel. */
    static String value(CommandContext ctx, int index) {
        String value = ctx.arg(index);
        if (value == null || (value.startsWith("%local_") && value.endsWith("%"))) {
            return "";
        }
        return value;
    }

    /**
     * /authlobby login &lt;mdp&gt; | register &lt;mdp&gt; &lt;confirmation&gt; | oublie | code &lt;code&gt; |
     * reset &lt;mdp&gt; &lt;confirmation&gt; | retour (actions des écrans de connexion).
     */
    public static final class Screen extends RootCommand {
        public Screen(KoriaAuthLobby plugin) {
            super(plugin, "authlobby", true, 1, false);
        }

        @Override
        public void execute(CommandContext ctx) {
            String action = ctx.arg(0).toLowerCase(Locale.ROOT);
            if (action.equals("login")) {
                // un mot de passe n'a pas d'espace : plusieurs mots seront refusés par le proxy
                plugin.auth().login(ctx.player, ctx.size() > 2 ? Text.join(ctx.args, 1, " ") : value(ctx, 1));
            } else if (action.equals("register")) {
                plugin.auth().register(ctx.player, value(ctx, 1), value(ctx, 2));
            } else if (action.equals("oublie")) {
                plugin.auth().forgot(ctx.player);
            } else if (action.equals("code")) {
                plugin.auth().code(ctx.player, value(ctx, 1));
            } else if (action.equals("reset")) {
                plugin.auth().reset(ctx.player, value(ctx, 1), value(ctx, 2));
            } else if (action.equals("retour")) {
                plugin.auth().back(ctx.player);
            }
        }
    }

    /** /koriaauthlobby [config [fichier] | reload] */
    public static final class Admin extends RootCommand {
        private static final List<String> ACTIONS = Arrays.asList("config", "reload");

        public Admin(KoriaAuthLobby plugin) {
            super(plugin, "koriaauthlobby", false, 0, false);
        }

        @Override
        public void execute(CommandContext ctx) {
            String action = ctx.size() == 0 ? "help" : ctx.arg(0).toLowerCase(Locale.ROOT);
            if (action.equals("config")) {
                if (!ctx.sender.hasPermission(PERM_CONFIG)) {
                    ctx.msg("general.no-permission");
                } else if (ctx.player == null) {
                    ctx.msg("general.players-only");
                } else if (!plugin.menus().openConfig(ctx.player, ctx.arg(1))) {
                    ctx.msg("config-editor.unknown-file", "file", ctx.arg(1));
                }
            } else if (action.equals("reload") || action.equals("rl")) {
                if (!ctx.sender.hasPermission(PERM_RELOAD)) {
                    ctx.msg("general.no-permission");
                    return;
                }
                List<String> problems = plugin.reload();
                ctx.msg("general.reload-done");
                if (!problems.isEmpty()) {
                    ctx.msg("general.reload-problems", "count", problems.size());
                    for (String problem : problems) {
                        ctx.msg("general.reload-problem", "problem", problem);
                    }
                }
            } else {
                ctx.msg("help.admin", "command", getName());
            }
        }

        @Override
        public List<String> complete(CommandContext ctx) {
            if (ctx.size() == 1) {
                return Text.complete(ctx.arg(0), ACTIONS);
            }
            if (ctx.size() == 2 && ctx.arg(0).equalsIgnoreCase("config")) {
                List<String> names = new ArrayList<String>();
                for (JsonFile file : plugin.configFiles()) {
                    names.add(file.getName().replace(".json", ""));
                }
                return Text.complete(ctx.arg(1), names);
            }
            return Collections.emptyList();
        }
    }
}
