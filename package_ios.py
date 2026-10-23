"""Bundle source for a user-run macOS build and a locally compiled JVM companion."""
from pathlib import Path
import zipfile, hashlib
root = Path(__file__).resolve().parent
dist = root/'dist'; dist.mkdir(exist_ok=True)
excluded = {'build', '.gradle', '.kotlin', '.idea', '.git', '__pycache__', 'xcuserdata', '.build-check'}
def allowed(path):
    return not (set(path.parts) & excluded) and path.name not in {'local.properties', 'dependency-diagnostic.txt', 'Mac-build.log'} and not path.name.endswith(('.secret', '.keystore'))
source = dist/'MusicTransi-iPhone-source.zip'
with zipfile.ZipFile(source, 'w', zipfile.ZIP_DEFLATED) as z:
    for folder in ['ios-app', 'android-app', '.github']:
        for file in (root/folder).rglob('*'):
            if file.is_file() and allowed(file.relative_to(root)):
                z.write(file, file.relative_to(root))
    z.write(root/'.gitignore', '.gitignore')
companion = dist/'MusicTransi-companion-windows.zip'
installation = root/'android-app/bridge/build/install/bridge'
assert (installation/'bin/bridge.bat').exists(), 'Build :bridge:installDist first'
with zipfile.ZipFile(companion, 'w', zipfile.ZIP_DEFLATED) as z:
    for file in installation.rglob('*'):
        if file.is_file(): z.write(file, file.relative_to(installation))
    z.write(root/'android-app/bridge/Start-Companion.ps1', 'Start-Companion.ps1')
    z.write(root/'android-app/THIRD_PARTY.md', 'THIRD_PARTY.md')
    z.write(root/'ios-app/WINDOWS-IPHONE-GUIDE.md', 'WINDOWS-IPHONE-GUIDE.md')
    z.write(root/'ios-app/README.md', 'README.md')
for archive in [source, companion]:
    with zipfile.ZipFile(archive) as z: assert z.testzip() is None
    print(archive.name, archive.stat().st_size, 'bytes; SHA256', hashlib.sha256(archive.read_bytes()).hexdigest())
