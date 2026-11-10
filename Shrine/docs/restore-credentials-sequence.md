# Shrine: サインインと復元のシーケンス

Shrine（`:app`）のパスワードでのサインインと、Restore Credentials（ゼロタップログイン）の流れ。

## 1. サインイン（リストアキーの作成まで）

```mermaid
sequenceDiagram
    actor User as ユーザー
    participant Screen as AuthenticationScreen
    participant VM as AuthenticationViewModel
    participant Repo as AuthRepository
    participant CMU as CredentialManagerUtils
    participant GMS as Credential Manager / Play 開発者サービス
    participant Server as Project Sesame

    User->>Screen: メール・パスワードを入力して Sign In
    Screen->>VM: signInWithPassword(email, password)
    VM->>Repo: login(email, password)
    Repo->>Server: POST auth/username-password
    Server-->>Repo: 200 {id, username, displayName} + Set-Cookie
    Repo->>Repo: DataStore に USERNAME / SESSION_ID を保存
    Repo-->>VM: Success
    VM->>Screen: onSignInSuccess()

    Note over Screen,Server: ここからリストアキーの作成
    Screen->>VM: createRestoreKey(...)
    VM->>Repo: createRestoreKey(...)
    Repo->>Server: POST webauthn/registerRequest (Cookie)
    Server-->>Repo: 200 {challenge, rp, user{id, name}, ...}
    Repo->>CMU: createRestoreKey(options)
    CMU->>GMS: createCredential(CreateRestoreCredentialRequest)
    alt E2EE（クラウドバックアップ）が使えない
        GMS-->>CMU: E2eeUnavailableException
        CMU->>GMS: createCredential(isCloudBackupEnabled = false)
    end
    GMS->>GMS: 鍵ペアを生成し、秘密鍵を Block Store に保存
    GMS-->>CMU: CreateRestoreCredentialResponse {rawId, attestationObject, ...}
    CMU-->>Repo: CreateCredentialSuccess
    Repo->>Server: POST webauthn/registerResponse?type=rc (Cookie)
    Server->>Server: 公開鍵を "Restore Credential" として保存
    Server-->>Repo: 200
    Repo->>Repo: DataStore に RESTORE_KEY_CREDENTIAL_ID を保存
    Screen->>Screen: navigateToHome()
```

新規登録（`RegisterPasswordScreen` → `RegistrationViewModel.onPasswordRegister`）も、`auth/username-password` 以降は同じ流れ。

## 2. 復元（バックアップ → 新端末で自動ログイン）

```mermaid
sequenceDiagram
    participant Old as 旧端末（Shrine）
    participant GMS as Play 開発者サービス / Block Store
    participant Backup as バックアップ（クラウド / 端末間転送）
    participant Splash as SplashViewModel
    participant Screen as AuthenticationScreen
    participant VM as AuthenticationViewModel
    participant Repo as AuthRepository
    participant Server as Project Sesame
    participant Home as MainMenuScreen / HomeViewModel

    Note over Old,Backup: 旧端末: バックアップ
    Old->>Backup: アプリデータ（auth DataStore は除外）
    GMS->>Backup: リストアキー

    Note over GMS,Home: 新端末: 復元してから初回起動
    Backup->>GMS: リストアキーを Block Store に書き戻す
    Splash->>Repo: isSessionIdValid()
    Repo-->>Splash: セッション ID なし（復元されていない）
    Splash->>Screen: AuthRoute から開始

    Screen->>VM: checkForStoredRestoreKey()（初回表示時）
    VM->>Repo: isSignedIn() → false
    VM->>Repo: signInRequest()
    Repo->>Server: POST webauthn/signinRequest
    Server-->>Repo: 200 {challenge, rpId, ...} + Set-Cookie
    VM->>GMS: getCredential(GetRestoreCredentialOption)（CredentialManagerUtils 経由）
    GMS->>GMS: 秘密鍵で challenge に署名（ユーザー操作なし）
    GMS-->>VM: RestoreCredential {rawId, signature, userHandle, ...}
    VM->>Repo: signInWithRestoreKeyResponse()
    Repo->>Server: POST webauthn/signinResponse (Cookie)
    Server->>Server: credential ID から公開鍵を引き、署名を検証
    Server-->>Repo: 200 {id, username, displayName} + Set-Cookie
    Repo->>Repo: DataStore に USERNAME / SESSION_ID / RESTORE_KEY_CREDENTIAL_ID を保存
    VM->>Screen: navigateToHome()
    Home->>Repo: getUsername()
    Repo-->>Home: ユーザー名を表示
```

## 補足

- 復元図の旧端末側の 2 本の矢印は、Android の仕組みを図にしたもの。Shrine のコードで行っている処理ではない。テストでは Android Studio の Backup App Data / Restore App Data（Device to Device）がこの役割を担う。手順は [README](../README.md) の「Testing zero-tap sign-in」。
- `auth` DataStore をバックアップから外している（`res/xml/data_extraction_rules.xml` / `backup_rules.xml`）ので、新端末では最初の `isSessionIdValid()` が失敗し、必ずログイン画面に入る。ここでリストアキーによるログインが動く。
- 新端末でリストアキーが見つからなかった場合は `getCredential` が失敗し、ログイン画面のままになる。
- サインアウト時は、端末のリストアキー（`clearCredentialState`）とサーバーの鍵（`webauthn/removeKey?credId=<RESTORE_KEY_CREDENTIAL_ID>`）の両方を削除する。
