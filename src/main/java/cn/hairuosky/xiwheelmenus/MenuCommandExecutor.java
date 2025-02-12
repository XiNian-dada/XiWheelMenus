package cn.hairuosky.xiwheelmenus;

import cn.hairuosky.xiwheelmenus.XiWheelMenus;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
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
import java.util.UUID;

public class MenuCommandExecutor implements CommandExecutor, Listener {

    private final XiWheelMenus plugin;
    private Map<Player, List<ArmorStand>> playerArmorStands = new HashMap<>();
    private Map<Player, Integer> playerMenuItemIndexes = new HashMap<>();
    private Map<Player, FileConfiguration> playerMenuConfigs = new HashMap<>();
    private Map<Player, BukkitRunnable> playerMoveTasks = new HashMap<>();

    public MenuCommandExecutor(XiWheelMenus plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin); // Register this as a listener
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Usage: /xiwheelmenus <subcommand> [menuName]");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "open":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /xiwheelmenus open <menuName>");
                    return true;
                }
                String menuName = args[1] + ".yml";
                openMenu(player, menuName);
                break;
            default:
                player.sendMessage(ChatColor.RED + "Unknown subcommand. Usage: /xiwheelmenus <subcommand>");
                break;
        }

        return true;
    }

    private void openMenu(Player player, String fileName) {
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

            ArmorStand armorStand = player.getWorld().spawn(armorStandLocation, ArmorStand.class);
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
        }

        playerArmorStands.put(player, armorStands);
        playerMenuItemIndexes.put(player, initialIndex);
        playerMenuConfigs.put(player, menuConfig);
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

    private void startSmoothMoveTask(Player player, int currentIndex) {
        BukkitRunnable moveTask = playerMoveTasks.get(player);
        if (moveTask != null) {
            moveTask.cancel();
        }

        moveTask = new BukkitRunnable() {
            private final List<ArmorStand> armorStands = playerArmorStands.get(player);
            private final FileConfiguration menuConfig = playerMenuConfigs.get(player);
            private final List<String> itemKeys = new ArrayList<>(menuConfig.getConfigurationSection("items").getKeys(false));
            private final int itemCount = itemKeys.size();
            private final double angleStep = 360.0 / Math.min(itemCount, 6);
            private final double radius = 3.0; // Distance from player
            private final Location playerLocation = player.getLocation();
            private int step = 0;
            private final int totalSteps = 20; // Increase number of steps for smoother movement

            @Override
            public void run() {
                if (step >= totalSteps) {
                    this.cancel();
                    playerMoveTasks.remove(player);
                    return;
                }

                // Recalculate adjustedYawRad inside task to account for possible rotation changes
                double playerYaw = playerLocation.getYaw();
                if (playerYaw < 0) {
                    playerYaw += 360;
                }
                double manualOffsetAngle = 90.0; // Offset angle
                double yawRad = Math.toRadians(playerYaw) + Math.toRadians(manualOffsetAngle);

                for (int i = 0; i < Math.min(itemCount, 6); i++) {
                    double targetAngle = yawRad + Math.toRadians(angleStep * ((currentIndex + i) % itemCount));

                    double targetX = playerLocation.getX() + radius * Math.cos(targetAngle);
                    double targetY = playerLocation.getY();
                    double targetZ = playerLocation.getZ() + radius * Math.sin(targetAngle);

                    Location targetLocation = new Location(playerLocation.getWorld(), targetX, targetY, targetZ, playerLocation.getYaw(), playerLocation.getPitch());
                    armorStands.get(i).teleport(targetLocation);

                    // Set the head pose to face the player
                    Vector direction = player.getLocation().toVector().subtract(targetLocation.toVector()).setY(0).normalize();
                    float yaw = (float) Math.toDegrees(Math.atan2(direction.getX(), direction.getZ())) - 90;
                    armorStands.get(i).setHeadPose(new EulerAngle(Math.toRadians(-90), Math.toRadians(yaw), 0));
                }

                step++;
            }
        };

        moveTask.runTaskTimer(plugin, 0, 3); // Run every 1 tick
        playerMoveTasks.put(player, moveTask);
    }


    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        List<ArmorStand> armorStands = playerArmorStands.get(player);
        if (armorStands != null && !armorStands.isEmpty()) {
            for (ArmorStand armorStand : armorStands) {
                armorStand.remove();
            }
            playerArmorStands.remove(player);
            playerMenuItemIndexes.remove(player);
            playerMenuConfigs.remove(player);
            playerMoveTasks.remove(player);
            player.sendMessage(ChatColor.RED + "Menu closed due to movement.");
            plugin.getLogger().info("Menu closed for player " + player.getName() + " due to movement.");
        }
    }
}
