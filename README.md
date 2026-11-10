Android Identity Samples Repository
===================================

This is a fork of [android/identity-samples](https://github.com/android/identity-samples),
reduced to a single sample for **Restore Credentials** (zero-tap sign-in) with Credential Manager.

* `Shrine` - An Android app that signs the user in with a password, creates a restore key after
  every sign-in, and signs the user in automatically with the restore key after the app is restored
  on a new device. See [Shrine/README.md](Shrine/README.md).

The other samples of the original repository (`CredentialManager`, `CredentialProvider/MyVault`,
`DigitalCredentials`, `SmsVerification`, `WebView`) and the Wear OS module of Shrine have been
removed.
