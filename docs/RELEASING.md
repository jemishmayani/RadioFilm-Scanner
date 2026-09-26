# Versions and releases

## Two numbers, both in `AndroidManifest.xml`

```xml
android:versionCode="6"      <!-- whole number, for Android -->
android:versionName="1.5"    <!-- what people see -->
```

- **versionCode** must go up by at least 1 for **every** APK you give to anyone. Android refuses to install an update whose versionCode is not higher than the installed one. Never reuse or lower it.
- **versionName** follows `MAJOR.MINOR.PATCH` from the next release on (for example `1.6.0`):
  - **PATCH** (1.6.0 → 1.6.1): bug fixes only.
  - **MINOR** (1.6.1 → 1.7.0): new features that don't break anything.
  - **MAJOR** (1.7.0 → 2.0.0): big redesigns or changes that break old data or habits.

## The signing key: the one rule you cannot break

Every version must be signed with **the same key**. If you sign with a different key, phones will refuse to update and users would have to uninstall first, losing their saved scans.

- Keep the `.jks` file **outside** the project folder, and keep two backups (for example an encrypted USB stick plus a password manager or private cloud folder).
- Never commit it. `.gitignore` already blocks `*.jks`, `*.keystore` and `signing.env`, but web uploads on github.com ignore `.gitignore`, so still keep the key out of the folder.
- Never change the package name `com.filmscan.app` either; to Android it would be a different app.

## Release checklist

1. **Record changes as you go** under `## [Unreleased]` in `CHANGELOG.md`.
2. **Bump the version** in `AndroidManifest.xml`: versionCode + 1, and the new versionName.
3. **Move the Unreleased notes** under a new heading, e.g. `## [1.6.0] (7) - 2026-10-15`.
4. **Run the tests and build:**
   ```bash
   ./test/run-tests.sh
   ./build.sh            # -> dist/RadioFilm-Scanner-v1.6.0.apk
   ```
5. **Install it over the previous version** on a real phone and check it updates without losing saved scans.
6. **Commit and tag:**
   ```bash
   git add -A
   git commit -m "Release v1.6.0"
   git tag -a v1.6.0 -m "RadioFilm Scanner 1.6.0"
   git push
   git push origin v1.6.0
   ```
7. **Publish on GitHub:** repository → Releases → Draft a new release → choose tag `v1.6.0` → paste that version's changelog → attach the APK from `dist/` → Publish.

## Day-to-day with git

```bash
git status                          # what changed
git add -A && git commit -m "Short description of the change"
git push                            # upload to GitHub
git log --oneline                   # history
git checkout v1.5                   # look at an old release (git switch main to come back)
```

For bigger experiments, work on a branch so `main` always builds:
```bash
git switch -c feature/ocr           # new branch
# ...work, commit...
git switch main && git merge feature/ocr
```

## Hotfix for an old release

If 1.6.0 is out and has a bug: fix it on `main`, bump to `1.6.1` with versionCode + 1, and release as above. Each release has its own tag, so you can always rebuild an old version with `git checkout <tag>`.
