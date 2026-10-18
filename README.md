# FastLoot (Paper 1.16+)

A high-performance Paper plugin designed to streamline inventory management by allowing players to instantly loot containers with a simple sneak-right-click.

## 🚀 Features

* **One-Tap Looting:** Sneak and right-click any container (chests, trapped chests, barrels, shulker boxes) to instantly transfer all contents into your inventory.
* **Smart Placeable Filtering:** Intelligently checks your main hand. If you are holding a placeable block while sneaking, FastLoot steps out of the way so you can still place blocks normally without accidentally vacuuming a chest.
* **Partial Stack Handling:** If your inventory fills up mid-loot, FastLoot takes what fits, leaves the remainder in the container, and updates everything cleanly.
* **Detailed Chat Feedback:** Sends clean messages tracking exact item and stack counts looted, plus a warning when your inventory becomes full.
* **Zero Tick Overhead:** Pure event-driven design ensures zero impact on server TPS.

## 🎮 How It Works

1. Hold Shift (sneak) while looking at any valid container.
2. Right-click the container block.
3. FastLoot loops through the container inventory, transfers valid item stacks into your player inventory using Paper's native `addItem` handler, and updates the container state instantly.

## 🛠️ Build & Installation

### Prerequisites
* **Java:** Version 11 or higher
* **Build Tool:** Apache Maven
* **Server Software:** Paper 1.16.5+

### Building from Source

```bash
mvn clean package
```

Grab the compiled `FastLoot.jar` from the `target/` folder and drop it into your server's `plugins/` directory.

## 🔐 Permissions

| Permission | Description | Default |
| :--- | :--- | :--- |
| `fastloot.use` | Grants permission to use the quick container looting mechanic | `true` |

## 📄 License

Licensed under the MIT License.
