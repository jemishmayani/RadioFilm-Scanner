# Changelog

All notable changes to RadioFilm Scanner. Newest first.
Format: [Keep a Changelog](https://keepachangelog.com). `versionCode` is shown in brackets.

## [1.7.0] (8) - 2026-09-26
Includes the 1.6.0 test build (7), which was not published separately.
### Added
- **DocScanner** identity: switch the app's name, launcher icon and save folders between RadioFilm Scanner (films, radiology) and DocScanner (everyday documents) in Settings > Appearance.
- Anonymise tools: black box, **blur** and **pixelate**.
- **Undo / Redo** for crop, rotate, filters, adjustments, split and anonymise; **Reset** in each tool and "Reset all edits" for a page.
- Pinch-to-zoom, two-finger pan and double-tap in crop mode; two-finger pan in the page view.
- "HOLD STEADY" prompt with a filling ring before auto-capture.
- Resume or discard an unfinished scan after the app was closed unexpectedly.
- Progress bars for export and import; estimated file size and free space on the save sheet; low-storage warning in the camera.
- Where files are saved: shown on the save sheet, after saving, in Saved scans and in Settings.
- About screen: story, privacy, links, check for updates (opens the latest release in the browser; the app still has no internet permission), and Buy me a coffee.
### Changed
- Targets Android 16 (API 36): edge-to-edge layout and the new back gesture system.
- Exports are built in strips, roughly halving peak memory; PDF pages go through temporary files.
- The camera no longer restarts when a tablet rotates, and the page outline glides smoothly instead of jumping.
### Fixed
- Viewfinder that was sometimes stretched: the preview's picture size is now restored whenever the view is resized.

## [1.5] (6) - 2026-09-25
### Fixed
- Stretched or sideways viewfinder on tablets whose natural orientation is landscape (e.g. Xiaomi Pad 6).
- Viewfinder that looked stretched for a moment on phones when opening or returning to the camera.
- App shown in a narrow portrait strip ("letterboxed") on tablets held in landscape.
### Added
- Tablets rotate freely, with a landscape camera layout (tools left, shutter right).
- Settings → Camera → Viewfinder rotation, a manual override for unusual devices.
### Changed
- Opted out of Android's camera-compatibility rotation; the app rotates its own preview.
- The editor keeps the current page and tool when the screen turns; exports lock rotation until done.
- The save sheet is a centred card on tablets.

## [1.4] (5) - 2026-09-25
### Added
- One full-screen editor from capture to save: thumbnail strip, "+" to add pages, Save in the top bar.
- Rearrange screen, offered only when a scan has two or more pages.
- Name suggestion sets (Radiology, Documents, General with smart words), customisable in Settings, with your own words.
- Suggestions when renaming saved scans.
- Accent colour setting (8 colours).
- Export and import of all settings.
### Changed
- Batch mode opens the editor in crop mode at the first new page.
- Save and Share are in one sheet; format tiles are icon-only.
### Removed
- Separate review and preview screens (merged into the editor).

## [1.3] (4) - 2026-09-25
### Added
- File name suggestions on the save sheet; file naming settings.
- Full Settings screen; preferred save format with "Ask every time".
- Preview before saving with drag-to-reorder.
- Smoother animations: finger-following page swipes, screen transitions, slide-up sheets.
### Fixed
- Editing a saved scan no longer merges it with other scans; each opens in its own workspace.

## [1.2] (3) - 2026-09-25
### Added
- Saved scans: saved batches move there and a new scan starts.
- Swipe between pages in the editor.
- "Apply to all" for filters and adjustments.
### Changed
- The editor opens in crop mode; cropping moved into the editor.
- Default filter is "No effects".

## [1.1] (2) - 2026-09-25
### Fixed
- Blank review screen: pages were hidden by a layout bug.
### Changed
- Several imported photos are cropped one after another.
- App renamed to RadioFilm Scanner.

## [1.0] (1) - 2026-09-25
### Added
- Full-resolution Camera2 capture, live edge detection, auto-capture, single and batch modes.
- Crop with magnifier and edge snapping; true-aspect perspective correction.
- 11 filters including X-ray enhance and X-ray detail; brightness, contrast, sharpness.
- Rotate, split multi-slice films into panels, hide patient details.
- Export to JPEG, PNG or PDF; share; gallery and share-to-app import.
