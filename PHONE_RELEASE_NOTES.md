# Driftfin Phone release notes

The phone app has its own release line: `phone-build-N` prereleases, separate from the TV app's
`build-N` releases. The phone's Check for updates reads these; the TV app never sees them.

## phone-build-12

Changes:
- Fix: finishing an episode no longer sends a second, late "stopped playing" report after the watched check. That stray report could make the server drop the watched check and bring back a halfway resume point - one more way a finished episode lost its place.

## phone-build-11

Changes:
- Foldable cover screen: on the Razr+ outer display the hero and the details header now fit the small screen instead of overflowing it.
- Updates: Settings shows a red dot when a new phone build is available, and you can check for an update right from Settings.
- Fix: resuming a show that plays through the server's transcode no longer loses your place. Your position is now saved against the real point in the episode, so picking it back up starts where you stopped.

## phone-build-10

Changes:
- Back from a title now keeps your place in the Movies list instead of jumping back to the top.
- An episode's cast on the details page no longer lists the whole series' cast: you see the episode's own guest stars, or no one.
- My Stuff: if your watchlist is empty but this phone still holds a list saved under another sign-in, the page now says so. Nothing in that list has been changed.
- Actor pages: changed how the page's grid reads content while it is loading, adopting the same guard used elsewhere in the app, after a crash appeared in automated testing.

## phone-build-9

Changes:
- Watchlist protection: your saved titles are never removed when the server answers with only part of the list. If the watchlist load fails, My Stuff now shows an error with a Retry instead of an empty list, and it tells you your saved titles are kept. This guards titles you still have; it does not bring back ones already gone.

## phone-build-8

Changes:
- New "Keep Continue Watching up to date" setting: Home quietly refreshes Continue Watching and Next Up about once a minute while Home is open, and when you come back to the app. Off: they update when you open Home.
- Actor pages: guest-star episode credits collapse into their series entry, and failures show an error with a retry.

## phone-build-7

Changes:
- Fix: the bottom tabs could get stuck: tapping Home or another tab could stay on Search, or bounce back to Search or a page opened from it. Switching tabs now closes Search and its pages, and tapping Home from a page opened straight off Home lands on Home.

## phone-build-6

Changes:
- New setting: "Exclude animated movies from Home banner" (Settings, off by default, same as the TV app). Movies tagged Animation are hidden from the Home hero; animated titles remain available in Continue Watching and browsing - only the Home banner is filtered.
- Possible fix for the keyboard covering the bottom navigation on Search: the app now resizes when the keyboard opens so the nav stays tappable. Verification with a real keyboard is still pending.

## phone-build-5

Changes:
- The phone Home is now a single combined feed: Continue Watching, Next Up, Recently Added Movies and Recently Added Episodes (each combining all your matching libraries), Favorites, then a More like row based on what you last watched.
- The Libraries card row and the separate per-library rows are gone - the combined Recently Added rows already cover every library, and the full libraries are always in the Shows and Movies tabs.
- Profiles still see only the libraries and titles they can access.

A matching TV app update is in the works.

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
