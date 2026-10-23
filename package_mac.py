"""Package the native iPhone source and platform-independent companion for a Mac."""
from pathlib import Path
import hashlib
import plistlib
import xml.etree.ElementTree as ET
import zipfile

root = Path(__file__).resolve().parent
ios = root / 'ios-app'
lib = root / 'android-app/bridge/build/install/bridge/lib'
assert list(lib.glob('*.jar')), 'Build :bridge:installDist first'
project = (ios/'MusicTransi.xcodeproj/project.pbxproj').read_text()
for file in (ios/'MusicTransi').glob('*.swift'):
    assert f'path = "{file.name}"' in project
info = plistlib.loads((ios/'MusicTransi/Info.plist').read_bytes())
assert info['UIBackgroundModes'] == ['audio']
ET.parse(ios/'MusicTransi.xcodeproj/xcshareddata/xcschemes/MusicTransi.xcscheme')
archive = root/'dist/MusicTransi-Mac-iPhone-kit.zip'
prefix = Path('MusicTransi-Mac-iPhone-kit')
excluded = {'.git', 'build', '.build-check', 'xcuserdata', '__pycache__'}
with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as z:
    def put(file, destination):
        entry = zipfile.ZipInfo.from_file(file, str(prefix/destination).replace('\\', '/'))
        entry.create_system = 3
        entry.external_attr = (0o100755 if file.suffix == '.command' else 0o100644) << 16
        z.writestr(entry, file.read_bytes(), compress_type=zipfile.ZIP_DEFLATED)
    for file in ios.rglob('*'):
        relative = file.relative_to(ios)
        if not file.is_file() or set(relative.parts) & excluded: continue
        if file.name == 'Mac-build.log' or file.suffix == '.secret': continue
        put(file, Path('ios-app')/relative)
    put(ios/'MAC-IPHONE-GUIDE.md', Path('START-HERE.md'))
    put(ios/'Start-Companion.command', Path('companion-mac/Start-Companion.command'))
    for file in lib.glob('*.jar'):
        put(file, Path('companion-mac/lib')/file.name)
    put(root/'android-app/THIRD_PARTY.md', Path('companion-mac/THIRD_PARTY.md'))
with zipfile.ZipFile(archive) as z:
    assert z.testzip() is None
    assert not any(n.endswith(('.secret', '.keystore', 'local.properties')) for n in z.namelist())
    assert z.getinfo(str(prefix/'companion-mac/Start-Companion.command').replace('\\', '/')).external_attr >> 16 == 0o100755
digest = hashlib.sha256(archive.read_bytes()).hexdigest()
archive.with_suffix('.sha256').write_text(f'{digest}  {archive.name}\n')
print(f'{archive.name}: {archive.stat().st_size:,} bytes; SHA256 {digest}')
