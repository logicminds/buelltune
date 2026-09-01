#!/usr/bin/env python3
"""Bump BuellTune's app version and generate a CHANGELOG.md entry.

Used by the `create-release` GitHub Actions workflow
(.github/workflows/create-release.yml), and safe to run locally:

    python3 scripts/prepare_release.py patch --dry-run

Responsibilities:
  1. Read the current versionCode/versionName out of app/build.gradle.kts.
  2. Compute the next versionCode (always +1) and versionName (semver bump
     per the given `major`/`minor`/`patch` argument).
  3. Rewrite app/build.gradle.kts in place with the new values.
  4. Collect every non-merge commit since the previous `v*.*.*` release tag
     (or, if none exists yet, since the commit that introduced
     CHANGELOG.md), excluding prior "chore(release): ..." commits, and
     insert a new dated section into CHANGELOG.md directly below
     `## [Unreleased]`.
  5. Print `key=value` lines (new version, new versionCode, previous
     version) suitable for appending straight to $GITHUB_OUTPUT, and write
     the generated changelog section alone to --notes-out for use as a pull
     request body.

--dry-run performs steps 1-2 and 4 in memory only: it prints what would
change without writing app/build.gradle.kts or CHANGELOG.md.
"""
from __future__ import annotations

import argparse
import datetime
import pathlib
import re
import subprocess
import sys

REPO_ROOT = pathlib.Path(__file__).resolve().parent.parent
BUILD_GRADLE = REPO_ROOT / "app" / "build.gradle.kts"
CHANGELOG = REPO_ROOT / "CHANGELOG.md"
UNRELEASED_MARKER = "## [Unreleased]"

VERSION_CODE_RE = re.compile(r"^(?P<indent>\s*)versionCode\s*=\s*(?P<value>\d+)\s*$", re.MULTILINE)
VERSION_NAME_RE = re.compile(r'^(?P<indent>\s*)versionName\s*=\s*"(?P<value>[^"]+)"\s*$', re.MULTILINE)
RELEASE_COMMIT_RE = re.compile(r"^chore\(release\): v\d+\.\d+\.\d+$")


def run_git(*args: str) -> str:
    return subprocess.check_output(["git", "-C", str(REPO_ROOT), *args], text=True).strip()


def bump_semver(version: str, bump: str) -> str:
    parts = version.split(".")
    if len(parts) != 3 or not all(p.isdigit() for p in parts):
        raise ValueError(f"versionName {version!r} is not MAJOR.MINOR.PATCH")
    major, minor, patch = (int(p) for p in parts)
    if bump == "major":
        major, minor, patch = major + 1, 0, 0
    elif bump == "minor":
        minor, patch = minor + 1, 0
    elif bump == "patch":
        patch += 1
    else:
        raise ValueError(f"unknown bump type {bump!r}")
    return f"{major}.{minor}.{patch}"


def read_current_version() -> tuple[int, str]:
    text = BUILD_GRADLE.read_text()
    code_match = VERSION_CODE_RE.search(text)
    name_match = VERSION_NAME_RE.search(text)
    if not code_match:
        raise SystemExit(f"could not find a single `versionCode = N` line in {BUILD_GRADLE}")
    if not name_match:
        raise SystemExit(f'could not find a single `versionName = "X.Y.Z"` line in {BUILD_GRADLE}')
    return int(code_match.group("value")), name_match.group("value")


def write_new_version(new_code: int, new_name: str) -> None:
    text = BUILD_GRADLE.read_text()
    text, n = VERSION_CODE_RE.subn(lambda m: f"{m.group('indent')}versionCode = {new_code}", text)
    if n != 1:
        raise SystemExit(f"expected exactly one versionCode line, replaced {n}")
    text, n = VERSION_NAME_RE.subn(lambda m: f'{m.group("indent")}versionName = "{new_name}"', text)
    if n != 1:
        raise SystemExit(f"expected exactly one versionName line, replaced {n}")
    BUILD_GRADLE.write_text(text)


def find_since_ref() -> str | None:
    """Previous release tag, else the commit that added CHANGELOG.md, else None."""
    try:
        return run_git("describe", "--tags", "--match", "v*.*.*", "--abbrev=0")
    except subprocess.CalledProcessError:
        pass
    added = run_git("log", "--diff-filter=A", "--format=%H", "--", "CHANGELOG.md")
    commits = [c for c in added.splitlines() if c]
    return commits[-1] if commits else None


def commit_range_subjects(since_ref: str | None) -> list[tuple[str, str]]:
    """[(short_sha, subject), ...] oldest first, excluding merges and prior release commits."""
    range_spec = f"{since_ref}..HEAD" if since_ref else "HEAD"
    log = run_git("log", "--no-merges", "--reverse", "--format=%h\t%s", range_spec)
    entries = []
    for line in log.splitlines():
        if not line:
            continue
        sha, _, subject = line.partition("\t")
        if RELEASE_COMMIT_RE.match(subject):
            continue
        entries.append((sha, subject))
    return entries


def repo_slug() -> str | None:
    try:
        url = run_git("remote", "get-url", "origin")
    except subprocess.CalledProcessError:
        return None
    match = re.search(r"[:/]([^/]+/[^/]+?)(?:\.git)?$", url)
    return match.group(1) if match else None


def build_changelog_section(version: str, since_ref: str | None) -> str:
    entries = commit_range_subjects(since_ref)
    date = datetime.date.today().isoformat()
    slug = repo_slug()
    lines = [f"## [{version}] - {date}", ""]
    if not entries:
        lines.append("_No changes recorded since the previous release._")
    else:
        for sha, subject in entries:
            if slug:
                lines.append(f"- {subject} ([`{sha}`](https://github.com/{slug}/commit/{sha}))")
            else:
                lines.append(f"- {subject} (`{sha}`)")
    lines.append("")
    return "\n".join(lines)


def insert_changelog_section(section: str) -> None:
    text = CHANGELOG.read_text()
    marker_pos = text.find(UNRELEASED_MARKER)
    if marker_pos == -1:
        raise SystemExit(f"could not find {UNRELEASED_MARKER!r} marker in {CHANGELOG}")
    insert_at = marker_pos + len(UNRELEASED_MARKER)
    new_text = text[:insert_at] + "\n\n" + section.rstrip("\n") + "\n" + text[insert_at:].lstrip("\n")
    CHANGELOG.write_text(new_text)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("bump", choices=["major", "minor", "patch"])
    parser.add_argument("--notes-out", type=pathlib.Path, default=None, help="write the generated changelog section here")
    parser.add_argument("--dry-run", action="store_true", help="print results without writing files")
    args = parser.parse_args()

    current_code, current_name = read_current_version()
    new_name = bump_semver(current_name, args.bump)
    new_code = current_code + 1

    since_ref = find_since_ref()
    section = build_changelog_section(new_name, since_ref)

    if args.dry_run:
        print(f"# would bump versionCode {current_code} -> {new_code}", file=sys.stderr)
        print(f"# would bump versionName {current_name} -> {new_name}", file=sys.stderr)
        print(f"# changelog since: {since_ref or '(full history)'}", file=sys.stderr)
        print(section)
    else:
        write_new_version(new_code, new_name)
        insert_changelog_section(section)

    if args.notes_out:
        args.notes_out.write_text(section)

    print(f"version={new_name}")
    print(f"version_code={new_code}")
    print(f"previous_version={current_name}")


if __name__ == "__main__":
    main()
