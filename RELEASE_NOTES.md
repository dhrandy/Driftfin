# Release notes

Each build lists its real changes here. The CI release workflow copies the section
matching the build tag onto the GitHub release page and appends the standard
automated-build footer.

## build-40

Changes:
- The Home banner no longer disappears when every unwatched, non-animated movie has been started but not finished: it now falls back to those started-but-unfinished movies instead of showing nothing. Watched movies and the animation option still apply.
- On a show's page, the Play/Resume button no longer repeats the episode number that the line right above it already shows; when that line is absent, the button still names the episode.
- On a show's page, the standalone year is no longer repeated next to the premiere date; the premiere date and the episode's air date stay.
- On the "Up next" card, Back now cancels the card as its own hint says, instead of leaving the player. The countdown number and the draining bar now run on the same clock, so they stay in step.
- Search now says when a search failed instead of staying quiet, and if the next episode can't be looked up when an episode ends, the player says so instead of ending silently.
- The startup screen is calmer: the DRIFTFIN logo on black with one slim dimmed row of posters along the bottom, replacing the angled poster wall.

## build-39

Changes:
- The "Up next" countdown card shown before the next episode starts is smaller, so it covers less of the picture. The wording, the 5-second countdown, and the OK and Back keys are unchanged.

## build-38

Changes:
- On a show's page, a line above the buttons now shows the episode that Play or Resume will start and when it aired, for example "S1:E3 · Aired Apr 21, 2023". The show's own premiere date stays where it was. If the server has no air date for that episode, the line is left out. Nothing else on the page changes.

## build-37

Changes:
- Settings now looks for an update when you open it, so a TV that has been running since before a release shows "Download" instead of "Check". Press OK on Download to download and verify the update and hand it to Android's installer; Android still asks you to confirm the install. If the look fails, the row stays on "Check".

## build-36

Changes:
- Movies and Shows have a new "Show" choice next to Sort and Filter: All, Kids only, or No kids. It works from your two libraries named "Kids Movies" and "Kids Shows", and saves your pick per tab. If a library with that name isn't found, it says so instead of showing everything.
- On a show's page, the backdrop picture now carries down behind the description and season buttons and fades out just below them. Picture size, text, buttons and the starting view are unchanged. Movies pages are unchanged.

## build-35

Changes:
- On a show's page, the backdrop picture now stays in place while the page scrolls down to the seasons and episodes, so you see the top of the picture instead of only its bottom edge. The starting view and layout are unchanged.
- Episode rows show the air date after the runtime, and a show's page shows "Premiered <date>" next to the year, when the server provides the date.

## build-34

Changes:
- The next-episode countdown is redesigned to match the player: a dark card with a faint outline and soft shadow, an "UP NEXT" label, the episode title in bold, a large countdown number, "OK plays now / Back cancels" on its own line, and a thicker bar that drains over the same 5 seconds. The countdown length and the OK and Back keys behave as before.

## build-33

Fixes:
- Search no longer opens just because focus rests on its icon. It opens only when you press OK on it or use the double-Left shortcut.
- Home without a banner now puts focus on the first row, and Up from the first row goes to the Home tab, so Down and Up keep working instead of landing on Search.
- Pressing OK on the Home tab you are already on clears leftover menu-sweep state first, so focus can return to the page.
- When Home reloads (for example after changing the animated-movie setting) and the banner request fails, the banner that was showing stays instead of disappearing. A banner request that succeeds with no eligible titles still shows no banner, and the empty banner area no longer leaves a gap.

## build-32

Fixes:
- The Skip Intro prompt is now a compact dark button with a gray outline and white text, about 40% of its former width, instead of a large white pill. Pressing OK checks the live playback position at that moment, so a press right as the intro starts is not dropped.
- Audio language and subtitle choices are remembered by language. New playback starts with English audio and subtitles Off unless you pick otherwise in the player panel; a pick is saved and reused for the next item and after restart. If the item has no English track, the player's default applies. For transcoded (HLS) playback the app asks the server for the preferred audio language and for no subtitle stream; whether the server honors that, and whether any subtitles are burned into the video, depends on the server and is not guaranteed.
- The screensaver shows the full description. A long description scrolls slowly after a short pause and the next slide waits until it has been read.
- Search headings read "Recommended Movies" and "Recommended Shows" with more space under the search bar.

Changes:
- Search also finds actors by name and shows them in an Actors row above the results. OK opens that actor's page.

## build-31

Fixes:
- Sign-in now uses this TV's own saved device ID from the first password or Quick Connect request, rather than a shared fallback. This prevents that shared-ID collision between TVs; it cannot restore a token already rejected by the server, so one new sign-in may be needed.
- Settings > Switch profile keeps the current session until another login succeeds. Back cancels pending sign-in work and returns to the previous screen. Explicit sign-out and forgetting a profile still sign out.
- The Home Trailer button now uses the clapperboard icon, matching Details.

Changes:
- Quick Connect sits directly below the server address, before profiles and password sign-in.
- Settings adds "Exclude animated movies from Home banner". When enabled, movies tagged Animation are excluded from that banner only; library rows stay unchanged. The choice is saved.
- Settings groups related controls under headings and uses rectangular panels with slightly softened corners. Check for updates remains first, with account actions pinned below the scrollable columns.

## build-30

Changes:
- Home fetches library rows in parallel, with at most four requests in flight, while preserving row order. This optimization passed controlled latency tests; real TV loading time has not been measured.
- The release workflow passes the build tag to the notes selector so it picks the matching section.

Fixes:
- Corrects build 29's release-note wording. Build 28 already displayed the corrected notes and My Stuff rename; the earlier claim that it repeated build 27's notes was wrong.

## build-29

New:
- Home fetches library rows in parallel, with at most four requests in flight, while preserving row order.

Fixes:
- The release workflow now passes the build tag to the notes selector so it picks the matching section.

## build-27

Fixes:
- The startup poster wall now stays up at least 3 seconds on the first load, so it no longer flashes away before you can see it.
- Details pages keep more of the banner's upper artwork in frame (crop adjustment toward the top of the art).

New:
- Settings uses two columns, so there's much less scrolling, and the two account buttons sit side by side at the bottom.
- Home banner shows the age rating and community score under the title, above the description. Settings: "Age rating and score on Home" turns it off.
- Search shows recommended movie and show rows before you type anything (your server's suggestions when it has them, otherwise top-rated unwatched). Settings: "Recommended rows on Search" turns it off.
- The My Driftfin tab is now called My Stuff.

## build-26

Fixes:
- Search now clears when you leave it and come back, instead of keeping your last query.
- The update Download button in Settings now starts the download on a single press.
- My Driftfin: pressing OK on an already-selected section no longer reloads the same list.
- Settings: the frame-timing test control is removed.

New:
- My Driftfin: Favorites, Watchlist and Collections switch as you move across them with the remote, like the top navigation tabs.
- Actor pages: read an actor's full biography with the remote - move Left onto the bio, press OK to open it as an overlay, Back to close it.
- Home banner adds a Trailer button alongside Play and More Info.
- Sign in with Quick Connect: approve a code from another device instead of typing your password on the TV.

## build-25

Fixes:
- Pressing Down from the Watchlist button on My Driftfin no longer gets stuck on the button; it moves on to the Sort row or into your list.

New:
- Watchlist has the same Sort and Filter controls as Favorites: sort the list, or filter by watched state, genre or year. It still opens in your starred order, newest first, until you pick a sort.

## build-24

New:
- Watchlist: star movies and shows to watch later. The star sits before the heart on a movie or show page - outline when not starred, gold when starred. Your Watchlist lives under My Driftfin between Favorites and Collections, newest first. It's your own list per profile on this device, separate from Jellyfin favorites. Settings can hide the star and section.
- Paused playback now shows the show's cast when the episode itself lists no actors.

## build-23

Fixes:
- Movie and show pages you reopen start at the top again instead of jumping down to the cast. Coming Back from an actor or the player still returns to your exact spot.
- While a video is paused, the progress bar and times stay bright and readable with the cast row showing.
- The Search icon stays visible when the Search tab is selected.
- The What's New card now shows each update's real changes.

Changes:
- The Home banner switches titles every 10 seconds.
- Home rows are now: Continue Watching, Next Up, Recently Added Movies, Recently Added Episodes, Favorites, then your libraries.

## build-22

Fixes:
- Search could still open from a single Left press at the far left edge right after scrolling that way. Now it always takes two separate presses at the edge, and holding Left never opens it.
- Back on Home now returns to the top bar with Home focused, so pressing Right moves along the tabs. Returning to Home from another section no longer lands on the hero Play button.
- On Movies and Shows, after pressing Back to jump to the tab bar (grid stays scrolled where you left it) and moving to another tab, pressing Down enters the grid again right where you were.

## build-21

Fixes:
- On Movies, Shows and My Driftfin, pressing Up out of the grid returns to the tab you came from instead of jumping to Search.
- Switching between tabs no longer drops the selection onto an old movie or show you scrolled to earlier; it stays on the tab until you press Down or OK.
- Search no longer pops up by accident when scrolling left: on Home, Shows, Movies and My Driftfin it takes a double press at the far left edge (holding Left just stops at the edge), and on details, actor and other pages Left never opens Search.
- Actor pages show the biography slightly smaller.

## build-20

Fixes:
- Top tabs: moving across Home, Shows, Movies, Search and Settings keeps the selection on the tab until you press Down or OK; it no longer drops onto a movie while you browse the tabs.
- Settings and Search open properly from the top bar again (Down or OK enters the page), and every Settings row can be reached.
- Smoother browsing: artwork for rows, grids and the next hero image is loaded in advance, before it scrolls into view.
- New poster wall while Home loads the first time. Turn it off in Settings ("Poster wall on startup").

## build-19

Fixes:
- Home: focus no longer gets trapped; returning from Home or the library no longer clips the top row.
- Hero banner now picks 6 random titles from your 50 most recent unwatched.
- Left edge of the screen opens Search; Back returns to the previous screen.
- Tabs activate when focused.
- Actor pages: bio and portrait fixes, Back no longer jumps the scroll position, and a person's page now lists their shows as well as their movies.
- Details: backdrop image centered in its box.
- Player: paused cast strip gets a dark bottom fade so photos and names stay readable on bright frames; leaving and re-entering the app no longer lands on the screensaver; finishing an episode while an actor page is open no longer closes the actor page; OK on a cast member opens only the actor page (a title opens on a second OK, not by itself); Back closing the cast strip no longer also exits the player.
- Trailers: YouTube captions off by default (CC); clapperboard trailer icon.
- Library: new 4K filter (Movies and Shows).
- Settings: "Check for updates" moved to the top; update availability shows a fading notice on startup; after an update, a "What's new" popup appears once per version.
- README: badges, Downloader install instructions, device coverage wording, screenshots.

## build-18

- Actor pages: tap a cast member to see their bio, portrait, and other movies in your library.
- Pause screen shows the cast; pick an actor while paused to open their page.
- Details shows HDR and Dolby Vision badges from your server's metadata for the title; whether they play in HDR depends on your TV.
- Profile picker fixes and title logo fixes.

## build-16

- (Notes not recorded at release time.)
