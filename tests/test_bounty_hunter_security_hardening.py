"""
Bounty Hunter security hardening regression tests.

These tests verify that the generated patch actually changed target code in a
measurable way without relying on external network calls.
"""

from pathlib import Path


def test_requests_calls_have_timeouts_or_no_requests_usage():
    root = Path(__file__).resolve().parents[1]
    py_files = [
        p for p in root.glob("**/*.py")
        if ".venv" not in str(p)
        and "site-packages" not in str(p)
        and ".bounty_hunter" not in str(p)
    ]

    offenders = []
    for path in py_files:
        text = path.read_text(errors="replace")
        if "requests." in text and "timeout=" not in text:
            offenders.append(str(path.relative_to(root)))

    assert not offenders, "requests calls without explicit timeout found: " + ", ".join(offenders)


def test_subprocess_shell_true_removed():
    root = Path(__file__).resolve().parents[1]
    offenders = []
    for path in root.glob("**/*.py"):
        if ".venv" in str(path) or "site-packages" in str(path):
            continue
        text = path.read_text(errors="replace")
        if "shell=True" in text:
            offenders.append(str(path.relative_to(root)))

    assert not offenders, "Unsafe shell=True usage remains: " + ", ".join(offenders)
