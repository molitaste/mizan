# Mizan / ميزان — recipe manager for chefs

Native Android app (Kotlin + Jetpack Compose) for gelato, pastry, bread, sauces and meals. It's designed for tablets first and also works on phones.
*Mizan is a working name. Trademark and store-name availability have not been checked.*

## What it is (and isn't)
- A real **native Android APK**, not a website or a web app wrapped as an APK.
- **Storage is device-local only.** Recipes, versions, step photos, notes and original scans/PDFs are kept in the app's private storage. There is **no cloud sync**. Use *Settings → Create backup (.zip)* to keep a copy and *Restore backup* to bring it back (on the same or another device).
- **No accounts, API keys or paid services.** OCR runs fully offline.

## Features
| Area | How it works |
|---|---|
| New Recipe | Manual entry. Rows can be added, removed, renamed and reordered; fractions (`1 1/2`, `½`), decimal commas and Arabic-Indic digits are accepted. |
| Scan / Import | Camera (multi-page), images, PDF (multi-page, text-layer or scanned). Tesseract OCR, English + Arabic, **on the device**. |
| Review | Editable draft. Low-confidence rows, missing quantities and cups/spoons with an unknown standard are highlighted with a text label (⚠). Nothing missing is invented. Unplaced lines and the full recognised text are shown. Original files stay attached to the recipe. |
| Scale Batch | Edit **any** row: factor = new ÷ baseline, and every row = baseline × factor. A multiplier and a target total weight (only when every weight is known) are also available. The baseline is never rounded or changed. |
| Edit Formula | Change one row without touching the others; add/remove/reorder; density (g/mL) and per-piece weight; % basis = total weight or one ingredient (baker's %). |
| Units | Metric / US customary / Custom. Cups and spoons are defined for US, Imperial and metric standards; an **unknown cup is never converted** until you choose its standard. oz (weight) ≠ fl oz (volume). Volume → weight only with a density you provide. °C⇄°F also converts temperatures inside step text. Originals are kept and can be shown next to converted values. |
| Preparation Method | Separate page with numbered steps: title, text, time, temperature (stored in °C, shown in your unit), multiple photos per step with captions, reorder/replace/remove, merge steps, dictation. Photos stay with their step when steps are reordered. |
| Notes | Dated notes typed as Observation / Change / Result, typed or dictated, editable. |
| Versions | Drafts autosave. **Save Version** makes a permanent, timestamped, optionally labelled snapshot. History lets you view, duplicate or restore a version; restoring creates a new version, so nothing in the history is deleted. Photos referenced by any version are never cleaned up. |
| Export | PDF (A4, embedded fonts, correct Arabic shaping and RTL, colour or B/W with no heavy fills, step photos and captions). Real **.xlsx** with editable number cells, unit column, live formulas (change the factor in B5 and the sheet recalculates), a Preparation sheet with embedded photos, a Notes sheet and an Info sheet (version, export date, units). Either the current scaled recipe or any saved version can be exported. |
| Bilingual | Full Arabic (RTL) and English (LTR) UI with a one-tap switch. Recipe content is never translated or overwritten. |
| E-Ink mode | Pure black and white: no grey fills, shadows, transparency, ripples or animation, and selection is shown with ● and borders. Photo previews are optionally dithered to two-tone; the original colour files are kept for viewing and export. |

## Services and network
| Feature | Network? | Notes |
|---|---|---|
| OCR | **No** | Tesseract models (`eng` fast + `ara` standard) are downloaded **at build time** into the APK (about 12 MB). Handwriting and blurry photos read less reliably, so review every highlighted row. |
| Dictation | **Depends on the phone** | Uses the device's own speech recogniser (Google, Huawei, Xiaomi, Samsung …). Many need internet; some have offline Arabic/English packs. If there's no engine, no network or the permission is denied, the app says so and you can type instead. |
| Camera | No permission | Uses the system camera app through a secure file link, so Mizan never asks for camera permission. |
| Microphone | Asked when first used | Only when you tap a microphone button. |

## Build
### GitHub Actions (no Android Studio needed)
Every push to `main` builds the APK, runs the core unit tests, uploads the APK as an Actions artifact and publishes it as a GitHub Release (`build-N`) under **Releases**.

### Local
Requirements: JDK 17 and Android SDK 35 (Android Studio Koala or newer).
```bash
mkdir -p app/src/main/assets/tessdata
curl -L -o app/src/main/assets/tessdata/eng.traineddata https://github.com/tesseract-ocr/tessdata_fast/raw/main/eng.traineddata
curl -L -o app/src/main/assets/tessdata/ara.traineddata https://github.com/tesseract-ocr/tessdata/raw/main/ara.traineddata
gradle testDebugUnitTest assembleRelease     # or ./gradlew after `gradle wrapper`
# → app/build/outputs/apk/release/app-release.apk
```

### Signing
- By default, builds are signed with the **debug key `app/mizan-debug.jks`**. It is committed on purpose so that each new build installs as an update and your data is kept. It is *not a secret* and must not be used for Google Play.
- For Play Store, create your own key and add these repository secrets: `MIZAN_KEYSTORE_B64` (output of `base64 -w0 your.jks`), `MIZAN_KEYSTORE_PASSWORD`, `MIZAN_KEY_ALIAS` and `MIZAN_KEY_PASSWORD`. The workflow uses them automatically. Note: switching keys means uninstalling the debug-signed app first, so **make a backup before you switch**.

## Known limitations
- OCR accuracy depends on photo quality. Arabic handwriting is often misread.
- PDF text layers in Arabic are normalised back to normal letters. If a PDF's text layer is garbled, tick *OCR every PDF page*.
- Hardware e-ink refresh modes are controlled by the device, not the app. Ordinary tablets are not e-ink devices.
- Scaling does not change cooking times or temperatures. Check them yourself for very different batch sizes.

## Project layout
```
app/src/main/java/com/mizan/app/
  core/    pure Kotlin: units, scaling, temperature, recipe parser (unit-tested)
  data/    JSON storage, versions, media files, settings, backup
  ocr/     Tesseract + PDF text/rasterisation
  voice/   device speech recogniser
  export/  PDF and XLSX writers
  ui/      Compose screens (library, import, review, editor, preparation, notes, history, export, settings)
```
