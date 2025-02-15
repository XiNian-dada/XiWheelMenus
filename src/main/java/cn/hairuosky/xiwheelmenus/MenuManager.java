package cn.hairuosky.xiwheelmenus;

import cn.hairuosky.xiwheelmenus.menuhandler.LargeMenuHandler;
import cn.hairuosky.xiwheelmenus.menuhandler.SmallMenuHandler;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.ArrayList;
import java.util.List;

public class MenuManager implements Listener {

    private final XiWheelMenus plugin;
    private final SmallMenuHandler smallMenuHandler;
    private final LargeMenuHandler largeMenuHandler;

    public MenuManager(XiWheelMenus plugin) {
        this.plugin = plugin;
        this.smallMenuHandler = new SmallMenuHandler(plugin);
        this.largeMenuHandler = new LargeMenuHandler(plugin);
        plugin.getServer().getPluginManager().registerEvents(this, plugin); // Register this as a listener
    }

    public void openMenu(Player player, String fileName) {
        FileConfiguration menuConfig = plugin.getMenuLoader().getMenu(fileName);
        if (menuConfig == null) {
            player.sendMessage(ChatColor.RED + "Menu not found.");
            return;
        }

        ConfigurationSection itemsSection = menuConfig.getConfigurationSection("items");
        if (itemsSection == null) {
            player.sendMessage(ChatColor.RED + "No items found in the menu configuration.");
            return;
        }

        List<String> itemKeys = new ArrayList<>(itemsSection.getKeys(false));
        int itemCount = itemKeys.size();

        if (itemCount == 0) {
            player.sendMessage(ChatColor.RED + "No items found in the menu configuration.");
            return;
        }

        if (itemCount <= 8) {
            smallMenuHandler.openMenu(player, fileName);
        } else {
            largeMenuHandler.openMenu(player, fileName);
        }
    }

    public void closeMenu(Player player) {
        // 可以选择根据情况调用 smallMenuHandler 或 largeMenuHandler 的 closeMenu 方法
        smallMenuHandler.closeMenu(player);
        largeMenuHandler.closeMenu(player);
    }

    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        int newSlot = event.getNewSlot();
        int oldSlot = event.getPreviousSlot();

        List<ArmorStand> armorStands = smallMenuHandler.playerArmorStands.get(player);
        if (armorStands == null || armorStands.isEmpty()) {
            armorStands = largeMenuHandler.playerArmorStands.get(player);
            if (armorStands == null || armorStands.isEmpty()) {
                return;
            }
        }

        int currentIndex = smallMenuHandler.playerMenuItemIndexes.getOrDefault(player, largeMenuHandler.playerMenuItemIndexes.getOrDefault(player, 0));
        int itemCount = armorStands.size();

        // Handle boundary conditions
        if (newSlot < oldSlot) {
            if (oldSlot == 8 && newSlot == 0) {
                currentIndex = (currentIndex - 1 + itemCount) % itemCount; // Treat as scroll up
            } else {
                currentIndex = (currentIndex + 1) % itemCount;
            }
        } else if (newSlot > oldSlot) {
            if (oldSlot == 0 && newSlot == 8) {
                currentIndex = (currentIndex + 1) % itemCount; // Treat as scroll down
            } else {
                currentIndex = (currentIndex - 1 + itemCount) % itemCount;
            }
        }

        if (armorStands == smallMenuHandler.playerArmorStands.get(player)) {
            smallMenuHandler.playerMenuItemIndexes.put(player, currentIndex);
            smallMenuHandler.startSmoothMoveTask(player, currentIndex);
        } else {
            largeMenuHandler.playerMenuItemIndexes.put(player, currentIndex);
            largeMenuHandler.startSmoothMoveTask(player, currentIndex);
        }

        plugin.getLogger().info("Player " + player.getName() + " scrolled. New index: " + currentIndex);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        List<ArmorStand> armorStands = smallMenuHandler.playerArmorStands.get(player);
        if (armorStands == null || armorStands.isEmpty()) {
            armorStands = largeMenuHandler.playerArmorStands.get(player);
            if (armorStands == null || armorStands.isEmpty()) {
                return;
            }
        }

        // Get the initial player location (menu center)
        Location menuCenter = smallMenuHandler.playerMenuCenters.get(player);
        if (menuCenter == null) {
            menuCenter = largeMenuHandler.playerMenuCenters.get(player);
            if (menuCenter == null) {
                closeMenu(player);
                return;
            }
        }

        // Calculate the distance from the player to the menu center
        Location playerLocation = player.getLocation();
        double distanceToCenter = playerLocation.distance(menuCenter);

        // Define threshold for movement
        double movementThreshold = 0.5; // 0.5 blocks

        // Only remove the menu if the player moves more than the threshold from the center
        if (distanceToCenter > movementThreshold) {
            closeMenu(player);
        }
    }
}
