# Unit of Work

## Steps
1. If there's a bug listed in `./open-bugs.md`, grab the first one and create an organized
   bug-fix plan for the prompter to review. Otherwise, grab the first feature from
   `./planned-features.md` and create an implementation plan instead.
   - If both lists are empty: analyze the code for evident and potential bugs, add them to
     `./open-bugs.md` (upon approval), and repeat this step. If there's truly nothing left even
     after that, suggest a new feature to be added to `./planned-features.md` (upon approval) and
     implement it (also upon approval).
2. Only implement the plan once permission is explicitly granted.
3. Prompt the user to manually verify the fix/feature, then remove the entry from `open-bugs.md`
   or `planned-features.md` and add a corresponding entry to `./STATUS.md`'s Resolved Bugs or
   Implemented Features section, and commit and push changes only once explicitly granted
   permission.
