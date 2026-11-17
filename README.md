# The Saviour — Android Emergency & Anti-Theft Safety App

**The Saviour** is a clean, Material You (Jetpack Compose Material 3) Android safety application built with three core emergency protections:

1. **Triple Power Button Tap SOS (`SaviourForegroundService`)**:
   - Rapidly pressing the power button **3 times** within 2.4 seconds triggers Emergency SOS.
   - Automatically enables **Mobile Data & High-Accuracy GPS** (via `WRITE_SECURE_SETTINGS` or automated Quick Settings Accessibility tile clicking).
   - Fetches live GPS coordinates (`FusedLocationProviderClient` + `LocationManager` fallback) and sends an emergency SMS with a clickable Google Maps link (`https://maps.google.com/?q=lat,lng`) and battery level to all saved contacts.
2. **Saved Emergency Contacts (`SaviourRepository` + `SmsManager`)**:
   - Add and manage trusted family/emergency contacts directly inside the clean Material You dashboard.
3. **Fake Switch-Off Protection (`SaviourAccessibilityService` + `FakeShutdownActivity`)**:
   - Intercepts the system power-off menu when someone tries to switch off the phone.
   - Dismisses the real power dialog and displays a realistic **"Shutting down..."** spinner for 2.4 seconds, vibrates once, and locks the screen into a pitch-black (`#000000`) dead-phone state.
   - Secretly enables GPS & Mobile Data in the background and dispatches an emergency SMS alert with live location to your saved contacts.
   - **Secret Owner Unlock**: Tap the **top-left corner 5 times rapidly** to exit the Fake Switch-Off screen.

---

## How to Build the APK with GitHub Actions

This folder already contains `.github/workflows/build.yml` configured with `gradle/actions/setup-gradle@v4` and JDK 17.

1. Extract `The_Saviour_Android_Project.zip` and push the contents to a new GitHub repository (`main` branch).
2. Open your repository on GitHub and click the **Actions** tab.
3. Select the **Build The Saviour APK** workflow run.
4. Once completed, scroll down to **Artifacts** and download **`The-Saviour-Debug-APK`**.

### Optional: Instant Silent Data/GPS Toggle via ADB
To allow Android to toggle GPS without opening Quick Settings tiles, you can optionally grant `WRITE_SECURE_SETTINGS` once via ADB:
```bash
adb shell pm grant com.roshan.thesaviour.debug android.permission.WRITE_SECURE_SETTINGS
```
Even without ADB, enabling **The Saviour Safety Guard** in **Settings → Accessibility** automatically toggles Quick Settings tiles and intercepts unauthorized power-off attempts.
