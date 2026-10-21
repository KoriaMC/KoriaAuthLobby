package fr.koriamc.authlobby.util;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Identifiant d'un objet au format du jeu (minecraft:diamond_sword, koriamc:jade), lu dans
 * le registre des items du serveur par réflexion : les objets ajoutés par KoriaMC n'ont pas
 * de nom Bukkit.
 */
public final class ItemIds {

    private static boolean initialized;
    private static Method asNmsCopy;
    private static Method getItem;
    private static Object registry;
    private static Method keyOf;

    private ItemIds() {
    }

    private static void init() {
        initialized = true;
        try {
            String pkg = Bukkit.getServer().getClass().getPackage().getName();
            String version = pkg.substring(pkg.lastIndexOf('.') + 1);
            Class<?> craftStack = Class.forName("org.bukkit.craftbukkit." + version + ".inventory.CraftItemStack");
            Class<?> nmsStack = Class.forName("net.minecraft.server." + version + ".ItemStack");
            Class<?> item = Class.forName("net.minecraft.server." + version + ".Item");
            asNmsCopy = craftStack.getMethod("asNMSCopy", ItemStack.class);
            getItem = nmsStack.getMethod("getItem");
            Field field = item.getField("REGISTRY");
            registry = field.get(null);
            // RegistryMaterials.c(valeur) : clé de la valeur
            keyOf = registry.getClass().getMethod("c", Object.class);
        } catch (Throwable t) {
            asNmsCopy = null;
        }
    }

    /** @return "namespace:nom", ou null si l'objet est vide */
    public static String of(ItemStack stack) {
        if (stack == null || stack.getTypeId() == 0) {
            return null;
        }
        if (!initialized) {
            init();
        }
        if (asNmsCopy != null) {
            try {
                Object nms = asNmsCopy.invoke(null, stack);
                Object item = nms == null ? null : getItem.invoke(nms);
                Object key = item == null ? null : keyOf.invoke(registry, item);
                if (key != null) {
                    String id = key.toString();
                    return id.contains(":") ? id : "minecraft:" + id;
                }
            } catch (Throwable ignored) {
                // repli ci-dessous
            }
        }
        return "minecraft:" + stack.getType().name().toLowerCase(Locale.ROOT);
    }
}
