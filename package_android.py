"""Package the Android preview without SDK/cache/emulator or machine-specific files."""
from pathlib import Path
import hashlib
import shutil
import zipfile
import json
import re
import sys

root = Path(__file__).resolve().parent
source = root / "android-app"
dist = root / "dist"
dist.mkdir(exist_ok=True)
version = sys.argv[1] if len(sys.argv) > 1 else "0.6"
assert re.fullmatch(r"\d+\.\d+", version), "Use a numeric preview version"
metadata = json.loads((source / "app/build/outputs/apk/debug/output-metadata.json").read_text())
assert metadata["elements"][0]["versionName"] == f"{version}-preview", "Build version does not match package version"
apk = dist / f"MusicTransi-v{version}-preview.apk"
shutil.copy2(source / "app/build/outputs/apk/debug/app-debug.apk", apk)
archive = dist / f"MusicTransi-v{version}-source.zip"
excluded_dirs = {"build", ".gradle", ".kotlin", ".idea", ".git"}
excluded_files = {"local.properties", "dependency-diagnostic.txt"}
with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as bundle:
    for path in source.rglob("*"):
        relative = path.relative_to(source)
        if path.is_file() and not (set(relative.parts) & excluded_dirs) and path.name not in excluded_files:
            bundle.write(path, Path("android-app") / relative)
digest = hashlib.sha256(apk.read_bytes()).hexdigest().upper()
(dist / f"MusicTransi-v{version}.sha256").write_text(f"{digest}  {apk.name}\n", encoding="ascii")
print(f"APK: {apk.stat().st_size:,} bytes; SHA256: {digest}")
print(f"Source: {archive.stat().st_size:,} bytes")
