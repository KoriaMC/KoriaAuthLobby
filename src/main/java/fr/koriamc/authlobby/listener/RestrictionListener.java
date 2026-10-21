package fr.koriamc.authlobby.listener;

import fr.koriamc.authlobby.KoriaAuthLobby;
import fr.koriamc.authlobby.auth.LobbyAuth;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Un joueur pas encore connecté est figé sur le lobby : il ne bouge pas, ne touche ni aux blocs, ni aux objets, ni aux
 * autres joueurs et ne prend pas de dégâts. Le chat et les commandes sont bloqués par le proxy (KoriaAuth).
 */
public final class RestrictionListener implements Listener {

    private final KoriaAuthLobby plugin;

    public RestrictionListener(KoriaAuthLobby plugin) {
        this.plugin = plugin;
    }

    private LobbyAuth auth() {
        return plugin.auth();
    }

    private boolean blocked(Player player) {
        return player != null && auth().isFrozen(player);
    }

    private void block(Player player, Cancellable event) {
        if (blocked(player)) {
            event.setCancelled(true);
        }
    }

    // ============================================================ Arrivée / départ

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        auth().onJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        auth().onQuit(event.getPlayer());
    }

    // ============================================================ Blocages

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!blocked(event.getPlayer())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to != null && (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ())) {
            Location back = from.clone();
            back.setYaw(to.getYaw());
            back.setPitch(to.getPitch());
            event.setTo(back);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        block(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        block(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        block(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        block(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        block(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPickup(PlayerPickupItemEvent event) {
        block(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        block(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        HumanEntity who = event.getWhoClicked();
        if (who instanceof Player) {
            block((Player) who, event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        HumanEntity who = event.getWhoClicked();
        if (who instanceof Player) {
            block((Player) who, event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        HumanEntity who = event.getPlayer();
        if (who instanceof Player) {
            block((Player) who, event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            block((Player) event.getEntity(), event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamageOther(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Projectile && ((Projectile) damager).getShooter() instanceof Player) {
            damager = (Player) ((Projectile) damager).getShooter();
        }
        if (damager instanceof Player) {
            block((Player) damager, event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player) {
            block((Player) event.getEntity(), event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (event.getTarget() instanceof Player) {
            block((Player) event.getTarget(), event);
        }
    }
}
