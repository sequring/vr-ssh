# Privacy Policy — VR SSH

_Effective date: November 6, 2026_

VR SSH ("the app") is an open-source SSH client for Meta Quest headsets, published by Sequre42 (<https://github.com/sequring>). This policy explains what data the app handles and where it goes.

**In short: the app does not collect, transmit, sell or share any personal data with us or with any third party. Everything stays on your headset, except the SSH traffic you send to servers you choose.**

## Data we collect

None. The app has no accounts, no analytics, no advertising, no crash reporting and no telemetry. It contains no third-party SDKs that send data anywhere. We operate no servers that the app talks to.

## Network connections

The app connects **only** to the SSH servers whose addresses you enter. All traffic to those servers is encrypted by the SSH protocol. The app makes no other network requests.

What you do on those servers (commands, files, output) is between you and the server operator and is governed by their policies, not this one.

## Data stored on your headset

To work, the app keeps the following **locally on your device**, in storage that belongs to the app:

| Data | Purpose | How to remove it |
|---|---|---|
| Saved hosts (host name, port, user name, preferred key, last-used time) | One-click reconnect | ✕ on the host card |
| Command history (commands as shown on screen when you press Enter) | The History panel | ✕ next to an entry, or uninstall |
| SSH private and public keys you generate or import | Authenticating to servers | Delete on the Keys page |
| Known host keys (server fingerprints) | Detecting server impersonation | Uninstall |
| Font size, theme and window color | Your preferences | Uninstall |

Notes:

- **Passwords are never written to storage.** A password you type is kept in memory only while its tab is open, so the tab can reconnect, and is discarded when the tab is closed.
- Text typed at prompts that hide input (for example `sudo` passwords) never appears on screen and is therefore never saved to history. Commands that contain secrets in visible text (such as `export TOKEN=…`) **are** saved; remove them from the History panel if needed.
- Private keys are stored unencrypted unless the key itself is protected with a passphrase.
- Android backup is disabled for the app, so this data is not copied to any cloud backup.
- Uninstalling the app deletes all of the above.

## Clipboard

The app reads the system clipboard only when you press **Paste** (or Ctrl+Shift+V, or "Paste from clipboard" on the Keys page), and writes to it only when you press **Copy** or **Copy public key**.

## Permissions

The app requests a single Android permission, `INTERNET`, which is required to open SSH connections.

## Children

The app is a technical tool and is not directed at children. It does not knowingly collect any information from anyone, including children.

## Changes to this policy

If this policy changes, the new version will be published in this file in the app's repository, together with a new effective date. The full history of changes is available in the repository's commit log.

## Contact

Questions about this policy: open an issue at <https://github.com/sequring/vr-ssh/issues>.
