Audit the project and ensure:
 * `FEATURES.md` is both up-to-date and correct.
 * `BUGS.md` is up-to-date and is not missing any bugs that could be found in the source code.

Before reading a file, hash it (content hash, e.g. `sha256sum`; not the git hash) and compare
against `audit-manifest.json` in this directory. Skip a file only if *both* hold: its hash matches
the manifest, and its `last_analyzed` date is within the last 3 months. Otherwise — hash changed,
file is new, or `last_analyzed` is over 3 months old even with a matching hash — read and evaluate
it, even if nothing about it looks likely to have changed.

After the audit, rewrite `audit-manifest.json` with a fresh hash and today's date as
`last_analyzed` for every file considered (including `FEATURES.md` and `BUGS.md` themselves,
hashed *after* any edits), so the next run can rely on it.