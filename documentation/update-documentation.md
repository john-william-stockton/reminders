Audit the project and ensure:
 * `STATUS.md`'s Implemented Features and Resolved Bugs sections are both up-to-date and correct.
 * `open-bugs.md` is up-to-date and is not missing any bugs that could be found in the source code.
 * `planned-features.md` is up-to-date.

Before reading a file, hash it (content hash, e.g. `sha256sum`; not the git hash) and compare
against `audit-manifest.json` in this directory. Skip a file only if *both* hold: its hash matches
the manifest, and its `last_analyzed` date is within the last 3 months. Otherwise — hash changed,
file is new, or `last_analyzed` is over 3 months old even with a matching hash — read and evaluate
it, even if nothing about it looks likely to have changed.

After the audit, rewrite `audit-manifest.json` with a fresh hash and today's date as
`last_analyzed` for every file considered (including `STATUS.md`, `open-bugs.md`, and
`planned-features.md` themselves, hashed *after* any edits), so the next run can rely on it.