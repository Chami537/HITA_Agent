#!/usr/bin/env python3
"""Check versioned paths and local Markdown file links; never read deployment data."""
import argparse
import os
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import unquote, urlsplit


ALLOWED_BINARIES = {"gradle/wrapper/gradle-wrapper.jar"}
FORBIDDEN_SUFFIXES = (
    ".db", ".db-wal", ".db-shm", ".sqlite", ".sqlite3", ".sqlite-wal",
    ".sqlite-shm", ".sqlite3-wal", ".sqlite3-shm", ".db-journal", ".apk", ".aab", ".bak", ".log", ".pyc", ".class",
    ".jar", ".exe", ".so", ".dylib", ".zip", ".tar", ".tar.gz", ".tgz",
)
MAGIC = (b"SQLite format 3\x00", b"\x7fELF", b"\xcf\xfa\xed\xfe", b"\xfe\xed\xfa\xcf",
         b"\xce\xfa\xed\xfe", b"\xfe\xed\xfa\xce", b"\xca\xfe\xba\xbe", b"MZ")


def repository_files(root):
    result = subprocess.check_output(
        ["git", "ls-files", "-z", "--cached", "--others", "--exclude-standard"], cwd=root
    )
    return sorted(set(os.fsdecode(p) for p in result.split(b"\x00") if p))


def markdown_targets(text):
    """Inline/image and reference destinations outside fenced code blocks."""
    fence = None
    for number, line in enumerate(text.splitlines(), 1):
        marker = re.match(r"^\s{0,3}(`{3,}|~{3,})", line)
        if marker:
            run = marker.group(1)
            if fence is None:
                fence = run
            elif run[0] == fence[0] and len(run) >= len(fence):
                fence = None
            continue
        if fence:
            continue
        line = re.sub(r"`[^`]*`", "", line)
        for match in re.finditer(r"!?\[[^\]\n]*\]\(\s*(<[^>]+>|[^\s)]+)", line):
            yield number, match.group(1).strip("<>")
        reference = re.match(r"^\s{0,3}\[[^]]+\]:\s*(<[^>]+>|\S+)", line)
        if reference:
            yield number, reference.group(1).strip("<>")


def check(root):
    root = root.resolve()
    paths = repository_files(root)
    existing = {p for p in paths if (root / p).exists()}
    errors = []
    for relative in paths:
        path = root / relative
        if not path.exists():
            if path.is_symlink():
                errors.append(f"{relative}: broken symlink")
            continue  # A tracked file removed in this working tree.
        if not path.resolve().is_relative_to(root):
            errors.append(f"{relative}: symlink leaves repository")
            continue
        if not path.is_file():
            continue
        if relative not in ALLOWED_BINARIES:
            if relative.lower().endswith(FORBIDDEN_SUFFIXES):
                errors.append(f"{relative}: runtime/build/archive artifact must stay outside Git")
            with path.open("rb") as stream:
                if stream.read(32).startswith(MAGIC):
                    errors.append(f"{relative}: database or executable content must stay outside Git")
        if path.stat().st_size > 8 * 1024 * 1024:
            errors.append(f"{relative}: exceeds the 8 MiB source-file limit")
        if path.suffix.lower() != ".md":
            continue
        for line, destination in markdown_targets(path.read_text(encoding="utf-8")):
            url = urlsplit(destination)
            if url.scheme or url.netloc or not url.path:
                continue
            target = unquote(url.path)
            candidate = (root / target.lstrip("/")) if target.startswith("/") else path.parent / target
            candidate = candidate.resolve()
            if not candidate.is_relative_to(root):
                errors.append(f"{relative}:{line}: local link leaves repository: {destination}")
                continue
            name = candidate.relative_to(root).as_posix()
            tracked = name in existing or any(p.startswith(name.rstrip("/") + "/") for p in existing)
            if not candidate.exists() or not tracked:
                errors.append(f"{relative}:{line}: missing versioned link target: {destination}")
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    errors = check(args.root)
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print("Repository checks passed: source artifacts and local Markdown file links")
    return 0


if __name__ == "__main__":
    sys.exit(main())
