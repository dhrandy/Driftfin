# Driftfin Phone release notes

The phone app has its own release line: `phone-build-N` prereleases, separate from the TV app's
`build-N` releases. The phone's Check for updates reads these; the TV app never sees them.

## phone-build-4

Changes:
- Movies and episodes that play to the end now mark themselves watched, and a finished episode offers the next one. (Earlier completion batch, first time in a release.)
- Actors without a photo get a disc with their initials instead of a blank spot.
- Search covers people too: typing a name shows an Actors row above the title results, and tapping one opens that actor's page. The same box still finds movies and shows.
- The video player's controls match the TV app: a slim progress bar (red played, gray buffered) with the current and remaining time, tap or drag to seek, plus center play/pause with 10-second skips. Audio, subtitles, fill/fit and pinch zoom are all still there.
- The Search tab starts empty each time you open it instead of showing the last search.
- The Trailer button on a details page is a wide pill, easier to hit.
- Home has two new rows: your server's libraries (jump straight into one), and a Recommended row built from the server's per-account suggestions.
- Profiles see only their own libraries: a restricted profile's Home shows just the libraries it can access.
- Series pages show the show's status in the details line, "Continuing" or "Ended", same as the TV app.
- Fix: a Recommended row could show an episode if a server answered a scoped request with the wrong item type; mismatched items are now dropped.

## phone-build-3

Changes:
- Actor pages show the person's birth date under their name, with the death date when the server has one ("Born Jun 1, 1926 · Died Aug 4, 1962"). Dates come from the server; when it has none, nothing is invented.

## phone-build-2

- Fix: the Home banner's Trailer button appears now. The banner's movie fetch asks Jellyfin for each title's trailer links; a list response only includes them when asked, so the button never showed.
- New render check locks it in: the hero request must ask for trailer links, and the banner must show the clapperboard icon in its pixels.
- Fix: a Browse crash or stall that could happen when filters changed quickly. The poster grid now reads one fixed snapshot of the list per screen update; previously the grid could observe the list changing mid-layout.

## phone-build-1

- First CI release build of Driftfin Phone (alpha).
- Check for updates in Settings downloads and installs new phone builds in place.
- Carries the test-build work: Year and Rating sorts that cannot fall back to the server's default order, the search-box clear button, season/episode identity above Play/Resume, trailer auto-rotation, and the screen staying awake between episodes.
- One-time step: earlier test APKs were debug-signed, and Android does not allow a signature change on an installed app, so this first release build is installed by hand once. Updates after that come from the button.
