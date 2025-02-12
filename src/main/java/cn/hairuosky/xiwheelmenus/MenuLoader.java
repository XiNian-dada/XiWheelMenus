package cn.hairuosky.xiwheelmenus;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

import java.util.HashMap;
import java.util.Map;

public class MenuLoader {

    private final Map<String, FileConfiguration> menus = new HashMap<>();
    private final File menusFolder;

    public MenuLoader(JavaPlugin plugin) {
        this.menusFolder = new File(plugin.getDataFolder(), "menus");
        loadMenus();
    }

    private void loadMenus() {
        if (!menusFolder.exists()) {
            menusFolder.mkdirs();
        }

        File[] files = menusFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                menus.put(file.getName(), config);
            }
        }
    }

    public FileConfiguration getMenu(String fileName) {
        return menus.get(fileName);
    }

    public Map<String, FileConfiguration> getAllMenus() {
        return menus;
    }
}
