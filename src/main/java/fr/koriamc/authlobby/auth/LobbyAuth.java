package fr.koriamc.authlobby.auth;

import fr.koriamc.authlobby.KoriaAuthLobby;
import fr.koriamc.authlobby.hook.GuiHook;
import fr.koriamc.authlobby.util.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Côté lobby de la connexion : le joueur arrive figé, le plugin prévient le proxy (KoriaAuth) quand son client est
 * prêt, puis affiche l'écran demandé par le proxy (inscription ou connexion) et lui transmet ce que le joueur tape.
 * Aucun mot de passe n'est gardé ici. Le joueur est libéré quand le proxy annonce qu'il est connecté.
 *
 * Canal koriamc:auth (voir fr.koriamc.auth.channel.AuthChannel dans KoriaAuth).
 */
public final class LobbyAuth implements PluginMessageListener {

    public static final String CHANNEL = "koriamc:auth";

    /** Joueur figé, en attente du proxy. */
    private static final class State {
        final long joinedAt = System.currentTimeMillis();
        /** Le proxy a répondu au moins une fois. */
        boolean answered;
        /** Dernier écran demandé : login ou register (vide : aucun). */
        String screen = "";
        String error = "";
        int attempts;
        int minLength;
        /** Information de l'écran (e-mail masqué où le code a été envoyé). */
        String info = "";
        /** Fin du délai de connexion (ms). */
        long deadline;
    }

    private final KoriaAuthLobby plugin;
    private final Map<UUID, State> frozen = new HashMap<UUID, State>();
    private BukkitTask task;

    public LobbyAuth(KoriaAuthLobby plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                tick();
            }
        }, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public boolean isFrozen(Player player) {
        return frozen.containsKey(player.getUniqueId());
    }

    // ============================================================ Arrivée / départ

    public void onJoin(final Player player) {
        frozen.put(player.getUniqueId(), new State());
        if (plugin.settings().blindness) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 1, false, false), true);
        }
        waitForClient(player, 0);
    }

    public void onQuit(Player player) {
        frozen.remove(player.getUniqueId());
    }

    /** Laisse au client KoriaMC le temps de se présenter, puis prévient le proxy (READY). */
    private void waitForClient(final Player player, final int waited) {
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !frozen.containsKey(player.getUniqueId())) {
                    return;
                }
                boolean client = plugin.gui().canDisplay(player);
                if (client || waited + 5 >= plugin.settings().clientWaitTicks) {
                    send(player, "READY", player.getUniqueId().toString(), client);
                } else {
                    waitForClient(player, waited + 5);
                }
            }
        }, 5L);
    }

    /** Compte à rebours dans l'écran, libération si le proxy ne répond jamais. */
    private void tick() {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, State>> it = frozen.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, State> entry = it.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            State state = entry.getValue();
            if (player == null) {
                it.remove();
                continue;
            }
            if (!state.answered && now - state.joinedAt > plugin.settings().proxyWaitSeconds * 1000L) {
                plugin.getLogger().warning("Aucune réponse du proxy pour " + player.getName() + " : KoriaAuth est-il installé"
                        + " sur le proxy ? Le joueur est libéré.");
                it.remove();
                release(player, false);
                continue;
            }
            if (!state.screen.isEmpty()) {
                plugin.gui().setVariable(player, "auth_temps", String.valueOf(Math.max(0, (state.deadline - now) / 1000L)));
            }
        }
    }

    // ============================================================ Messages du proxy

    @Override
    public void onPluginMessageReceived(String channel, Player carrier, byte[] data) {
        if (!CHANNEL.equals(channel)) {
            return;
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
            String type = in.readUTF();
            Player player = Bukkit.getPlayer(UUID.fromString(in.readUTF()));
            if (player == null) {
                return;
            }
            if ("SHOW".equals(type)) {
                String screen = in.readUTF();
                String error = in.readUTF();
                int attempts = in.readInt();
                int seconds = in.readInt();
                int minLength = in.readInt();
                show(player, screen, error, attempts, seconds, minLength, in.available() > 0 ? in.readUTF() : "");
            } else if ("DONE".equals(type)) {
                State state = frozen.remove(player.getUniqueId());
                release(player, state != null);
            }
        } catch (IOException | IllegalArgumentException e) {
            plugin.getLogger().warning("Message illisible du proxy sur " + CHANNEL);
        }
    }

    private void show(Player player, String screen, String error, int attempts, int secondsLeft, int minLength, String info) {
        State state = frozen.get(player.getUniqueId());
        if (state == null) {
            // le joueur avait été libéré (proxy très lent) : on le refige
            state = new State();
            frozen.put(player.getUniqueId(), state);
        }
        state.answered = true;
        state.screen = screen;
        state.error = error;
        state.attempts = attempts;
        state.minLength = minLength;
        state.info = info;
        state.deadline = System.currentTimeMillis() + secondsLeft * 1000L;
        if (!error.isEmpty()) {
            Sounds.play(player, plugin.settings().sound("erreur"));
        }
        open(player, state);
    }

    private void open(Player player, State state) {
        GuiHook gui = plugin.gui();
        gui.setVariable(player, "auth_joueur", player.getName());
        gui.setVariable(player, "auth_commande", plugin.commands().label("authlobby"));
        gui.setVariable(player, "auth_erreur", state.error);
        gui.setVariable(player, "auth_erreur_oui", String.valueOf(!state.error.isEmpty()));
        gui.setVariable(player, "auth_essais", String.valueOf(state.attempts));
        gui.setVariable(player, "auth_longueur_min", String.valueOf(state.minLength));
        gui.setVariable(player, "auth_info", state.info);
        gui.setVariable(player, "auth_temps", String.valueOf(Math.max(0, (state.deadline - System.currentTimeMillis()) / 1000L)));
        String layout = plugin.settings().layoutFor(state.screen);
        if (!gui.open(player, layout)) {
            plugin.getLogger().warning("L'écran " + layout + " n'a pas pu s'ouvrir : le fichier koriamc/guis/" + layout
                    + ".json existe-t-il ?");
        }
    }

    /** Écran fermé alors que le joueur n'est pas connecté : il revient. */
    public void onScreenClosed(final Player player, String layoutId) {
        final State state = frozen.get(player.getUniqueId());
        if (state == null || state.screen.isEmpty() || !plugin.settings().isAuthLayout(layoutId)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (player.isOnline() && frozen.get(player.getUniqueId()) == state) {
                    open(player, state);
                }
            }
        }, 2L);
    }

    private void release(Player player, boolean connected) {
        if (plugin.settings().blindness) {
            player.removePotionEffect(PotionEffectType.BLINDNESS);
        }
        plugin.gui().close(player);
        if (connected) {
            Sounds.play(player, plugin.settings().sound("connecte"));
        }
    }

    // ============================================================ Boutons des écrans

    /** Bouton Se connecter : le mot de passe part au proxy. */
    public void login(Player player, String password) {
        State state = frozen.get(player.getUniqueId());
        if (state == null || !state.answered) {
            return;
        }
        send(player, "LOGIN", player.getUniqueId().toString(), password);
    }

    /** Lien "Mot de passe oublié ?" (ou "Renvoyer le code") : le proxy envoie un code par mail. */
    public void forgot(Player player) {
        relay(player, "FORGOT");
    }

    /** Bouton Valider de l'écran du code. */
    public void code(Player player, String code) {
        relay(player, "CODE", code);
    }

    /** Bouton Valider de l'écran du nouveau mot de passe. */
    public void reset(Player player, String password, String confirmation) {
        relay(player, "RESET", password, confirmation);
    }

    /** Retour à l'écran de connexion. */
    public void back(Player player) {
        relay(player, "BACK");
    }

    /** Message au proxy pour un joueur en attente de connexion. */
    private void relay(Player player, String type, Object... values) {
        State state = frozen.get(player.getUniqueId());
        if (state == null || !state.answered) {
            return;
        }
        Object[] all = new Object[values.length + 2];
        all[0] = type;
        all[1] = player.getUniqueId().toString();
        System.arraycopy(values, 0, all, 2, values.length);
        send(player, all);
    }

    /** Bouton S'enregistrer. */
    public void register(Player player, String password, String confirmation) {
        State state = frozen.get(player.getUniqueId());
        if (state == null || !state.answered) {
            return;
        }
        send(player, "REGISTER", player.getUniqueId().toString(), password, confirmation);
    }

    private void send(Player player, Object... values) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        try {
            for (Object value : values) {
                if (value instanceof Boolean) {
                    out.writeBoolean((Boolean) value);
                } else if (value instanceof Integer) {
                    out.writeInt((Integer) value);
                } else {
                    out.writeUTF(String.valueOf(value));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        player.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
    }

    /** Joueurs présents au rechargement : on redemande l'état au proxy. */
    public void adoptOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!frozen.containsKey(player.getUniqueId())) {
                onJoin(player);
            }
        }
    }
}
