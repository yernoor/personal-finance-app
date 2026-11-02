# Releasing and the git workflow

## Branches
- `main` is always releasable. Do not commit to it directly.
- Work on a branch per change (`feat/…`, `fix/…`, `docs/…`, `chore/…`), push it, open a pull request into `main`.
  The **CI** workflow (`.github/workflows/ci.yml`) runs the unit tests and a debug build on every pull request.
- Commit messages: `type: subject`, one line (see the global style in CLAUDE.md).

## Cutting a release
1. In a branch: raise `versionCode` and `versionName` in `app/build.gradle.kts`, merge the PR.
2. Tag the merge commit and push the tag:
   ```bash
   git switch main && git pull
   git tag v0.2.0
   git push origin v0.2.0
   ```
3. The **Release** workflow (`.github/workflows/release.yml`) refuses a tag that differs from `versionName`, runs the tests,
   builds `assembleRelease` signed with your key, and attaches `money-tracker-v0.2.0.apk` to a GitHub release.

## One-time setup: the signing key (never in git)
```bash
export PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH   # the system `java` is only a stub; keytool ships with the project JDK
mkdir -p ~/keys && keytool -genkeypair -v -keystore ~/keys/money-tracker-release.jks -alias release \
  -keyalg RSA -keysize 4096 -validity 36500
base64 -i ~/keys/money-tracker-release.jks | pbcopy        # the keystore as one line, for the secret below
keytool -list -v -keystore ~/keys/money-tracker-release.jks -alias release | grep SHA1
```
Keep a copy of the `.jks` and its passwords somewhere safe outside the repo: lose it and updates can no longer install over old versions.

GitHub → repo → Settings → Secrets and variables → Actions → New repository secret:

| Secret | Value |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | the base64 text copied above |
| `RELEASE_KEYSTORE_PASSWORD` | the keystore password |
| `RELEASE_KEY_ALIAS` | `release` |
| `RELEASE_KEY_PASSWORD` | the key password |

Build a signed APK locally with the same names exported as environment variables (`RELEASE_KEYSTORE_FILE` is the path):
`./gradlew assembleRelease`. Without them the release build is unsigned.

## Things to know
- The release APK has a **different signing key** from the debug build: Android will not install one over the other, you must
  uninstall first, which deletes the app's local data. Re-import your `.mmbackup` afterwards (or sync from the sheet once M7 exists).
- Google sign-in needs a **second Android OAuth client** for the release SHA-1 (printed by the `keytool -list` line above), same
  package name `dev.yernur.moneytracker`: docs/GOOGLE_SETUP.md step 5.
- Not yet run: both workflows have never run on GitHub; the first run may need small fixes (SDK install step, action versions).
