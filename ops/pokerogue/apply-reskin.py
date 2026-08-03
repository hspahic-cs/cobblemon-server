#!/usr/bin/env python3
"""Apply the reskin overlays in ops/pokerogue/reskin/ to a PokéRogue checkout.

Idempotent by construction, same pattern as the cookies.ts patch in
build-and-stage.sh: every target file is restored from git first (from the
main repo or the right submodule), then the override is re-applied. Safe to
run any number of times; leaves the checkout in its normal patched state.

Layout of the reskin/ directory (see reskin/README.md):
  reskin.json           -> non-JSON edits; "title" rewrites index.html's
                           <title> + title/og:title/twitter:title metas
  <path>/<file>.json    -> deep-merged into the same path in the checkout,
                           e.g. reskin/locales/en/splash-texts.json overlays
                           locales/en/splash-texts.json

Usage: apply-reskin.py <pokerogue-checkout-dir>
"""

import json
import re
import subprocess
import sys
from pathlib import Path

RESKIN_DIR = Path(__file__).resolve().parent / "reskin"


def git_restore(checkout: Path, rel: str) -> None:
    """git checkout -- <rel>, routed into a submodule when rel lives in one."""
    top = rel.split("/", 1)[0]
    if (checkout / top / ".git").exists():  # submodule (locales/, assets/)
        repo, path = checkout / top, rel.split("/", 1)[1]
    else:
        repo, path = checkout, rel
    subprocess.run(["git", "-C", str(repo), "checkout", "--", path], check=True)


def deep_merge(base: dict, override: dict) -> dict:
    for key, value in override.items():
        if isinstance(value, dict) and isinstance(base.get(key), dict):
            deep_merge(base[key], value)
        else:
            base[key] = value
    return base


def apply_title(checkout: Path, title: str) -> None:
    git_restore(checkout, "index.html")
    index = checkout / "index.html"
    html = index.read_text(encoding="utf-8")
    html = re.sub(r"(<title>)[^<]*(</title>)", rf"\g<1>{title}\g<2>", html, count=1)
    html = re.sub(
        r'((?:name="title"|property="og:title"|property="twitter:title") content=")[^"]*(")',
        rf"\g<1>{title}\g<2>",
        html,
    )
    index.write_text(html, encoding="utf-8")
    print(f"reskin: index.html title -> {title!r}")


def apply_json_overlay(checkout: Path, rel: str, overlay_file: Path) -> None:
    git_restore(checkout, rel)
    target = checkout / rel
    merged = deep_merge(
        json.loads(target.read_text(encoding="utf-8")),
        json.loads(overlay_file.read_text(encoding="utf-8")),
    )
    target.write_text(
        json.dumps(merged, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(f"reskin: merged {overlay_file.relative_to(RESKIN_DIR)} -> {rel}")


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__, file=sys.stderr)
        return 2
    checkout = Path(sys.argv[1]).expanduser().resolve()
    if not (checkout / ".git").exists():
        print(f"not a git checkout: {checkout}", file=sys.stderr)
        return 1

    for overlay in sorted(RESKIN_DIR.rglob("*.json")):
        rel = str(overlay.relative_to(RESKIN_DIR))
        if rel == "reskin.json":
            continue
        if not (checkout / rel).exists():
            print(f"reskin: target missing in checkout, skipping: {rel}", file=sys.stderr)
            continue
        apply_json_overlay(checkout, rel, overlay)

    config_file = RESKIN_DIR / "reskin.json"
    if config_file.exists():
        config = json.loads(config_file.read_text(encoding="utf-8"))
        if "title" in config:
            apply_title(checkout, config["title"])
    return 0


if __name__ == "__main__":
    sys.exit(main())
