# Changelog

All notable changes to RadioFilm Scanner. Newest first.
Format: [Keep a Changelog](https://keepachangelog.com). `versionCode` is shown in brackets.

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
