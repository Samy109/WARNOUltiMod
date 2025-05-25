# WARNO Mod Maker

A comprehensive Java Swing-based application for modifying WARNO game files (NDF format). This powerful tool provides an intuitive interface for editing unit properties, creating balanced modifications, and managing mod profiles for the WARNO real-time strategy game.

![Java](https://img.shields.io/badge/Java-11+-orange.svg)
![License](https://img.shields.io/badge/License-MIT-blue.svg)
![Platform](https://img.shields.io/badge/Platform-Windows-lightgrey.svg)

## 🚀 Key Features

### 📁 **Multi-Format NDF Support**
- **Unit Descriptors** - Complete unit editing with all module types
- **Weapon Descriptors** - Weapon systems, ammunition, and ballistics
- **Ammunition Files** - Damage, range, and projectile properties
- **Missile Descriptors** - Guided weapon systems and targeting
- **Building Descriptors** - Structures and defensive positions
- **And many more** - Comprehensive support for all WARNO NDF file types

### ⚡ **Advanced Mass Modification System**
- **Dynamic Property Discovery** - Automatically scans and categorizes all available properties
- **Intelligent Categorization** - Properties organized by Combat Offense, Combat Defense, Movement, AI Behavior, etc.
- **Multiple Modification Types** - Set values, multiply, add, subtract, percentage changes
- **Wildcard Support** - Use `ModulesDescriptors[*].PropertyName` to modify all matching modules
- **Template Reference Editing** - Modify experience packs, weapon references, and other template links
- **Tag-Based Filtering** - Filter units by tags for precise targeting of modifications

### 🎯 **Precision Property Editing**
- **Context-Aware Property Names** - "Armor Thickness" instead of generic "Index", "Resistance Family" instead of "Family"
- **Nested Property Support** - Edit deep properties like `BlindageProperties.ArmorThickness`
- **Array Property Handling** - Add/remove tags, modify lists and arrays
- **Boolean, Enum, and String Support** - Full support for all NDF data types
- **Real-time Validation** - Immediate feedback on property paths and values

### 🏷️ **Tag and Order Management**
- **Bulk Tag Editing** - Add or remove tags across multiple units simultaneously
- **Tag-Based Unit Filtering** - Find units by their classification tags
- **Order System Editing** - Modify unit orders and abilities per-unit
- **Smart Tag Categories** - Organized tag management with intuitive interface

### 💾 **Mod Profile System**
- **JSON Mod Profiles** - Save all modifications as reusable profiles
- **Change Tracking** - Complete history of all modifications made
- **Profile Reloading** - Apply saved modifications after game updates
- **Automatic Path Fixing** - Smart recovery when game updates break mod paths
- **Metadata Support** - Profile names, descriptions, creation dates, and version info

### 🔍 **Advanced Search and Navigation**
- **Property Search** - Find properties by name across all categories
- **Unit Filtering** - Filter units by name, type, or characteristics
- **Tree View Navigation** - Hierarchical browsing of unit structures
- **Expansion State Memory** - Remembers your navigation preferences
- **Last Location Memory** - Automatically returns to your last working directory

## 📋 Requirements

- **Java 11 or higher** (Java 24 recommended for distribution builds)
- **Windows OS** (primary platform, may work on other platforms)
- **2GB RAM minimum** (for large NDF files)

## 🛠️ Building and Running

### Quick Start (Windows)
```bash
# Build and run in one command
build.bat

# Create redistributable package
distribute.bat
```

### Manual Build
```bash
# Compile the application
javac -d build -source 11 -target 11 src/com/warnomodmaker/*.java src/com/warnomodmaker/model/*.java src/com/warnomodmaker/parser/*.java src/com/warnomodmaker/gui/*.java

# Create JAR file
jar -cfe WarnoModMaker.jar com.warnomodmaker.WarnoModMaker -C build .

# Run the application
java -jar WarnoModMaker.jar
```

### Distribution Build
The `distribute.bat` script creates a standalone application package using jpackage:
- No Java installation required for end users
- Clean app folder with just the executable
- Optimized for distribution and deployment

## 📖 Usage Guide

### Getting Started
1. **Launch the application** using `build.bat` or `java -jar WarnoModMaker.jar`
2. **Open an NDF file** via File → Open (supports all WARNO NDF file types)
3. **Browse units** in the left tree panel
4. **Edit properties** in the right panel or use mass modification tools

### Individual Unit Editing
- **Select a unit** from the tree view to see all its properties
- **Expand modules** to access specific module properties
- **Edit values directly** in the property table
- **Changes are tracked** automatically for mod profile creation

### Mass Modification Workflow
1. **Open Tools → Mass Modify** to access the mass modification dialog
2. **Choose a category** (Combat Defense, Combat Offense, Movement, etc.)
3. **Select a property** from the dropdown or enter a custom path
4. **Set modification type** (Set, Multiply, Add, Percentage, etc.)
5. **Enter the new value** or modification amount
6. **Filter units** (optional) by name or tags
7. **Apply changes** to all matching units

### Advanced Property Paths
```
# Direct properties
MaxPhysicalDamages
ClassNameForDebug

# Specific module index
ModulesDescriptors[5].BlindageProperties.ArmorThickness
ModulesDescriptors[12].MaxSpeed

# Wildcard (all matching modules)
ModulesDescriptors[*].BlindageProperties.ArmorThickness
ModulesDescriptors[*].MaxSpeed

# Template references
ModulesDescriptors[*].ExperienceLevelsPackDescriptor
ModulesDescriptors[*].WeaponDescriptor

# Array properties (tags)
ModulesDescriptors[*].TagSet
```

### Tag and Order Management
- **Access via Tools → Tags & Orders** for bulk tag editing
- **Filter by tags** to find specific unit types
- **Add/remove tags** across multiple units simultaneously
- **Modify unit orders** on a per-unit basis
- **Use tag filtering** in mass modify for precise targeting

### Mod Profile Management
- **Save profiles** via File → Save Mod Profile
- **Load profiles** via File → Load Mod Profile
- **Track all changes** automatically during your session
- **Apply profiles** after game updates with automatic path fixing
- **Export/import** profiles for sharing with other modders

## 🎮 Common Modding Examples

### Balancing Unit Health
```
Property Path: MaxPhysicalDamages
Modification: Multiply by 1.5
Result: Increases all unit health by 50%
```

### Adjusting Armor Values
```
Property Path: ModulesDescriptors[*].BlindageProperties.ArmorThickness
Modification: Set to value 25
Result: Sets armor thickness to 25 for all units with armor
```

### Changing Experience Systems
```
Property Path: ModulesDescriptors[*].ExperienceLevelsPackDescriptor
Modification: Set to value ~/ExperienceLevelsPackDescriptor_XP_pack_AA_v3
Result: Replaces experience system with custom AA variant
```

### Speed Modifications
```
Property Path: ModulesDescriptors[*].MaxSpeed
Modification: Increase by 20%
Result: Makes all units 20% faster
```

### Tag-Based Filtering
```
Filter by tags: Infantry, Elite
Property Path: ModulesDescriptors[*].MaxPhysicalDamages
Modification: Multiply by 1.3
Result: Only elite infantry units get 30% more health
```

## 🔧 Technical Details

### NDF File Format Support
The application supports the complete WARNO NDF specification:
- **Object Definitions** - `TEntityDescriptor`, `TWeaponDescriptor`, etc.
- **Module Systems** - All module descriptor types with full property access
- **Data Types** - Numbers, strings, booleans, enums, arrays, objects
- **References** - Template references (`~/`), resource references (`$/`)
- **Complex Structures** - Nested objects, arrays of objects, maps
- **Exact Formatting** - Preserves original formatting to prevent game crashes

### Performance Optimizations
- **In-Memory Object Model** - Fast access to all properties without re-parsing
- **Efficient Mass Updates** - Direct object model manipulation for speed
- **Smart Caching** - Property discovery results cached for performance
- **Multi-threading** - Background processing for large operations
- **Memory Management** - Optimized for large NDF files (2GB+ support)

### Architecture Highlights
- **Clean Separation** - Parser, model, and GUI layers clearly separated
- **Extensible Design** - Easy to add new NDF file types and property categories
- **Robust Error Handling** - Graceful handling of malformed files and edge cases
- **Modification Tracking** - Complete audit trail of all changes made
- **Type Safety** - Strong typing throughout the application

## 📁 Project Structure

```
WARNO-Mod-Maker/
├── src/com/warnomodmaker/
│   ├── WarnoModMaker.java          # Main application entry point
│   ├── gui/                        # User interface components
│   │   ├── MainWindow.java         # Main application window
│   │   ├── MassModifyDialog.java   # Mass modification interface
│   │   ├── TagAndOrderEditor.java  # Tag and order management
│   │   └── UnitEditor.java         # Individual unit editing
│   ├── model/                      # Data model and business logic
│   │   ├── NDFValue.java           # Core NDF value types
│   │   ├── PropertyScanner.java    # Property discovery system
│   │   ├── PropertyUpdater.java    # Property modification engine
│   │   ├── ModificationTracker.java # Change tracking system
│   │   └── ModProfile.java         # Mod profile management
│   └── parser/                     # NDF file parsing and writing
│       ├── NDFParser.java          # Main NDF parser
│       ├── NDFTokenizer.java       # Tokenization engine
│       └── NDFWriter.java          # NDF file output
├── resources/                      # Game data files and references
├── build.bat                       # Build and run script
├── distribute.bat                  # Distribution package creation
└── README.md                       # This file
```

## 🚨 Troubleshooting

### Common Issues

**Application won't start**
- Ensure Java 11+ is installed and in your PATH
- Try running `java -version` to verify Java installation
- Check that `JAVA_HOME` is set correctly for distribution builds

**NDF file won't load**
- Verify the file is a valid WARNO NDF file
- Check file permissions and ensure it's not locked by another application
- Try loading a smaller NDF file first to test functionality

**Mass modify not finding properties**
- Use the "Refresh" button to rescan properties
- Check that the property path format is correct
- Verify the property exists by browsing individual units first

**Changes not saving**
- Ensure you have write permissions to the target directory
- Check that the NDF file isn't read-only
- Verify there's sufficient disk space

**Performance issues with large files**
- Increase JVM memory: `java -Xmx4g -jar WarnoModMaker.jar`
- Close other applications to free up system memory
- Consider processing files in smaller chunks

### Getting Help
- Check the in-application help dialogs (Help buttons throughout the UI)
- Review property path examples in the Mass Modify dialog
- Use the Debug Info feature to understand unit structure

## 🤝 Contributing

We welcome contributions to the WARNO Mod Maker! Here's how you can help:

### Development Setup
1. **Clone the repository** and ensure Java 11+ is installed
2. **Import into your IDE** (IntelliJ IDEA recommended)
3. **Run `build.bat`** to verify everything compiles correctly
4. **Make your changes** following the existing code style
5. **Test thoroughly** with various NDF file types

### Contribution Guidelines
- **Follow existing patterns** - The codebase uses explicit, direct approaches
- **No smart assumptions** - Prefer clear, predictable behavior
- **Maintain clean architecture** - Keep parser, model, and GUI layers separate
- **Use comprehensive tests** - Test new features with various NDF files
- **Update documentation** - Include relevant README updates

### Areas for Contribution
- **New NDF file type support** - Add support for additional WARNO file formats
- **Property categorization improvements** - Better organization of properties
- **Performance optimizations** - Faster loading and processing of large files
- **UI/UX enhancements** - Improved user interface and workflow
- **Bug fixes** - Address issues and edge cases

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- **WARNO Community** - For feedback, testing, and feature requests
- **Eugen Systems** - For creating WARNO and the NDF file format
- **Java Swing Community** - For UI components and design patterns
- **Open Source Contributors** - For libraries and tools that made this possible

## 📞 Support

- **Issues** - Report bugs and request features via GitHub Issues
- **Discussions** - Join community discussions about modding and features
- **Documentation** - Comprehensive help available within the application

---

**Made with ❤️ for the WARNO modding community**

---

**Credits:**
**Main Tester: cbrid**
**Helpers: cteplr, Dandywalken**

*Transform your WARNO experience with precision, power, and ease.*
