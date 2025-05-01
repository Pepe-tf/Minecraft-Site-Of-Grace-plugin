package me.billhubs.site_Of_Grace;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("ALL")
public final class Site_Of_Grace extends JavaPlugin implements Listener {
    private String graceMenuTitle;
    private String fastTravelMenuTitle;
    private FileConfiguration config;
    private File graceFile;
    private FileConfiguration graceConfig;
    private String graceItemName;
    private final Map<String, Long> graceMenuCooldown = new HashMap<>();
    private static final long GRACE_MENU_COOLDOWN = 1000; // 1 second
    private final NamespacedKey graceLocationKey;
    private static final String PLAYER_PLACEHOLDER = "%player%";
    private final Map<UUID, String> lastGraceLocation = new HashMap<>();
    private int nextGraceId = 1;
    private File userFile;
    private FileConfiguration userConfig;

    public Site_Of_Grace() {
        this.graceLocationKey = new NamespacedKey(this, "grace_location");
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.config = getConfig();
        this.graceItemName = config.getString("grace-item-name", "§6Site of Grace");
        this.graceMenuTitle = config.getString("grace-menu.title", "§6Site of Grace");
        this.fastTravelMenuTitle = config.getString("fast-travel-menu.title", "§bFast Travel Menu");
        setupGraceFile();
        setupUserFile();
        loadNextGraceId();
        cleanupInvalidGraces(); // Add this line
        startValidationTask(); // Add this line
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("Site Of Grace plugin enabled.");
    }

    @Override
    public void onDisable() {
        saveGraceFile();
        saveUserFile(); // Add this line
        getLogger().info("Site Of Grace plugin disabled.");
    }

    private void setupGraceFile() {
        graceFile = new File(getDataFolder(), "grace.yml");
        if (!graceFile.exists()) {
            try {
                graceFile.getParentFile().mkdirs();
                graceFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Failed to create grace.yml: " + e.getMessage());
            }
        }
        graceConfig = YamlConfiguration.loadConfiguration(graceFile);
    }

    private void saveGraceFile() {
        try {
            graceConfig.save(graceFile);
        } catch (IOException e) {
            getLogger().severe("Failed to save grace.yml: " + e.getMessage());
        }
    }

    private void setupUserFile() {
        userFile = new File(getDataFolder(), "users.yml");
        if (!userFile.exists()) {
            try {
                userFile.getParentFile().mkdirs();
                userFile.createNewFile();

                // Initialize with default structure
                userConfig = YamlConfiguration.loadConfiguration(userFile);
                userConfig.createSection("users");
                saveUserFile();

            } catch (IOException e) {
                getLogger().severe("Failed to create users.yml: " + e.getMessage());
            }
        }
        userConfig = YamlConfiguration.loadConfiguration(userFile);
    }

    private void saveUserFile() {
        try {
            // Make a copy of the configuration before saving to prevent null key errors
            FileConfiguration tempConfig = new YamlConfiguration();
            
            // Copy all valid sections
            ConfigurationSection usersSection = userConfig.getConfigurationSection("users");
            if (usersSection != null) {
                ConfigurationSection newUsersSection = tempConfig.createSection("users");
                
                for (String userKey : usersSection.getKeys(false)) {
                    if (userKey == null || userKey.isEmpty()) continue;
                    
                    ConfigurationSection userSection = usersSection.getConfigurationSection(userKey);
                    if (userSection == null) continue;
                    
                    ConfigurationSection newUserSection = newUsersSection.createSection(userKey);
                    
                    // Copy last_grace if present and not null
                    String lastGrace = userSection.getString("last_grace");
                    if (lastGrace != null && !lastGrace.isEmpty()) {
                        newUserSection.set("last_grace", lastGrace);
                    }
                    
                    // Copy activated_graces if present and not null
                    List<String> activatedGraces = userSection.getStringList("activated_graces");
                    if (activatedGraces != null && !activatedGraces.isEmpty()) {
                        // Filter out any null or empty entries
                        activatedGraces = activatedGraces.stream()
                            .filter(g -> g != null && !g.isEmpty())
                            .collect(Collectors.toList());
                        
                        if (!activatedGraces.isEmpty()) {
                            newUserSection.set("activated_graces", activatedGraces);
                        }
                    }
                }
            }
            
            // Save the cleaned configuration
            tempConfig.save(userFile);
            
            // Reload the config from file to ensure consistency
            userConfig = YamlConfiguration.loadConfiguration(userFile);
        } catch (IOException e) {
            getLogger().severe("Failed to save users.yml: " + e.getMessage());
        }
    }

    private void saveUserData(UUID playerId) {
        if (playerId == null) return;
        
        ConfigurationSection userSection = getUserSection(playerId);
        if (userSection == null) return;
    
        // Save last grace location
        String lastGrace = lastGraceLocation.get(playerId);
        if (lastGrace != null && !lastGrace.isEmpty()) {
            userSection.set("last_grace", lastGrace);
        } else {
            // Instead of setting null, which can cause YAML issues, set to empty
            userSection.set("last_grace", "");
        }

        saveUserFileAsync();
    }

    private ConfigurationSection getUserSection(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        
        ConfigurationSection usersSection = userConfig.getConfigurationSection("users");
        if (usersSection == null) {
            usersSection = userConfig.createSection("users");
        }
    
        String userKey = playerId.toString();
        if (userKey == null || userKey.isEmpty()) {
            return null;
        }
        
        ConfigurationSection userSection = usersSection.getConfigurationSection(userKey);
        if (userSection == null) {
            userSection = usersSection.createSection(userKey);
            
            // Initialize with default values to prevent null entries
            userSection.set("activated_graces", new ArrayList<String>());
            userSection.set("last_grace", "");
        }

        return userSection;
    }

    private void loadUserData(UUID playerId) {
        ConfigurationSection userSection = getUserSection(playerId);

        // Load last grace location
        String lastGrace = userSection.getString("last_grace");
        if (lastGrace != null) {
            lastGraceLocation.put(playerId, lastGrace);
        }

        // Load activated graces
        List<String> activatedGraces = userSection.getStringList("activated_graces");
        setActivatedGraces(playerId, activatedGraces);
    }

    private List<String> getActivatedGraces(UUID playerId) {
        ConfigurationSection userSection = getUserSection(playerId);
        if (userSection == null) {
            return new ArrayList<>();
        }
        
        List<String> graces = userSection.getStringList("activated_graces");
        if (graces == null) {
            return new ArrayList<>();
        }
        
        // Filter out any null values that might have gotten in somehow
        return graces.stream()
            .filter(g -> g != null && !g.isEmpty())
            .collect(Collectors.toList());
    }

    private void setActivatedGraces(UUID playerId, List<String> graces) {
        ConfigurationSection userSection = getUserSection(playerId);
        userSection.set("activated_graces", graces);
        saveUserFileAsync();
    }

    private void addActivatedGrace(UUID playerId, String graceKey) {
        if (playerId == null || graceKey == null || graceKey.isEmpty()) {
            return;
        }
        
        List<String> activated = getActivatedGraces(playerId);
        if (activated == null) {
            activated = new ArrayList<>();
        }
        
        if (!activated.contains(graceKey)) {
            activated.add(graceKey);
            setActivatedGraces(playerId, activated);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("grace")) return false;

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            showHelpMessage(sender);
        } else if (args[0].equalsIgnoreCase("get")) {
            giveGraceItem(sender);
        } else if (args[0].equalsIgnoreCase("reload")) {
            reloadPlugin(sender);
        } else if (args[0].equalsIgnoreCase("rename") && args.length >= 3) {
            renameGrace(sender, args[1], String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
        } else if (args[0].equalsIgnoreCase("list")) {
            listGraces(sender);
        }
        return true;
    }

    private void renameGrace(CommandSender sender, String idStr, String newName) {
        if (!sender.hasPermission("grace.admin")) {
            return;
        }
    
        try {
            int id = Integer.parseInt(idStr);
            ConfigurationSection gracesSection = graceConfig.getConfigurationSection("graces");
            if (gracesSection == null) {
                return;
            }
    
            for (String key : gracesSection.getKeys(false)) {
                ConfigurationSection graceSection = gracesSection.getConfigurationSection(key);
                if (graceSection != null && graceSection.getInt("id") == id) {
                    graceSection.set("name", newName);
                    saveGraceFile();
                    break;
                }
            }
        } catch (NumberFormatException e) {
            // Invalid ID format, do nothing
        }
    }

    private void showHelpMessage(CommandSender sender) {
        // Empty implementation - no messages displayed
    }

    private void reloadPlugin(CommandSender sender) {
        if (!sender.hasPermission("grace.admin")) {
            return;
        }
    
        reloadConfig();
        this.config = getConfig();
        setupGraceFile();
        setupUserFile();
        loadNextGraceId();
        cleanupInvalidGraces();
    
        // Validate all online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            validateUserGraces(player.getUniqueId());
        }
    }

    private void giveGraceItem(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return;
        }
    
        ItemStack graceItem = createGraceItem();
        player.getInventory().addItem(graceItem);
    }

    private ItemStack createGraceItem() {
        ItemStack item = new ItemStack(Material.CAMPFIRE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(graceItemName);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!isGraceItem(item)) return;

        Location loc = event.getBlock().getLocation();
        registerGraceLocation(loc);
    }

    private boolean isGraceItem(ItemStack item) {
        if (!item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && graceItemName.equals(meta.getDisplayName());
    }

    private void registerGraceLocation(Location loc) {
        String key = getLocationKey(loc);
        ConfigurationSection section = graceConfig.createSection("graces." + key);
        int graceId = getNextGraceId();

        section.set("id", graceId);
        section.set("world", loc.getWorld().getName());
        section.set("x", loc.getBlockX());
        section.set("y", loc.getBlockY());
        section.set("z", loc.getBlockZ());
        section.set("activated_players", new ArrayList<String>());
        section.set("name", "Site of Grace #" + graceId); // Default name based on ID
        saveGraceFile();
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.CAMPFIRE) return;

        Location loc = block.getLocation();
        String key = getLocationKey(loc);

        ConfigurationSection gracesSection = graceConfig.getConfigurationSection("graces");
        if (gracesSection != null && gracesSection.contains(key)) {
            Player player = event.getPlayer();
            if (!player.hasPermission("grace.remove")) {
                event.setCancelled(true);
                return;
            }
    
            // Remove the grace
            removeGrace(loc);
        }
    }

    private boolean isValidGraceInteraction(PlayerInteractEvent event) {
        return event.getClickedBlock() != null && event.getClickedBlock().getType() == Material.CAMPFIRE && event.getAction().toString().startsWith("RIGHT_CLICK");
    }

    private void handleGraceInteraction(Player player, Location loc, String graceKey) {
        UUID playerId = player.getUniqueId();
        List<String> activated = getActivatedGraces(playerId);

        if (!activated.contains(graceKey)) {
            activateGrace(player, loc, graceKey);
            return;
        }

        if (isOnCooldown(player, graceKey)) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 2.0f);
            return;
        }
    
        // Update last grace location
        lastGraceLocation.put(playerId, graceKey);
        saveUserData(playerId);

        updateCooldown(player, graceKey);
        openGraceMenu(player, loc);
    }

    private void activateGrace(Player player, Location loc, String graceKey) {
        UUID playerId = player.getUniqueId();

        // Add to activated graces
        addActivatedGrace(playerId, graceKey);

        // Set as the last grace
        lastGraceLocation.put(playerId, graceKey);
        saveUserData(playerId);

        playActivationEffects(player, loc);
        updateCooldown(player, graceKey);
    }

    private void playActivationEffects(Player player, Location loc) {
        Location effectLoc = loc.clone().add(0.5, 0.1, 0.5);
        Location playerEffectLoc = player.getLocation().clone().add(0, 0.1, 0);
        World world = loc.getWorld();

        // Elden Ring activation sequence - initial soft glow
        world.playSound(effectLoc, Sound.BLOCK_BEACON_AMBIENT, 0.4f, 1.5f);
        world.playSound(effectLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 0.8f);

        // Initial ground effect - subtle golden dust
        for (int i = 0; i < 5; i++) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                world.spawnParticle(Particle.END_ROD, effectLoc, 3, 0.5, 0.05, 0.5, 0.01, null);
            }, i * 3L);
        }

        // The first phase - grace begins to form (5 ticks = 0.25 s after start)
        Bukkit.getScheduler().runTaskLater(this, () -> {
            // Main sound - the distinctive Elden Ring "grace found" sound
            world.playSound(effectLoc, Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.2f);
            world.playSound(effectLoc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4f, 0.6f);

            // Begin the radial golden light rays - Elden Ring's signature converging light
            for (int i = 0; i < 10; i++) {
                final int step = i;
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    radialGoldEffect(world, effectLoc, 1.8 - (step * 0.15), step * 0.05);
                }, i * 2L);
            }

            // Second phase - grace intensifies (20 ticks = 1 s after the first phase)
            Bukkit.getScheduler().runTaskLater(this, () -> {
                // The distinctive chime when a site of grace fully forms
                world.playSound(effectLoc, Sound.BLOCK_BELL_USE, 0.8f, 0.8f);
                world.playSound(effectLoc, Sound.BLOCK_CONDUIT_ACTIVATE, 0.6f, 1.2f);

                // Main golden pillar - representing the vertical line of the site of grace
                for (double y = 0; y < 2.5; y += 0.2) {
                    Location pillarLoc = effectLoc.clone().add(0, y, 0);
                    world.spawnParticle(Particle.END_ROD, pillarLoc, 1, 0.05, 0, 0.05, 0.001, null);
                }

                // Light particles at player's position - the blessing effect
                world.spawnParticle(Particle.SMALL_GUST, playerEffectLoc, 30, 0.4, 0.4, 0.4, 0.02, null);
                world.spawnParticle(Particle.END_ROD, playerEffectLoc, 15, 0.3, 0.5, 0.3, 0.02, null);

                // Add floating ember particles - iconic for Elden Ring's grace
                for (int i = 0; i < 6; i++) {
                    final int multiplier = i;
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        spawnEmberParticles(world, effectLoc, multiplier * 0.3);
                    }, i * 5L);
                }

                // Final phase - grace stabilizes (15 ticks = 0.75 s after the second phase)
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    // Final sounds
                    world.playSound(effectLoc, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 0.8f);
                    world.playSound(effectLoc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 1.0f);

                    // Create the final stable grace light
                    world.spawnParticle(Particle.WITCH, effectLoc.clone().add(0, 0.5, 0), 40, 0.4, 0.8, 0.4, 0.01, null);
                    world.spawnParticle(Particle.END_ROD, effectLoc.clone().add(0, 1.2, 0), 25, 0.1, 0.8, 0.1, 0.01, null);

                    // Player's final blessing effect - the distinctive Elden Ring recovery animation
                    world.spawnParticle(Particle.SMALL_GUST, player.getLocation().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.03, null);
                    world.spawnParticle(Particle.END_ROD, player.getLocation().add(0, 0.5, 0), 15, 0.3, 0.2, 0.3, 0.02, null);
                }, 15L);
            }, 20L);
        }, 5L);
    }

    // Creates the signature Elden Ring converging light rays effect
    private void radialGoldEffect(World world, Location center, double radius, double height) {
        int rays = 16; // Number of light rays converging toward the center
        for (int i = 0; i < rays; i++) {
            double angle = (2 * Math.PI * i) / rays;
            double x = radius * Math.cos(angle);
            double z = radius * Math.sin(angle);

            // Position the ray points - farther out and converging toward the center
            Location rayLoc = center.clone().add(x, height, z);

            // Use END_ROD for the golden light rays effect
            world.spawnParticle(Particle.END_ROD, rayLoc, 1, 0, 0, 0, 0);

            // Add the small flame particle at the base for the amber/golden effect
            if (i % 4 == 0 && height < 0.2) {
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, center.clone().add(x * 0.3, 0.05, z * 0.3), 1, 0.05, 0.05, 0.05, 0.001, null);
            }
        }
    }

    // Creates the floating ember particles distinctive to Elden Ring's grace points
    private void spawnEmberParticles(World world, Location center, double heightOffset) {
        // The slowly rising ember particles
        for (int i = 0; i < 5; i++) {
            double offset = Math.random() * 0.5;
            double x = (Math.random() - 0.5) * 0.8;
            double z = (Math.random() - 0.5) * 0.8;
            Location emberLoc = center.clone().add(x, heightOffset + offset, z);

            // Soul fire flame for golden embers, with extremely slow upward drift
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, emberLoc, 1, 0.05, 0.01, 0.05, 0.001, null);

            // Some end rod particles for the additional golden glow
            if (i % 2 == 0) {
                world.spawnParticle(Particle.END_ROD, emberLoc, 1, 0.02, 0.02, 0.02, 0.001, null);
            }
        }
    }

    private boolean isOnCooldown(Player player, String graceKey) {
        long lastAccess = graceMenuCooldown.getOrDefault(getCooldownKey(player.getUniqueId(), graceKey), 0L);
        return System.currentTimeMillis() - lastAccess < GRACE_MENU_COOLDOWN;
    }

    private void updateCooldown(Player player, String graceKey) {
        graceMenuCooldown.put(getCooldownKey(player.getUniqueId(), graceKey), System.currentTimeMillis());
    }

    private void openGraceMenu(Player player, Location graceLocation) {
        ConfigurationSection menuConfig = config.getConfigurationSection("grace-menu");
        if (menuConfig == null) return;

        int size = menuConfig.getInt("size", 27);
        Inventory menu = Bukkit.createInventory(null, size, graceMenuTitle);

        ConfigurationSection items = menuConfig.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection itemSection = items.getConfigurationSection(key);
                if (itemSection == null) continue;

                String materialName = itemSection.getString("material", "STONE");
                String name = itemSection.getString("name", "");
                int slot = itemSection.getInt("slot", 0);
                List<String> lore = itemSection.getStringList("lore");

                ItemStack item = createMenuItem(Material.valueOf(materialName), name, lore);
                menu.setItem(slot, item);
            }
        }

        player.openInventory(menu);
    }

    private ItemStack createMenuItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getCurrentItem() == null) return;

        String title = event.getView().getTitle();
        event.setCancelled(true);

        if (title.equals(graceMenuTitle)) {
            handleGraceMenuClick(event, player);
        } else if (title.equals(fastTravelMenuTitle)) {
            handleFastTravelMenuClick(event, player);
        }
    }

    private void handleGraceMenuClick(InventoryClickEvent event, Player player) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null) return;

        ConfigurationSection itemsSection = config.getConfigurationSection("grace-menu.items");
        if (itemsSection == null) return;

        // Find which item was clicked based on material and name
        for (String key : itemsSection.getKeys(false)) {
            ConfigurationSection itemSection = itemsSection.getConfigurationSection(key);
            if (itemSection == null) continue;

            Material material = Material.valueOf(itemSection.getString("material", "STONE"));
            String name = itemSection.getString("name", "");

            if (clickedItem.getType() == material && clickedItem.getItemMeta() != null && name.equals(clickedItem.getItemMeta().getDisplayName())) {

                // Handle a fast travel menu separately
                if (key.equals("fast-travel")) {
                    openFastTravelMenu(player);
                    player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 1.0f);
                }

                // Execute commands if configured
                executeItemCommands(player, itemSection);

                // Close the menu if configured
                if (itemSection.getBoolean("commands.close_menu", false)) {
                    player.closeInventory();
                }

                break;
            }
        }
    }

    private void executeItemCommands(Player player, ConfigurationSection itemSection) {
        ConfigurationSection commandSection = itemSection.getConfigurationSection("commands");
        if (commandSection == null) return;

        List<String> commands = commandSection.getStringList("list");
        boolean asConsole = commandSection.getBoolean("as_console", false);

        if (commands.isEmpty()) return;

        for (String command : commands) {
            // Replace placeholders
            command = command.replace(PLAYER_PLACEHOLDER, player.getName());

            // Execute command
            if (asConsole) {
                ConsoleCommandSender console = Bukkit.getConsoleSender();
                Bukkit.dispatchCommand(console, command);
            } else {
                player.performCommand(command);
            }
        }

        // Check if the menu should be closed
        if (commandSection.getBoolean("close_menu", false)) {
            player.closeInventory();
        }
    }

    private void openFastTravelMenu(Player player) {
        validateUserGraces(player.getUniqueId()); // Add this line
        ConfigurationSection gracesSection = graceConfig.getConfigurationSection("graces");
        if (gracesSection == null) {
            return;
        }

        int graceCount = gracesSection.getKeys(false).size();
        int invSize = Math.min(54, ((graceCount + 8) / 9) * 9);
        invSize = Math.max(9, invSize);

        Inventory menu = Bukkit.createInventory(null, invSize, fastTravelMenuTitle);
        populateFastTravelMenu(menu, gracesSection, player);

        player.openInventory(menu);
    }

    private void populateFastTravelMenu(Inventory menu, ConfigurationSection gracesSection, Player player) {
        int slot = 0;
        UUID playerId = player.getUniqueId();
        List<String> activatedGraces = getActivatedGraces(playerId);

        for (String graceKey : gracesSection.getKeys(false)) {
            if (!activatedGraces.contains(graceKey)) continue;

            ConfigurationSection graceSection = gracesSection.getConfigurationSection(graceKey);
            if (graceSection == null) continue;

            ItemStack graceItem = createGraceTravelItem(graceSection);
            if (graceItem != null) {
                menu.setItem(slot++, graceItem);
            }
        }
    }

    private ItemStack createGraceTravelItem(ConfigurationSection graceSection) {
        String worldName = graceSection.getString("world");
        if (worldName == null) return null;

        int x = graceSection.getInt("x");
        int y = graceSection.getInt("y");
        int z = graceSection.getInt("z");
        int id = graceSection.getInt("id", 0);
        String graceName = graceSection.getString("name", "Site of Grace #" + id);

        ConfigurationSection itemConfig = config.getConfigurationSection("fast-travel-menu.grace-item");
        if (itemConfig == null) return null;

        Material material = Material.valueOf(itemConfig.getString("material", "CAMPFIRE"));
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6" + graceName + " §7(#" + id + ")");

            List<String> lore = new ArrayList<>();
            for (String loreLine : itemConfig.getStringList("lore")) {
                loreLine = loreLine.replace("%world%", worldName).replace("%x%", String.valueOf(x)).replace("%y%", String.valueOf(y)).replace("%z%", String.valueOf(z)).replace("%id%", String.valueOf(id)).replace("%name%", graceName);
                lore.add(loreLine);
            }
            meta.setLore(lore);

            meta.getPersistentDataContainer().set(graceLocationKey, PersistentDataType.STRING, String.format("%s;%d;%d;%d", worldName, x, y, z));

            item.setItemMeta(meta);
            return item;
        }
        return null;
    }

    private void handleFastTravelMenuClick(InventoryClickEvent event, Player player) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() != Material.CAMPFIRE) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        String locString = meta.getPersistentDataContainer().get(graceLocationKey, PersistentDataType.STRING);
        if (locString == null) return;

        teleportToGrace(player, locString);
    }

    private void teleportToGrace(Player player, String locString) {
        String[] locData = locString.split(";");
        if (locData.length != 4) return;

        World world = Bukkit.getWorld(locData[0]);
        if (world == null) return;

        // Get original grace center location for effects
        int graceX = Integer.parseInt(locData[1]);
        int graceY = Integer.parseInt(locData[2]);
        int graceZ = Integer.parseInt(locData[3]);
        Location graceLocation = new Location(world, graceX + 0.5, graceY + 0.5, graceZ + 0.5);
        
        // Find adjacent location for the player to spawn
        Location destination = findAdjacentSpawnLocation(world, graceX, graceY, graceZ);
    
        playTeleportEffects(player, player.getLocation());
        player.teleport(destination);
        player.closeInventory();
        playTeleportEffects(player, graceLocation);
    }

    private void playTeleportEffects(Player player, Location location) {
        World world = location.getWorld();
        Location effectLoc = location.clone().add(0, 0.1, 0);

        // Initial dematerialization sound (Elden Ring fast travel starts with a distinct chime)
        world.playSound(effectLoc, Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 1.2f);
        world.playSound(effectLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 0.9f);

        // Initial particles - the golden light begins to envelop the player
        world.spawnParticle(Particle.END_ROD, effectLoc.clone().add(0, 1, 0), 25, 0.3, 0.7, 0.3, 0.02, null);

        // Sequence the teleport animation
        Bukkit.getScheduler().runTaskLater(this, () -> {
            // Main teleport sound - the distinctive whoosh
            world.playSound(effectLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 0.8f);
            world.playSound(effectLoc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 1.0f);

            // Main disintegration effect - creates the dissolving effect
            for (int i = 0; i < 4; i++) {
                final int y = i;
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    // Create horizontal rings of particles at different heights
                    for (int j = 0; j < 8; j++) {
                        double angle = (2 * Math.PI * j) / 8;
                        double radius = 0.4;
                        double x = radius * Math.cos(angle);
                        double z = radius * Math.sin(angle);

                        Location particleLoc = effectLoc.clone().add(x, 0.3 + (y * 0.4), z);
                        world.spawnParticle(Particle.WITCH, particleLoc, 1, 0.05, 0.05, 0.05, 0, null);

                        // Add some golden embers
                        if (j % 3 == 0) {
                            world.spawnParticle(Particle.SOUL_FIRE_FLAME, particleLoc, 1, 0.05, 0.05, 0.05, 0.001, null);
                        }
                    }
                }, i * 2L);
            }

            // Add vertical light column - Elden Ring's signature vertical grace lines
            for (double y = 0; y < 2.5; y += 0.25) {
                Location pillarLoc = effectLoc.clone().add(0, y, 0);
                world.spawnParticle(Particle.END_ROD, pillarLoc, 1, 0.05, 0, 0.05, 0.001, null);
            }

            // Final vanishing particles
            Bukkit.getScheduler().runTaskLater(this, () -> {
                world.spawnParticle(Particle.FLASH, effectLoc.clone().add(0, 1, 0), 2, 0.1, 0.1, 0.1, 0, null);
                world.spawnParticle(Particle.END_ROD, effectLoc.clone().add(0, 1, 0), 30, 0.3, 0.7, 0.3, 0.05, null);
            }, 5L);
        }, 3L);
    }

    private String getLocationKey(Location loc) {
        return String.format("%s,%d,%d,%d", loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    private String getCooldownKey(UUID player, String graceLocKey) {
        return player.toString() + ":" + graceLocKey;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        String lastGrace = lastGraceLocation.get(player.getUniqueId());

        // If the player has no last grace or is in a world without graces, use default spawn
        if (lastGrace == null) return;

        // Get the grace location for effects
        ConfigurationSection graceSection = graceConfig.getConfigurationSection("graces." + lastGrace);
        if (graceSection == null) return;
        
        String worldName = graceSection.getString("world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;
        
        int graceX = graceSection.getInt("x");
        int graceY = graceSection.getInt("y");
        int graceZ = graceSection.getInt("z");
        
        // Original grace location for effects
        Location graceLocation = new Location(world, graceX + 0.5, graceY + 0.5, graceZ + 0.5);
        
        // Adjacent spot for player to spawn
        Location respawnLoc = findAdjacentSpawnLocation(world, graceX, graceY, graceZ);
        if (respawnLoc == null) return;
    
        // Only override if the death world has Sites of Grace
        if (hasGracesInWorld(player.getWorld().getName())) {
            event.setRespawnLocation(respawnLoc);
    
            // Schedule effects for the next tick since respawn location is not immediate
            Bukkit.getScheduler().runTask(this, () -> {
                playRespawnEffects(player, graceLocation);
            });
        }
    }

    private Location getGraceLocation(String graceKey) {
        ConfigurationSection graceSection = graceConfig.getConfigurationSection("graces." + graceKey);
        if (graceSection == null) return null;

        String worldName = graceSection.getString("world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        int x = graceSection.getInt("x");
        int y = graceSection.getInt("y");
        int z = graceSection.getInt("z");
        
        // Find an adjacent block to spawn on
        return findAdjacentSpawnLocation(world, x, y, z);
    }
    
    private Location findAdjacentSpawnLocation(World world, int x, int y, int z) {
        if (world == null) {
            return null;
        }
        
        // Possible adjacent block positions (cardinal directions first, then diagonals as fallbacks)
        int[][] adjacentOffsets = new int[][] {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}, 
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
        };
        
        try {
            // Try to find a safe block in one of the cardinal directions first
            for (int[] offset : adjacentOffsets) {
                int newX = x + offset[0];
                int newZ = z + offset[1];
                
                // Check if this location is safe (solid block below, two air blocks above)
                Block block = world.getBlockAt(newX, y, newZ);
                Block blockBelow = world.getBlockAt(newX, y - 1, newZ);
                Block blockAbove = world.getBlockAt(newX, y + 1, newZ);
                Block blockAbove2 = world.getBlockAt(newX, y + 2, newZ);
                
                if (block.getType() == Material.AIR && 
                    blockAbove.getType() == Material.AIR &&
                    blockAbove2.getType() == Material.AIR &&
                    blockBelow.getType().isSolid()) {
                    // Found a safe spot, return it with proper centering
                    return new Location(world, newX + 0.5, y + 0.5, newZ + 0.5, 0, 0); // Add yaw and pitch as 0
                }
            }
        } catch (Exception e) {
            getLogger().warning("Error finding adjacent spawn location: " + e.getMessage());
        }
        
        // If no safe adjacent block was found, return the original grace location as fallback
        return new Location(world, x + 0.5, y + 0.5, z + 0.5, 0, 0); // Add yaw and pitch as 0
    }

    private boolean hasGracesInWorld(String worldName) {
        ConfigurationSection gracesSection = graceConfig.getConfigurationSection("graces");
        if (gracesSection == null) return false;

        for (String key : gracesSection.getKeys(false)) {
            ConfigurationSection graceSection = gracesSection.getConfigurationSection(key);
            if (graceSection != null && worldName.equals(graceSection.getString("world"))) {
                return true;
            }
        }
        return false;
    }

    private void playRespawnEffects(Player player, Location location) {
        World world = location.getWorld();
        Location effectLoc = location.clone().add(0.5, 0.1, 0.5);
        Location playerLoc = player.getLocation();

        // Initial revival sound - more subtle and atmospheric like Elden Ring
        world.playSound(effectLoc, Sound.BLOCK_BEACON_AMBIENT, 0.6f, 1.3f);
        world.playSound(effectLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 0.9f);

        // Initial grace manifestation
        Bukkit.getScheduler().runTaskLater(this, () -> {
            // The Elden Ring revival chime
            world.playSound(effectLoc, Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.1f);
            world.playSound(effectLoc, Sound.BLOCK_CONDUIT_ACTIVATE, 0.5f, 1.2f);

            // Grace pillar - the main light shaft
            for (double y = 0; y < 2.0; y += 0.2) {
                Location pillarLoc = effectLoc.clone().add(0, y, 0);
                world.spawnParticle(Particle.END_ROD, pillarLoc, 1, 0.05, 0, 0.05, 0.001, null);
            }

            // Ground effect with golden particles
            for (int i = 0; i < 3; i++) {
                final int step = i;
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    radialGoldEffect(world, effectLoc, 1.2 - (step * 0.3), step * 0.05);
                }, i * 2L);
            }

            // Player reconstitution effect
            Bukkit.getScheduler().runTaskLater(this, () -> {
                // Elden Ring resurrection sound
                world.playSound(playerLoc, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 0.7f);
                world.playSound(playerLoc, Sound.BLOCK_BEACON_POWER_SELECT, 0.4f, 1.2f);

                // The golden particles that swirl around the player as they revive
                world.spawnParticle(Particle.END_ROD, playerLoc.clone().add(0, 1, 0), 20, 0.3, 0.7, 0.3, 0.02, null);
                world.spawnParticle(Particle.SMALL_GUST, playerLoc.clone().add(0, 0.5, 0), 30, 0.3, 0.3, 0.3, 0.02, null);

                // Add some ember particles around the player
                for (int i = 0; i < 3; i++) {
                    final int mul = i;
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        for (int j = 0; j < 4; j++) {
                            double angle = (2 * Math.PI * j) / 4;
                            double x = 0.7 * Math.cos(angle);
                            double z = 0.7 * Math.sin(angle);
                            Location emberLoc = playerLoc.clone().add(x, 0.1 + (mul * 0.3), z);
                            world.spawnParticle(Particle.SOUL_FIRE_FLAME, emberLoc, 1, 0.05, 0.05, 0.05, 0.001, null);
                        }
                    }, i * 4L);
                }
            }, 5L);
        }, 3L);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!isValidGraceInteraction(event)) return;

        Player player = event.getPlayer();
        Location loc = event.getClickedBlock().getLocation();
        String graceKey = getLocationKey(loc);

        ConfigurationSection graceSection = graceConfig.getConfigurationSection("graces." + graceKey);
        if (graceSection == null) return;

        handleGraceInteraction(player, loc, graceKey);
    }

    private void loadNextGraceId() {
        int highestId = 0;
        ConfigurationSection gracesSection = graceConfig.getConfigurationSection("graces");
        if (gracesSection != null) {
            for (String key : gracesSection.getKeys(false)) {
                ConfigurationSection graceSection = gracesSection.getConfigurationSection(key);
                if (graceSection != null) {
                    int id = graceSection.getInt("id", 0);
                    highestId = Math.max(highestId, id);
                }
            }
        }
        nextGraceId = highestId + 1;
    }

    private int getNextGraceId() {
        int id = nextGraceId;
        nextGraceId++;
        return id;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        loadUserData(playerId);
        validateUserGraces(playerId); // Add this line
    }

    private void cleanupInvalidGraces() {
        ConfigurationSection gracesSection = graceConfig.getConfigurationSection("graces");
        if (gracesSection == null) return;

        Set<String> validGraces = gracesSection.getKeys(false);

        // Get all users
        ConfigurationSection usersSection = userConfig.getConfigurationSection("users");
        if (usersSection == null) return;

        boolean anyChanges = false;

        for (String userUUID : usersSection.getKeys(false)) {
            ConfigurationSection userSection = usersSection.getConfigurationSection(userUUID);
            if (userSection == null) continue;

            boolean userChanged = false;

            // Cleanup activated graces
            List<String> activatedGraces = userSection.getStringList("activated_graces");
            int originalSize = activatedGraces.size();
            activatedGraces.removeIf(grace -> !validGraces.contains(grace));

            if (originalSize != activatedGraces.size()) {
                userSection.set("activated_graces", activatedGraces);
                userChanged = true;
            }

            // Clean up last grace if it's invalid
            String lastGrace = userSection.getString("last_grace");
            if (lastGrace != null && !validGraces.contains(lastGrace)) {
                userSection.set("last_grace", null);
                // If the player is online, update their memory state
                Player player = Bukkit.getPlayer(UUID.fromString(userUUID));
                if (player != null) {
                    lastGraceLocation.remove(player.getUniqueId());
                }
                userChanged = true;
            }

            if (userChanged) {
                anyChanges = true;
            }
        }

        if (anyChanges) {
            saveUserFileAsync();
        }
    }

    private void removeGrace(Location location) {
        String key = getLocationKey(location);
        ConfigurationSection gracesSection = graceConfig.getConfigurationSection("graces");
        if (gracesSection != null && gracesSection.contains(key)) {
            gracesSection.set(key, null);
            saveGraceFile();

            // Cleanup user data
            cleanupInvalidGraces();
        }
    }

    private boolean isValidGrace(String graceKey) {
        ConfigurationSection gracesSection = graceConfig.getConfigurationSection("graces");
        return gracesSection != null && gracesSection.contains(graceKey);
    }

    private void validateUserGraces(UUID playerId) {
        if (playerId == null) return;
        
        ConfigurationSection userSection = getUserSection(playerId);
        if (userSection == null) return;
        
        try {
            List<String> activatedGraces = userSection.getStringList("activated_graces");
            String lastGrace = userSection.getString("last_grace");
            boolean changed = false;
    
            // Remove invalid activated graces and null values
            List<String> validActivated = activatedGraces.stream()
                .filter(grace -> grace != null && !grace.isEmpty() && isValidGrace(grace))
                .collect(Collectors.toList());
    
            if (validActivated.size() != activatedGraces.size()) {
                userSection.set("activated_graces", validActivated);
                changed = true;
            }
    
            // Check last grace validity
            if (lastGrace != null && !lastGrace.isEmpty() && !isValidGrace(lastGrace)) {
                // Set to empty string instead of null to avoid YAML issues
                userSection.set("last_grace", "");
                lastGraceLocation.remove(playerId);
                changed = true;
            }
    
            if (changed) {
                saveUserFileAsync();
            }
        } catch (Exception e) {
            getLogger().warning("Error validating user graces: " + e.getMessage());
        }
    }

    private void listGraces(CommandSender sender) {
        if (!sender.hasPermission("grace.admin")) {
            return;
        }
        
        // No messages displayed, empty implementation
    }

    private void startValidationTask() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            cleanupInvalidGraces();

            // Validate for online players
            for (Player player : Bukkit.getOnlinePlayers()) {
                validateUserGraces(player.getUniqueId());
            }

            // Clean cooldown map to prevent memory leaks
            long currentTime = System.currentTimeMillis();
            graceMenuCooldown.entrySet().removeIf(entry -> currentTime - entry.getValue() > GRACE_MENU_COOLDOWN * 10);

        }, 6000L, 6000L); // Run every 5 minutes (300 seconds × 20 ticks)
    }

    private void saveUserFileAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(this, this::saveUserFile);
    }
}