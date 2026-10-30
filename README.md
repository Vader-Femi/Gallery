# Gallery

A fast, private, on-device photo gallery for Android that recreates the look and feel of **Samsung Gallery** — built natively with Kotlin and Jetpack Compose, and made for a Google Pixel.

> **Not affiliated with Samsung or Google.** Samsung Gallery is the design and feature reference; this is an independent, from-scratch implementation. No Samsung code or assets are used.

---

## Why this exists

Google Photos is the default gallery on Pixel phones, and on the author's Pixel it has been slow and unreliable in day-to-day use. It is also built around the cloud, so simply browsing the photos stored on the phone is not what it is optimised for.

Samsung Gallery, on the other hand, does the basics very well: instant scrolling, a clean timeline grouped by day, pinch-to-zoom between detail levels, a fast scrubber, sensible albums, automatic stories and a quick viewer. It just doesn't run on a Pixel.

This project closes that gap. It is a **Samsung Gallery–style app for Pixel (and any recent Android phone)** that:

- reads photos and videos **directly from the device** (MediaStore) — no account, no cloud, no ads, no network permission;
- stays **fast on very large libraries** by paging, using system thumbnails and never loading the whole library into memory;
- **reflects changes immediately** — a photo taken with the camera shows up as soon as it is saved.

It is a personal project, developed and tested on a **Pixel 7 running Android 17**, and released to the public domain so anyone can use it for anything.

---

## Features

### Done

**Timeline (Pictures tab)**
- [x] Newest-first grid of all photos and videos with **day headers** (Today, Yesterday, `Sat, Oct 3`, `Dec 31, 2025`)
- [x] Photos are placed by **date taken**, falling back to the date they were added for files that have none (WhatsApp images, screenshots, downloads)
- [x] **Pinch-to-zoom** through six levels: 2, 3, 4 and 5 columns by day, then **month** view, then **year** view — headers and the scroll bubble follow the level
- [x] **Fast scroller**: appears while scrolling, drag it to jump anywhere in the library, shows the month (or year) under your finger
- [x] Video badge with duration, favorite heart on favorites
- [x] Live updates: new camera photos, screenshots and downloads appear without restarting

**Map**
- [x] Map button on the Pictures tab opens an **offline map**: land, borders and cities are drawn from data bundled in the app (Natural Earth, public domain), so the app still has no internet permission
- [x] Photos with a location are grouped into **bubbles with a cover photo and a count**; pinch, drag (the map **coasts on after a throw**) and double-tap to zoom (**animated**; plus + / − buttons), tap a bubble to open its photos (one photo opens the viewer)
- [x] A background scan reads where each photo was taken (EXIF, needs the "location in photos" permission), is incremental and resumable, and its results never leave the phone
- [x] `in:south,west,north,east` works in search too, so any area can be searched

**Search**
- [x] Search button on the Pictures tab opens a search screen with suggestion chips
- [x] Understands **file and folder names** (`whatsapp`, `screenshot`, `img_20`), **text written in photos** (on-device OCR), **colours** (`red`, `blue`, `dark`, also `navy`, `violet`, `beige`, ...), **objects and scenes** found by ML Kit's bundled image labeller (`dog`, `beach`, `food`, `car`, ...; everyday words like `puppy` or `dinner` work too), **place names** offline (`paris`, `sao paulo`; accents ignored), **dates** (`today`, `yesterday`, `2025`, `october`, `october 2025`, `oct 15`, `from:2026-10-01 to:2026-10-05`), **types** (`videos`, `photos`) and `favorites` — all combinable
- [x] Results use the same timeline, zoom, selection and viewer (swiping stays inside the results)

**Albums**
- [x] Albums tab with device folders (cover + item count), **Camera pinned first**
- [x] Album detail screen with the same timeline, zoom and fast scroller, scoped to one folder
- [x] **Move or copy** photos and videos to an album, or to a **new album** you name (in the selection bar's More menu)
- [x] **Rename an album** (long-press it in the Albums tab)
- [x] **Favorites** album and **Recycle bin**, shown only when they contain something
- [x] **Recycle bin**: restore, delete forever, empty bin (Android keeps trashed items for 30 days)

**Stories**
- [x] **Stories tab** finds events automatically: a busy day (5+ photos) or a run of busy days in a row (up to 10), with at least 12 photos in total
- [x] **"On this day" memories**: photos taken on today's month and day in previous years (3 or more that day), titled "On this day in YYYY", shown before the trips; a leap day is remembered on Feb 28 in other years, and a busy day that is already a story is not listed twice
- [x] **Story title and cover**: rename a story and pick any photo or video as its cover from the story's menu (stored in `story_edits.bin`, private to the app). The edit follows the story when a trip grows by a few days; an "On this day" memory never takes over the title of a longer trip
- [x] **Videos in stories**: a video count on the story card, and All / Pictures / Videos chips inside a story (shown when it has videos; the viewer opens exactly what the grid shows)
- [x] **Slideshow from any album** (play button in the album's top bar; not offered for the recycle bin or the Locked folder), plus **shuffle** and **speed** (slow, normal, fast) buttons that appear while the slideshow is paused. Shuffle covers the whole album (a loading indicator shows while it is prepared) and keeps its order until you turn it off and on again
- [x] **Duplicate finder** (Menu > Duplicates): finds photos and videos that are byte-for-byte the same, reading as little as possible (same size first, then three small pieces of each file, then the whole file only for the survivors). In each group the copy to keep is suggested (favorite, then a file under DCIM, then the earliest, then the shorter name); tap another to change it, and one button moves all other copies to the recycle bin through the normal system confirmation
- [x] **Index database** (`gallery.db`, Room): the text read from photos, labels, colours, locations and the duplicate finder's file fingerprints are kept in one database instead of separate files. A scan writes only the rows that changed, in one transaction (before, every checkpoint rewrote a whole file), and the duplicate search no longer reads files again after the app was closed. The old files are imported once and deleted, so nothing is scanned twice. The database is excluded from backups (it holds text from photos and locations)
- [x] **Library mirror** (the `media` table in the index database): a local copy of the library (one row per photo or video, with its URI, dates, size, album and favorite flag). The timeline order, the album list, the favorites, photo-only and video-only lists, date ranges (stories) and the rows of every page are read from it with SQL instead of from MediaStore row by row. It is kept in step through MediaStore's generation number: if the number has not changed since the last time, nothing is done at all; otherwise only the rows changed since then are read, and the item counts are compared so that deleted and trashed items are dropped. The first copy is made in the background while MediaStore keeps answering. Searches for words, places and map areas, and the recycle bin, still go to MediaStore
- [x] **Scans keep going in the background** (WorkManager): the places, text, colour and label scans carry on after you leave the app, and a daily job reads new photos even if you do not open it. They wait for the battery not to be low, and Settings can restrict them to while charging or turn them off. A job gives up after about eight minutes (the system allows ten) and is started again; the scans save their progress as they go, so it carries on from there. A scan you are watching in the app is never stopped by the job
- [x] Cards with a cover from the middle of the event, a date title (`Sat, Oct 3`, `Oct 30 – Nov 2`, `Dec 30, 2025 – Jan 2, 2026`) and the number of photos and videos
- [x] A story opens as a normal timeline (zoom, fast scroller, selection, viewer), covering exactly the days of the story
- [x] **Slideshow** of a story's photos: tap to pause, swipe to skip, X to close

**Viewer**
- [x] Full-screen viewer with swipe between photos (stays inside the album, search or story you opened it from)
- [x] **Pinch / double-tap zoom** with panning that hands back to the pager at the edges
- [x] Instant system thumbnail with the sharp image fading in on top
- [x] **Video playback** (Media3 / ExoPlayer) with standard controls
- [x] Tap to hide/show the interface and system bars
- [x] **Shared-element transition**: the tapped thumbnail grows into the viewer and the photo shrinks back into the grid; when you close the viewer after swiping, the grid scrolls to the photo you ended on (from Pictures, albums, search and stories)
- [x] **Details panel**: file name, date, folder, resolution, size, duration, camera, aperture, shutter speed, ISO, focal length and GPS (from EXIF)
- [x] Share, favorite, edit, lock and move to trash

**Photo editor**
- [x] **Edit** button in the viewer opens the editor (photos only)
- [x] **Crop** with free or fixed shapes (1:1, 4:3, 3:4, 16:9, 9:16, original), rule-of-thirds grid and draggable corners
- [x] **Rotate** in 90° steps, **straighten** (-45° to 45°) and **flip**
- [x] **Adjust** brightness, contrast, saturation, warmth, **sharpen**, **vignette** and **straighten** with live preview; straightening trims the empty corners automatically
- [x] **Filters**: Vivid, Warm, Cool, Mono, Noir, Sepia, Fade and Dramatic, previewed on your photo
- [x] **Save copy** next to the original with EXIF metadata preserved (date, camera, GPS, etc.) — the original is never touched

**Locked folder**
- [x] **Move photos and videos into a private Locked folder** that other apps — including other galleries and the system photo picker — cannot see
- [x] Opens only after **fingerprint / face / screen lock**, and re-locks when you leave it or the app goes to the background
- [x] Hidden from screenshots, screen recording and the recent-apps preview while it is open
- [x] Same timeline, zoom, viewer and selection as everywhere else; **Unlock** puts items back into the gallery, **Delete** removes them for good
- [x] Never included in backups

**Works with other apps**
- [x] **Open with**: photos and videos opened from Files, Chrome, a camera app or a chat show in a simple viewer (zoom, video playback) with an **Open Gallery** button — they don't need to be in your library
- [x] **Choose from Gallery**: apps that ask you to pick a photo or video (profile pictures, attachments, forms) can use this app as the picker, for one item or several, photos only or videos only
- [x] Registered as a gallery app, so Android offers it in "Open with" and gallery chooser dialogs (choose **Always** to make it your default)

**Selection and actions**
- [x] Long-press to select, tap to toggle, **Select all**, Back to exit
- [x] **Share** several items at once, **favorite / unfavorite**, **move to trash**
- [x] **More** menu: move to album, copy to album, lock

**Foundation**
- [x] Clean architecture + MVVM, Hilt dependency injection, Navigation Compose
- [x] Photo/video permission flow (including "open settings" when permanently denied)
- [x] Material 3 with dynamic color, edge-to-edge, light and dark themes
- [x] Unit tests for the pure logic (see [Testing](#testing))
- [x] Performance and hardening passes on the timeline, albums, search, viewer, Locked folder and batch actions

### Still to do

**Next up**
- [ ] **Map extras**: a map with streets (offline vector tiles you import once), a round-bubble morph into the viewer (fling, smooth zoom and the animated "show all" are done)
- [ ] **Slideshow music** (cannot be tested without a device), remembering the shuffle and speed choice between launches, a progress bar, videos in slideshows
- [ ] **Editor extras**: draw, text and stickers, and offer to replace the original (straighten, sharpen, vignette and keeping the camera details are done)
- [ ] **Describe a photo in your own words** ("a dog running on a beach"): needs a CLIP-style image/text model (about 100 MB) that is not in the app yet; the plumbing (`ImageEmbedder`, `clip.bin`, cosine search) exists but is switched off, so nothing is scanned and no search is affected

**Later**
- [ ] More on-device ML: copy text from a photo, **people / face groups**, auto scene albums
- [ ] Video editor: trim, speed and basic effects
- [ ] Find **similar** photos (not byte-identical: bursts, re-saved or resized copies) with perceptual hashing; exact duplicates are done
- [ ] Motion photo playback, set as wallpaper
- [ ] Tiled full-resolution zoom for very large photos
- [ ] Remember zoom level and a small settings screen (DataStore); sort options; hide albums; delete empty albums
- [ ] Select a whole day by tapping its header; drag-to-select
- [ ] Optional encrypted backup of the Locked folder, and encryption of its files at rest
- [ ] Tablet / landscape layouts
- [ ] Baseline Profiles: measured with `tools/startup_bench.sh` on a debug build, cold start was 958 ms with `verify` and 984 ms with `speed` compilation, so ahead-of-time compilation does not help there; repeat on a release build before deciding. Further jank work: watch the `Frames` log (`adb logcat -s Frames` also works on release builds)
- [ ] **Videos on the map**: read the location of videos (`MediaMetadataRetriever`, the ISO 6709 tag) and show them in the bubbles next to photos

**Out of scope**
- Samsung-exclusive AI features (Generative Edit, Object Eraser, Remaster, Portrait Studio) rely on proprietary models.
- Cloud sync / backup of the gallery. The app is deliberately offline; backing up can be added later through a service you choose.
- iOS. The whole point is to use Android's MediaStore, trash and scoped-storage APIs directly, which have no iOS equivalent.

---

## How it works (performance notes)

A gallery lives or dies on scrolling speed, so these are the decisions that matter:

| Concern | Approach |
| --- | --- |
| Large libraries | The app reads two numbers per photo (its id and date) from **MediaStore**, sorts them, and **Paging 3** with placeholders loads only the rows of the page on screen. The grid knows its full size immediately. |
| Ordering | Photos are ordered by date taken, else date added. Android cannot sort by "one column, else another", so the sort happens in the app (a fraction of a second for 50,000 photos) and every screen — grid, headers, albums, search, stories, Locked folder — uses the same order. |
| Jumping around | Because placeholders cover every item, the fast scroller and zoom can jump to any position instantly. |
| Day / month / year headers | The sorted dates are grouped by day in memory; days are merged into months or years when zoomed out. Header positions are computed, not stored. |
| Stories | Detected from the same day groups (busy days and runs of busy days), so they cost one extra pass over data that is already in memory plus one query for the covers. A story's photos are loaded through the search machinery with a day range. |
| Thumbnails | Grid thumbnails come from Android's pre-generated **system thumbnails** (`ContentResolver.loadThumbnail`) through a custom Coil fetcher, instead of decoding full 12 MP+ photos. Large requests (the viewer) fall back to real decoding. |
| Live updates | A `ContentObserver` on MediaProvider invalidates the paging sources, plus a cheap MediaStore *generation* check when the app resumes. |
| Changing files | Trash, favorite, delete, move and rename go through `MediaStore.create*Request`, which shows the system confirmation Android requires for files owned by other apps. Large selections are split into batches of 500. |

---

## Architecture

The app follows **clean architecture** with **MVVM** in the presentation layer. Dependencies point inward: `presentation → domain ← data`.

```
┌──────────────────────────── presentation ────────────────────────────┐
│  Compose screens  ←→  ViewModels (state as StateFlow / PagingData)   │
└───────────────────────────────┬──────────────────────────────────────┘
                                │ use cases
┌───────────────────────────────▼──────────────────────────────────────┐
│ domain  models · repository interfaces · use cases · pure logic      │
│         (grouping, ordering, stories, search parsing, edit math)     │
└───────────────────────────────▲──────────────────────────────────────┘
                                │ implements
┌───────────────────────────────┴──────────────────────────────────────┐
│ data   MediaStore · paging · EXIF · Locked folder · bitmap editor    │
└──────────────────────────────────────────────────────────────────────┘
```

- **Domain** is plain Kotlin apart from a few pragmatic exceptions: AndroidX Paging types and `Bitmap` in repository interfaces, and `WriteRequest`, an opaque wrapper around the system confirmation `IntentSender`. Almost all the interesting logic (grouping, ordering, story detection, search parsing, album folder rules, crop and color math) lives here and is unit-tested.
- **Data** talks to Android: MediaStore queries, `ContentObserver`s, EXIF, write requests, thumbnails, the private Locked folder and bitmap editing.
- **Presentation** is Jetpack Compose. Each screen has a Hilt `ViewModel` exposing `StateFlow`s and `PagingData`; the Pictures tab, albums, search results and stories share one `TimelineGrid` composable.

### Project structure

```
app/src/main/java/com/femi/gallery/
├── core/
│   ├── permission/           Media permission list and checker
│   └── security/             Locked-folder session (who has unlocked it)
├── di/                       Hilt modules (dispatchers, repository bindings)
├── domain/
│   ├── model/                MediaItem, Album, Story, StoryDetector, TimelineGrouper, MediaOrdering, AlbumPaths …
│   ├── search/               SearchCriteria, SearchQueryParser
│   ├── edit/                 EditState, ColorMatrices, CropMath, StraightenMath, Sharpen, Vignette
│   ├── repository/           MediaRepository, EditRepository (interfaces)
│   └── usecase/              Paged media, timeline, albums, stories, selection actions, lock, edit …
├── data/
│   ├── mediastore/           MediaStoreDataSource, MediaPagingSource
│   ├── repository/           MediaRepositoryImpl
│   ├── locked/               LockedStore, LockedPagingSource
│   ├── edit/                 BitmapEditor
│   ├── exif/                 ExifReader
│   └── image/                MediaThumbnailFetcher (Coil)
├── presentation/
│   ├── pictures/             Timeline grid, zoom levels, fast scroller, day labels
│   ├── search/               Search screen and ViewModel
│   ├── albums/               Albums list and album detail
│   ├── stories/              Stories list, story detail, slideshow, story titles
│   ├── viewer/               Pager, zoomable image, video player, details sheet, external viewer
│   ├── launch/               How the app was opened (launcher, "Open with", picker)
│   ├── picker/               Photo picker for other apps
│   ├── editor/               Editor screen, crop overlay, ViewModel
│   ├── locked/               Biometric prompt and the lock gate
│   ├── selection/            Selection state, actions, album picker, system confirmations
│   ├── navigation/           Routes, destinations, NavHost
│   ├── permission/           Permission screen and ViewModel
│   └── common/               Shared helpers (share sheet, chrome state, immersive mode …)
└── ui/theme/                 Material 3 theme
```

---

## Tech stack

| Area | Library | Version |
| --- | --- | --- |
| Language | Kotlin | 2.4.10 |
| Build | Android Gradle Plugin / Gradle | 9.4.1 / 9.6.0 |
| UI | Jetpack Compose (BOM), Material 3 | 2026.02.01 |
| Navigation | Navigation Compose | 2.9.5 |
| Lifecycle / ViewModel | AndroidX Lifecycle | 2.9.4 |
| DI | Hilt (with KSP 2.3.12) | 2.60.1 |
| Paging | Paging 3 (+ Compose) | 3.3.6 |
| Images | Coil 3 (+ video frames) | 3.6.3 |
| Video | Media3 ExoPlayer + UI | 1.11.1 |
| EXIF | AndroidX ExifInterface | 1.4.1 |
| Biometrics | AndroidX Biometric, Fragment | 1.1.0 / 1.8.6 |
| Async | Kotlin Coroutines / Flow | 1.10.2 |

**Targets:** `minSdk 31` (Android 12), `targetSdk 37`.

> Note: AGP 9 bundles an older Kotlin, so the root `build.gradle.kts` overrides the Kotlin Gradle Plugin version through its `buildscript` classpath. Keep that version in sync with `kotlin` in `gradle/libs.versions.toml`.

---

## Getting started

**Requirements**
- Android Studio (recent stable, with AGP 9 support) and a JDK that it bundles
- A device or emulator running **Android 12 (API 31) or newer** with some photos on it (a screen lock is needed to use the Locked folder)

**Run it**
```bash
git clone https://github.com/Vader-Femi/Gallery.git
cd Gallery
./gradlew installDebug        # or press Run in Android Studio
```

**Run the unit tests**
```bash
./gradlew testDebugUnitTest
```

**Judging performance:** debug builds of Compose apps are noticeably slower than release builds. The `release` build type is signed with the debug key for local testing, so you can pick the **release** build variant in Android Studio and run it on your phone to see real-world speed.

### Permissions

| Permission | Why |
| --- | --- |
| `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` (Android 13+) / `READ_EXTERNAL_STORAGE` (Android 12, up to API 32) | Read your photos and videos. Grant **"Allow all"**; limited access only shows the photos you pick. |
| `ACCESS_MEDIA_LOCATION` | Optional. Without it Android strips GPS data from photos, so the Details panel can't show a location. |
| Biometric / fingerprint | Added automatically by the biometric library; used only for the system prompt that unlocks the Locked folder. |

There is no internet permission. Nothing leaves the phone.

When you favorite, trash, delete, move or rename files, Android asks you to confirm in a system dialog — this is a platform rule for files created by other apps, not something the app can skip.

---

## Testing

The pure logic is covered by JVM unit tests (`app/src/test`): day/month/year grouping, zoom levels, grid index mapping, date ordering, selection, album folder rules, search parsing (including date ranges), story detection and titles, and the editor's colour, crop, straighten, sharpen and vignette math, label, colour and place matching. Screens, MediaStore access and the Locked folder are checked by hand on a device.

---

## Known limitations

- **Photos are ordered by "date taken", falling back to the date they were added** when a file has no date taken. A photo with a wrong or missing date in the file itself appears where that date says.
- **Stories are based on dates only.** There are no place names or people, the thresholds (5 photos a day, 12 per story, 10 days) are fixed, and the slideshow is a simple crossfade without music.
- **Zoomed-in photos soften past roughly 2–3×**, because the viewer decodes up to 2160 px. Tiled decoding is on the to-do list.
- **Trashed photos may not open full-size in the viewer**; thumbnails always show. This depends on how a given Android version exposes trashed files.
- **System confirmation dialogs** appear for every favorite / trash / delete / move / rename action, and very large selections show several in a row.
- **Moving and renaming need Android's permission** to change files other apps created. New albums are created under `DCIM`. Android only allows videos under `DCIM` and `Movies` and photos under `DCIM` and `Pictures`, so a video moved into a `Pictures/...` album lands in the matching `Movies/...` folder. Renaming merges into an existing folder if one already has that name, and the camera folder and apps' private folders (like WhatsApp) cannot be renamed or used as destinations.
- **The editor saves a copy, never replaces the original,** and renders it from the original at up to 4096 px on the longest side (a 50 MP photo would not fit in memory). Copies are JPEG (PNG stays PNG); the date, location and camera details (EXIF) are carried over (not the orientation tag, since the pixels are already upright), HDR gain maps are not, and the copy appears under the original's date taken. Rotating, flipping or straightening resets the crop box.
- **The Locked folder lives inside the app.** Uninstalling the app or clearing its data deletes everything in it, and it is excluded from backups (so it is also not copied to a new phone by cloud restore). Files are protected by Android's per-app storage and your screen lock, but not separately encrypted. Moving items in permanently deletes the originals from your gallery (after Android asks you to confirm).
- **Unlocking** restores a photo to its original folder when that is Pictures, DCIM or Movies; anything else (for example WhatsApp's private folder) goes to `Pictures/Restored` or `Movies/Restored`, and the date it appears under is its date taken.
- **The Locked folder needs a phone screen lock** (PIN, pattern, password, fingerprint or face).
- **Photos opened from other apps are shown one at a time:** there is no swiping to neighbouring photos, editing or sharing from that viewer — press **Open Gallery** for the full app. The picker only offers the whole library, not albums or search.
- **Zoom level is not remembered** between launches.
- Tested on one device (Pixel 7, Android 17). Other devices and manufacturers' MediaStore quirks are untested.

---

## Development history

The app was built incrementally as a series of reviewable patches, applied one at a time:

| # | Patch | What it added |
| --- | --- | --- |
| 1 | Foundation | Hilt, navigation shell with three tabs, theme, media permission flow |
| 2 | Media data layer | MediaStore queries, paging, repository, Coil thumbnails |
| 3 | Kotlin version fix | Aligned the Kotlin compiler with AGP 9 and dependencies |
| 4–9 | Performance and correctness | System thumbnails, cached counts, live refresh, newest-first sorting fixes |
| 10 | Timeline | Day headers, pinch-to-zoom columns, fast scroller |
| 11 | Viewer | Pager, zoom, video playback, details sheet, share |
| 12 | Albums | Device folders and album detail |
| 13 | Selection | Multi-select, share, favorites, trash |
| 14 | Recycle bin and Favorites | Restore, delete forever, favorites album |
| 15 | Zoom levels | Month and year views |
| 16 | README | First full README |
| 17 | License | Released into the public domain (Unlicense) |
| 18 | Search | File/folder/date/type search with the shared timeline |
| 19 | Locked folder | Private, biometric-protected folder; lock, unlock, delete |
| 20 | Secure window fix | Screenshot protection now stays on when opening a locked photo |
| 21 | Album management | Move and copy to albums, new albums, rename album |
| 22 | Ordering fix | Sorted by date taken (else date added) so unlocked photos return to their real place |
| 23 | Photo editor | Crop, rotate, flip, brightness/contrast/saturation/warmth and filters; saves a copy |
| 25 | Stories | Automatic events, story screen, slideshow, `from:`/`to:` search ranges |
| 26 | README | This document, brought up to date |
| 27 | Default gallery | Open with, camera review and photo picker for other apps |
| 28 | Shared transition | Grid-to-viewer animation and scrolling back to the viewed photo |
| 29 | Viewer opens by id | The viewer and the grid now pass the photo's id, not just its position, so a fast scroll can no longer open (or scroll back to) a different photo |
| 30 | Paging fix | Fixed the real cause of wrong photos after a fast scroll: pages loaded backwards after a jump were placed 120 positions off until the next refresh |
| 31 | Map groundwork | Pure-Kotlin map core: Mercator projection and viewport maths, photo clustering, a compact file format for scanned locations, and an `in:south,west,north,east` search term for "photos taken here" (no screens yet) |
| 32 | Location scan | Background scan reads where photos were taken (EXIF, via `ACCESS_MEDIA_LOCATION`) into a private, backup-excluded file; `in:` search terms now filter by place |
| 33 | Map view | Offline map screen (land, borders, cities from bundled Natural Earth data) with photo bubbles, a "Photos here" screen, a scan progress / permission card, and scan diagnostics in Logcat (`LocationScan`, `MapData`) |
| 34 | Smoothness (1): measure | Frame monitor logs which screen drops frames and in which phase (`Frames` in Logcat); screen transitions shortened from the 700 ms library default to 120-180 ms; MediaStore change notifications are logged (`MediaChange`) |
| 35 | Smoothness (2): video | Videos open on their thumbnail instead of a black screen (texture-view player, built after the transition, paused first, shared transition like photos); the grid's thumbnail is reused as the viewer's first frame; frame log gains a `delay` phase |
| 36 | Viewer as an overlay | The photo viewer now floats above the live grid (its own one-screen navigation) instead of replacing it: groundwork for dragging a photo away with the grid visible behind it. Opening/closing looks the same; Back from the editor now returns to the grid |
| 37 | Smoother closing | Closing a photo or video no longer cuts the flight back to the grid short: the viewer's exit lasts 340 ms and the photo travels for exactly 300 ms (both ways) |
| 38 | Map photo + shared-photo log | Tapping a lone photo on the map opens it through the viewer overlay (it navigated to a screen that no longer exists); map bubbles are shared elements; the tag `SharedPhoto` logs why a photo is or is not travelling between grid and viewer |
| 39 | Media strip | A row of thumbnails along the bottom of the viewer, like Samsung Gallery's: the current photo sits in the middle with a white border; tap one to jump to it, or drag the strip to scrub through the photos; swiping the photos moves the strip |
| 40 | Back closes the viewer | On screens opened by navigating (albums, Recycle bin, stories, search, map...) Back popped the screen under the viewer instead of the viewer; the viewer's Back handler is now registered each time it opens, so it is asked first |
| 41 | Viewer gestures | Pull a photo down and it follows your finger, shrinking, over the live grid (the background and bars fade); let go past a point (or flick) and it flies into its cell, otherwise it springs back. Pull it up for the info sheet. The Back gesture drives the same motion. Zoomed photos still pan, swiping still changes photo |
| 42 | Close from the picture | The photo (or video) that flies between the grid and the viewer is now the picture itself, as in Google Photos, not the whole full-screen page with the picture inside it, so closing shrinks from the picture instead of from the bottom of the screen |
| 43 | Text in photos | Search now finds words written in photos (screenshots, receipts, signs, documents): ML Kit's bundled text recogniser reads every photo in the background (resumable, incremental, on the phone; results in a private file kept out of backups) and each typed word matches a file name, an album or the text in a photo. The app's INTERNET permission stays removed |
| 44 | Colour search | Search now finds photos by dominant colour ("red", "blue", "dark", ...): a background scan classifies each photo's thumbnail into color tags (resumable, incremental, stored in `colors.bin`, excluded from backups) with a pure-Kotlin colour classifier and unit tests |
| 45 | Object/scene labels | Search now finds photos by object and scene labels ("dog", "beach", "food", "car", "document", ...): a background scan indexes photo tags (resumable, incremental, stored in `labels.bin`, excluded from backups) with pure-Kotlin scene tagger and unit tests |
| 46 | Search by place name | Search now finds photos by offline place name ("paris", "london", "lagos", ...): matches each located photo to the nearest of the bundled ~1,250 cities (`places.bin`) via Haversine distance with unit tests |
| 47 | Semantic free-text search | Search now supports natural language semantic description matching via local image and text embeddings (`clip.bin`, resumable background scan, cosine similarity matching) with unit tests |
| 48 | "On this day" stories | Stories tab now automatically surfaces past memories taken on today's month and day in previous years, titled "On this day in YYYY", with unit tests |
| 49 | Editor extras | Photo editor gains straighten (-45° to 45°), sharpen, and vignette adjustments, plus automatic EXIF metadata preservation (date, GPS, camera model, etc.) when saving edited copies |
| 50 | EXIF fix | Edited copies keep the original's EXIF (rw file descriptor for `ExifInterface`) |
| 51 | Making 44–50 real | Semantic search was a placeholder (made-up vectors, so every search returned junk): switched off until a real model exists. Object labels are now real (ML Kit image labelling, synonyms, whole-word matching; the old guesses from file names are gone). Stricter colour thresholds and colour synonyms. Place search ignores accents and matches word starts. "On this day" no longer lists a day twice and handles Feb 29. Editor: sharpen and vignette now actually work (preview and save), straighten trims its empty corners, edited copies no longer get a double rotation from the copied EXIF orientation. Old index files are discarded and rebuilt |
| 52 | Story title, cover, videos | Rename a story and choose its cover (kept across a trip growing: exact days first, then mostly overlapping multi-day ranges; a single day only matches itself). Video count badge on story cards; All / Pictures / Videos filter inside a story. Unit tests for the matching, the file format, the video count and the filter-to-query round trip |
| 53 | Better slideshows | Play a slideshow from any album (route takes a `bucketId`), shuffle (seeded Fisher-Yates, covers the whole album) and slow/normal/fast speed, both changed while paused. Unit tests for the order and the speed cycle |
| 54 | Shuffle without black slides | A shuffled slideshow now loads and shuffles the whole list first (M3 Expressive `LoadingIndicator`), then plays from memory; before, random positions in a 4000+ item paged list were mostly not loaded yet and showed black slides. `tools/startup_bench.sh` measures cold start with `verify` vs `speed` compilation to decide whether a baseline profile is worth adding |
| 55 | Smoother viewer video and strip | One reusable `ExoPlayer` for the whole viewer visit, prepared only after the pager has rested on a video for 250 ms (before, one was built and released for every video the pager passed, and a second one was built by the video page itself before the first was ready). The position clock only runs while playing and is read where it is shown, so it no longer recomposes the whole bottom area every 100 ms. Media strip items no longer change key when they load, and only the two items whose selection changes recompose when the page changes |
| 56 | Full video player | New `FullVideoPlayer`: a full-screen player with the standard Media3 controls and sound, used for videos opened from other apps ("Open with"; before, that screen reused the viewer's video page, which has no controls, starts paused and needs a thumbnail and a known size, none of which an outside file has) and for the viewer's new **Play** action, which replaces the expanded seek bar button and carries on from the current position. Keeps its position across rotation and pauses when you leave the app. Tapping the selected video in the media strip still opens the expanded seek bar |
| 57 | Map fling and animated zoom | The map coasts after a throw (velocity from a `VelocityTracker`, exponential decay; touching the map stops it; no throw after a pinch), double-tap and the new + / − buttons zoom smoothly about a point, and "show all" flies there (`MapViewport.between`, unit tested). `MapMotion` runs one movement at a time and always changes the viewport from its latest value |
| 58 | Duplicate finder, strip review | New **Duplicates** screen (Menu). Pure `DuplicateFinder` (size, then pieces, then whole file; unreadable files left out; progress per phase) with a keep rule, 19 tests; `ContentFileHasher` (SHA-256, kept in memory so searching again is fast); trashing reuses `MediaSelectionController` and `WriteRequestHandler`. Review of the new video strip: the playback clock is read in a flow instead of while composing (it recomposed the strip every 100 ms), the expanded filmstrip has at most 40 tiles (it could compose 200+ images), following the pager is protected from a cancelled run clearing the flag of a newer one, and the viewer's player seeks to the nearest key frame so scrubbing is smooth |
| 59 | Room index, first half | Room 2.8 (`GalleryDatabase`, version 1): tables `scan_text`, `scan_label`, `scan_color`, `scan_location`, `file_hash`. The four scan stores keep their interfaces, so the scans themselves are unchanged; behind them `DiffingStore` keeps a copy of what the database holds and `RowDiff` (unit tested) works out which rows to write and which to delete. One-time import of `texts.bin`, `labels.bin`, `colors.bin` and `locations.bin`. `ContentFileHasher` keeps fingerprints in `file_hash` (valid while size and date added are unchanged; fingerprints of deleted files are dropped when searching). Backup rules now exclude the database and `labels.bin` / `colors.bin`. Schemas are exported to `app/schemas`: commit them |
| 60 | Background scans | `ScanWorker` (a `CoroutineWorker` that gets its dependencies through a Hilt entry point, so no custom worker factory or manifest change is needed) runs `BackgroundScanRunner`, which starts the four scans and waits for them (`ScanWaitPlan`, unit tested). `BackgroundScans` queues one job to run now and one daily, with battery-not-low (and charging, if chosen) as conditions. The four scan repositories gained `isRunning` and `stopScan()`. Two new switches in Settings. `AppForeground` tells the runner whether the app is on screen before it stops anything |
| 61 | Room index, second half: the library mirror | Database version 2 (an `AutoMigration` from the exported schema 1.json adds the `media` and `index_meta` tables; commit the new `2.json`). `MediaMirror` copies the library and keeps it up to date (`MirrorSync` decides: import, update or nothing, and works out which items are gone; unit tested), `MirrorQuery` says which requests the copy can answer (tested), `MediaDao` sorts, groups and filters in SQL. `MediaStoreDataSource.loadOrder`, `loadAlbums`, `loadByIds` and `loadAllItems` ask the mirror first and fall back to MediaStore if it is not ready, cannot be read, or lacks any id of a page. Date ranges are applied to the sorted list in Kotlin (`MediaOrdering.withinDateRanges`, tested); album order is `AlbumSorting` (tested) |
| 62 | Foreground scan notification, review fixes | Review of the foreground-service change (manifest, `setForeground` with `dataSync`, notification permission request and the checkpoint on cancellation all fine). Fixes: the progress text is a localized string with a real progress bar instead of English text in code; updates are throttled (`ProgressThrottle`, unit tested) instead of redrawn every second; after the system refuses a foreground start (Android 12+ only allows it while the app is visible, so a run started by the system in the background has no notification) the worker stops trying; `CancellationException` is no longer swallowed by the `setForeground` calls; tapping the notification brings the app back instead of restarting it |

Between patches, pull requests #1–#6 tuned the timeline, albums, search, viewer, Locked folder and batch actions (smoother scrolling and thumbnails, album names by bucket id, search parsing and interaction fixes, viewer chrome and video layout, hardened Locked-folder restore, parallel batch operations).

---

## License

This project is released into the **public domain** under [The Unlicense](LICENSE). You can copy, modify, publish, use, compile, sell or distribute it, in source or binary form, for any purpose, commercial or not, with no conditions — no attribution required.

The libraries it depends on (Jetpack Compose, Hilt, Coil, Media3 and others) are separate projects under their own licenses, mostly Apache 2.0. If you distribute a built app, those libraries' own notices still apply to the parts you ship.

## Disclaimer

This is an independent personal project. "Samsung" and "Samsung Gallery" are trademarks of Samsung Electronics; "Google", "Pixel" and "Google Photos" are trademarks of Google LLC. They are mentioned only to describe what this app is inspired by and what it replaces for its author.
