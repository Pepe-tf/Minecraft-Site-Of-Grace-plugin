![Site of Grace Banner](assets/plugin.png)

[![Version](https://img.shields.io/github/v/release/billhub/Site_Of_Grace?style=flat-square)](https://github.com/billhub/Site_Of_Grace/releases)
[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21-green?style=flat-square)](https://www.minecraft.net)
[![Downloads](https://img.shields.io/github/downloads/billhub/Site_Of_Grace/total?style=flat-square)](https://github.com/billhub/Site_Of_Grace/releases)
[![License](https://img.shields.io/github/license/billhub/Site_Of_Grace?style=flat-square)](LICENSE)

## 🔥 Overview

Site of Grace is a Minecraft plugin that faithfully recreates the "Site of Grace" mechanic from FromSoftware's Elden Ring. Players can place Sites of Grace throughout the world, which serve as teleportation points and respawn locations, complete with stunning visual effects and animations inspired by the game.

## ✨ Features

- **Place Sites of Grace**: Admins can place Sites of Grace items in the world
- **Fast Travel System**: Teleport between discovered Sites of Grace
- **Last Grace Respawn**: Respawn at your last activated Site of Grace when you die
- **Beautiful Animations**: Custom particle effects and sounds that recreate the Elden Ring experience
- **Customizable**: Configure menu layouts, names, and more
- **Permissions-Based**: Fine-grained control over who can use what features
- **Custom Menus**: Interact with Sites of Grace to open custom menus with configurable actions
- **World-specific**: Sites of Grace work per world, allowing multi-world support

## 📥 Installation

1. Download the latest release from the [releases page](https://github.com/billhub/Site_Of_Grace/releases)
2. Place the JAR file in your server's `plugins` folder
3. Restart your server or use a plugin manager to load the plugin
4. The plugin will generate a default configuration file which you can customize

## 🛠️ Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/grace help` | Shows help information | `siteofgrace.use` |
| `/grace get` | Get a Site of Grace item | `siteofgrace.get` |
| `/grace reload` | Reload the plugin configuration | `siteofgrace.reload` |
| `/grace rename <id> <name>` | Rename a Site of Grace | `grace.admin` |
| `/grace list` | List all Site of Grace locations | `grace.admin` |

**Aliases**: `sog`, `gracesite`

## 🔐 Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `siteofgrace.*` | All permissions | OP |
| `siteofgrace.use` | Basic usage of the plugin | true |
| `siteofgrace.get` | Get Site of Grace items | OP |
| `siteofgrace.reload` | Reload configuration | OP |
| `siteofgrace.place` | Place Sites of Grace | OP |
| `siteofgrace.break` | Break Sites of Grace | OP |
| `siteofgrace.teleport` | Teleport to any Site of Grace | OP |
| `siteofgrace.list` | List all Sites of Grace | OP |
| `siteofgrace.remove` | Remove Sites of Grace | OP |
| `grace.admin` | Admin operations | OP |

## 🎮 Usage

### Basic Workflow

1. **Get a Site of Grace**: As an admin, use `/grace get` to receive a Site of Grace item
2. **Place the Site**: Place the campfire item in a desired location (requires `siteofgrace.place`)
3. **Activate the Site**: Right-click on the placed campfire to activate it
4. **Use Fast Travel**: Interact with any activated Site of Grace and select the fast travel option
5. **Respawn Point**: When you die, you'll respawn at your last activated Site of Grace

### Customizing Sites

- **Rename Sites**: Use `/grace rename <id> <name>` to give your Sites of Grace custom names
- **Find Site IDs**: Use `/grace list` to see all Sites of Grace with their IDs and locations

## ⚙️ Configuration

The plugin creates several configuration files:

- **config.yml**: Main configuration for menus, items, and general settings
- **grace.yml**: Data file storing all Sites of Grace locations
- **users.yml**: User data file tracking player's activated Sites of Grace

### Example Configuration

#### Main Configuration (config.yml)

```yaml
# Site Of Grace Plugin Configuration

# The display name for the Site of Grace campfire item
grace-item-name: "§6Site of Grace"

# Menu Configurations
grace-menu:
  title: "§6Site of Grace"
  size: 27  # Must be multiple of 9
  items:
    rest:
      material: GOLDEN_APPLE
      name: "§eRest at Grace"
      slot: 13
      lore: []
      commands:
        list:
          - "heal %player%"
        as_console: true
        close_menu: true
    fast-travel:
      material: ENDER_PEARL
      name: "§bFast Travel"
      slot: 11
      lore:
        - "§7Click to view available"
        - "§7Sites of Grace"
      commands:
        list: []
        as_console: false
        close_menu: false
    custom_buff:
      material: POTION
      name: "§dReceive Buffs"
      slot: 15
      lore:
        - "§7Click to receive"
        - "§7temporary buffs"
      commands:
        list:
          - "effect give %player% strength 300 1"
          - "effect give %player% speed 300 1"
        as_console: true
        close_menu: true

fast-travel-menu:
  title: "§bFast Travel Menu"
  grace-item:
    material: CAMPFIRE
    name: "§6Site of Grace"
    lore:
      - "§7World: %world%"
      - "§7Location: %x%, %y%, %z%"
      - ""
      - "§eClick to travel"

# Should debug messages be printed in the console?
debug: false
```

#### Grace Data (grace.yml)
```yaml
sites:
  spawn:
    world: world
    x: 0
    y: 64
    z: 0
    name: "Spawn Site of Grace"
  castle:
    world: world
    x: 100
    y: 70
    z: -150
    name: "Castle Gate"
```

#### User Data (users.yml)
```yaml
players:
  550e8400-e29b-41d4-a716-446655440000:  # Player UUID
    name: "Steve"
    last_grace: "spawn"  # ID of last activated Site of Grace
    discovered_sites:    # List of discovered Sites of Grace
      - "spawn"
      - "castle"
    settings:
      particles: true    # Player's particle effect preferences
      sounds: true      # Player's sound effect preferences
  
  f47ac10b-58cc-4372-a567-0e02b2c3d479:   # Another player
    name: "Alex"
    last_grace: "castle"
    discovered_sites:
      - "spawn"
      - "castle"
      - "dungeon"
    settings:
      particles: true
      sounds: false
```

## 🐛 Bug Reports & Feature Requests

If you encounter any issues or have suggestions for improvements:

1. Check the [existing issues](https://github.com/Pepe-tf/Minecraft-Site-Of-Grace-plugin/issues) first
2. Submit a new issue using the appropriate template
3. Provide as much detail as possible, including:
   - Server version
   - Plugin version
   - Error messages (if any)
   - Steps to reproduce

## 🤝 Contributing

Contributions are welcome! Here's how you can help:

1. Fork the repository
2. Create a new branch (`git checkout -b feature/improvement`)
3. Make your changes
4. Commit your changes (`git commit -am 'Add new feature'`)
5. Push to the branch (`git push origin feature/improvement`)
6. Create a Pull Request

## 📜 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 📬 Contact

- Website: https://billhub.site
- Contact Form: https://billhub.site/contact
- Issues: https://github.com/Pepe-tf/Minecraft-Site-Of-Grace-plugin/issues

## ❤️ Support

If you find this plugin useful, please consider:

- Giving it a star on GitHub
- Sharing it with others
- Reporting bugs and suggesting improvements
- Contributing to the code