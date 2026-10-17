# Gallery

A fast, private, on-device photo gallery for Android that recreates the look and feel of **Samsung Gallery** — built natively with Kotlin and Jetpack Compose, and made for a Google Pixel.

> **Not affiliated with Samsung or Google.** Samsung Gallery is the design and feature reference; this is an independent, from-scratch implementation. No Samsung code or assets are used.

---

## Why this exists

Google Photos is the default gallery on Pixel phones, and on the author's Pixel it has been slow and unreliable in day-to-day use. It is also built around the cloud, so simply browsing the photos stored on the phone is not what it is optimised for.

Samsung Gallery, on the other hand, does the basics very well: instant scrolling, a clean timeline grouped by day, pinch-to-zoom between detail levels, a fast scrubber, sensible albums and a quick viewer. It just doesn't run on a Pixel.

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
- [x] **Pinch-to-zoom** through six levels: 2, 3, 4 and 5 columns by day, then **month** view, then **year** view — headers and the scroll bubble follow the level
- [x] **Fast scroller**: appears while scrolling, drag it to jump anywhere in the library, shows the month (or year) under your finger
- [x] Video badge with duration, favorite heart on favorites
- [x] Live updates: new camera photos, screenshots and downloads appear without restarting

**Albums**
- [x] Albums tab with device folders (cover + item count), **Camera pinned first**
- [x] Album detail screen with the same timeline, zoom and fast scroller, scoped to one folder
- [x] **Favorites** album and **Recycle bin**, shown only when they contain something

**Search**
- [x] Search button on the Pictures tab opens a search screen with suggestion chips
- [x] Understands **file and folder names** (`whatsapp`, `screenshot`, `img_20`), **dates** (`today`, `yesterday`, `2025`, `october`, `october 2025`, `oct 15`), **types** (`videos`, `photos`) and `favorites` — all combinable, e.g. `whatsapp videos 2025`
- [x] Results use the same timeline, zoom, selection and viewer (swiping stays inside the results)

**Viewer**
- [x] Full-screen viewer with swipe between photos (stays inside the album you opened it from)
- [x] **Pinch / double-tap zoom** with panning that hands back to the pager at the edges
- [x] Instant system thumbnail with the sharp image fading in on top
- [x] **Video playback** (Media3 / ExoPlayer) with standard controls
- [x] Tap to hide/show the interface and system bars
- [x] **Details panel**: file name, date, folder, resolution, size, duration, camera, aperture, shutter speed, ISO, focal length and GPS (from EXIF)
- [x] Share, favorite and move to trash

**Locked folder**
- [x] **Move photos and videos into a private Locked folder** that other apps — including other galleries and the system photo picker — cannot see
- [x] Opens only after **fingerprint / face / screen lock**, and re-locks when you leave it or the app goes to the background
- [x] Hidden from screenshots, screen recording and the recent-apps preview while it is open
- [x] Same timeline, zoom, viewer and selection as everywhere else; **Unlock** puts items back into the gallery, **Delete** removes them for good
- [x] Never included in backups

**Selection and actions**
- [x] Long-press to select, tap to toggle, **Select all**, Back to exit
- [x] **Share** several items at once, **favorite / unfavorite**, **move to trash**
- [x] **Recycle bin**: restore, delete forever, empty bin (Android keeps trashed items for 30 days)

**Foundation**
- [x] Clean architecture + MVVM, Hilt dependency injection, Navigation Compose
- [x] Photo/video permission flow (including "open settings" when permanently denied)
- [x] Material 3 with dynamic color, edge-to-edge, light and dark themes
- [x] Unit tests for grouping, zoom levels, grid layout mapping and selection

### Still to do

**Next up**
- [ ] **Search by place** (needs reverse geocoding) and by content (see ML below)
- [ ] **Create, rename, move and copy** albums and photos (needs the same system write confirmations as trash)
- [ ] **Stories** tab — automatic collections by date and place, plus slideshow *(the tab exists as a placeholder)*
- [ ] **Basic photo editor** — crop, rotate, filters, brightness / contrast and other adjustments
- [ ] **Map view** of geotagged photos

**Later**
- [ ] On-device ML (ML Kit / TFLite): search by what is *in* a photo, text-in-image search and copy, **people / face groups**, auto scene albums
- [ ] Video editor: trim, speed and basic effects
- [ ] Duplicate finder (perceptual hashing)
- [ ] Motion photo playback, set as wallpaper, slideshow
- [ ] A local **Room index** — gives exact "date taken, else modified" ordering and powers search and ML (see limitations)
- [ ] Shared-element transition from grid to viewer, and scrolling the grid to the photo you were just viewing
- [ ] Tiled full-resolution zoom for very large photos
- [ ] Remember zoom level and a small settings screen (DataStore)
- [ ] Baseline Profiles and further performance work

**Out of scope**
- Samsung-exclusive AI features (Generative Edit, Object Eraser, Remaster, Portrait Studio) rely on proprietary models.
- Cloud sync / backup. The app is deliberately offline; backing up can be added later through a service you choose.
- iOS. The whole point is to use Android's MediaStore, trash and scoped-storage APIs directly, which have no iOS equivalent.

---

## How it works (performance notes)

A gallery lives or dies on scrolling speed, so these are the decisions that matter:

| Concern | Approach |
| --- | --- |
| Large libraries | Offset/limit queries against **MediaStore** feed **Paging 3** with placeholders, so the grid knows its full size immediately and only loads what is on screen. |
| Jumping around | Because placeholders cover every item, the fast scroller and zoom can jump to any position instantly. |
| Day / month / year headers | One lightweight query reads only `date_added` for every item and groups it by day in memory (grouping 50,000 photos takes about 40 ms in a JVM benchmark); days are merged into months or years when zoomed out. Header positions are computed, not stored. |
| Thumbnails | Grid thumbnails come from Android's pre-generated **system thumbnails** (`ContentResolver.loadThumbnail`) through a custom Coil fetcher, instead of decoding full 12 MP+ photos. Large requests (the viewer) fall back to real decoding. |
| Live updates | A `ContentObserver` on MediaProvider invalidates the paging source, plus a cheap MediaStore *generation* check when the app resumes. |
| Changing files | Trash, favorite and delete go through `MediaStore.create*Request`, which shows the system confirmation Android requires for files owned by other apps. Large selections are split into batches of 500. |

---

## Architecture

The app follows **clean architecture** with **MVVM** in the presentation layer. Dependencies point inward: `presentation → domain ← data`.

```
┌──────────────────────────── presentation ────────────────────────────┐
│  Compose screens  ←→  ViewModels (state as StateFlow / PagingData)   │
└───────────────────────────────┬──────────────────────────────────────┘
                                │ use cases
┌───────────────────────────────▼──────────────────────────────────────┐
│ domain      models · repository interfaces · use cases · grouping    │
└───────────────────────────────▲──────────────────────────────────────┘
                                │ implements
┌───────────────────────────────┴──────────────────────────────────────┐
│ data   MediaStore data source · paging source · EXIF · Coil fetcher  │
└──────────────────────────────────────────────────────────────────────┘
```

- **Domain** is plain Kotlin apart from two pragmatic exceptions: AndroidX Paging types in the repository interface, and `WriteRequest`, an opaque wrapper around the system confirmation `IntentSender`. Grouping into days, months and years lives here and is unit-tested.
- **Data** talks to Android: MediaStore queries, `ContentObserver`s, EXIF, write requests and thumbnails.
- **Presentation** is Jetpack Compose. Each screen has a Hilt `ViewModel` exposing `StateFlow`s and `PagingData`; the Pictures tab and every album share one `TimelineGrid` composable.

### Project structure

```
app/src/main/java/com/femi/gallery/
├── core/permission/          Media permission list and checker
├── di/                       Hilt modules (dispatchers, repository binding)
├── domain/
│   ├── model/                MediaItem, Album, MediaDetails, TimelineSection, TimelineGrouper …
│   ├── repository/           MediaRepository (interface)
│   └── usecase/              GetPagedMedia, ObserveTimeline, ObserveAlbums, Request{Trash,Favorite,Delete} …
├── data/
│   ├── mediastore/           MediaStoreDataSource, MediaPagingSource
│   ├── repository/           MediaRepositoryImpl
│   ├── exif/                 ExifReader
│   └── image/                MediaThumbnailFetcher (Coil)
├── presentation/
│   ├── pictures/             Timeline grid, zoom levels, fast scroller, day labels
│   ├── albums/               Albums list and album detail
│   ├── viewer/               Pager, zoomable image, video player, details sheet
│   ├── selection/            Selection state, actions, system write confirmations
│   ├── navigation/           Routes, destinations, NavHost
│   ├── permission/           Permission screen and ViewModel
│   └── common/               Shared helpers (share sheet, chrome state, activity lookup)
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
| Async | Kotlin Coroutines / Flow | 1.10.2 |

**Targets:** `minSdk 31` (Android 12), `targetSdk 37`.

> Note: AGP 9 bundles an older Kotlin, so the root `build.gradle.kts` overrides the Kotlin Gradle Plugin version through its `buildscript` classpath. Keep that version in sync with `kotlin` in `gradle/libs.versions.toml`.

---

## Getting started

**Requirements**
- Android Studio (recent stable, with AGP 9 support) and a JDK that it bundles
- A device or emulator running **Android 12 (API 31) or newer** with some photos on it

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

There is no internet permission. Nothing leaves the phone.

When you favorite, trash or permanently delete files, Android asks you to confirm in a system dialog — this is a platform rule for files created by other apps, not something the app can skip.

---

## Known limitations

- **Ordering uses the date a file was added, not the date it was taken.** MediaStore's `date_taken` is empty for many files (WhatsApp images, screenshots, downloads) and MediaProvider doesn't allow the expression needed for "date taken, else modified". For camera photos the two dates are the same; for photos copied onto the phone later they can differ. A local Room index will fix this.
- **Zoomed-in photos soften past roughly 2–3×**, because the viewer decodes up to 2160 px. Tiled decoding is on the to-do list.
- **Trashed photos may not open full-size in the viewer**; thumbnails always show. This depends on how a given Android version exposes trashed files.
- **System confirmation dialogs** appear for every favorite / trash / delete action, and very large selections show several in a row.
- **Zoom level is not remembered** between launches.
- **The Locked folder lives inside the app.** Uninstalling the app or clearing its data deletes everything in it, and it is excluded from backups (so it is also not copied to a new phone by cloud restore). Files are protected by Android's per-app storage and your screen lock, but not separately encrypted. Moving items in permanently deletes the originals from your gallery (after Android asks you to confirm).
- **Unlocking** restores a photo to its original folder when that is Pictures, DCIM or Movies; anything else (for example WhatsApp's private folder) goes to `Pictures/Restored` or `Movies/Restored`, and the date it appears under becomes the date it was restored.
- **The Locked folder needs a phone screen lock** (PIN, pattern, password, fingerprint or face).
- Tested on one device (Pixel 7, Android 17). Other devices and manufacturers' MediaStore quirks are untested.

---

## Development history

The app was built incrementally as a series of reviewable patches:

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
| 16 | README | This document |
| 17 | License | Released into the public domain (Unlicense) |
| 18 | Search | File/folder/date/type search with the shared timeline |
| 19 | Locked folder | Private, biometric-protected folder; lock, unlock, delete |

---

## License

This project is released into the **public domain** under [The Unlicense](LICENSE). You can copy, modify, publish, use, compile, sell or distribute it, in source or binary form, for any purpose, commercial or not, with no conditions — no attribution required.

The libraries it depends on (Jetpack Compose, Hilt, Coil, Media3 and others) are separate projects under their own licenses, mostly Apache 2.0. If you distribute a built app, those libraries' own notices still apply to the parts you ship.

## Disclaimer

This is an independent personal project. "Samsung" and "Samsung Gallery" are trademarks of Samsung Electronics; "Google", "Pixel" and "Google Photos" are trademarks of Google LLC. They are mentioned only to describe what this app is inspired by and what it replaces for its author.
