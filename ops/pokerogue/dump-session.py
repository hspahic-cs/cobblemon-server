#!/usr/bin/env python3
"""Fetch and pretty-print a PokeRogue session (and system) save via the API.

Usage:
    dump-session.py <base-api-url> <username> <password> [slot]

    base-api-url  e.g. http://pokerogue.example.lan/api  (no trailing slash needed)
    slot          0-4; omit to dump all five slots

The rogueserver API returns DECOMPRESSED JSON — zstd(+Go gob) exists only in
the DB `data` blobs. If you ever read sessionSaveData.data straight from
MariaDB, it is a zstd frame wrapping a Go `encoding/gob` stream (NOT JSON);
decode it with a Go helper importing rogueserver/defs.

WARNING: session/get overwrites the account's activeClientSessions row with
the clientSessionId below, which invalidates a live browser session for this
account (its next save sync is rejected until reload). Use on test accounts,
not on players mid-run.
"""

import json
import random
import string
import sys
import urllib.error
import urllib.parse
import urllib.request


def request(url: str, token: str | None = None, data: bytes | None = None) -> tuple[int, bytes]:
    req = urllib.request.Request(url, data=data)
    if token:
        req.add_header("Authorization", token)
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            return resp.status, resp.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()


def main() -> int:
    if len(sys.argv) not in (4, 5):
        print(__doc__.strip(), file=sys.stderr)
        return 2

    base = sys.argv[1].rstrip("/")
    username, password = sys.argv[2], sys.argv[3]
    slots = [int(sys.argv[4])] if len(sys.argv) == 5 else list(range(5))

    # Login (form-encoded) -> {"token": "..."}
    form = urllib.parse.urlencode({"username": username, "password": password}).encode()
    status, body = request(f"{base}/account/login", data=form)
    if status != 200:
        print(f"login failed ({status}): {body.decode(errors='replace').strip()}", file=sys.stderr)
        return 1
    token = json.loads(body)["token"]

    # rogueserver requires a 32-char alnum clientSessionId on savedata reads
    csid = "".join(random.choices(string.ascii_lowercase + string.digits, k=32))

    out: dict = {}
    for slot in slots:
        status, body = request(
            f"{base}/savedata/session/get?slot={slot}&clientSessionId={csid}", token=token
        )
        if status == 200:
            out[f"session_slot_{slot}"] = json.loads(body)
        elif status == 404:
            out[f"session_slot_{slot}"] = None  # save does not exist
        else:
            out[f"session_slot_{slot}"] = {
                "error": f"HTTP {status}: {body.decode(errors='replace').strip()}"
            }

    status, body = request(f"{base}/savedata/system/get?clientSessionId={csid}", token=token)
    out["system"] = json.loads(body) if status == 200 else {
        "error": f"HTTP {status}: {body.decode(errors='replace').strip()}"
    }

    json.dump(out, sys.stdout, indent=2, sort_keys=True)
    print()
    return 0


if __name__ == "__main__":
    sys.exit(main())
