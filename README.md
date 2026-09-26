# RadioFilm Scanner

**A small, private, offline document scanner for Android, built around CT and MRI films.**

Photograph a film on a lightbox (or a report, a prescription, a whiteboard), and RadioFilm Scanner finds the edges, straightens the perspective, enhances it, and saves a clean full-resolution JPEG, PNG or PDF. No account, no cloud, no ads. The app does not even request internet access.

![Android 5.0+](https://img.shields.io/badge/Android-5.0%2B-3DDC84) ![APK size](https://img.shields.io/badge/APK-~280%20KB-blue) ![No internet permission](https://img.shields.io/badge/internet%20permission-none-success) ![Version](https://img.shields.io/badge/version-1.5-orange)

<!-- Screenshots: add images to docs/screenshots/ and uncomment, e.g.
<p>
  <img src="docs/screenshots/camera.png" width="24%">
  <img src="docs/screenshots/crop.png" width="24%">
  <img src="docs/screenshots/filters.png" width="24%">
  <img src="docs/screenshots/save.png" width="24%">
</p>
-->

## Why this exists

Microsoft Lens was the simple scanner many of us relied on for years. Microsoft retired it in 2026: it was pulled from the app stores on February 9, new scans stopped working after March 9, and users were pointed to OneDrive and the Microsoft 365 Copilot app instead.

RadioFilm Scanner is a replacement that does the one job well: a fast camera-to-file flow, strong edge detection on backlit films, filters made for X-ray and CT/MRI films, and files that stay on your phone.

## Features

### Capture
- **Full-resolution capture.** Uses the largest photo size the camera offers, including high-resolution modes, at JPEG quality 100 with high-quality noise reduction and sharpening.
- **Live edge detection** with an outline that tracks the film, plus optional **auto-capture** when the shot is steady.
- **Single or Batch mode.** Review each photo right away, or shoot a stack of films and review at the end.
- Tap to focus, flash off / auto / on / torch, framing grid.
- **Import** one or many photos from the gallery, or share images into the app from any other app.
- **Tablets** turn freely with a landscape camera layout; phones stay in portrait.

### Crop and edit (one full-screen editor)
- Opens in **crop mode** with the detected edges. Drag a corner or a whole edge; a magnifier shows the pixels under your finger, and handles **snap to the nearest film or paper edge**.
- **Perspective correction with true aspect ratio.** The real shape of the film is estimated from the camera geometry, so results are not squashed.
- **Thumbnail strip** to jump to any page, swipe to move between pages, **+** to add more pages, and **Rearrange** (drag and drop) when there are two or more.
- **11 filters:** No effects, Auto, Pro color, Grayscale, **X-ray enhance**, **X-ray detail**, Negative, High contrast, Brighten, Whiteboard, B&W document.
- Brightness, contrast and sharpness sliders, with **Apply to all** pages.
- Rotate, **split multi-slice films into panels** (up to 8 × 8, each saved as its own image), **hide patient details** with black boxes, and duplicate a page to crop a second area.

### Save and share
- **JPEG, PNG (lossless) or multi-page PDF**, always rendered from the full-resolution original.
- Images go to `Pictures/RadioFilm Scanner`, PDFs to `Download/RadioFilm Scanner`. Share directly from the same sheet.
- **Name suggestions** while saving and renaming: date pieces and date styles, recent names, and three customisable sets:
  - **Radiology:** modality, body part, technique (plain / contrast / …), side and view, material.
  - **Documents:** document type, people and places, details.
  - **General:** topics, your own tags, and smart words (weekday, time, month, page count, auto-incrementing counter).
- Choose a preferred format in Settings, or be asked every time.

### Saved scans
- Every saved batch is kept with a cover image: view it, share it again, rename it or delete it.
- **Edit a saved scan in its own workspace.** Its pages are never mixed with other scans, and saving again updates that same scan.

### Settings
- Save format, output size limit, JPEG quality.
- File naming: prefix, date style, time; fully customisable suggestion sets (switch words on or off, add your own).
- Accent colour (8 choices).
- **Export and import all settings** to a file, for backup or a new phone.
- Camera: auto-capture, edge snap, grid, shutter sound, and a viewfinder rotation override for unusual devices.

### Privacy
- No internet permission, no account, no analytics, no ads.
- Permissions: camera, plus storage on Android 9 and older (for saving to the gallery).

## Install

1. Download the latest `RadioFilm-Scanner-v*.apk` from [Releases](../../releases).
2. Open it on your phone and allow "Install unknown apps" for your browser or file manager when asked.
3. If Play Protect warns about an unknown developer, choose **Install anyway**.

Works on Android 5.0 and newer, on any processor (the APK contains no native code).

## Build from source

The project uses no Gradle: a single script calls the Android SDK tools directly, which keeps the APK around 280 KB.

**You need:**
- A JDK (17 or 21) with `javac` and `jar` on your PATH.
- The Android SDK with build-tools and one platform, for example:
  ```bash
  sdkmanager "build-tools;34.0.0" "platforms;android-34"
  ```
- Your signing key (see [docs/RELEASING.md](docs/RELEASING.md)).

**Build:**
```bash
cp signing.env.example signing.env   # then edit: key path, alias, passwords
./build.sh                           # -> dist/RadioFilm-Scanner-v<version>.apk
```
`build.sh` looks for the SDK in `ANDROID_HOME` (or `ANDROID_SDK_ROOT`). You can also set `BUILD_TOOLS` and `ANDROID_JAR` yourself. On Windows, run it from Git Bash or WSL.

Released APKs are built against the `android-23` platform; newer platforms also work, since the code targets API 34 behaviour but only calls APIs available on older Android versions.

## Tests

The image-processing core is plain Java and is tested on the desktop:

```bash
./test/run-tests.sh
```

It covers edge detection accuracy on synthetic film and document scenes, corner and edge snapping, aspect-ratio estimation, filters (including large-image band processing), and the viewfinder orientation maths for phones and tablets.

## Project structure

```
AndroidManifest.xml
build.sh                     build + sign without Gradle
src/com/filmscan/core/       pure-Java imaging: edge detection, snapping, perspective,
                             filters, PDF writer (desktop-testable)
src/com/filmscan/app/        Android app: camera, editor, rearrange, saved scans,
                             settings, name suggestions, export
res/                         vector icons, themes, animations, launcher icon
test/                        desktop tests
docs/RELEASING.md            versioning and release checklist
CHANGELOG.md                 what changed in each version
```

## Versions

See [CHANGELOG.md](CHANGELOG.md) for the history and [docs/RELEASING.md](docs/RELEASING.md) for how versions are numbered and released.

## Disclaimer

RadioFilm Scanner is a scanning and sharing tool, **not a medical device**. Scans are for records and communication; make diagnostic decisions from the original films or the original digital images.

## Credits

Icons: [Material Design Icons](https://pictogrammers.com/library/mdi/) by Pictogrammers, used under their free open-source license.

## License

See [LICENSE](LICENSE).
