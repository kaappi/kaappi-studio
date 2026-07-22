---
name: github-release
description: Create a GitHub release with a version tag. Bumps version strings, commits, tags, pushes, and creates a GitHub release with customer-facing notes. Use when the user asks to tag a release, create a GitHub release, or cut a version.
---

# GitHub Release

Create a tagged GitHub release. This is typically the first step before platform-specific builds.

## Workflow

### Step 1: Verify clean state

```bash
git status
git branch --show-current
```

Must be on `main` with a clean working tree.

### Step 2: Determine the next version

```bash
git describe --tags --abbrev=0 2>/dev/null || echo "No tags yet"
```

Ask the user for the version type (major/feature/bugfix) if not specified.

### Step 3: Bump versions

Update version strings in:

| File | Field |
|------|-------|
| `app/build.gradle.kts` | `versionName`, `versionCode` |
| `iosApp/project.yml` | `MARKETING_VERSION`, `CURRENT_PROJECT_VERSION` |
| `app/src/.../SettingsScreen.kt` | Version display string |
| `iosApp/.../SettingsView.swift` | Version display string |

### Step 4: Commit and tag

```bash
git add -A
git commit -m "Release v<version>"
git tag -a v<version> -m "v<version>"
git push origin main v<version>
```

### Step 5: Create GitHub release

Review commits since last tag for user-facing changes:

```bash
git log <previous-tag>..HEAD --oneline
```

Write customer-facing release notes (plain language, no code jargon):

```bash
gh release create v<version> \
  --title "v<version>" \
  --notes "$(cat <<'EOF'
## What's New

- <user-visible changes>

EOF
)"
```

### Step 6: Report

Provide the tag name and GitHub release URL.
