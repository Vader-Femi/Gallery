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

**Search**
- [x] Search button on the Pictures tab opens a search screen with suggestion chips
- [x] Understands **file and folder names** (`whatsapp`, `screenshot`, `img_20`), **dates** (`today`, `yesterday`, `2025`, `october`, `october 2025`, `oct 15`, `from:2026-10-01 to:2026-10-05`), **types** (`videos`, `photos`) and `favorites` — all combinable, e.g. `whatsapp videos 2025`
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
- [x] **Rotate** in 90° steps and **flip**
- [x] **Adjust** brightness, contrast, saturation and warmth with live preview
- [x] **Filters**: Vivid, Warm, Cool, Mono, Noir, Sepia, Fade and Dramatic, previewed on your photo
- [x] **Save copy** next to the original — the original is never touched

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
- [ ] **Map view** of geotagged photos
- [ ] **Better stories**: "On this day" memories, a title and cover you can change, highlight videos, music for the slideshow, slideshow from any album
- [ ] **Editor extras**: draw, text and stickers, straighten, sharpen/vignette, keep camera details (EXIF) and offer to replace the original
- [ ] **Search by place** (needs reverse geocoding, so it has to be an opt-in network feature)

**Later**
- [ ] On-device ML (ML Kit / TFLite): search by what is *in* a photo, text-in-image search and copy, **people / face groups**, auto scene albums
- [ ] Video editor: trim, speed and basic effects
- [ ] Duplicate finder (perceptual hashing)
- [ ] Motion photo playback, set as wallpaper
- [ ] A local **Room index** — speeds up opening the app on huge libraries and will power search, stories and ML
- [ ] Tiled full-resolution zoom for very large photos
- [ ] Remember zoom level and a small settings screen (DataStore); sort options; hide albums; delete empty albums
- [ ] Select a whole day by tapping its header; drag-to-select
- [ ] Optional encrypted backup of the Locked folder, and encryption of its files at rest
- [ ] Tablet / landscape layouts
- [ ] Baseline Profiles and further performance work

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
│   ├── edit/                 EditState, ColorMatrices, CropMath
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

The pure logic is covered by JVM unit tests (`app/src/test`): day/month/year grouping, zoom levels, grid index mapping, date ordering, selection, album folder rules, search parsing (including date ranges), story detection and titles, and the editor's color and crop math. Screens, MediaStore access and the Locked folder are checked by hand on a device.

---

## Known limitations

- **Photos are ordered by "date taken", falling back to the date they were added** when a file has no date taken. A photo with a wrong or missing date in the file itself appears where that date says.
- **Stories are based on dates only.** There are no place names or people, the thresholds (5 photos a day, 12 per story, 10 days) are fixed, and the slideshow is a simple crossfade without music.
- **Zoomed-in photos soften past roughly 2–3×**, because the viewer decodes up to 2160 px. Tiled decoding is on the to-do list.
- **Trashed photos may not open full-size in the viewer**; thumbnails always show. This depends on how a given Android version exposes trashed files.
- **System confirmation dialogs** appear for every favorite / trash / delete / move / rename action, and very large selections show several in a row.
- **Moving and renaming need Android's permission** to change files other apps created. New albums are created under `DCIM`. Android only allows videos under `DCIM` and `Movies` and photos under `DCIM` and `Pictures`, so a video moved into a `Pictures/...` album lands in the matching `Movies/...` folder. Renaming merges into an existing folder if one already has that name, and the camera folder and apps' private folders (like WhatsApp) cannot be renamed or used as destinations.
- **The editor saves a copy, never replaces the original,** and renders it from the original at up to 4096 px on the longest side (a 50 MP photo would not fit in memory). Copies are JPEG (PNG stays PNG); camera details (EXIF), location and HDR gain maps are not carried over, and the copy appears under the original's date taken. Rotating or flipping resets the crop box.
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

Between patches, pull requests #1–#6 tuned the timeline, albums, search, viewer, Locked folder and batch actions (smoother scrolling and thumbnails, album names by bucket id, search parsing and interaction fixes, viewer chrome and video layout, hardened Locked-folder restore, parallel batch operations).

---

## License

This project is released into the **public domain** under [The Unlicense](LICENSE). You can copy, modify, publish, use, compile, sell or distribute it, in source or binary form, for any purpose, commercial or not, with no conditions — no attribution required.

The libraries it depends on (Jetpack Compose, Hilt, Coil, Media3 and others) are separate projects under their own licenses, mostly Apache 2.0. If you distribute a built app, those libraries' own notices still apply to the parts you ship.

## Disclaimer

This is an independent personal project. "Samsung" and "Samsung Gallery" are trademarks of Samsung Electronics; "Google", "Pixel" and "Google Photos" are trademarks of Google LLC. They are mentioned only to describe what this app is inspired by and what it replaces for its author.
