package fr.koriamc.authlobby.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Réglages typés de config.json, relus à chaque rechargement.
 */
public final class Settings {

    public boolean debug;

    // Écrans
    public String loginLayout;
    public String registerLayout;
    /** Mot de passe oublié : saisie du code reçu par mail, puis du nouveau mot de passe. */
    public String codeLayout;
    public String resetLayout;
    public boolean installLayout;

    // Attente
    /** Attente du client KoriaMC avant de répondre au proxy (ticks). */
    public int clientWaitTicks;
    /** Sans réponse du proxy (KoriaAuth absent), le joueur est libéré après ce délai (secondes). */
    public int proxyWaitSeconds;
    public boolean blindness;

    // Titres (messages [title])
    public int titleFadeIn;
    public int titleStay;
    public int titleFadeOut;

    private final Map<String, String> sounds = new HashMap<String, String>();

    public void load(ConfigurationSection c, Logger logger) {
        debug = c.getBoolean("debug");
        loginLayout = layout(c, "ecrans.connexion", "login", logger);
        registerLayout = layout(c, "ecrans.inscription", "register", logger);
        codeLayout = layout(c, "ecrans.code", "reset_code", logger);
        resetLayout = layout(c, "ecrans.nouveau-mot-de-passe", "reset_password", logger);
        installLayout = c.getBoolean("ecrans.installer-layouts", true);

        clientWaitTicks = Math.max(0, c.getInt("attente.client-ticks", 60));
        proxyWaitSeconds = Math.max(3, c.getInt("attente.proxy-secondes", 15));
        blindness = c.getBoolean("cecite", true);
        titleFadeIn = c.getInt("titres.apparition", 5);
        titleStay = c.getInt("titres.duree", 40);
        titleFadeOut = c.getInt("titres.disparition", 10);

        sounds.clear();
        ConfigurationSection soundSection = c.getConfigurationSection("sons");
        if (soundSection != null) {
            for (String key : soundSection.getKeys(false)) {
                sounds.put(key, soundSection.getString(key, ""));
            }
        }
    }

    private static String layout(ConfigurationSection c, String path, String def, Logger logger) {
        String value = c.getString(path);
        value = value == null ? def : value.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            logger.warning("config.json : " + path + " est vide, \"" + def + "\" utilisé.");
            return def;
        }
        return value;
    }

    /** Layout d'un écran demandé par le proxy (login, register, reset_code, reset_password). */
    public String layoutFor(String screen) {
        if ("login".equals(screen)) {
            return loginLayout;
        }
        if ("reset_code".equals(screen)) {
            return codeLayout;
        }
        if ("reset_password".equals(screen)) {
            return resetLayout;
        }
        return registerLayout;
    }

    /** Un des écrans de connexion ? */
    public boolean isAuthLayout(String layoutId) {
        return layoutId.equals(loginLayout) || layoutId.equals(registerLayout) || layoutId.equals(codeLayout)
                || layoutId.equals(resetLayout);
    }

    /** Son de la section sons (chaîne vide si absent). */
    public String sound(String key) {
        String value = sounds.get(key);
        return value == null ? "" : value;
    }
}
