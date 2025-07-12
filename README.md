# WARNO Mod Maker

WARNO Mod Maker is a comprehensive Java Swing application designed for advanced editing of WARNO game files (NDF format). Built with the sleek FlatLaf dark UI theme, it offers a professional modding experience with enterprise-grade features for both casual tinkerers and advanced modders. Edit units, weapons, ammunition, buildings, and more with surgical precision and powerful automation tools.

<img width="1283" height="713" alt="image" src="https://github.com/user-attachments/assets/fea700cf-2f9a-48a8-bc1d-ab7df72dad56" />


---

## 🚀 Core Features

### **📁 Comprehensive NDF File Support**
- **25+ File Types**: UniteDescriptor, WeaponDescriptor, Ammunition, MissileDescriptors, BuildingDescriptors, EffetsSurUnite, SoundDescriptors, WeaponSoundHappenings, DamageResistance, FireDescriptor, SmokeDescriptor, and many more
- **Multi-Tab Interface**: Work with multiple files simultaneously with intelligent tab management
- **Cross-File Integrity**: Advanced validation system ensures references between files remain valid

### **⚡ Advanced Mass Editing System**
- **Smart Property Discovery**: Automatically scans and categorizes all editable properties
- **Multiple Operation Types**: Set, Add, Multiply, Percentage Change with precision control
- **Wildcard Paths**: Use `ModulesDescriptors[*].PropertyName` for bulk operations across all modules
- **Indexed Access**: Target specific modules with `ModulesDescriptors[4].MaxSpeed`
- **Tag-Based Filtering**: Filter units by tags like `Infantry`, `Elite`, `Recon`, `Tank` for precise targeting
- **Unit Name Filtering**: Apply changes to units matching specific name patterns

### **🏷️ Intelligent Tag & Order Management**
- **Bulk Tag Operations**: Add/remove tags across multiple units simultaneously
- **Order System Management**: Modify BasicOrders and AdvancedOrders for unit capabilities
- **Tag-Based Filtering**: Use tags to create targeted modification groups
- **Smart Tag Discovery**: Automatically discovers existing tags from loaded files

### **💾 Professional Profile System**
- **JSON-Based Profiles**: Save complete modification sets as reusable profiles
- **Change Tracking**: Full history of all modifications with timestamps and details
- **Auto-Migration**: Intelligent path correction when game updates change file structure
- **Profile Sharing**: Export and import profiles for community sharing
- **Validation**: Comprehensive validation before applying profiles to prevent conflicts

### **🔧 Advanced Entity Creation System**
- **Complete Entity Creation**: Create new units across multiple files with proper cross-references
- **Dynamic Template Learning**: Learns from existing entities to create realistic templates
- **Cross-File Dependencies**: Automatically handles UniteDescriptor → WeaponDescriptor → Ammunition chains
- **GUID Management**: Generates unique GUIDs and maintains consistency across files
- **Template Name Generation**: Follows WARNO naming conventions automatically

### **➕ Additive Operations Framework**
- **Add New Objects**: Create new top-level objects in any NDF file
- **Add Modules**: Insert new modules into existing units (weapons, armor, sensors, etc.)
- **Add Properties**: Add new properties to existing objects with type validation
- **Add Array Elements**: Extend arrays with new elements (tags, weapon lists, etc.)
- **Schema Learning**: Dynamically learns object schemas from loaded files

### **🔍 Cross-System Integrity Management**
- **Reference Validation**: Ensures all template references point to valid objects
- **GUID Tracking**: Prevents duplicate GUIDs and tracks ownership across files
- **Dependency Analysis**: Maps complex relationships between different file types
- **Migration Support**: Automatically fixes broken references after game updates
- **System Statistics**: Provides detailed reports on file relationships and integrity

### **🎯 Precision Editing Tools**
- **Context-Aware Property Labels**: Friendly names like "Armor Thickness" instead of raw identifiers
- **Tree Navigation & Search**: Browse complex NDF structures with expansion memory
- **Property Type Detection**: Automatically handles strings, numbers, booleans, arrays, objects
- **Template Reference Support**: Edit template paths with validation and suggestions
- **Enum Value Validation**: Ensures enum values are valid for the game

### **🛡️ Safety & Validation**
- **Format Preservation**: Maintains exact NDF formatting to prevent game crashes
- **Backup Integration**: Automatic backup suggestions before major changes
- **Validation Engine**: Prevents invalid modifications that could break the game
- **Error Recovery**: Graceful handling of malformed files with detailed error reporting
- **Undo Support**: Track and revert changes with comprehensive modification history

### **🎨 Modern User Interface**
- **FlatLaf Dark Theme**: Professional dark mode optimized for long editing sessions
- **Multi-Threading**: Responsive UI during heavy operations with progress indicators
- **Tabbed Workflow**: Work with multiple files simultaneously
- **Status Bar**: Real-time information about current file and modification status
- **Keyboard Shortcuts**: Efficient navigation and editing with hotkeys

---

## 📋 Requirements

- **Java 11 or higher** (Java 17+ recommended for optimal performance)
- **Windows OS** (primary support, Linux/Mac may work but untested)
- **2GB RAM minimum** (4GB recommended for large files and multiple tabs)
- **1GB disk space** for application and temporary files

---

## 🛠️ Building and Running

### Quick Start (Windows)
```bash
# Clone the repository
git clone https://github.com/your-repo/warno-mod-maker.git
cd warno-mod-maker

# Build and run
build.bat
```

### Manual Build
```bash
# Compile
javac -cp "lib/*" -d build src/com/warnomodmaker/**/*.java

# Create JAR
jar -cfe WarnoModMaker.jar com.warnomodmaker.WarnoModMaker -C build .

# Run
java -jar WarnoModMaker.jar
```

### For Large Files (Recommended)
```bash
java -Xmx8g -jar WarnoModMaker.jar
```

---

## 🎮 Usage Guide

### Getting Started
1. **Launch** the application with `build.bat` or `java -jar WarnoModMaker.jar`
2. **Open Files** via **File → Open** (supports multiple files in tabs)
3. **Browse Units** in the left tree panel with search and filtering
4. **Edit Properties** directly in the right panel with real-time validation

### Individual Unit Editing
- Select a unit from the tree browser
- Expand modules to see all properties
- Click on any property to edit inline
- Changes are tracked automatically with modification history

### Mass Modification Workflow
1. **Open Tools → Mass Modify**
2. **Select Category**: Combat, Defense, Movement, AI, or Custom
3. **Choose Property**: From discovered properties or enter custom path
4. **Set Operation**: Set, Add, Multiply, or Percentage Change
5. **Apply Filters**: By tags, unit names, or custom criteria
6. **Preview Changes**: Review affected units before applying
7. **Execute**: Apply changes with full undo support

### Tag and Order Management
1. **Open Tools → Tags & Orders**
2. **Select Units**: Choose units to modify
3. **Manage Tags**: Add/remove tags like `Elite`, `Recon`, `Heavy`
4. **Modify Orders**: Change BasicOrders and AdvancedOrders
5. **Apply Changes**: Bulk update all selected units

### Profile Management
1. **Create Profile**: Tools → Save Profile after making modifications
2. **Load Profile**: Tools → Load Profile to apply saved changes
3. **Share Profiles**: Export JSON files for community sharing
4. **Auto-Migration**: Profiles automatically adapt to game updates

### Entity Creation (Advanced)
1. **Open Tools → Create Entity**
2. **Select Entity Type**: Choose from discovered patterns (Tank, Infantry, etc.)
3. **Enter Name**: Provide unique name for the new entity
4. **Create**: Generate complete entity across multiple files

### Additive Operations (Expert)
1. **Open Tools → Additive Operations**
2. **Choose Operation**: Add Object, Module, Property, or Array Element
3. **Select Target**: Choose where to add the new element
4. **Configure**: Set properties and values for the new element
5. **Execute**: Add with full validation and error checking

---

## 🔍 Example Modifications

| Goal | Property Path | Operation | Filter |
|------|---------------|-----------|---------|
| Increase all unit health | `MaxPhysicalDamages` | Multiply by 1.5 | None |
| Buff tank armor | `ModulesDescriptors[*].BlindageProperties.ArmorThickness` | Set to 25 | Tag: Tank |
| Speed boost for recon | `ModulesDescriptors[*].MaxSpeed` | Add 20% | Tag: Recon |
| Elite infantry damage | `ModulesDescriptors[*].WeaponDescriptor` | Custom | Tag: Elite Infantry |
| Change experience system | `ModulesDescriptors[*].ExperienceLevelsPackDescriptor` | Set to `~/NewExperiencePack` | All |

---

## ⚙️ Technical Architecture

### Core Systems
- **Non-token based parser**: 1-1 accurate NDF parsing with format preservation
- **In-Memory Object Model**: Efficient representation of complex NDF structures
- **Dynamic Schema Discovery**: Learns file structures without hardcoded assumptions
- **Cross-File Reference Engine**: Tracks and validates relationships between files
- **Modification Tracking**: Complete audit trail of all changes

### Performance Features
- **Multi-Threading**: Background operations don't block the UI
- **Memory Optimization**: Efficient handling of large files (100MB+)
- **Caching System**: Smart caching for improved responsiveness
- **Lazy Loading**: Load file sections on-demand for faster startup

### Safety Features
- **Format Preservation**: Maintains exact whitespace, comments, and structure
- **Validation Engine**: Prevents game-breaking modifications
- **Backup Integration**: Automatic backup recommendations
- **Error Recovery**: Graceful handling of corrupted or malformed files

---

## 📁 Project Structure

```
WARNO-Mod-Maker/
├── src/com/warnomodmaker/
│   ├── WarnoModMaker.java              # Main entry point
│   ├── gui/                            # User interface components
│   │   ├── MainWindow.java             # Main application window
│   │   ├── MassModifyDialog.java       # Mass editing interface
│   │   ├── TagAndOrderEditorDialog.java # Tag/order management
│   │   ├── EntityCreationDialog.java   # Entity creation wizard
│   │   ├── AdditiveOperationsDialog.java # Additive operations
│   │   ├── UnitEditor.java             # Individual unit editor
│   │   ├── UnitBrowser.java            # Tree navigation
│   │   └── components/                 # Reusable UI components
│   ├── model/                          # Core business logic
│   │   ├── NDFValue.java               # NDF data structures
│   │   ├── PropertyScanner.java        # Property discovery
│   │   ├── ModificationTracker.java    # Change tracking
│   │   ├── ModProfile.java             # Profile management
│   │   ├── CrossSystemIntegrityManager.java # Integrity validation
│   │   ├── EntityCreationManager.java  # Entity creation system
│   │   ├── AdditiveOperationManager.java # Additive operations
│   │   └── PropertyUpdater.java        # Property modification engine
│   └── parser/                         # NDF file processing
│       ├── NDFParser.java              # NDF file parser
│       ├── NDFWriter.java              # NDF file writer
│       └── TokenPreservingParser.java  # Format-preserving parser
├── lib/                                # External libraries
├── resources/                          # Application resources
├── build.bat                           # Build script
├── distribute.bat                      # Distribution script
└── README.md                           # This file
```

---

## 🚨 Troubleshooting

### Common Issues
- **App won't start**: Ensure Java 11+ is installed (`java -version`)
- **Out of memory**: Use `java -Xmx8g -jar WarnoModMaker.jar`
- **File won't load**: Check file permissions and ensure it's a valid NDF file
- **Changes not saving**: Verify write permissions and available disk space
- **Performance issues**: Close other applications and increase JVM memory

### Advanced Troubleshooting
- **Profile won't load**: Check for path migration issues in the profile
- **Entity creation fails**: Ensure all required files are open
- **Mass modify not finding properties**: Use the Refresh button to rescan

---

## 🤝 Contributing

1. **Setup**: Clone repo and ensure Java 11+ is installed
2. **IDE**: Import into IntelliJ IDEA (recommended) or Eclipse
3. **Build**: Use `build.bat` or manual compilation
4. **Test**: Thoroughly test with various NDF files
5. **Code Style**: Follow existing patterns and maintain layer separation
6. **Submit**: Create pull requests with clear descriptions

### Development Guidelines
- Maintain separation between parser, model, and GUI layers
- Add comprehensive error handling and validation
- Include unit tests for new functionality
- Document complex algorithms and data structures
- Follow Java naming conventions and best practices

---

## 📄 License

This project is licensed under the MIT License. See LICENSE file for details.

---

## 🙏 Acknowledgments

- **WARNO Community**: For feedback, testing, and feature requests
- **Eugen Systems**: For creating WARNO and the NDF format
- **Java Swing Community**: For UI components and best practices
- **Open Source Contributors**: For libraries and tools that make this possible

Made with ❤️ for WARNO modders worldwide.

---

## 📞 Support

For support, bug reports, feature requests, and community discussions:
- **GitHub Issues**: Report bugs and request features
- **GitHub Discussions**: Community support and modding tips

**Happy Modding!** 🎮✨
