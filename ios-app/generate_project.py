"""Create a dependency-free Xcode project. Runs with Python 3 on Windows or macOS."""
from pathlib import Path
import hashlib
import plistlib
root = Path(__file__).resolve().parent
project = root / 'MusicTransi.xcodeproj'
project.mkdir(exist_ok=True)
def uid(value): return hashlib.sha256(value.encode()).hexdigest()[:24].upper()
def ref(value): return uid(value)
objects = []
def add(key, content): objects.append(f'{ref(key)} = {{ {content} }};')
sources = sorted((root/'MusicTransi').glob('*.swift'))
for file in sources:
    add(file.name, f'isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = "{file.name}"; sourceTree = "<group>";')
    add('build:'+file.name, f'isa = PBXBuildFile; fileRef = {ref(file.name)};')
add('app', 'isa = PBXFileReference; explicitFileType = wrapper.application; path = MusicTransi.app; sourceTree = BUILT_PRODUCTS_DIR;')
add('sources', 'isa = PBXGroup; path = MusicTransi; sourceTree = "<group>"; children = (' + ','.join(ref(f.name) for f in sources) + ');')
add('products', f'isa = PBXGroup; name = Products; sourceTree = "<group>"; children = ({ref("app")});')
add('root', f'isa = PBXGroup; sourceTree = "<group>"; children = ({ref("sources")},{ref("products")});')
add('compile', 'isa = PBXSourcesBuildPhase; buildActionMask = 2147483647; runOnlyForDeploymentPostprocessing = 0; files = (' + ','.join(ref('build:'+f.name) for f in sources) + ');')
add('resources', 'isa = PBXResourcesBuildPhase; buildActionMask = 2147483647; runOnlyForDeploymentPostprocessing = 0; files = ();')
add('frameworks', 'isa = PBXFrameworksBuildPhase; buildActionMask = 2147483647; runOnlyForDeploymentPostprocessing = 0; files = ();')
for mode in ['Debug', 'Release']:
    add('project:'+mode, f'isa = XCBuildConfiguration; name = {mode}; buildSettings = {{ SDKROOT = iphoneos; IPHONEOS_DEPLOYMENT_TARGET = 17.0; CLANG_ENABLE_MODULES = YES; SWIFT_VERSION = 5.0; }};')
    settings = f'''PRODUCT_NAME = MusicTransi; PRODUCT_BUNDLE_IDENTIFIER = org.musictransi.preview;
        INFOPLIST_FILE = MusicTransi/Info.plist; GENERATE_INFOPLIST_FILE = NO;
        TARGETED_DEVICE_FAMILY = "1,2"; SUPPORTED_PLATFORMS = "iphoneos iphonesimulator";
        SWIFT_VERSION = 5.0; IPHONEOS_DEPLOYMENT_TARGET = 17.0; CODE_SIGN_STYLE = Automatic;
        CURRENT_PROJECT_VERSION = 12; MARKETING_VERSION = 0.12; ENABLE_USER_SCRIPT_SANDBOXING = YES;
        LD_RUNPATH_SEARCH_PATHS = "$(inherited) @executable_path/Frameworks";
        SWIFT_OPTIMIZATION_LEVEL = "{'-Onone' if mode == 'Debug' else '-O'}";'''
    add('target:'+mode, f'isa = XCBuildConfiguration; name = {mode}; buildSettings = {{ {settings} }};')
for scope in ['project', 'target']:
    add(scope+':configs', f'isa = XCConfigurationList; buildConfigurations = ({ref(scope+":Debug")},{ref(scope+":Release")}); defaultConfigurationIsVisible = 0; defaultConfigurationName = Release;')
add('target', f'isa = PBXNativeTarget; name = MusicTransi; productName = MusicTransi; productReference = {ref("app")}; productType = "com.apple.product-type.application"; buildConfigurationList = {ref("target:configs")}; buildPhases = ({ref("compile")},{ref("frameworks")},{ref("resources")}); buildRules = (); dependencies = ();')
add('project', f'isa = PBXProject; attributes = {{ LastUpgradeCheck = 1600; }}; buildConfigurationList = {ref("project:configs")}; compatibilityVersion = "Xcode 14.0"; developmentRegion = en; knownRegions = (en,Base); mainGroup = {ref("root")}; productRefGroup = {ref("products")}; projectDirPath = ""; projectRoot = ""; targets = ({ref("target")});')
(project/'project.pbxproj').write_text('// !$*UTF8*$!\n{ archiveVersion = 1; classes = {}; objectVersion = 56; objects = {\n' + '\n'.join(objects) + '\n}; rootObject = ' + ref('project') + '; }\n')
info = dict(CFBundleDevelopmentRegion='en', CFBundleDisplayName='MusicTransi', CFBundleExecutable='$(EXECUTABLE_NAME)',
    CFBundleIdentifier='$(PRODUCT_BUNDLE_IDENTIFIER)', CFBundleInfoDictionaryVersion='6.0', CFBundleName='MusicTransi',
    CFBundlePackageType='APPL', CFBundleShortVersionString='0.12', CFBundleVersion='12', LSRequiresIPhoneOS=True,
    UIBackgroundModes=['audio'], UILaunchScreen={}, UISupportedInterfaceOrientations=['UIInterfaceOrientationPortrait', 'UIInterfaceOrientationLandscapeLeft', 'UIInterfaceOrientationLandscapeRight'],
    NSLocalNetworkUsageDescription='Connect to your MusicTransi companion computer to search and stream music.',
    NSAppTransportSecurity={'NSAllowsLocalNetworking': True})
(root/'MusicTransi/Info.plist').write_bytes(plistlib.dumps(info))
scheme = project/'xcshareddata/xcschemes'; scheme.mkdir(parents=True, exist_ok=True)
(scheme/'MusicTransi.xcscheme').write_text(f'''<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion="1600" version="1.3">
<BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES"><BuildActionEntries><BuildActionEntry buildForTesting="YES" buildForRunning="YES" buildForProfiling="YES" buildForArchiving="YES" buildForAnalyzing="YES"><BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{ref('target')}" BuildableName="MusicTransi.app" BlueprintName="MusicTransi" ReferencedContainer="container:MusicTransi.xcodeproj"/></BuildActionEntry></BuildActionEntries></BuildAction>
<LaunchAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" launchStyle="0" useCustomWorkingDirectory="NO" ignoresPersistentStateOnLaunch="NO" debugDocumentVersioning="YES" debugServiceExtension="internal" allowLocationSimulation="YES"><BuildableProductRunnable runnableDebuggingMode="0"><BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{ref('target')}" BuildableName="MusicTransi.app" BlueprintName="MusicTransi" ReferencedContainer="container:MusicTransi.xcodeproj"/></BuildableProductRunnable></LaunchAction>
<ArchiveAction buildConfiguration="Release" revealArchiveInOrganizer="YES"/></Scheme>''')
print('Generated', project)
