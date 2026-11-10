# Shrine Sample App

This is a reduced version of the Credential Manager API sample app, also known as the
**"Shrine"** app, that focuses on **Restore Credentials** (zero-tap sign-in): the user is signed
in automatically, without any interaction, after the app is restored on a new device.

Passkeys, Sign in with Google, passkey management and the other demo screens of the original
sample have been removed from the Android app. The Wear OS module is unchanged.

## Features

This sample app implements the following use cases:

* Create an account, or sign in, with an email address and a password. Passwords are not saved to
  the password manager, since zero-tap sign-in doesn't use them.
* Create a restore key after every successful sign-up or sign-in.
* Sign in automatically with the restore key on the first launch after the app is restored on a
  new device (zero-tap sign-in).
* Sign out, which deletes the restore key from the device and the server.

## Requirements

* Latest release of [Android Studio](https://developer.android.com/studio)
* Java 11 or higher
* A device running Android 9 (API level 28) or higher with Google Play services, signed in to a
  Google Account and with a screen lock. Restore keys can't be created without a screen lock.

## Typical flow

1. Launch the app.
2. Tap **Sign Up** and create an account with an email address and a password, or sign in with an
   existing account. A restore key is created in the background.
3. Back up the app and restore it on a new device (or on the same device after reinstalling it).
4. Launch the restored app. The user is signed in automatically and the home screen is shown.
5. Tap **Sign Out** to delete the restore key and restart the flow.

## Testing zero-tap sign-in

Use Android Studio's **Backup App Data** / **Restore App Data** (Running Devices window, ⋮ menu).
For a physical device, start mirroring it in the Running Devices window first.

1. Sign in to the app. Don't sign out, since signing out deletes the restore key.
2. Run **Backup App Data** with the **Device to Device** backup type.
3. Reinstall the app without launching it:
   `adb uninstall com.authentication.shrine` and then `./gradlew :app:installDebug`.
4. Run **Restore App Data** and select the backup.
5. Launch the app. Logcat shows `webauthn/signinRequest` and `webauthn/signinResponse`, and the
   home screen is shown.

`adb shell bmgr backupnow` / `bmgr restore` alone don't bring back the restore key.

The `auth` DataStore that holds the session ID is excluded from backups
(`res/xml/data_extraction_rules.xml` and `res/xml/backup_rules.xml`). Otherwise the restored
session ID would sign the user in, and the restore key would not be used.

## Design

The app has three screens:

| Screen | View Model | Description |
|---|---|---|
| `AuthenticationScreen` | `AuthenticationViewModel` | Checks for a restore key on first display, and signs in with a password. |
| `RegisterPasswordScreen` | `RegistrationViewModel` | Creates an account with a password. |
| `MainMenuScreen` | `HomeViewModel` | Shows the signed-in user and signs out. |

`SplashViewModel` checks whether the stored session is still valid and starts at the home screen or
the authentication screen.

### Server

This app utilizes the server specified in the `app/build.gradle.kts` file (`API_BASE_URL`). The
server is [Project Sesame](https://github.com/GoogleChromeLabs/project-sesame). Its
`/.well-known/assetlinks.json` must list the app's package name and the SHA-256 fingerprint of
`debug.keystore`, which is used to sign both debug and release builds.

The app uses Retrofit to handle web communications with the server, with endpoints specified in
`api/AuthApiService.kt` and called in `repository/AuthRepository.kt`:

* `auth/username-password`: sign-up and sign-in with a password.
* `webauthn/registerRequest` and `webauthn/registerResponse?type=rc`: registration of a restore key.
* `webauthn/signinRequest` and `webauthn/signinResponse`: sign-in with a restore key.
* `webauthn/removeKey`: deletion of the restore key on sign-out.
* `webauthn/getKeys`: check whether the session is still valid.

The server stores and verifies a restore key in the same way as a passkey.

### Credential Manager

Credential Manager calls are made in `CredentialManagerUtils.kt`. As a Credential Manager call
requires an Activity, the corresponding Context is passed in through the Screen.

* `createRestoreKey()`: creates a restore key. If the device doesn't meet the cloud backup
  requirements (`E2eeUnavailableException`), the restore key is created again without cloud backup,
  so that it can still be transferred device to device.
* `getRestoreKey()`: retrieves the restore key for zero-tap sign-in.
* `deleteRestoreKey()`: deletes the restore key on sign-out.

`AuthRepository.createRestoreKey()` combines these with the server calls. You can read more about
how Restore Credentials work at https://developer.android.com/identity/sign-in/restore-credentials.

## Wear OS Module

This sample also includes a Wear OS module that implements Credential Manager for a Wear OS app.
See the wear/README.md file to learn about Wear OS behaviors and limitations.

## **License**

Shrine is distributed under the terms of the Apache License (Version 2.0). See the license for more
information.
