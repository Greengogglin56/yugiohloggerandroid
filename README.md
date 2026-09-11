Markdown
# Yu-Gi-Oh! Binder Logger

A fast, lightweight, and fully open-source native Android application built with Kotlin and Jetpack Compose. Designed for Yu-Gi-Oh! card collectors to quickly search cards, log binder collections locally, and export data.

Powered by the **YGOPRODeck API**.

---

## Features

* **Quick Card & Set Search:** Look up cards instantly by set code (e.g., `MZTM-EN039`), passcode/ID, or card name.
* **Local Binder Logging:** Track card details including name, quantity, rarity, edition, set name, and set code.
* **Recent Entry History:** View your latest logged cards directly from the main dashboard with instant "Undo" support.
* **CSV Import & Export:** Automatically saves collection data to a local CSV file (`binder_log.csv`) and allows direct sharing via system file providers.
* **Native & Offline-First UI:** Responsive dark-mode interface built with Jetpack Compose that works smoothly across phone screens and foldables.

---

## Tech Stack

* **Language:** Kotlin
* **UI Framework:** Jetpack Compose (Material 3)
* **Networking:** Retrofit + Gson
* **Image Loading:** Coil
* **API:** [YGOPRODeck API](https://db.ygoprodeck.com/api-guide/)

---

## Building from Source

1. Clone this repository:
   ```bash
   git clone (https://github.com/Greengogglin56/yugiohloggerandroid.git)