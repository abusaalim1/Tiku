#!/usr/bin/env python3
"""ROUND 8: deploy the username-claim delete rule.
Old: /usernames/{name} -> update, delete denied for everyone.
New: update still denied; delete allowed ONLY for the claim's owner
(resource.data.uid == request.auth.uid) — the rename transaction frees the
old name while claiming the new one. Everything else byte-identical."""
import json, subprocess, tempfile, time, urllib.request, urllib.error, urllib.parse, base64, os, sys

SA = json.load(open(os.path.expanduser("~/workspace/tiku/firebase-admin/service-account.json")))
PROJ = SA["project_id"]

def b64u(b): return base64.urlsafe_b64encode(b).rstrip(b"=").decode()
now = int(time.time())
unsigned = (b64u(json.dumps({"alg": "RS256", "typ": "JWT"}).encode()) + "." +
            b64u(json.dumps({"iss": SA["client_email"],
                             "scope": "https://www.googleapis.com/auth/cloud-platform",
                             "aud": SA["token_uri"], "iat": now, "exp": now + 3600}).encode()))
with tempfile.NamedTemporaryFile("w", suffix=".pem", delete=False) as f:
    f.write(SA["private_key"]); keyfile = f.name
os.chmod(keyfile, 0o600)
sig = subprocess.run(["openssl", "dgst", "-sha256", "-sign", keyfile],
                     input=unsigned.encode(), capture_output=True)
os.unlink(keyfile)
assert sig.returncode == 0, sig.stderr.decode()
jwt = unsigned + "." + b64u(sig.stdout)
req = urllib.request.Request(SA["token_uri"],
    data=urllib.parse.urlencode({"grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
                                 "assertion": jwt}).encode(),
    headers={"Content-Type": "application/x-www-form-urlencoded"})
with urllib.request.urlopen(req, timeout=30) as r:
    TOKEN = json.load(r)["access_token"]

def call(method, url, body=None):
    data = json.dumps(body).encode() if body is not None else None
    rq = urllib.request.Request(url, data=data, method=method,
        headers={"Authorization": f"Bearer {TOKEN}", "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(rq, timeout=30) as r:
            return json.load(r)
    except urllib.error.HTTPError as e:
        print("HTTP", e.code, e.read().decode()[:800]); sys.exit(1)

src = open(os.path.expanduser("~/workspace/tiku/firebase-admin/deployed-rules.txt")).read()
old_block = """      allow update, delete: if false;"""
new_block = """      allow update: if false;
      // ROUND 8: a user may release ONLY their own claim (username change —
      // the rename transaction frees the old name while claiming the new
      // one). Nobody can ever delete another user's claim.
      allow delete: if signedIn() && resource.data.uid == request.auth.uid;"""
assert old_block in src, "usernames block not found verbatim"
new_src = src.replace(old_block, new_block)
assert new_src != src

rs = call("POST", f"https://firebaserules.googleapis.com/v1/projects/{PROJ}/rulesets",
          {"source": {"files": [{"name": "firestore.rules", "content": new_src}]}})
rs_name = rs["name"]
print("RULESET CREATED:", rs_name)

rel = call("PATCH",
           f"https://firebaserules.googleapis.com/v1/projects/{PROJ}/releases/cloud.firestore",
           {"release": {"name": f"projects/{PROJ}/releases/cloud.firestore",
                        "rulesetName": rs_name},
            "updateMask": "rulesetName"})
print("RELEASE NOW POINTS TO:", rel.get("rulesetName"))

check = call("GET", f"https://firebaserules.googleapis.com/v1/{rs_name}")
live = "\n".join(f.get("content", "") for f in check.get("source", {}).get("files", []))
ok = "allow delete: if signedIn() && resource.data.uid == request.auth.uid;" in live
open(os.path.expanduser("~/workspace/tiku/firebase-admin/deployed-rules.txt"), "w").write(live)
print("VERIFIED username delete rule live:", ok)
