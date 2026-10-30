# Driftfin Phone release notes

The phone app has its own release line: `phone-build-N` prereleases, separate from the TV app's
`build-N` releases. The phone's Check for updates reads these; the TV app never sees them.

## phone-build-1

- First CI release build of Driftfin Phone (alpha).
- Check for updates in Settings downloads and installs new phone builds in place.
- Carries the test-build work: Year and Rating sorts that cannot fall back to the server's default order, the search-box clear button, season/episode identity above Play/Resume, trailer auto-rotation, and the screen staying awake between episodes.
- One-time step: earlier test APKs were debug-signed, and Android does not allow a signature change on an installed app, so this first release build is installed by hand once. Updates after that come from the button.
