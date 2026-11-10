# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## このリポジトリの性質

- Google の `android/identity-samples` のフォーク。上流のコード・README・コメントは英語なので、既存ファイルを直すときは英語のまま合わせる。
- ルートはビルド単位ではなく、**独立した Android Studio プロジェクトの集合**。Gradle wrapper・AGP・Kotlin のバージョンもプロジェクトごとに違う。コマンドは必ず各プロジェクトのディレクトリで実行する。
- 上流には `main` のほかに `credman_codelab` ブランチ（Credential Manager Codelab の開始状態）がある。

| ディレクトリ | 内容 | 補足 |
|---|---|---|
| `CredentialManager` | Credential Manager の基本操作（passkey / password） | モックサーバー。サーバー応答は `assets` 内のモックファイル |
| `Shrine` (`:app`, `:wear`) | このフォークでは `:app` を **パスワード + Restore Credentials（ゼロタップログイン）だけ** に絞っている。`:wear` は上流のまま | 自前の Project Sesame（`seotaro-sesame.appspot.com`）を Retrofit で叩く |
| `CredentialProvider/MyVault` | `CredentialProviderService` を実装したプロバイダーアプリ | Android 14+。保存は Room のローカル DB |
| `DigitalCredentials` | Digital Credentials API（OpenID4VP / DCQL、mdoc の CBOR、SD-JWT のパース） | Java 17。CI 対象はこれだけ |
| `SmsVerification` | Play Services の SMS Verification API（レガシー） | Gradle 6.7.1。`google-services.json` 等の秘匿ファイルが必要 |
| `WebView/WebkitWebView` | Webkit ライブラリで WebView 内の passkey 作成 | サーバーは glitch.me |
| `WebView/CredentialManagerWebView` | WebView 連携のコード断片のみ | ビルド対象ではない |

## ビルド・lint・テスト

各プロジェクトのディレクトリで:

```sh
./gradlew assembleDebug                 # Shrine は :app:assembleDebug / :wear:assembleDebug
./gradlew installDebug                  # 接続中の実機・エミュレーターへ
```

- **CI**: `.github/workflows/build-digital-credentials.yml` が `DigitalCredentials` の `app:assembleDebug`（JDK 17）だけを走らせ、debug APK をアーティファクトに上げる。他のプロジェクトは CI で検証されない。
- **Spotless（ktlint + ライセンスヘッダー）** は適用方法がプロジェクトで違う:
  - `Shrine`: ルートの `build.gradle.kts` で全サブプロジェクトに適用済み → `./gradlew spotlessCheck` / `spotlessApply`
  - `CredentialProvider/MyVault`, `WebView/WebkitWebView`: init script 経由 → `./gradlew --init-script init.gradle.kts spotlessApply`
  - ライセンスヘッダーの雛形は各プロジェクトの `spotless/copyright.kt(s)`。
- **テスト**: 実質 `SmsVerification` にしか `src/test` / `src/androidTest` が無い。単一テストは `./gradlew :app:testDebugUnitTest --tests '<FQCN>'`。Credential Manager の動作確認は実機（画面ロック設定・Google アカウント・Play Services が必要）で行うのが前提。

## 署名と Digital Asset Links（重要）

passkey 系のサンプルは、RP サーバー側の Digital Asset Links / `expectedOrigin` に登録された **APK 署名の SHA-256** と一致しないと `CreatePublicKeyCredentialDomException`（"The incoming request cannot be validated"）で失敗する。

- そのため `CredentialManager` / `Shrine` / `MyVault` / `WebkitWebView` は **リポジトリに同梱した `debug.keystore`** で debug・release とも署名する（パスワード等は各プロジェクトの `keystore.properties`、`Shrine/wear` は `debug.keystore` を直接参照）。
- `debug.keystore`・`keystore.properties`・`applicationId` を変えるとサーバー側の登録と食い違って passkey が動かなくなる。変えるならサーバー側（Shrine なら Project Sesame、WebkitWebView なら glitch のプロジェクト）の設定も必要。

## アーキテクチャ（Shrine）

Compose の Screen → ViewModel → `AuthRepository` → `AuthApiService`（Retrofit）という流れ。Hilt で DI。`:app` の画面は `AuthenticationScreen`（サインイン）・`RegisterPasswordScreen`（登録）・`MainMenuScreen`（ユーザー名とサインアウト）の 3 つだけ。passkey・Sign in with Google・passkey 管理などは上流から削除済みなので、戻さない。

- Credential Manager の呼び出しは `CredentialManagerUtils.kt` に集約。Activity Context が必要なので Screen から渡す。
- サーバーの URL は `app/build.gradle.kts` の `buildConfigField`（`API_BASE_URL` / `API_BASE_DOMAIN`）と、`strings.xml` の `asset_statements`。リクエスト・レスポンスの型は `model` パッケージで、Project Sesame（GoogleChromeLabs/project-sesame）の応答形式に合わせてある。
- パスワードは登録もサインインも `auth/username-password`（サーバー側がアカウントが無ければ作る）。
- Restore Credentials:
  - 作成: サインアップ・サインイン成功時に `AuthRepository.createRestoreKey()`（`webauthn/registerRequest` → `CreateRestoreCredentialRequest` → `webauthn/registerResponse?type=rc`）。E2EE が使えない端末ではクラウドバックアップ無しで作り直す。
  - 自動ログイン: `AuthenticationScreen` の初回表示時に `checkForStoredRestoreKey()`（`webauthn/signinRequest` → `GetRestoreCredentialOption` → `webauthn/signinResponse`）。セッション ID が残っていると動かない。
  - 削除: サインアウト時に端末（`clearCredentialState`）とサーバー（`webauthn/removeKey`）の両方から消す。
  - サーバーはリストアキーを passkey と同じ WebAuthn エンドポイントで扱う。
- **セッション ID を保存する DataStore（`auth`）はバックアップ対象から除外している**（`res/xml/data_extraction_rules.xml` / `backup_rules.xml`）。除外しないと、復元したセッション ID でログインしてしまい、リストアキーが使われない。
- **ゼロタップログインの実機テスト**は Android Studio の Running Devices → ⋮ → Backup App Data（Device to Device）/ Restore App Data で行う。`adb shell bmgr` だけではリストアキーが戻らない。手順は `Shrine/README.md`。
- `wear` は上流のまま（passkey を作成できない、Restore Credentials 非対応）。`:app` には依存していない。

## アーキテクチャ（DigitalCredentials）

- `CredentialManagerUtil.kt` が `GetDigitalCredentialOption` で OpenID4VP 形式の JSON リクエストを投げる。リクエスト（DCQL・client metadata）は `Requests.kt` で `JSONObject` として組み立てる。
- 応答のパースは自前実装: mdoc は `Cbor.kt`（Tag 24 の手動アンラップ、長さ・メモリのチェック込み）、メールは SD-JWT の disclosure をデコード。
- UI は `MainScreen` / `MainViewModel` / `MainUiState` の MVI 風構成。汎用の取得フローを試すには CMWallet などのウォレットアプリを端末に入れておく必要がある。

## アーキテクチャ（MyVault）

- `MyVaultService`（`CredentialProviderService`）が `onBegin*` で選択肢エントリと `PendingIntent` を返し、選択後に `CreatePasskeyActivity` / `GetPasskeyActivity` / `CreatePasswordActivity` / `GetPasswordActivity` / `UnlockActivity` が実処理をする二段構成。
- `fido` パッケージの WebAuthn 応答生成はデモ用の簡易実装。
- 端末の「パスワードとアカウント」で MyVault をプロバイダーとして有効にし、`CredentialManager` サンプルから passkey を作ると動作確認できる。
