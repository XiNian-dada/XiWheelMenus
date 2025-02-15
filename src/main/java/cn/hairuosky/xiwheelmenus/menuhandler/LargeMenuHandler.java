package cn.hairuosky.xiwheelmenus.menuhandler;

import cn.hairuosky.xiwheelmenus.XiWheelMenus;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class LargeMenuHandler {

    private final XiWheelMenus plugin;
    private String fileName;
    public final Map<Player, List<ArmorStand>> playerArmorStands = new HashMap<>();
    private final Map<Player, List<Location>> playerArmorStandLocations = new HashMap<>();
    public final Map<Player, Location> playerMenuCenters = new HashMap<>();
    public final Map<Player, Integer> playerMenuItemIndexes = new HashMap<>();
    private final Map<Player, FileConfiguration> playerMenuConfigs = new HashMap<>();
    private final Map<Player, BukkitRunnable> playerMoveTasks = new HashMap<>();
    private final Map<Player, BukkitRunnable> playerAnimationTasks = new HashMap<>();

    // Speed control variables
    private final int openAnimationSteps = 20; // Steps for opening animation
    private final int closeAnimationSteps = 20; // Steps for closing animation (reduced to 20 for faster closing)
    private final int moveAnimationSteps = 40; // Steps for moving animation

    // Map to store closing animation flag for each player
    private final Map<Player, Boolean> playerClosingFlags = new HashMap<>();

    public LargeMenuHandler(XiWheelMenus plugin) {
        this.plugin = plugin;
    }

    public void openMenu(Player player, String fileName) {
        fileName = fileName + ".yml"; // Ensure file name includes extension
        plugin.getLogger().info("Loading menu: " + fileName); // Log the file name
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

        double angleStep = 360.0 / 8; // 360 degrees divided by 8
        double radius = 3.0; // Distance from player

        List<ArmorStand> armorStands = new ArrayList<>();
        List<Location> armorStandLocations = new ArrayList<>();

        // Get player's yaw (which is in degrees)
        float playerYaw = playerLocation.getYaw();
        if (playerYaw < 0) {
            playerYaw += 360;
        }

        double manualOffsetAngle = -90.0; // Adjust this value to manually adjust the angle.
        double yawRad = Math.toRadians(playerYaw) + Math.toRadians(manualOffsetAngle);

        // First, create the empty "void" armor stand at position 0
        Location voidLocation = playerLocation.clone().add(radius * Math.cos(yawRad), 0, radius * Math.sin(yawRad));
        ArmorStand voidArmorStand = player.getWorld().spawn(voidLocation, ArmorStand.class);
        voidArmorStand.setVisible(false);
        voidArmorStand.setGravity(false);
        voidArmorStand.setBasePlate(false);
        voidArmorStand.setArms(false);
        voidArmorStand.setRotation(0, 0); // Keep the void armor stand transparent

        armorStands.add(voidArmorStand);
        armorStandLocations.add(voidLocation);

        // Now create armor stands for positions 1 to 7
        for (int i = 1; i <= 7; i++) {
            double angle = yawRad + Math.toRadians(angleStep * i); // Adjust angle based on index
            double xOffset = radius * Math.cos(angle);
            double zOffset = radius * Math.sin(angle);

            Location armorStandLocation = playerLocation.clone().add(xOffset, 0, zOffset);
            armorStandLocations.add(armorStandLocation);

            ArmorStand armorStand = player.getWorld().spawn(playerLocation, ArmorStand.class);
            armorStand.setVisible(false);
            armorStand.setGravity(false);
            armorStand.setBasePlate(false);
            armorStand.setArms(false);
            armorStand.setRotation(0, 0);

            // Set the item for each armor stand (starting from index 1)
            String itemName = menuConfig.getString("items." + itemKeys.get((i - 1) % itemCount) + ".item");
            ItemStack itemStack = createItemStack(itemName);
            armorStand.setHelmet(itemStack);

            armorStand.setCustomNameVisible(true);
            armorStand.setCustomName(menuConfig.getString("items." + itemKeys.get((i - 1) % itemCount) + ".name"));

            PersistentDataContainer pdc = armorStand.getPersistentDataContainer();
            pdc.set(plugin.getArmorStandKey(), PersistentDataType.STRING, player.getUniqueId().toString());

            armorStands.add(armorStand);
        }

        playerArmorStands.put(player, armorStands);
        playerArmorStandLocations.put(player, armorStandLocations);

        Location center = playerLocation.clone();
        center.add(radius * Math.cos(yawRad), 0, radius * Math.sin(yawRad));
        playerMenuCenters.put(player, playerLocation.clone());

        // Start the animation for armor stands
        animateArmorStandsOpen(player, playerLocation, armorStandLocations, openAnimationSteps);

        // Add particle effect for position 0 (empty location)
        player.getWorld().spawnParticle(
                Particle.REDSTONE,
                armorStandLocations.get(0), // This is where the empty spot is (index 0)
                10,
                new Particle.DustOptions(Color.BLUE, 1)
        );
        plugin.getLogger().info("Menu config for " + fileName + ": " + menuConfig.saveToString());

    }
    public void closeMenu(Player player) {
        if (playerClosingFlags.getOrDefault(player, false)) {
            return; // Do nothing if a closing animation is already in progress
        }

        playerClosingFlags.put(player, true);

        List<ArmorStand> armorStands = playerArmorStands.get(player);
        List<Location> armorStandLocations = playerArmorStandLocations.get(player);
        Location menuCenter = playerMenuCenters.get(player);

        if (armorStands != null && !armorStands.isEmpty() && armorStandLocations != null && menuCenter != null) {
            System.out.println("[DEBUG] Menu center location: " + menuCenter);

            List<Location> currentLocations = new ArrayList<>();
            for (ArmorStand armorStand : armorStands) {
                if (armorStand != null) {
                    currentLocations.add(armorStand.getLocation());
                }
            }

            animateArmorStandsClose(player, currentLocations, menuCenter, closeAnimationSteps, () -> {
                for (ArmorStand armorStand : armorStands) {
                    if (armorStand != null) {
                        armorStand.remove();
                    }
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

                playerClosingFlags.remove(player);
            });
        }
    }



    // Function to animate armor stands' movement to new locations
    private void animateArmorStandsCommon(Player player, List<Location> fromLocations, List<Location> toLocations, int totalSteps, Runnable onComplete) {
        List<ArmorStand> armorStands = playerArmorStands.get(player);
        if (armorStands == null || armorStands.isEmpty() || fromLocations == null || toLocations == null) {
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
                    if (onComplete != null) {
                        onComplete.run();
                    }
                    return;
                }

                // Loop through armor stands, starting from index 1 to avoid index 0
                for (int i = 1; i < armorStands.size(); i++) {
                    ArmorStand armorStand = armorStands.get(i);
                    Location currentLocation = armorStand.getLocation();
                    Location fromLocation = fromLocations.get(i); // Directly use i
                    Location targetLocation = toLocations.get(i); // Directly use i

                    // Calculate the intermediate location
                    double t = (double) step / totalSteps;
                    double x = fromLocation.getX() + t * (targetLocation.getX() - fromLocation.getX());
                    double y = fromLocation.getY() + t * (targetLocation.getY() - fromLocation.getY());
                    double z = fromLocation.getZ() + t * (targetLocation.getZ() - fromLocation.getZ());

                    Location intermediateLocation = new Location(currentLocation.getWorld(), x, y, z);
                    armorStand.teleport(intermediateLocation);

                    // Check if the armor stand has reached the "void" location (position 0)
                    if (intermediateLocation.equals(toLocations.get(0))) {
                        // If it's at position 0, make it a "void" armor stand (transparent with no items)
                        armorStand.setVisible(false);
                        armorStand.setGravity(false);
                        armorStand.setBasePlate(false);
                        armorStand.setArms(false);
                        armorStand.setCustomName(null);
                        armorStand.setCustomNameVisible(false);
                        // Clear all equipment on the armor stand
                        EntityEquipment equipment = armorStand.getEquipment();
                        if (equipment != null) {
                            equipment.clear();
                        }
                    }

                    // Set the armor stand to face the menu center
                    setArmorStandRotation(armorStand, intermediateLocation, playerMenuCenters.get(player));
                }

                step++;
            }
        };

        animationTask.runTaskTimer(plugin, 0, 1); // Run every tick (50 ms)
        playerAnimationTasks.put(player, animationTask);
    }


    // Animate armor stands from one location to another
    private void animateArmorStandsOpen(Player player, Location fromLocation, List<Location> toLocations, int totalSteps) {
        // Skip index 0, start from index 1
        List<Location> fromLocations = new ArrayList<>(Collections.nCopies(toLocations.size(), fromLocation));
        animateArmorStandsCommon(player, fromLocations, toLocations, totalSteps, null);
    }

    private void animateArmorStandsClose(Player player, List<Location> fromLocations, Location toLocation, int totalSteps, Runnable onComplete) {
        // Skip index 0, start from index 1
        List<Location> toLocations = new ArrayList<>(Collections.nCopies(fromLocations.size(), toLocation));
        animateArmorStandsCommon(player, fromLocations, toLocations, totalSteps, onComplete);
    }

    public void startSmoothMoveTask(Player player, int currentIndex) {
        BukkitRunnable moveTask = playerMoveTasks.get(player);
        if (moveTask != null) {
            moveTask.cancel();
        }
        FileConfiguration menuConfig = plugin.getMenuLoader().getMenu(fileName);
        if (menuConfig == null) {
            player.sendMessage(ChatColor.RED + "Menu configuration not found.");
            return;
        }

        ConfigurationSection itemsSection = menuConfig.getConfigurationSection("items");
        if (itemsSection == null) {
            player.sendMessage(ChatColor.RED + "No items section found in the menu configuration.");
            return;
        }
        moveTask = new BukkitRunnable() {
            private final List<ArmorStand> armorStands = playerArmorStands.get(player);
            private final List<Location> armorStandLocations = playerArmorStandLocations.get(player);
            private final List<String> itemKeys = new ArrayList<>(playerMenuConfigs.get(player).getConfigurationSection("items").getKeys(false));
            private final int itemCount = itemKeys.size();
            private int step = 0;
            private final int totalSteps = moveAnimationSteps; // Use moveAnimationSteps for smooth movement

            @Override
            public void run() {
                if (step >= totalSteps) {
                    this.cancel();
                    playerMoveTasks.remove(player);
                    return;
                }

                // Ensure armorStands and armorStandLocations have the same size
                if (armorStands.size() != armorStandLocations.size()) {
                    System.out.println("[DEBUG] armorStands and armorStandLocations size mismatch");
                    this.cancel();
                    return;
                }

                // Create a new list to hold the new armor stands
                List<ArmorStand> newArmorStands = new ArrayList<>(armorStands);

                // Always get coordinates from armorStandLocations
                for (int i = 0; i < armorStands.size(); i++) {
                    // Use pre-calculated 8-way points coordinates
                    int targetIndex = (currentIndex + i + 1) % 8; // Adjusted to ensure index is within 0-7
                    if (targetIndex == 0) {
                        // Skip the buffer position (index 0)
                        continue;
                    }
                    Location targetLocation = armorStandLocations.get(targetIndex); // Adjusted to skip buffer position
                    ArmorStand armorStand = armorStands.get(i);

                    armorStand.teleport(targetLocation);
                    setArmorStandRotation(armorStand, targetLocation, playerMenuCenters.get(player));

                    // Add debug information
                    System.out.println("[DEBUG] Moved armor stand " + i + " to: " + targetLocation);
                }

                // Check if the 0th position is empty (virtual armor stand)
                Location bufferLocation = armorStandLocations.get(0);
                ArmorStand newArmorStand = player.getWorld().spawn(bufferLocation, ArmorStand.class);
                newArmorStand.setVisible(false);
                newArmorStand.setGravity(false);
                newArmorStand.setBasePlate(false);
                newArmorStand.setArms(false);
                newArmorStand.setRotation(0, 0); // Set base rotation to 0

                // Set the armor stand to face the menu center
                setArmorStandRotation(newArmorStand, bufferLocation, playerMenuCenters.get(player));

                // Use the correct item key for the new armor stand
                String itemName = playerMenuConfigs.get(player).getString("items." + itemKeys.get((currentIndex + 7) % itemCount) + ".item");
                ItemStack itemStack = createItemStack(itemName);
                newArmorStand.setHelmet(itemStack);

                newArmorStand.setCustomNameVisible(true);
                newArmorStand.setCustomName(playerMenuConfigs.get(player).getString("items." + itemKeys.get((currentIndex + 7) % itemCount) + ".name"));

                // Add player UUID tag to the armor stand
                PersistentDataContainer pdc = newArmorStand.getPersistentDataContainer();
                pdc.set(plugin.getArmorStandKey(), PersistentDataType.STRING, player.getUniqueId().toString());

                // Add the new armor stand to the list
                newArmorStands.add(newArmorStand);

                // Update the armor stands list
                playerArmorStands.put(player, newArmorStands);

                step++;
            }
        };

        moveTask.runTaskTimer(plugin, 0, 5); // Run every 5 ticks to slow down the movement
        playerMoveTasks.put(player, moveTask);
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

    private void setArmorStandRotation(ArmorStand armorStand, Location armorStandLocation, Location targetLocation) {
        // 1. 计算从盔甲架指向圆心的方向向量
        Vector direction = targetLocation.toVector().subtract(armorStandLocation.toVector()).setY(0).normalize();

        // 检查方向向量是否有效
        if (!isFinite(direction)) {
            System.out.println("[DEBUG] 方向向量无效: " + direction);
            // 设置默认方向（例如，面向玩家）
            armorStand.setRotation(armorStandLocation.getYaw(), 0);
            return;
        }

        // 2. 计算正确的基础角度（Minecraft坐标系）
        double yaw = Math.toDegrees(Math.atan2(direction.getZ(), direction.getX())) - 90;

        // 检查yaw是否有效
        if (!Double.isFinite(yaw)) {
            System.out.println("[DEBUG] Yaw值无效: " + yaw);
            // 设置默认方向（例如，面向玩家）
            armorStand.setRotation(armorStandLocation.getYaw(), 0);
            return;
        }

        // 3. 设置盔甲架的朝向（注意：Y轴旋转需要取反）
        armorStand.setRotation((float) yaw, 0);

        // 调试粒子（红色：盔甲架位置，绿色：实际面向方向）
        Location headPos = armorStand.getLocation().add(0, 1.5, 0);
        armorStand.getWorld().spawnParticle(Particle.REDSTONE, headPos, 5,
                new Particle.DustOptions(Color.RED, 1));

        // 获取盔甲架实际方向向量
        Vector headDirection = new Vector(
                Math.sin(Math.toRadians(-yaw)),
                0,
                Math.cos(Math.toRadians(-yaw))
        );
        armorStand.getWorld().spawnParticle(Particle.REDSTONE,
                headPos.clone().add(headDirection), 5,
                new Particle.DustOptions(Color.LIME, 1));
    }


    private boolean isFinite(Vector vector) {
        return Double.isFinite(vector.getX()) && Double.isFinite(vector.getY()) && Double.isFinite(vector.getZ());
    }
}
