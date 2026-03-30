
![alt](band.png)

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/icon.png" alt="EUC SoH logo" width="128" height="128" />
</p>

<h1 align="center">EUC SoH – Electric Unicycle State of Health</h1>

[![Run Tests](https://github.com/Tritbool/euc_soh_kotlin/actions/workflows/test.yml/badge.svg)](https://github.com/Tritbool/euc_soh_kotlin/actions/workflows/test.yml)

[![License: AGPL v3+](https://img.shields.io/badge/License-AGPL_v3+-blue.svg)](https://www.gnu.org/licenses/agpl-3.0)

[![HAL](https://img.shields.io/badge/HAL-hal-05553115-4b6c8b)](https://hal.science/hal-05553115)

<!-- Uncomment once published on F-Droid
[![F-Droid](https://img.shields.io/f-droid/v/io.github.eucsoh.android)](https://f-droid.org/packages/io.github.eucsoh.android/)
-->


EUC SoH is an Android app that analyzes the **state of health (SoH)** of electric unicycles from ride logs produced by apps such as **WheelLog** and **EUC World**.
It estimates battery health and internal resistance, and generates charts and reports suitable for resale, maintenance or long‑term monitoring.

> The project is based on [EUC SOH: A Vehicle Health and Usage Monitoring System to Enhance the Safety of Electric Unicycles
](https://hal.science/hal-05553115)
> 
> The project free and open source, licensed under **AGPL‑3.0**.

---

## Features

### Log discovery and ingestion

- The app **automatically scans** known WheelLog and EUC World storage locations on your device.
- No manual folder selection needed: EUC SoH finds your logs by itself.
- CSV files are discovered recursively and **grouped by wheel** based on their metadata.

### File validation and filtering

- Logs are parsed and classified as:
  - **Accepted**: usable for SoH analysis.
  - **Rejected**: too few points, inconsistent data, or Req could not be computed.
- Each rejected file comes with a **clear rejection reason**, so you can clean or re‑export bad logs if needed.

### SoH metrics and charts

- Compute **equivalent resistance (Req)** and other SoH metrics, following the methodology of the original EUC_SOH Python tools.
- Generate time‑series charts using **MPAndroidChart**, allowing you to:
  - Visualize Req evolution over time.
  - Detect long‑term degradation trends.
  - Compare different logs or time ranges for the same wheel.

### Per‑wheel file management

- Inspect the list of logs associated with each wheel.
- Exclude individual files from the analysis if they are noisy, corrupted or irrelevant.
- (Planned/optional) Preview CSV content directly or via a third‑party CSV viewer.

### Export and sharing

- Generate a **structured archive** for each wheel, containing:
  - A **PDF report** (e.g. `soh.pdf`) summarizing the analysis.
  - The underlying WheelLog and EUC World logs, organized by wheel identifier and type.
- The UI revolves around a small number of **clear export buttons** that:
  - Save the generated files.
  - Immediately offer **sharing** via Android’s standard share sheet (mail, messengers, cloud, etc.).

This makes it easy to provide a complete, reproducible dataset when:
- Selling your wheel.
- Sending it for repair.
- Documenting long‑term battery performance.

### Privacy and offline operation

- All processing happens **locally on your device**.
- No remote server, no analytics, no trackers.
- The app only touches the directories you explicitly select for log analysis.

### Localization

- The app is being internationalized, with **English base strings and French translations** already in place.
- Additional translations are planned for many languages (European, Asian and regional languages), to make the tool accessible to riders worldwide.

---

## Typical user workflow

This is what a typical SoH analysis session looks like:

1. **Prepare your logs**

  - Make sure your rides have been recorded with **WheelLog** or **EUC World**.
  - The logs must be accessible on your device's internal storage or SD card in their default location.

2. **Launch the scan**

  - Open EUC SoH and trigger a scan (or let the app scan on startup).
  - The app automatically discovers all compatible CSV logs and groups them by wheel.
    No folder selection required.


3. **Run the SoH analysis**

  - Start the analysis for a given wheel.
  - EUC SoH computes Req and related SoH metrics, then displays **charts** showing how internal resistance evolves over time.
  - Use these charts to spot:
    - Gradual degradation.
    - Sudden changes after incidents or repairs.
    - Differences between battery packs or firmware versions.

4. **Review discovered logs**

- EUC SoH groups logs by wheel and marks them as **accepted** or **rejected**.
- Open the per‑wheel view to:
    - Inspect which files are used in the analysis.
    - Reasons of reject for invalid logs.

5. **Export report and archive**

  - When satisfied with the analysis, export:
    - A **PDF report** summarizing the main graphs and metrics.
    - A **structured archive** containing the exact logs used for the computation.
  - Immediately share the files using Android’s share sheet (mail, messaging, cloud backup, etc.).
  - The recipient gets everything needed to **reproduce the analysis** or cross‑check the results.

---

## Project structure

The project is being split into three logical parts:

- **Core** – pure Kotlin SoH computation engine (`euc-soh-core`).
- **Android** – this app (`euc-soh-android`), with UI, storage and exports.
- **Desktop** – a future desktop/CLI front‑end using the same core logic.

This separation allows the same analysis algorithms to be reused across platforms while keeping the Android app focused on UX and integration.

---

## License

EUC SoH is licensed under the **GNU Affero General Public License v3.0 or later (AGPL‑3.0‑or‑later)**.

You are free to run, study, share and modify the software under the terms of this license. See [`LICENSE`](LICENSE) for details.

---

## Contributing

Contributions are welcome:

- Bug reports and feature requests via the issue tracker.
- Pull requests for:
  - New charts or metrics.
  - Additional file formats.
  - Translations or UX improvements.

Please discuss larger changes in an issue before starting significant work, so we can keep the roadmap coherent.

---

## F-Droid

The project is being prepared for inclusion in **F‑Droid**, with:

- A reproducible Gradle build.
- Fastlane metadata (descriptions, screenshots, changelog).
- In‑app third‑party license listing generated from dependencies.

Once the F‑Droid metadata merge request is accepted, the app will be available directly from the official F‑Droid repository.