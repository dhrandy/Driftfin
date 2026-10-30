# Phone release CI: how the phone app ships from this repo

The phone app (`phone/`) shares this repository with the TV app (`app/`) but has its own release
line, so phone work never prompts, renumbers, or rebuilds the TV app's releases.

## Release scheme

- TV: `build-N` releases from `.github/workflows/android.yml`, asset `jetflix-release.apk`.
  `releases/latest` always points at one of these, and the TV updater requires it to.
- Phone: `phone-build-N` **prereleases** from `.github/workflows/phone.yml`, asset
  `driftfin-phone-release.apk` (+ `.sha256`). Prerelease status keeps them out of
  `releases/latest`, which is load-bearing: the TV updater reads only that endpoint.
- The phone updater (`phone/.../update/Updater.kt`) lists recent releases, takes the highest
  `phone-build-N` tag, and requires the phone asset at its exact expected URL. TV `build-N` tags
  never match.
- Build numbers: each line numbers itself (max of releases and tags, plus one), so neither line
  skips or reuses a number.
- Phone notes live in `PHONE_RELEASE_NOTES.md` under `## phone-build-N` headings; the workflow
  refuses to build without a matching section, mirroring the TV gate.

## Signing

- Release phone APKs are signed by the same private CI keystore as the TV app
  (`JETFLIX_KEYSTORE_*` secrets). That key is not in the repo and stays that way.
- The checked-in `phone/keystore/debug.keystore` is a public test-only identity so every local
  debug APK installs over the last one. It must never sign a release.
- `UpdatePreflight` rejects a candidate signed by a different certificate than the installed app.
  Consequence: anyone running a debug-signed test build does exactly one manual install of the
  first release build; in-app updates work between release-signed builds from then on. Android
  correctly forbids "migrating" signatures. The release app is a different package
  (`tv.jetflix.phone` vs the debug build's `tv.jetflix.phone.debug`), so it installs alongside the
  test build - nothing has to be uninstalled, and the test build keeps its sign-in and data.

## Why the TV side cannot regress

- No TV file changes: the phone app is a separate Gradle module and a separate workflow.
- The TV updater selects its asset by exact name from `releases/latest`; phone prereleases are
  invisible to it.
- TV build numbering, tag rules, and the notes gate are untouched.
