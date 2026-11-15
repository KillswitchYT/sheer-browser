#!/usr/bin/env python3
"""Makes the update files for one build, so a phone that has an earlier build can update by downloading a few MB instead of the whole app.

usage: release-assets.py --apk Sheer.apk --code 110 --name 0.1.10 --prev prev/ --out out/ [--keep 2]

prev/ holds earlier builds named Sheer-<versionCode>.apk. For the newest --keep of them this makes out/patch-<from>-to-<code>.bin
(bsdiff, converted by mkpatch.py) and writes out/update.json, which the app reads. A patch that is not clearly smaller than the whole
app (an engine upgrade changes almost everything) is left out, and the app then offers the full download instead."""
import argparse, hashlib, json, os, re, subprocess, sys

ap = argparse.ArgumentParser()
ap.add_argument('--apk', required=True)
ap.add_argument('--code', type=int, required=True)
ap.add_argument('--name', required=True)
ap.add_argument('--prev', required=True)
ap.add_argument('--out', required=True)
ap.add_argument('--keep', type=int, default=2)
args = ap.parse_args()
here = os.path.dirname(os.path.abspath(__file__))
os.makedirs(args.out, exist_ok=True)


def sha(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(1 << 20), b''):
            h.update(chunk)
    return h.hexdigest()


apk_size = os.path.getsize(args.apk)
apk_sha = sha(args.apk)

older = []
if os.path.isdir(args.prev):
    for f in os.listdir(args.prev):
        m = re.fullmatch(r'Sheer-(\d+)\.apk', f)
        if m and int(m.group(1)) < args.code:
            older.append((int(m.group(1)), os.path.join(args.prev, f)))
older = sorted(older, reverse=True)[:args.keep]

# bsdiff takes about a minute per pair, so the pairs run side by side.
jobs = []
for code, path in older:
    raw = os.path.join(args.out, 'raw-%d.bsdiff' % code)
    jobs.append((code, path, raw, subprocess.Popen(['bsdiff', path, args.apk, raw])))

patches = []
for code, path, raw, proc in jobs:
    if proc.wait() != 0:
        print('bsdiff failed for build %d, no patch for it' % code)
        continue
    name = 'patch-%d-to-%d.bin' % (code, args.code)
    subprocess.run([sys.executable, os.path.join(here, 'mkpatch.py'), raw, os.path.join(args.out, name)], check=True)
    os.remove(raw)
    size = os.path.getsize(os.path.join(args.out, name))
    if size > apk_size * 0.7:
        print('patch from build %d is %d bytes, not much smaller than the app, leaving it out' % (code, size))
        os.remove(os.path.join(args.out, name))
        continue
    patches.append({'from': code, 'fromSha': sha(path), 'name': name, 'size': size})
    print('patch from build %d: %.2f MB (the app is %.1f MB)' % (code, size / 1e6, apk_size / 1e6))

manifest = {
    'versionCode': args.code,
    'versionName': args.name,
    'apk': {'name': 'Sheer.apk', 'size': apk_size, 'sha256': apk_sha},
    'patches': patches,
}
with open(os.path.join(args.out, 'update.json'), 'w') as f:
    json.dump(manifest, f, indent=1)
print('update.json: build %d, %d patch(es)' % (args.code, len(patches)))
