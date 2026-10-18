package me.pangrusak.excavation;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ExcavationPlugin extends JavaPlugin implements Listener {

    private static final String BOOK_NAME = ChatColor.AQUA + "Excavation I";
    private static final String PICKAXE_TAG = "excavation_pickaxe";
    private static final String BOOK_TAG = "excavation_book";
    private static final long COOLDOWN_MS = 350L;
    private static final double BOOK_PRICE = 500_000.0D;

    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Set<UUID> processing = new HashSet<>();

    private NamespacedKey pickaxeKey;
    private NamespacedKey bookKey;
    private Economy economy;

    @Override
    public void onEnable() {
        pickaxeKey = new NamespacedKey(this, PICKAXE_TAG);
        bookKey = new NamespacedKey(this, BOOK_TAG);

        if (!setupEconomy()) {
            getLogger().severe("Vault economy provider was not found. Disabling Excavation.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(this, this);

        if (getCommand("excavation") != null) {
            getCommand("excavation").setExecutor((sender, command, label, args) -> {
                if (args.length != 2 || !args[0].equalsIgnoreCase("givebook")) {
                    sender.sendMessage(ChatColor.RED + "Usage: /excavation givebook <player>");
                    return true;
                }

                Player target = getServer().getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "That player is not online.");
                    return true;
                }

                giveBook(target);
                sender.sendMessage(ChatColor.GREEN + "Gave an Excavation I book to " + target.getName() + ".");
                target.sendMessage(ChatColor.AQUA + "You received an Excavation I book.");
                return true;
            });
        }

        if (getCommand("buyexcavation") != null) {
            getCommand("buyexcavation").setExecutor((sender, command, label, args) -> {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Only players can buy Excavation books.");
                    return true;
                }

                Player player = (Player) sender;

                if (args.length != 0) {
                    player.sendMessage(ChatColor.RED + "Usage: /buyexcavation");
                    return true;
                }

                if (player.getInventory().firstEmpty() == -1) {
                    player.sendMessage(ChatColor.RED + "Your inventory is full. Make one empty slot first.");
                    return true;
                }

                if (!economy.has(player, BOOK_PRICE)) {
                    player.sendMessage(
                        ChatColor.RED + "You need " + ChatColor.GOLD + "$500,000"
                            + ChatColor.RED + " to buy an Excavation I book."
                    );
                    return true;
                }

                if (!economy.withdrawPlayer(player, BOOK_PRICE).transactionSuccess()) {
                    player.sendMessage(ChatColor.RED + "Your payment failed. No money was taken.");
                    return true;
                }

                giveBook(player);
                player.sendMessage(
                    ChatColor.GREEN + "Purchased " + ChatColor.AQUA + "Excavation I"
                        + ChatColor.GREEN + " for " + ChatColor.GOLD + "$500,000" + ChatColor.GREEN + "."
                );
                return true;
            });
        }

        getLogger().info("Excavation enabled. Book price: $500,000.");
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> provider =
            getServer().getServicesManager().getRegistration(Economy.class);

        if (provider == null) {
            return false;
        }

        economy = provider.getProvider();
        return economy != null;
    }

    private void giveBook(Player player) {
        player.getInventory().addItem(createBook()).forEach((slot, item) ->
            player.getWorld().dropItemNaturally(player.getLocation(), item)
        );
    }

    private ItemStack createBook() {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();

        meta.setDisplayName(BOOK_NAME);
        meta.setLore(Arrays.asList(
            ChatColor.GRAY + "Apply to a pickaxe in an anvil.",
            ChatColor.GRAY + "Sneak while mining to excavate",
            ChatColor.GRAY + "a 3x3x3 area of pickaxe blocks.",
            "",
            ChatColor.GOLD + "Value: $500,000"
        ));

        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.addEnchant(Enchantment.DURABILITY, 1, true);
        meta.getPersistentDataContainer().set(bookKey, PersistentDataType.BYTE, (byte) 1);

        book.setItemMeta(meta);
        return book;
    }

    private boolean isExcavationBook(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK || !item.hasItemMeta()) {
            return false;
        }

        return item.getItemMeta().getPersistentDataContainer()
            .has(bookKey, PersistentDataType.BYTE);
    }

    private boolean isExcavationPickaxe(ItemStack item) {
        if (!isPickaxe(item) || !item.hasItemMeta()) {
            return false;
        }

        return item.getItemMeta().getPersistentDataContainer()
            .has(pickaxeKey, PersistentDataType.BYTE);
    }

    private boolean isPickaxe(ItemStack item) {
        return item != null && item.getType().name().endsWith("_PICKAXE");
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inventory = event.getInventory();
        ItemStack left = inventory.getItem(0);
        ItemStack right = inventory.getItem(1);

        if (!isPickaxe(left) || !isExcavationBook(right)) {
            return;
        }

        ItemStack result = left.clone();
        ItemMeta meta = result.getItemMeta();
        meta.getPersistentDataContainer().set(pickaxeKey, PersistentDataType.BYTE, (byte) 1);

        List<String> lore = meta.hasLore()
            ? new ArrayList<>(meta.getLore())
            : new ArrayList<>();

        String line = ChatColor.AQUA + "Excavation I";
        if (!lore.contains(line)) {
            lore.add(line);
        }

        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        result.setItemMeta(meta);

        event.setResult(result);
        inventory.setRepairCost(15);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (processing.contains(player.getUniqueId())
            || player.getGameMode() == GameMode.CREATIVE
            || !player.isSneaking()
            || !isExcavationPickaxe(player.getInventory().getItemInMainHand())
            || !isPickaxeBlock(event.getBlock().getType())) {
            return;
        }

        long now = System.currentTimeMillis();
        long previous = cooldowns.getOrDefault(player.getUniqueId(), 0L);

        if (now - previous < COOLDOWN_MS) {
            return;
        }

        cooldowns.put(player.getUniqueId(), now);

        Block origin = event.getBlock();
        Vector face = player.getEyeLocation().getDirection();
        int axis;

        double x = Math.abs(face.getX());
        double y = Math.abs(face.getY());
        double z = Math.abs(face.getZ());

        if (y >= x && y >= z) {
            axis = 1;
        } else if (x >= z) {
            axis = 0;
        } else {
            axis = 2;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                processing.add(player.getUniqueId());

                try {
                    for (int a = -1; a <= 1; a++) {
                        for (int b = -1; b <= 1; b++) {
                            for (int c = -1; c <= 1; c++) {
                                if (a == 0 && b == 0 && c == 0) {
                                    continue;
                                }

                                Block target;

                                if (axis == 0) {
                                    target = origin.getRelative(c, a, b);
                                } else if (axis == 1) {
                                    target = origin.getRelative(a, c, b);
                                } else {
                                    target = origin.getRelative(a, b, c);
                                }

                                if (!isPickaxeBlock(target.getType()) || isBlocked(target.getType())) {
                                    continue;
                                }

                                BlockBreakEvent extraBreak = new BlockBreakEvent(target, player);
                                getServer().getPluginManager().callEvent(extraBreak);

                                if (extraBreak.isCancelled()) {
                                    continue;
                                }

                                ItemStack tool = player.getInventory().getItemInMainHand();

                                if (!isExcavationPickaxe(tool)
                                    || (tool.getType().getMaxDurability() > 0
                                    && tool.getDurability() >= tool.getType().getMaxDurability() - 1)) {
                                    return;
                                }

                                target.breakNaturally(tool);

                                PlayerItemDamageEvent damageEvent =
                                    new PlayerItemDamageEvent(player, tool, 1);

                                getServer().getPluginManager().callEvent(damageEvent);

                                if (!damageEvent.isCancelled()) {
                                    tool.setDurability((short) (
                                        tool.getDurability() + damageEvent.getDamage()
                                    ));
                                    player.getInventory().setItemInMainHand(tool);
                                }
                            }
                        }
                    }
                } finally {
                    processing.remove(player.getUniqueId());
                }
            }
        }.runTask(this);
    }

    private boolean isBlocked(Material material) {
        return material == Material.BEDROCK
            || material == Material.BARRIER
            || material == Material.END_PORTAL_FRAME
            || material == Material.SPAWNER
            || material.name().endsWith("_CHEST")
            || material == Material.SHULKER_BOX
            || material.name().endsWith("_SHULKER_BOX");
    }

    private boolean isPickaxeBlock(Material material) {
        if (material == Material.AIR
            || material == Material.CAVE_AIR
            || material == Material.VOID_AIR) {
            return false;
        }

        String name = material.name();

        return name.contains("STONE")
            || name.contains("ORE")
            || name.contains("DEEPSLATE")
            || name.contains("NETHERRACK")
            || name.contains("OBSIDIAN")
            || name.contains("ANVIL")
            || name.contains("BRICK")
            || name.contains("TERRACOTTA")
            || name.contains("CONCRETE")
            || name.contains("GLASS")
            || name.contains("IRON_BLOCK")
            || name.contains("GOLD_BLOCK")
            || name.contains("DIAMOND_BLOCK")
            || name.contains("EMERALD_BLOCK")
            || name.contains("LAPIS_BLOCK")
            || name.contains("REDSTONE_BLOCK")
            || name.contains("QUARTZ_BLOCK")
            || name.contains("NETHERITE_BLOCK")
            || name.contains("PRISMARINE")
            || name.contains("SANDSTONE")
            || name.contains("BASALT")
            || name.contains("BLACKSTONE")
            || name.contains("MAGMA")
            || name.contains("ICE");
    }
}
