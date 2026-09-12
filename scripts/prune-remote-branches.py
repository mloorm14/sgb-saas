#!/usr/bin/env python3
"""List or delete remote branches that are already merged into a base branch.

Default mode is a dry run. Use --delete to run `git push origin --delete` for
each candidate. Protected branches are never deleted.
"""

from __future__ import annotations

import argparse
import subprocess
from pathlib import Path


PROTECTED = {
    "main",
    "master",
    "develop",
    "demo/interfaces-completas",
    "final-biblioteca",
    "DEMO-PRESENTAR",
    "Presentacion_Final",
}


def git(args: list[str], check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(["git", *args], check=check, text=True, capture_output=True)


def remote_branches() -> list[str]:
    result = git(["for-each-ref", "--format=%(refname:short)", "refs/remotes/origin"])
    branches: list[str] = []
    for raw in result.stdout.splitlines():
        if not raw or raw == "origin/HEAD":
            continue
        if raw.startswith("origin/"):
            branches.append(raw.removeprefix("origin/"))
    return sorted(set(branches))


def is_merged(branch: str, base: str) -> bool:
    return git(["merge-base", "--is-ancestor", f"origin/{branch}", f"origin/{base}"], check=False).returncode == 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Prune merged remote branches safely.")
    parser.add_argument("--base", default="main", help="Remote base branch used as integration target")
    parser.add_argument("--delete", action="store_true", help="Actually delete candidates from origin")
    parser.add_argument("--include", action="append", default=[], help="Extra branch name to allow deleting")
    parser.add_argument("--exclude", action="append", default=[], help="Extra branch name to protect")
    args = parser.parse_args()

    repo = git(["rev-parse", "--show-toplevel"]).stdout.strip()
    if Path(repo) != Path.cwd():
        print(f"Run from repository root: {repo}")
        return 2

    protected = (PROTECTED | set(args.exclude)) - set(args.include)
    branches = remote_branches()
    candidates = [branch for branch in branches if branch not in protected and is_merged(branch, args.base)]

    print(f"Base: origin/{args.base}")
    print(f"Remote branches: {len(branches)}")
    print(f"Protected branches: {len(protected & set(branches))}")
    print(f"Merged deletion candidates: {len(candidates)}")
    for branch in candidates:
        print(branch)

    if not args.delete:
        print("\nDry run only. Re-run with --delete to remove the candidates above.")
        return 0

    for branch in candidates:
        print(f"Deleting origin/{branch}")
        git(["push", "origin", "--delete", branch])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
