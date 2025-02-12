package cn.hairuosky.xiwheelmenus;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;

//TODO 头颅没有面向玩家，而是抬头状态？
//TODO 现在只实现了生成和消失，注意，在菜单存在时执行指令会导致无法消失
//TODO 盔甲架名字不一样
//TODO 还没有测试6+的情况

public final class XiWheelMenus extends JavaPlugin {

    private MenuLoader menuLoader;
    private NamespacedKey armorStandKey;

    @Override
    public void onEnable() {
        // 创建 menus 文件夹
        File menusFolder = new File(getDataFolder(), "menus");
        if (!menusFolder.exists()) {
            menusFolder.mkdirs();
        }

        // 释放 menu.yml 模板文件
        File menuYmlFile = new File(menusFolder, "menu.yml");
        if (!menuYmlFile.exists()) {
            try (InputStream inputStream = getResource("menu.yml")) {
                if (inputStream != null) {
                    Files.copy(inputStream, menuYmlFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } else {
                    getLogger().severe("Failed to find menu.yml in resources!");
                }
            } catch (IOException e) {
                getLogger().severe("Failed to copy menu.yml to menus folder: " + e.getMessage());
            }
        }

        // 初始化 MenuLoader
        menuLoader = new MenuLoader(this);

        // 打印所有菜单内容
        printAllMenus();

        // 注册指令处理器
        this.getCommand("xiwheelmenus").setExecutor(new MenuCommandExecutor(this));


        // 初始化 armorStandKey
        armorStandKey = new NamespacedKey(this, "armor_stand_uuid");
    }

    private void printAllMenus() {
        Map<String, FileConfiguration> menus = menuLoader.getAllMenus();
        for (Map.Entry<String, FileConfiguration> entry : menus.entrySet()) {
            String fileName = entry.getKey();
            FileConfiguration config = entry.getValue();
            getLogger().info("Menu File: " + fileName);
            getLogger().info("Menu Content: " + config.saveToString());
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public MenuLoader getMenuLoader() {
        return menuLoader;
    }

    public NamespacedKey getArmorStandKey() {
        return armorStandKey;
    }
}
