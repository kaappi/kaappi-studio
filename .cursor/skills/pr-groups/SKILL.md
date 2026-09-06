---
name: pr-groups
description: Group open GitHub issues into cohesive sets, each landable as a single PR, with a merge order and a parallelism verdict. Use when the user asks which issues can be fixed in one PR, how to batch a milestone into PRs, how to plan the work for a release, or how to split a set of issues across sessions. Accepts a milestone title, a label, or a comma-separated list of issue numbers.
argument-hint: "[milestone-title | label:<name> | NNN,NNN,...]"
---

# PR Groups

Arguments: `$ARGUMENTS`

Turn a set of open issues into **groups, each of which one PR can close**, plus
the order to land them in.

This is the opposite of maximizing parallelism. Two issues in the same function
belong in one PR even though that serializes them — splitting them means two
reviews of the same code and a merge conflict between your own branches.

## 1. Scope the set

Interpret the argument:

| Argument shape | Query |
|---|---|
| empty | `gh issue list --state open --limit 500 --json number,title,labels,assignees` |
| a milestone title (e.g. `0.22.2`) | add `--milestone "<title>"` |
| `label:<name>` | add `--label "<name>"` (repeat per comma-separated label; they AND) |
| comma-separated numbers | fetch exactly those with `gh issue view` |

If a filter returns nothing, say so and stop. Never silently widen to the whole
tracker. If the returned count equals the limit, raise the limit and rerun.

Drop from consideration, and say which you dropped and why: epics and tracking
issues, issues already assigned, issues with an open linked PR, and anything
labelled blocked / blocked-upstream / wontfix / duplicate.

## 2. Read the bodies

Titles almost never name files; bodies usually do, often with line numbers.
Every rule below depends on knowing which code an issue touches, so fetch
bodies:

```bash
gh issue list --state open --limit 500 --milestone "<title>" --json number,title,labels,body
```

For a large set, write the bodies to a scratch file and read that rather than
paging them through several tool calls.

## 3. Verify each issue still reproduces

**Do this before grouping, not after.** An issue can have been fixed by a PR
that closed its siblings and never got closed itself — scheduling it wastes a
slot in the plan and, worse, makes the whole plan look untrustworthy when
someone discovers it.

Most issues in this repo cite exact `file:line` evidence. The cheapest
verification is to read those lines and confirm the cited code is still there
and still wrong; do that for the whole set before anything else. Genuinely
runnable reproductions come next — a Scheme program that shows the bug in the
editor, a CI run — cheapest first. Android runs need `bash scripts/fetch-wasm.sh`
plus the copy to `app/src/main/assets/kaappi.wasm`, or Run fails for reasons
the issue is not about (see #5). Skip anything needing an emulator or a device
unless the grouping hinges on it.

Report anything that no longer reproduces as **verify-and-close**, with the
observed output next to the issue's own "Expected" block, and leave it out of
the groups. Do not close it yourself unless the user asks.

Also check whether a merged PR already claims the area: if several sibling
issues were closed together, ask whether this one was simply missed.

## 4. Ground the file claims in the source

An issue's diagnosis is a hypothesis, and hypotheses in this tracker have been
wrong often enough to be worth a minute of checking. Before grouping on a
claim, confirm it:

```bash
grep -n 'setCodeBase64' app/src/main/assets/webview/bridge.js   # do the named sites exist?
wc -l app/src/main/java/com/kaappi/studio/ui/screens/EditorScreen.kt
```

What you are looking for is cheap and specific:

- Do the two issues you want to pair really sit in the same function, or just
  the same file — or merely the same *platform label*? (#6 and #7 both carry
  `webview`, but they fix different mechanisms in different files.)
- Is the "one-line fix" one line?
- Has the surrounding code moved since the issue was filed?
- Name which area a group touches: `app/` (Android), `shared/` (KMP),
  `iosApp/` (Swift), and for webview assets, *which copy* — the two asset
  trees are separate files that must usually change together (step 6).

A grouping built on a stale line number falls apart on contact.

## 5. Group by cohesion

Strongest signal first — pair on the highest one that applies:

1. **Same function, or adjacent arms of one switch.** One diff, one review.
2. **Same file.** One reviewer context, one file-level test update. (#1 and #2
   are one PR: both are `iosApp/.../webview/bridge.js`, and one is the broken
   load while the other is the missing error plumbing that hides it — fixing
   the load alone still leaves failures invisible.)
3. **Same root cause across different files.** State the shared cause in one
   sentence; if you cannot, it is not a group. ("User-typed filenames reach
   the filesystem unsanitized and write errors are swallowed" groups the
   Android `FileRepository` actual with the Swift `FileBrowserViewModel` in
   unrelated modules.)
4. **Same verification harness.** Fixes proven by one Gradle invocation, one
   test directory, or one platform leg batch well even when otherwise
   unrelated — e.g. everything verified by `:shared:testAndroidHostTest`.
5. **Same zero-risk class.** Docs-only or test-only fixes touching no app
   code (e.g. the architecture-doc correction in #14 on its own) batch
   broadly, because the review risk of adding one more is near zero. Say they
   are trivially splittable.

Do **not** group:

- A fix needing a design decision with one that doesn't. The design discussion
  will hold the whole PR hostage. (#4's durable fix — generating
  `Examples.swift` from Kotlin or migrating iOS onto the shared module — is
  hostage to #14's framework decision.)
- A platform-shell change with local fixes, for the same reason.
- Issues whose only link is a shared label or milestone.

Keep a group to what one reviewer can hold at once. Beyond roughly four issues,
split unless they are the zero-risk class.

## 6. Check what the grouped edits would blow

This repo has no file-size policy, but it has **duplicated code and assets**
that only bite in aggregate. Check, per group:

- **Duplicated files.** Webview assets exist in two copies that must change
  together — `app/src/main/assets/webview/` and
  `iosApp/KaappiStudio/Resources/webview/` — **except `bridge.js`, which
  intentionally differs** (Android editor-only vs iOS WASM runner). Example
  programs exist in `shared/.../ExampleRepository.kt` and
  `iosApp/KaappiStudio/Helpers/Examples.swift` and must match
  id/title/description/category/code. A group touching one copy must name the
  mirrored edit in the plan; a group whose fix is legitimately
  single-platform must say why the other copy is untouched. This invariant is
  already broken (#4) — do not make it worse.
- **Bridge protocol lockstep.** A group adding or changing a JS↔native event
  must update both native handlers (`KaappiBridge.kt`,
  `SchemeWebView.swift`), both `bridge.js` variants, and
  `docs/bridge-protocol.md` in the same PR, or the platforms drift.
- **detekt baseline.** New code must not extend `app/detekt-baseline.xml` —
  it is for pre-existing violations only. If a group's refactor would add
  baseline entries, the refactor needs its own PR.
- **Release files off-limits.** Version fields (`app/build.gradle.kts`,
  `project.yml`, the Settings version strings) change only via the release
  skill. A group whose fix touches them is mis-scoped — split the version
  work out.
- **iOS project generation.** Any group adding, removing, or moving files
  under `iosApp/` must run `xcodegen generate`, or the committed Xcode project
  is stale and CI builds the wrong file set.

## 7. Order the groups

Two categories land first, and both are load-bearing:

- **Instrument before subject.** If one issue is a missing detector, test, or
  guard for the bug class the others are in, it goes first — otherwise the
  other fixes cannot be verified. The examples-parity guard implied by #4 is
  the canonical case: it lands with (or before) the content fix it protects.
- **Signal before work.** If one issue makes verification or CI untrustworthy,
  it goes first — otherwise every later PR's evidence is suspect. #5
  (`fetch-wasm.sh` writes a path nothing reads) is the standing example: until
  it lands, a failing Android Run does not disprove your fix, and #13 (iOS CI
  executes zero tests) makes every iOS PR green for free.

Then: groups touching disjoint files run **in parallel**. Groups needing a
design decision go last, on their own.

Apply any dependency stated in a body ("related to", "root cause of", "see
#NNN") as a hard edge — #3 and #11 both lean on #9's per-run isolation, and #4
is entangled with #14. A satisfied edge (target already closed) is not an edge.

## 8. Name the long pole

Say explicitly which group gates the release, and whether the remaining groups
are shippable without it. If they are, say so in one sentence — it is the most
actionable thing in the whole plan, because it converts a blocked release into
a scoping decision the user can make.

## Output format

For each group: a letter, the issue numbers, the files, and one sentence on why
they are one PR. Then the order, then the caveats.

```text
A — #1 + #2 · iOS Scheme execution silently dead   (land first: signal)
    files: iosApp/KaappiStudio/Resources/webview/bridge.js:12, :52
    <one sentence: why one PR>

B — #3 + #9 · Android editor state and run lifecycle
    files: app/src/main/java/.../EditorScreen.kt:68, MainActivity.kt:72
```

Follow with the order as a short diagram:

```text
A  →  B, C, D  (parallel, disjoint files)  →  E  (design-first)
```

Close with: anything that no longer reproduces (from step 3), any repo
constraint a group would blow (step 6), and the long-pole sentence (step 8).

If the user wants to launch concurrent sessions rather than review a plan, also
emit the group leaders as bare paste-able lines, one wave per line, containing
nothing else:

```text
Wave 1: NNN, NNN
Wave 2: NNN, NNN, NNN
```

Waves come from the order in step 7 — every group in a wave touches files no
other group in that wave touches, so their PRs can merge in any order.

## 9. Working the PRs — one git worktree per group

The plan is the deliverable. But when the user says to *start* a wave — "start
wave 1", "launch these", "go" — each group becomes a PR, and **every PR is built
on its own git worktree**. Never work two groups in the shared checkout, and
never commit a group's fix onto `main`: a worktree gives each session an
isolated copy of the repo on its own branch, so concurrent sessions cannot
collide on files or on the working tree, and a stalled or abandoned group leaves
nothing to clean up in the others.

Every branch is cut from a **freshly-fetched `origin/main`**, not from whatever
the local checkout happens to sit at — the checkout you are running in can be
behind (a session branched from a stale local `main` builds on old code and
rebases painfully later, or conflicts on a file the tip already changed). So the
first act of any session is `git fetch origin`, and the branch is `origin/main`.

Launch one session per group in the wave, each in a worktree:

- **Preferred: the `Agent` tool, one call per group, all groups of a wave in a
  single message so they run concurrently.** Each session works in its own
  worktree (`git worktree add ../wt-<group> -b <branch> origin/main` — the
  session can create it, after `git fetch origin`). The agent starts fresh, so
  the prompt must be self-contained — name the issue numbers, tell it to
  `gh issue view` each body in full (the plan's file/line claims are stale
  often enough to re-check), and state the deliverable below.
- **Or, driving it yourself:** `git fetch origin` then
  `git worktree add ../wt-<group> -b <branch> origin/main`, do the work there,
  and `git worktree remove` when the PR is up.

Give every session the same wrap-up contract, and hold it to finishing — a
session that stops with the fix uncommitted has produced nothing:

- Branch from the freshly-fetched `origin/main` (above); implement the fix **and
  extend/adjust the relevant unit tests** (AGENTS.md test layout: JVM-only —
  `shared/src/commonTest/` runs via `:shared:testAndroidHostTest`,
  `app/src/test/` runs via `./gradlew test`; Compose UI is untested and needs
  no instrumentation tests from a bug-fix session).
- Commit with a short imperative subject ("Add regression test for …"); never
  bump versions or touch release files (step 6).
- Push the branch and open the PR with `gh pr create`. The body repeats the
  closing keyword **per issue** (`Closes #NNN` on its own line for each —
  "Closes #A, #B" closes only #A).

Two operational gotchas, both of which cost a session in practice:

- **Run Gradle checks in the foreground**, as one blocking call with a long
  timeout — `./gradlew assembleDebug test :shared:testAndroidHostTest
  :app:detekt` — never backgrounded waiting on a completion notification the
  session will not receive. A session that backgrounds its tests and stops "to
  wait" stalls indefinitely.
- **Concurrent Gradle builds serialize on the project lock.** A second
  worktree's `./gradlew` blocks until the first build finishes and looks hung
  for minutes — it is not. Do not interrupt it, and do not run your own build
  against the shared checkout while wave sessions are building.

After a wave lands, verify the merge base rather than trusting each session's
"tests pass": a green run inside one worktree does not prove `main` is green,
and a *pre-existing* red (a flaky or already-broken test on `main`) will be
reported by every session as if it were theirs — confirm it against a clean
checkout before treating it as a wave regression. A real pre-existing red is
itself a signal-before-work item (step 7) for the next wave. Two known limits
of the safety net, both per AGENTS.md: Compose UI (`ui/screens`, `ui/theme`)
has no tests, so UI regressions will not surface in any session's green run;
and CI-built iOS artifacts never contain `kaappi.wasm`, so iOS CI green proves
buildability, not runnability.
