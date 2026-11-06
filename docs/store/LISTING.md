# Meta Horizon Store listing — VR SSH

Copy-paste material for the Developer Dashboard. Character counts are given so you can check them against the limits the dashboard shows.

## Basics

| Field | Value |
|---|---|
| App name | VR SSH |
| Package | `io.github.sequring.vrssh` |
| Category | Productivity (secondary: Utilities) |
| Price | Free |
| Supported devices | Quest 2, Quest Pro, Quest 3, Quest 3S |
| Play mode | Sitting, standing |
| Input | Touch controllers, hands, Bluetooth keyboard |
| Privacy policy URL | https://github.com/sequring/vr-ssh/blob/main/PRIVACY.md |
| Website / support | https://github.com/sequring/vr-ssh |

## Tagline (49 chars)

```
Your servers, in your space. A real SSH terminal.
```

## Short description (190 chars)

```
A full SSH terminal for your headset. Open tabs to several servers, reconnect automatically, keep sessions alive with tmux, and manage SSH keys — all directly from Quest, no computer needed.
```

## Long description (~1,730 chars)

```
VR SSH turns your headset into a workstation for your servers. Pair a Bluetooth keyboard, place a giant terminal anywhere around you, and work over SSH directly from the headset — no PC, no streaming, no middleman.

A REAL TERMINAL
Full xterm-compatible emulation with 256 colors, Unicode and mouse support. vim, htop, tmux, less and git all work exactly as they do on your desktop. Adjust the font size to fit as many columns as you like.

TABS AND AUTO-RECONNECT
Keep several sessions open side by side and switch between them with a click or Ctrl+Tab. If Wi-Fi drops or the headset goes to sleep, tabs reconnect on their own. Turn on tmux mode and you return to exactly the same session, with everything still running.

SSH KEYS, BUILT IN
Generate Ed25519 or RSA keys right on the headset, import an existing key from the clipboard, copy the public key, or add it to a server's authorized_keys with one tap. The app remembers which key works for each host.

MADE FOR VR
• One-click saved hosts with connection status
• Special-keys bar (Esc, Tab, Ctrl, arrows) for the virtual keyboard
• Copy and paste between tabs and other apps
• Searchable command history shared across tabs
• Light and dark themes

PRIVATE BY DESIGN
No accounts, no analytics, no ads, no tracking. The app talks only to the servers you connect to. Host keys are pinned on first use to protect you from impersonation, passwords are never stored, and command history skips anything typed at hidden password prompts.

OPEN SOURCE
VR SSH is free and open source under the MIT license. Read the code, report issues or contribute at github.com/sequring/vr-ssh.

Tip: a Bluetooth keyboard paired with your headset makes VR SSH a comfortable place to work for hours.
```

## What's new — 1.0.0

```
First release: SSH terminal with tabs, auto-reconnect and tmux resume, SSH key generation and import, clipboard, command history, light and dark themes.
```

## Search keywords

```
ssh, terminal, shell, console, linux, server, devops, sysadmin, tmux, vim, command line, cli, remote, productivity, developer
```

## Store art

Ready in `docs/store/art/` (24-bit PNG, exact sizes required by the store):

| Dashboard field | File | Size |
|---|---|---|
| Hero Cover | `hero-cover.png` | 3000×900 |
| Cover Landscape | `cover-landscape.png` | 2560×1440 |
| Cover Square | `cover-square.png` | 1440×1440 |
| Cover Portrait | `cover-portrait.png` | 1008×1440 |
| Mini Landscape | `mini-landscape.png` | 1080×360 |
| Icon | `icon.png` | 512×512 |

All covers carry the exact app title and nothing else, as the guidelines require. To change the design or the title, edit `cover.html` and run `docs/store/art/render.sh` (needs Chrome and Python with Pillow).

## Screenshots

The store needs **at least 5** unique screenshots at **2560×1440** of real in-app content, without titles or marketing text. The images in `docs/screenshots/` (1750×1100) are for the README and do not meet this; they have to be captured from the headset at 16:9.

---

## Data Use Checkup — suggested answers

The app collects **no data**: nothing is sent to the developer or to third parties (see PRIVACY.md). For every data type in the checkup (personal info, device info, usage data, user-generated content, and so on), answer **"Not collected"**.

If the form asks about data processed on device only:

| Data | Answer |
|---|---|
| SSH hosts, keys, command history, preferences | Stored locally on the device only; never transmitted to the developer |
| Passwords | Kept in memory for the open session only; never stored or transmitted |
| Network traffic | Sent only to SSH servers chosen by the user, encrypted with SSH |

## IARC age rating — notes for the questionnaire

- No violence, sexual content, gambling, drugs or bad language **in the app itself**.
- No in-app purchases, no ads.
- No chat or communication with other users of the app, and no sharing of user content between users.
- **Unrestricted internet:** answer honestly. The app opens network connections to any server the user enters, and the server can show arbitrary text. If the questionnaire asks whether users can access unfiltered content from the internet, the answer is **Yes**.
- Location: not shared.
