# Release notes

Each build lists its real changes here. The CI release workflow copies the section
matching the build tag onto the GitHub release page and appends the standard
automated-build footer.

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
