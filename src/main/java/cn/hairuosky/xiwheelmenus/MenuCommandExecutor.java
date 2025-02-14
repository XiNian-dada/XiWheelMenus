package cn.hairuosky.xiwheelmenus;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MenuCommandExecutor implements CommandExecutor {

    private final MenuManager menuManager;

    public MenuCommandExecutor(XiWheelMenus plugin) {
        this.menuManager = new MenuManager(plugin);
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
                menuManager.openMenu(player, menuName);
                break;
            default:
                player.sendMessage(ChatColor.RED + "Unknown subcommand. Usage: /xiwheelmenus <subcommand>");
                break;
        }

        return true;
    }
}
