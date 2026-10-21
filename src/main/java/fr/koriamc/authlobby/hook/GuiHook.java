package fr.koriamc.authlobby.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pont vers les GUI custom du serveur KoriaMC (fr.koriamc.gui.KoriaGuiAPI), par réflexion :
 * le plugin fonctionne aussi sur un Spigot classique (menu remplacé par un résumé dans le chat).
 */
public final class GuiHook {

    private final Logger logger;
    private boolean available;
    private Method isKoriaClient;
    private Method openGui;
    private Method setVariable;
    private Method setList;
    private Method pushClientActions;
    private Method closeGui;
    private Method managerGet;
    private Method managerReload;

    public GuiHook(Logger logger) {
        this.logger = logger;
        try {
            Class<?> api = Class.forName("fr.koriamc.gui.KoriaGuiAPI");
            isKoriaClient = api.getMethod("isKoriaClient", Player.class);
            openGui = api.getMethod("openGui", Player.class, String.class, boolean.class);
            setVariable = api.getMethod("setVariable", Player.class, String.class, String.class);
            setList = api.getMethod("setList", Player.class, String.class, List.class);
            pushClientActions = api.getMethod("pushClientActions", Player.class, String.class, String.class);
            closeGui = api.getMethod("closeGui", Player.class);
            Class<?> manager = Class.forName("fr.koriamc.gui.KoriaGuiManager");
            managerGet = manager.getMethod("get");
            managerReload = manager.getMethod("reload");
            available = true;
        } catch (Throwable t) {
            available = false;
        }
    }

    /** Serveur KoriaMC avec le système de GUI. */
    public boolean isAvailable() {
        return available;
    }

    /** Joueur sur le client KoriaMC (seul capable d'afficher le menu custom). */
    public boolean canDisplay(Player player) {
        if (!available) {
            return false;
        }
        try {
            return (Boolean) isKoriaClient.invoke(null, player);
        } catch (Throwable t) {
            return false;
        }
    }

    public void setVariable(Player player, String key, String value) {
        call(setVariable, player, key, value);
    }

    public void setList(Player player, String key, List<Map<String, String>> items) {
        call(setList, player, key, items);
    }

    /** Ouvre le layout sans vérifier sa permission (le plugin l'a déjà fait). */
    public boolean open(Player player, String layoutId) {
        Object result = call(openGui, player, layoutId, Boolean.TRUE);
        return Boolean.TRUE.equals(result);
    }

    /** Ferme l'écran custom ouvert. */
    public void close(Player player) {
        call(closeGui, player);
    }

    /**
     * Prévient quand un écran custom se ferme (évènement fr.koriamc.gui.event.GuiCloseEvent du serveur KoriaMC).
     *
     * @param callback reçoit le joueur et l'identifiant du layout fermé
     */
    public void onClose(Plugin plugin, final CloseCallback callback) {
        if (!available) {
            return;
        }
        try {
            final Class<? extends Event> type = Class.forName("fr.koriamc.gui.event.GuiCloseEvent").asSubclass(Event.class);
            final Method layoutId = type.getMethod("getLayoutId");
            Listener listener = new Listener() {
            };
            Bukkit.getPluginManager().registerEvent(type, listener, EventPriority.MONITOR, new EventExecutor() {
                @Override
                public void execute(Listener l, Event event) {
                    if (!type.isInstance(event)) {
                        return;
                    }
                    try {
                        callback.closed(((PlayerEvent) event).getPlayer(), String.valueOf(layoutId.invoke(event)));
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Fermeture d'écran KoriaMC", e);
                    }
                }
            }, plugin);
        } catch (Throwable t) {
            logger.warning("Évènement de fermeture des GUI KoriaMC introuvable : le menu de connexion ne sera pas rouvert"
                    + " automatiquement s'il est fermé.");
        }
    }

    public interface CloseCallback {
        void closed(Player player, String layoutId);
    }

    /** Actions client (page:, show:...) sur le layout affiché. */
    public void clientActions(Player player, String layoutId, String lines) {
        call(pushClientActions, player, layoutId, lines);
    }

    /** Relit les layouts de koriamc/guis (après installation du menu). */
    public void reloadLayouts() {
        if (!available) {
            return;
        }
        try {
            managerReload.invoke(managerGet.invoke(null));
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Impossible de recharger les GUI KoriaMC", t);
        }
    }

    private Object call(Method method, Object... args) {
        if (!available) {
            return null;
        }
        try {
            return method.invoke(null, args);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Appel du GUI KoriaMC impossible (" + method.getName() + ")", t);
            return null;
        }
    }
}
