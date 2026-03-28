# JunkIt

JunkIt is an Android application designed to help users efficiently manage temporary or "junk" photos. 
Have you ever taken a burst of photos just to capture some temporary information, like a parking spot, a Wi-Fi password, or a receipt, only to have them clutter up your gallery forever? 
JunkIt solves this problem by allowing you to mark photos as "junk" right at the moment of capture and automatically cleaning them up later.

## Features

- **Quick Settings Tile ("Junk Mode"):** Easily toggle Junk Mode on or off directly from your Quick Settings panel before taking photos.
- **Auto-Cleanup:** Photos taken while Junk Mode is active are automatically tagged with an expiration time. 
- **Smart Deletion:** Once the expiration time is reached, photos are safely moved to the Android Recycle Bin (on Android 11+) or permanently deleted on older versions, keeping your gallery clean without explicit user action. Runs efficiently in the background using WorkManager.
- **Modern Gallery UI:** View all your pending junk photos in a sleek, thumbnail-only grid layout, inspired by modern gallery apps.
- **Batch Operations:** Multiselect mode allows you to easily manage multiple pending photos at once. Choose to instantly **Delete Now** or **Keep** them (removing the junk tag) directly from the home screen.
- **Customizable Expiration:** Configure how long junk photos should be kept before deletion (e.g., 2 hours, 12 hours, 24 hours) via the app settings.

## Architecture & Technology Stack

- **UI:** Built entirely with Jetpack Compose.
- **Background Processing:** Uses Android `WorkManager` for scheduled background cleanups. To reduce battery usage cleanup process runs at 6 hrs interval.
- **Service:** Utilizes a Foreground Service (`PhotoMonitorService`) combined with `FileObserver` (`CameraDirectoryObserver`) to instantly detect new capture events in the `DCIM/Camera` directory without polling.
- **Local Storage:** `Room` database for tracking pending junk photos and their metadata. `DataStore` for type-safe preference management.
- **Storage APIs:** Uses Scoped Storage and `MediaStore` APIs to properly manage file lifecycles and send deleted items to the Android Recycle Bin.

## How to Build and Run

1. Open **Android Studio** -> **File** -> **Open** -> select the project directory.
2. Allow Gradle to sync and download the necessary dependencies.
3. Build & run on a device or emulator running **Android 11+** (API level 30 or higher recommended).
4. Upon launching the app, follow the prompts to grant necessary storage access permissions.
5. Add the **"Junk Mode"** tile to your Quick Settings tray for rapid access.

## Privacy

With privacy in mind, JunkIt processes all photos locally on your device. It leverages Android's established privacy practices, including scoped storage APIs, to ensure files are accessed and deleted securely. No images are uploaded to external servers. For more details, see our `PRIVACY_POLICY.md`.

## License

This project is licensed under the terms described in the `LICENSE.md` file.
