# WARNO Mod Maker

WARNO Mod Maker is a powerful Java Swing application designed for editing WARNO game files (NDF format). Built with the sleek FlatLaf dark UI theme, it offers a modern and intuitive modding experience for casual tinkerers and advanced users alike. Edit units, weapons, templates, and more with fine control over every detail — from armor values to experience packs.

---

## 🚀 Key Features

- **Multi-format NDF Support**  
  Supports Unit, Weapon, Ammunition, Missile, and Building descriptors — plus more.

- **Mass Editing Tools**  
  Quickly batch-edit stats with operations like Set, Add, Multiply, or Percentage Change.

- **Wildcard and Indexed Paths**  
  Use `ModulesDescriptors[*].PropertyName` or `ModulesDescriptors[4].MaxSpeed` for precise or wide-scope edits.

- **Tag-Based Filtering**  
  Target edits using tags like `Infantry`, `Elite`, `Recon`, etc.

- **Profile Management**  
  Save, load, and share mod profiles in JSON format — with change tracking and auto path-fixing.

- **Context-Aware Property Labels**  
  Friendly names like “Armor Thickness” instead of raw field identifiers.

- **Tree Navigation & Search**  
  Easily browse NDF structure with expansion memory and property search.

- **Smart Tag Editor**  
  Add, remove, or group-edit tags and orders across multiple units.

- **Safe Edits**  
  Built-in validation to prevent crashes from malformed paths or values.

- **Modern FlatLaf UI**  
  Fast, dark-mode-optimized interface for improved modding workflow.

---

## 📋 Requirements

- Java 11 or higher (Java 24 recommended)  
- Windows OS (primary support)  
- 2GB RAM minimum (4GB recommended for large files)

---

## 🛠️ Building and Running

### Quick Start (Windows)

### Manual Build

javac -d build -source 11 -target 11 src/com/warnomodmaker/.java src/com/warnomodmaker/model/.java src/com/warnomodmaker/parser/.java src/com/warnomodmaker/gui/.java

jar -cfe WarnoModMaker.jar com.warnomodmaker.WarnoModMaker -C build .

java -jar WarnoModMaker.jar


---

## 🎮 Usage Guide

Launch the application with `build.bat` or `java -jar WarnoModMaker.jar`. Open WARNO NDF files via **File → Open**. Browse units in the left tree panel. Edit properties directly in the right panel or use **Tools → Mass Modify** for bulk changes.

- **Individual Unit Editing**: Select a unit, expand its modules, and edit properties inline. Changes are tracked automatically.
- **Mass Modification**: Select a category (Combat, Defense, Movement, AI), choose a property or enter a path, set the modification type (Set, Add, Multiply, Percent), apply filters by tags or unit names, and apply to matching units.
- **Tag and Order Management**: Bulk add or remove tags across units via **Tools → Tags & Orders**. Filter units by tags for precise targeting.
- **Mod Profiles**: Save and load mod profiles with full change history. Profiles can be reapplied after game updates with automatic path corrections.

---

## 🔍 Example Modifications

| Goal                     | Property Path                                             | Operation          |
|--------------------------|-----------------------------------------------------------|--------------------|
| Increase unit health      | `MaxPhysicalDamages`                                      | Multiply by 1.5    |
| Set armor values          | `ModulesDescriptors[*].BlindageProperties.ArmorThickness` | Set to 25          |
| Change experience system  | `ModulesDescriptors[*].ExperienceLevelsPackDescriptor`    | Set to `~/...`     |
| Speed boost               | `ModulesDescriptors[*].MaxSpeed`                          | Add 20%            |
| Buff elite infantry       | Filter: `Tag:Elite Infantry` <br> Path: `MaxPhysicalDamages` | Multiply by 1.3  |

---

## ⚙️ Technical Details

- Fully supports all WARNO NDF descriptor types and modules.  
- Handles complex nested objects, arrays, and maps.  
- Preserves original formatting, whitespace, and comments to avoid game crashes.  
- Uses efficient in-memory models and caching for fast editing and large files.  
- Multi-threaded operations for responsive UI during bulk modifications.  
- Clean separation of parser, model, and GUI layers for extensibility and maintainability.  
- Strong typing and validation to prevent errors.

---

## 📁 Project Structure

WARNO-Mod-Maker/
├── src/com/warnomodmaker/
│ ├── WarnoModMaker.java # Main entry point
│ ├── gui/ # UI components (MainWindow, MassModifyDialog, TagAndOrderEditor, UnitEditor)
│ ├── model/ # Data models and business logic
│ └── parser/ # NDF parsing and writing
├── resources/ # Game data files and references
├── build.bat # Build and run script
├── distribute.bat # Distribution package script
└── README.md # This file


---

## 🚨 Troubleshooting

- **App won’t start**: Ensure Java 11+ is installed and in your system PATH. Check with `java -version`.
- **NDF file won’t load**: Confirm it is a valid WARNO NDF file and not locked by another app.
- **Mass modify not finding properties**: Use the Refresh button to rescan. Verify property path format.
- **Changes not saving**: Check file permissions and disk space.
- **Performance issues**: Increase JVM memory with `java -Xmx4g -jar WarnoModMaker.jar` and close other apps.

---

## 🤝 Contributing

- Clone repo and ensure Java 11+ installed.  
- Import into your IDE (IntelliJ recommended).  
- Build with `build.bat` and test thoroughly with various NDFs.  
- Follow existing code patterns and keep layers separated.  
- Submit pull requests with clear descriptions and test coverage.

---

## 📄 License

This project is licensed under the MIT License. See LICENSE file for details.

---

## 🙏 Acknowledgments

Thanks to the WARNO community, Eugen Systems for the game and NDF format, the Java Swing community, and open-source contributors for libraries and support.

Made with ❤️ for WARNO modders.

---

For support, bug reports, and discussions, visit the GitHub repository’s Issues and Discussions sections.
