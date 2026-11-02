# Third-party notices for the browser app

## hls.js v1.7.4 (public/vendor/hls.min.js)

- Project: https://github.com/video-dev/hls.js
- License: Apache License 2.0 (https://github.com/video-dev/hls.js/blob/master/LICENSE)
- Why it is here: plays Jellyfin HLS transcodes in browsers without
  native HLS support (desktop Chrome, Firefox, Edge). Served as a
  static file, no build step, consistent with the rest of this app.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

## TVMaze API (season episode guest credits)

- Project: https://www.tvmaze.com / API docs: https://www.tvmaze.com/api
- Data license: CC BY-SA 4.0 (https://creativecommons.org/licenses/by-sa/4.0/)
- Why it is here: some seasons carry no cast on the Jellyfin server because the
  metadata provider splits the season into its own entry (Jury Duty season 2 =
  TVMaze's separate "Jury Duty Presents: Company Retreat"). When a season is
  verified episode-by-episode (number + title + air date), the season Cast row
  can show that season's TVMaze guest credits. Free public API, no account or
  key. Called server-side by src/server.js; credited in Settings.
