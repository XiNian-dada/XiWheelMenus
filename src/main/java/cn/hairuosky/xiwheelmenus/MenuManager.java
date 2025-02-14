package cn.hairuosky.xiwheelmenus;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MenuManager implements Listener {

    private final XiWheelMenus plugin;
    private final Map<Player, List<ArmorStand>> playerArmorStands = new HashMap<>();
    private final Map<Player, List<Location>> playerArmorStandLocations = new HashMap<>();
    private final Map<Player, Location> playerMenuCenters = new HashMap<>();
    private final Map<Player, Integer> playerMenuItemIndexes = new HashMap<>();
    private final Map<Player, FileConfiguration> playerMenuConfigs = new HashMap<>();
    private final Map<Player, BukkitRunnable> playerMoveTasks = new HashMap<>();
    private final Map<Player, BukkitRunnable> playerAnimationTasks = new HashMap<>();

    // Speed control variables
    private final int openAnimationSteps = 20; // Steps for opening animation
    private final int closeAnimationSteps = 20; // Steps for closing animation (reduced to 20 for faster closing)
    private final int moveAnimationSteps = 40; // Steps for moving animation

    // Map to store closing animation flag for each player
    private final Map<Player, Boolean> playerClosingFlags = new HashMap<>();

    public MenuManager(XiWheelMenus plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin); // Register this as a listener
    }

    public void openMenu(Player player, String fileName) {
        FileConfiguration menuConfig = plugin.getMenuLoader().getMenu(fileName);
        if (menuConfig == null) {
            player.sendMessage(ChatColor.RED + "Menu not found.");
            return;
        }

        Location playerLocation = player.getLocation();
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

        double angleStep = 360.0 / Math.min(itemCount, 6); // 360 degrees divided by min(itemCount, 6)
        double radius = 3.0; // Distance from player

        List<ArmorStand> armorStands = new ArrayList<>();
        List<Location> armorStandLocations = new ArrayList<>();
        int initialIndex = 0;

        // Get player's yaw (which is in degrees)
        float playerYaw = playerLocation.getYaw();

        // Ensure the yaw is in the correct range (0 to 360 degrees)
        if (playerYaw < 0) {
            playerYaw += 360;
        }

        // Define a manual offset angle (in degrees). Adjust this value to shift the rotation of all armor stands.
        double manualOffsetAngle = 90.0; // You can modify this value to manually adjust the angle.

        // Calculate adjusted yaw (player's yaw + manual offset)
        double yawRad = Math.toRadians(playerYaw) + Math.toRadians(manualOffsetAngle);

        // Calculate the offset for the first armor stand (directly in front of the player)
        for (int i = 0; i < Math.min(itemCount, 6); i++) {
            // The angle for the current armor stand, adjusted by the player's yaw and the manual offset
            double angle = yawRad + Math.toRadians(angleStep * i); // Add angleStep for subsequent positions

            // Calculate the offset for the armor stand based on the player's yaw
            double xOffset = radius * Math.cos(angle); // Use cosine for x offset
            double zOffset = radius * Math.sin(angle); // Use sine for z offset

            // Position the armor stand at the correct location in front of the player
            Location armorStandLocation = playerLocation.clone().add(xOffset, 0, zOffset);

            ArmorStand armorStand = player.getWorld().spawn(playerLocation, ArmorStand.class); // Spawn at player location
            armorStand.setVisible(false);
            armorStand.setGravity(false);
            armorStand.setBasePlate(false);
            armorStand.setArms(false);

            // Set the head pose to face the player
            Vector headDirection = player.getLocation().toVector().subtract(armorStand.getLocation().toVector()).setY(0).normalize();
            float yaw = (float) Math.toDegrees(Math.atan2(headDirection.getX(), headDirection.getZ())) - 90;
            armorStand.setHeadPose(new EulerAngle(Math.toRadians(-90), Math.toRadians(yaw), 0));

            String itemName = menuConfig.getString("items." + itemKeys.get(i) + ".item");
            ItemStack itemStack = createItemStack(itemName);
            armorStand.setHelmet(itemStack);

            armorStand.setCustomNameVisible(true);
            armorStand.setCustomName(menuConfig.getString("items." + itemKeys.get(i) + ".name"));

            // Add player UUID tag to the armor stand
            PersistentDataContainer pdc = armorStand.getPersistentDataContainer();
            pdc.set(plugin.getArmorStandKey(), PersistentDataType.STRING, player.getUniqueId().toString());

            armorStands.add(armorStand);
            armorStandLocations.add(armorStandLocation);
        }

        playerArmorStands.put(player, armorStands);
        playerArmorStandLocations.put(player, armorStandLocations);
        playerMenuCenters.put(player, playerLocation);
        playerMenuItemIndexes.put(player, initialIndex);
        playerMenuConfigs.put(player, menuConfig);

        // Start the animation to move armor stands to their target locations
        animateArmorStands(player, playerLocation, armorStandLocations, openAnimationSteps);
    }

    public void closeMenu(Player player) {
        // Check if a closing animation is already in progress
        if (playerClosingFlags.getOrDefault(player, false)) {
            return; // Do nothing if a closing animation is already in progress
        }

        // Set the closing flag to true
        playerClosingFlags.put(player, true);

        List<ArmorStand> armorStands = playerArmorStands.get(player);
        List<Location> armorStandLocations = playerArmorStandLocations.get(player);
        Location playerLocation = playerMenuCenters.get(player);

        if (armorStands != null && !armorStands.isEmpty() && armorStandLocations != null && playerLocation != null) {
            // Start the animation to move armor stands back to the player location
            animateArmorStands(player, armorStandLocations, playerLocation, closeAnimationSteps, () -> {
                // Remove armor stands after animation
                for (ArmorStand armorStand : armorStands) {
                    armorStand.remove();
                }
                playerArmorStands.remove(player);
                playerArmorStandLocations.remove(player);
                playerMenuCenters.remove(player);
                playerMenuItemIndexes.remove(player);
                playerMenuConfigs.remove(player);
                BukkitRunnable moveTask = playerMoveTasks.remove(player);
                if (moveTask != null) {
                    moveTask.cancel();
                }
                player.sendMessage(ChatColor.RED + "Menu closed.");
                plugin.getLogger().info("Menu closed for player " + player.getName());

                // Reset the closing flag
                playerClosingFlags.remove(player);
            });
        }
    }

    private void animateArmorStands(Player player, Location fromLocation, List<Location> toLocations, int totalSteps) {
        List<ArmorStand> armorStands = playerArmorStands.get(player);
        if (armorStands == null || armorStands.isEmpty() || toLocations == null) {
            return;
        }

        // Cancel any existing animation task
        BukkitRunnable existingTask = playerAnimationTasks.remove(player);
        if (existingTask != null) {
            existingTask.cancel();
        }

        BukkitRunnable animationTask = new BukkitRunnable() {
            private int step = 0;

            @Override
            public void run() {
                if (step >= totalSteps) {
                    this.cancel();
                    return;
                }

                for (int i = 0; i < Math.min(armorStands.size(), toLocations.size()); i++) {
                    ArmorStand armorStand = armorStands.get(i);
                    Location currentLocation = armorStand.getLocation();
                    Location targetLocation = toLocations.get(i);

                    // Calculate the intermediate location
                    double t = (double) step / totalSteps;
                    double x = fromLocation.getX() + t * (targetLocation.getX() - fromLocation.getX());
                    double y = fromLocation.getY() + t * (targetLocation.getY() - fromLocation.getY());
                    double z = fromLocation.getZ() + t * (targetLocation.getZ() - fromLocation.getZ());

                    Location intermediateLocation = new Location(currentLocation.getWorld(), x, y, z);
                    armorStand.teleport(intermediateLocation);

                    // Set the head pose to face the player
                    Vector direction = player.getLocation().toVector().subtract(intermediateLocation.toVector()).setY(0).normalize();
                    float yaw = (float) Math.toDegrees(Math.atan2(direction.getX(), direction.getZ())) - 90;
                    armorStand.setHeadPose(new EulerAngle(Math.toRadians(-90), Math.toRadians(yaw), 0));
                }

                step++;
            }
        };

        animationTask.runTaskTimer(plugin, 0, 1); // Run every tick (50 ms)
        playerAnimationTasks.put(player, animationTask);
    }

    private void animateArmorStands(Player player, List<Location> fromLocations, Location toLocation, int totalSteps, Runnable onComplete) {
        List<ArmorStand> armorStands = playerArmorStands.get(player);
        if (armorStands == null || armorStands.isEmpty() || fromLocations == null) {
            return;
        }

        // Cancel any existing animation task
        BukkitRunnable existingTask = playerAnimationTasks.remove(player);
        if (existingTask != null) {
            existingTask.cancel();
        }

        // Store the target location at the start of the animation
        Location fixedTargetLocation = toLocation.clone();

        BukkitRunnable animationTask = new BukkitRunnable() {
            private int step = 0;

            @Override
            public void run() {
                if (step >= totalSteps) {
                    this.cancel();
                    if (onComplete != null) {
                        onComplete.run();
                    }
                    return;
                }

                for (int i = 0; i < Math.min(armorStands.size(), fromLocations.size()); i++) {
                    ArmorStand armorStand = armorStands.get(i);
                    Location currentLocation = armorStand.getLocation();
                    Location fromLocation = fromLocations.get(i);

                    // Calculate the intermediate location
                    double t = (double) step / totalSteps;
                    double x = currentLocation.getX() + t * (fixedTargetLocation.getX() - currentLocation.getX());
                    double y = currentLocation.getY() + t * (fixedTargetLocation.getY() - currentLocation.getY());
                    double z = currentLocation.getZ() + t * (fixedTargetLocation.getZ() - currentLocation.getZ());

                    Location intermediateLocation = new Location(currentLocation.getWorld(), x, y, z);
                    armorStand.teleport(intermediateLocation);

                    // Keep the current head pose
                    armorStand.setHeadPose(armorStand.getHeadPose());
                }

                step++;
            }
        };

        animationTask.runTaskTimer(plugin, 0, 1); // Run every tick (50 ms)
        playerAnimationTasks.put(player, animationTask);
    }

    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        int newSlot = event.getNewSlot();
        int oldSlot = event.getPreviousSlot();

        List<ArmorStand> armorStands = playerArmorStands.get(player);
        if (armorStands == null || armorStands.isEmpty()) {
            return;
        }

        int currentIndex = playerMenuItemIndexes.getOrDefault(player, 0);
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

        playerMenuItemIndexes.put(player, currentIndex);
        startSmoothMoveTask(player, currentIndex);

        plugin.getLogger().info("Player " + player.getName() + " scrolled. New index: " + currentIndex);
    }

    public void startSmoothMoveTask(Player player, int currentIndex) {
        BukkitRunnable moveTask = playerMoveTasks.get(player);
        if (moveTask != null) {
            moveTask.cancel();
        }

        moveTask = new BukkitRunnable() {
            private final List<ArmorStand> armorStands = playerArmorStands.get(player);
            private final List<Location> armorStandLocations = playerArmorStandLocations.get(player);
            private final int itemCount = armorStands.size();
            private int step = 0;
            private final int totalSteps = moveAnimationSteps; // Use moveAnimationSteps for smooth movement

            @Override
            public void run() {
                if (step >= totalSteps) {
                    this.cancel();
                    playerMoveTasks.remove(player);
                    return;
                }

                for (int i = 0; i < Math.min(itemCount, 6); i++) {
                    int targetIndex = (currentIndex + i) % itemCount;
                    Location targetLocation = armorStandLocations.get(targetIndex);
                    armorStands.get(i).teleport(targetLocation);

                    // Set the head pose to face the player
                    Vector direction = player.getLocation().toVector().subtract(targetLocation.toVector()).setY(0).normalize();
                    float yaw = (float) Math.toDegrees(Math.atan2(direction.getX(), direction.getZ())) - 90;
                    armorStands.get(i).setHeadPose(new EulerAngle(Math.toRadians(-90), Math.toRadians(yaw), 0));
                }

                step++;
            }
        };

        moveTask.runTaskTimer(plugin, 0, 5); // Run every 5 ticks to slow down the movement
        playerMoveTasks.put(player, moveTask);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        List<ArmorStand> armorStands = playerArmorStands.get(player);
        if (armorStands != null && !armorStands.isEmpty()) {
            // Get the initial player location (menu center)
            Location menuCenter = playerMenuCenters.get(player);
            if (menuCenter == null) {
                closeMenu(player);
                return;
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

    private ItemStack createItemStack(String itemName) {
        if (itemName.startsWith("player_head:")) {
            String[] parts = itemName.split(":");
            String texture = parts[1];
            ItemStack itemStack = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skullMeta = (SkullMeta) itemStack.getItemMeta();
            skullMeta.setOwner("MHF_ArrowDown"); // Placeholder, replace with actual texture
            skullMeta.setDisplayName(texture);
            itemStack.setItemMeta(skullMeta);
            return itemStack;
        } else {
            Material material = Material.matchMaterial(itemName);
            if (material != null) {
                return new ItemStack(material);
            } else {
                return new ItemStack(Material.STONE); // Default item if material not found
            }
        }
    }
}
