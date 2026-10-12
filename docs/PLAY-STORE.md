# Publishing RadioFilm Scanner on Google Play

Account: **Insula Neurosciences** (personal account). Package: `com.filmscan.app`.

## 0. Before you start

- **Signing.** On the first upload, Play Console offers *Play App Signing*. Keep the default: **Google generates and holds the app signing key**. Your own key (`filmscan-release.jks`) becomes the *upload key* and never leaves your hands. Back up the `.jks` file and its password somewhere safe; if it is lost, Google support can reset the upload key, but it takes time.
- **Play and GitHub copies cannot update each other**, because Google signs the Play copy with its own key. To switch between them, a user must uninstall first, which removes the scans kept inside the app (files already saved to Gallery or Downloads stay).
- **The privacy policy must be online** before you submit: `https://github.com/jemishmayani/RadioFilm-Scanner/blob/main/PRIVACY.md`

## 1. Create the app

Play Console, **Create app**:

| Field | Answer |
|---|---|
| App name | RadioFilm Scanner |
| Default language | English (United Kingdom) or English (United States) |
| App or game | App |
| Free or paid | Free (this cannot be changed to paid later) |
| Declarations | Tick both (Developer Programme Policies, US export laws) |

## 2. Store listing (Grow users > Store presence > Main store listing)

**App name** (max 30): `RadioFilm Scanner`

**Short description** (max 80):
```
Scan CT, MRI and X-ray films and documents. Private, offline, no account.
```

**Full description** (max 4000):
```
RadioFilm Scanner turns a phone photo of a CT, MRI or X-ray film into a clean, straight, full-resolution image or PDF. It also works well for reports, prescriptions, forms and whiteboards.

It was built by a neurosurgery resident as a simple replacement for the retired Microsoft Lens.

PRIVATE BY DESIGN
• No internet permission: the app cannot send anything anywhere
• No account, no cloud, no ads, no tracking
• Your scans stay on your phone until you save or share them

CAPTURE
• Full-resolution photos from your camera's largest photo size
• Live edge detection, with optional auto-capture when the shot is steady
• Single or batch mode for a stack of films
• Import photos from your gallery, or share images into the app

CROP AND EDIT
• Drag corners or whole edges; handles snap to film and paper edges, with a magnifier
• Perspective correction that keeps the true shape of the film
• 11 filters, including X-ray enhance and X-ray detail
• Brightness, contrast and sharpness, with Apply to all pages
• Rotate, split multi-slice films into separate panels, duplicate a page
• Anonymise names, IDs and dates with a black box, blur or pixelate
• Undo, redo and reset for every edit; the original photo is never changed

SAVE AND SHARE
• JPEG, PNG or multi-page PDF, always from the full-resolution original
• PDF options: page size (A4, A3, Letter or fit to image), margins and file size, with a live preview
• Shows where files are saved, their estimated size and your free space
• Name suggestions for radiology, documents and general use

SAVED SCANS
• Search, date filters and sorting
• Reopen and edit any saved scan
• Layout: place several pages on one image, such as a 2 x 2 contact sheet

TWO NAMES, ONE APP
The app can also appear on your home screen as "DocScanner" for everyday paperwork. You choose this in Settings; it changes the icon, name and save folders. All features are the same.

Not a medical device: RadioFilm Scanner is a scanning and sharing tool. Make diagnostic decisions from the original films or digital images. Please follow your institution's rules when sharing patient images.

Free and open source: github.com/jemishmayani/RadioFilm-Scanner
```

**Graphics:**

| Item | File |
|---|---|
| App icon (512 x 512) | `play-icon-512.png` |
| Feature graphic (1024 x 500) | `play-feature-1024x500.png` |
| Phone screenshots (2 to 8) | Take on your phone from version 1.9.1 (see below) |
| Tablet screenshots | Optional; Xiaomi Pad 6 shots help the app show as tablet-ready |

Suggested screenshots, in this order: camera with the edge outline on a film; crop screen with the magnifier; editor with an X-ray filter; Anonymise with a black box over a name; save sheet with PDF options; Saved scans list. **Use a film or document with no real patient details** (or anonymise them first).

## 3. Store settings (Grow users > Store presence > Store settings)

| Field | Answer |
|---|---|
| Category | Productivity |
| Tags | Document scanner, PDF, Productivity (choose what Play offers) |
| Email | insulaneurosciences@gmail.com |
| Website | https://github.com/jemishmayani/RadioFilm-Scanner |
| Phone | Leave empty |

## 4. App content (Policy > App content)

| Section | Answer |
|---|---|
| Privacy policy | https://github.com/jemishmayani/RadioFilm-Scanner/blob/main/PRIVACY.md |
| App access | All functionality is available without special access |
| Ads | No, my app does not contain ads |
| Content rating | See below |
| Target audience | 18 and over only. "Could appeal to children": No |
| News app | No |
| Data safety | See below |
| Government app | No |
| Financial features | My app doesn't provide any financial features |
| Health | See below |

**Content rating questionnaire:** email insulaneurosciences@gmail.com; category **All other app types** (utility / productivity). Answer **No** to every question: violence, sexuality, language, controlled substances, gambling, user interaction or sharing between users, sharing location, digital purchases, unrestricted internet. Expected result: rated for everyone (for example PEGI 3 / Everyone).

**Data safety:**

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| (If asked) Is all user data encrypted in transit? | Not applicable: nothing is transmitted |
| Account creation | My app does not allow users to create an account |

This is accurate: the app has no internet permission, and data that stays on the device is not "collected" under Google's definitions. The result on the store page will say "No data collected" and "No data shared with third parties".

**Health apps declaration:** select **My app does not have any health features**. The app scans and files images; it does not diagnose, measure, record health data, give medical information or connect to health services. If a reviewer disagrees, they will say which category they want; change the declaration to that one, adding in *Other* that the app photographs and files medical images on the device only, and is not a medical device.

## 5. Closed test (required for personal accounts)

Production stays locked until **at least 12 testers have been opted in continuously for the last 14 days**. Aim for 15 to 20 people so a few dropping out does not reset the count.

1. **Test and release > Testing > Closed testing > Create track** (or use the default *Alpha* track).
2. **Testers:** create an email list and add each tester's **Google account email** (the one signed into Play on their phone).
3. **Create release:** upload `RadioFilm-Scanner-v1.9.1.aab`. Release name: `1.9.1`. Release notes:
   ```
   First Google Play release. Scan CT, MRI and X-ray films and documents: edge detection, perspective correction, X-ray filters, anonymise, and JPEG, PNG or PDF export. Private and offline.
   ```
4. **Countries:** choose where it is available (for example India, or all countries).
5. Review and **Start rollout to Closed testing**. Google reviews it (usually a few days for a new account).
6. Once approved, copy the **opt-in link** (Testers tab) and send it to your testers. Each must open it on their phone, tap **Become a tester**, then install the app from the Play link.

   Testers who already have the GitHub version must **uninstall it first**.

7. Ask testers to use the app a few times over the two weeks and tell you about problems. Google asks about this when you apply.
8. After 14 days, **Dashboard > Apply for production**. You will answer questions about how you recruited testers, what feedback you got and what you changed. Answer honestly and specifically.

After approval, create a **Production** release with the same (or a newer) bundle.

## 6. Future updates

The Play build never contains the Buy me a coffee link: `build-play.sh` removes it, because Google Play does not allow links to outside payment methods, including donations. The GitHub APK (`build.sh`) keeps it.


Each update needs a higher version code (`android:versionCode` in `AndroidManifest.xml`). Build the bundle with `./build-play.sh` (set `ANDROID_JAR` and `BUNDLETOOL`; see the script), upload it to the track you want, and add release notes. Keep publishing the GitHub APK too if you want; the two copies stay separate.
