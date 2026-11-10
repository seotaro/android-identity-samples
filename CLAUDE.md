# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## このリポジトリの性質

- Google の `android/identity-samples` のフォーク。**`Shrine/app` 1 つだけ**に絞り、Restore Credentials（ゼロタップログイン）の確認用に作り替えてある。上流の他のサンプル（`CredentialManager`、`CredentialProvider/MyVault`、`DigitalCredentials`、`SmsVerification`、`WebView`）と `Shrine/wear` は削除済みなので、戻さない。
- 上流のコード・README・コメントは英語なので、既存ファイルを直すときは英語のまま合わせる。
- CI は無い。

## ビルド・lint・テスト

`Shrine` ディレクトリで:

```sh
./gradlew :app:assembleDebug
./gradlew :app:installDebug             # 接続中の実機・エミュレーターへ
./gradlew :app:lintDebug
./gradlew spotlessCheck                 # ktlint + ライセンスヘッダー（spotless/copyright.kt(s)）
```

- Spotless は `ui/common/ShrineButton.kt` と `utility/Constants.kt` に上流由来の違反が残っている。触ったファイルだけ整形するなら `./gradlew :app:spotlessApply -PspotlessIdeHook=<絶対パス>`。
- テストコードは無い。動作確認は実機（画面ロック・Google アカウント・Play Services が必要）で行う。

## 署名と Digital Asset Links（重要）

リストアキーは WebAuthn の鍵なので、サーバーの `/.well-known/assetlinks.json` に登録された **APK 署名の SHA-256** と一致しないと作成・利用に失敗する。

- **リポジトリに同梱した `Shrine/debug.keystore`** で debug・release とも署名する（パスワード等は `Shrine/keystore.properties`）。
- `debug.keystore`・`keystore.properties`・`applicationId`（`com.authentication.shrine`）を変えるなら、サーバー側（Project Sesame の `associated_domains` 設定）も合わせて変える。

## アーキテクチャ

Compose の Screen → ViewModel → `AuthRepository` → `AuthApiService`（Retrofit）という流れ。Hilt で DI。画面は `AuthenticationScreen`（サインイン）・`RegisterPasswordScreen`（登録）・`MainMenuScreen`（ユーザー名とサインアウト）の 3 つだけ。passkey・Sign in with Google・passkey 管理・パスワードマネージャーへの保存は削除済みなので、戻さない。

- Credential Manager の呼び出しは `CredentialManagerUtils.kt` に集約（リストアキーの作成・取得・削除だけ）。Activity Context が必要なので Screen から渡す。
- サーバーは自前の Project Sesame（`seotaro-sesame.appspot.com`）。URL は `app/build.gradle.kts` の `buildConfigField`（`API_BASE_URL` / `API_BASE_DOMAIN`）と、`strings.xml` の `asset_statements`。リクエスト・レスポンスの型は `model` パッケージで、Project Sesame（GoogleChromeLabs/project-sesame）の応答形式に合わせてある。
- パスワードは登録もサインインも `auth/username-password`。サーバーはパスワードを検証せず、未登録ならアカウントを作る（Project Sesame の仕様。サーバーはこのままにする方針）。
- Restore Credentials:
  - 作成: サインアップ・サインイン成功時に `AuthRepository.createRestoreKey()`（`webauthn/registerRequest` → `CreateRestoreCredentialRequest` → `webauthn/registerResponse?type=rc`）。E2EE が使えない端末ではクラウドバックアップ無しで作り直す。
  - 自動ログイン: `AuthenticationScreen` の初回表示時に `checkForStoredRestoreKey()`（`webauthn/signinRequest` → `GetRestoreCredentialOption` → `webauthn/signinResponse`）。セッション ID が残っていると動かない。ユーザー名は `signinResponse` のレスポンスから保存する。
  - 削除: サインアウト時に端末（`clearCredentialState`）とサーバー（`webauthn/removeKey?credId=<RESTORE_KEY_CREDENTIAL_ID>`）の両方から消す。リストアキーでログインしたときもその ID を保存している。
  - サーバーはリストアキーを passkey と同じ WebAuthn エンドポイントで扱う。
  - シーケンス図は `Shrine/docs/restore-credentials-sequence.md`。
- **セッション ID を保存する DataStore（`auth`）はバックアップ対象から除外している**（`res/xml/data_extraction_rules.xml` / `backup_rules.xml`）。除外しないと、復元したセッション ID でログインしてしまい、リストアキーが使われない。
- **ゼロタップログインの実機テスト**は Android Studio の Running Devices → ⋮ → Backup App Data（Device to Device）/ Restore App Data で行う。`adb shell bmgr` だけではリストアキーが戻らない。手順は `Shrine/README.md`。
