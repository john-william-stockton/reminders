# Open Bugs

Known defects not yet fixed. Update this alongside bug fixes and new discoveries — move a
resolved entry to `STATUS.md`'s Resolved Bugs section instead of deleting it here.

- **Reopening a Complete/Missed reminder doesn't reset its streak, so repeated mark/unmark cycles
  over-count it.** `Main.kt`'s `onToggleComplete`/`onToggleMissed` "unmark" branches (`Main.kt:169`,
  `Main.kt:181`) reset `status` back to `Open` but leave `streak` untouched. Since `nextStreak()`
  (`AlarmScheduler.kt:74`) only resets a streak that's pointing the *opposite* direction before
  applying it, marking → unmarking → marking again keeps compounding in the same direction instead
  of landing back where the first mark left it (e.g. missed → unmissed → missed again yields a
  streak of -2, not -1). `STATUS.md` already flags reopening as leaving the streak "as-is
  (not specified)" — this is that gap made concrete. For a non-recurring reminder, resetting the
  streak to its pre-mark value on reopen looks safe and unambiguous. For a genuinely recurring one,
  it's murkier: a next occurrence may already have spawned (carrying the post-mark streak forward)
  by the time the user reopens the current one, so a clean revert needs a design decision about
  whether/how to also correct that already-spawned row before this gets fixed.
