"""Integration smoke test; starts a loopback-only companion and always stops it."""
import os, json, time, subprocess, secrets, urllib.request, urllib.error
from pathlib import Path
root = Path(__file__).resolve().parent.parent
token = secrets.token_urlsafe(32)
env = dict(os.environ, MUSICTRANSI_TOKEN=token, MUSICTRANSI_HOST='127.0.0.1', MUSICTRANSI_PORT='18787')
process = subprocess.Popen(['java', '-cp', str(root/'android-app/bridge/build/install/bridge/lib/*'),
    'com.example.musictransi.bridge.MainKt'], env=env, stdout=subprocess.DEVNULL)
base = 'http://127.0.0.1:18787'
def request(path, body=None, auth=True, headers=None):
    data = json.dumps(body).encode() if body is not None else None
    h = {'Authorization':'Bearer '+token} if auth else {}
    if data: h['Content-Type'] = 'application/json'
    h.update(headers or {})
    return urllib.request.urlopen(urllib.request.Request(base+path, data=data, headers=h), timeout=120)
def fetch(path, body=None):
    with request(path, body) as r: return json.load(r)
try:
    for _ in range(60):
        try:
            assert fetch('/health')['status']=='ok'
            break
        except urllib.error.URLError: time.sleep(.5)
    else: raise RuntimeError('Bridge did not start')
    try: request('/health', auth=False); raise AssertionError('Unauthenticated access accepted')
    except urllib.error.HTTPError as e: assert e.code == 401
    try: fetch('/prepare', {'id':'https://example.com/private','title':'invalid','artist':'x','local':False}); raise AssertionError('Invalid provider accepted')
    except urllib.error.HTTPError as e: assert e.code == 400
    print('PASS health, authentication and provider validation', flush=True)
    tracks = fetch('/search?q=The%20Weeknd%20Blinding%20Lights&type=songs')
    assert tracks and tracks[0]['artwork']
    albums = fetch('/search?q=The%20Weeknd%20After%20Hours&type=albums')
    album = next(a for a in albums if a['title'].lower()=='after hours')
    songs = fetch('/album?id='+urllib.parse.quote(album['id'], safe=''))
    assert len(songs) == 14 and songs[0]['title']=='Alone Again'
    print('PASS music search, artwork metadata and 14 ordered album tracks', flush=True)
    related = fetch('/radio', {'seed':tracks[0], 'excluded':tracks[:1]})
    assert related and all(t['id'] != tracks[0]['id'] for t in related)
    prepared = fetch('/prepare', tracks[0])
    with request(prepared['path'], auth=False, headers={'Range':'bytes=0-1023'}) as audio:
        assert audio.status == 206 and len(audio.read(1024)) == 1024
    print('PASS radio, stream resolution and range-proxied audio bytes (not iPhone playback)', flush=True)
finally:
    process.terminate()
    try: process.wait(timeout=10)
    except subprocess.TimeoutExpired: process.kill(); process.wait()
