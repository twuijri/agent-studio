# Ekko Studio

Electron desktop distribution for Ekko Studio.

### Opening the unsigned macOS build

Core Hub builds are not signed with an Apple Developer ID yet, so macOS shows
"Apple could not verify “Core Hub” is free of malware" the first time. Click
**Done**, open **System Settings → Privacy & Security**, scroll to the bottom
and click **Open Anyway**, then open the app again. This is the same flow as any
other unsigned app from GitHub. The release build ad-hoc signs the bundle
(`scripts/adhoc-sign-macos.mjs`) so macOS never reports it as "damaged"; if you
still see that message on an older download, run
`xattr -dr com.apple.quarantine "/Applications/Core Hub.app"`. Adding the
`MAC_CSC_LINK` / `APPLE_*` secrets to the repository produces notarized builds
that open without any prompt.
## New-version notice (no auto-updater)

The auto-updater stays disabled in this fork. Instead, packaged builds check the
fork's GitHub releases 30 s after start and every 6 hours; when a newer release
exists the app shows a system notification once per version and keeps a
"Download version X…" entry in the tray menu. Nothing is downloaded or
installed: the entry opens the release page. "Check for updates" in the tray
runs the same check on demand, and "Notify about new versions" turns the
automatic check off. State lives in `release-notice.json` under the app data
directory; `CORE_HUB_RELEASE_API_URL` overrides the source for testing.

## Test-channel builds (Core Hub Test)

Every green `test` branch commit produces owner-only builds through
`.github/workflows/test-track.yml`: desktop apps named **Core Hub Test** (app id
`us.i3u.agentstudio.test`, own data folder, version `<pkg>-test.<run>`) uploaded
as private workflow artifacts, and the image `ghcr.io/twuijri/core-hub-test:test`
in a private package. The channel is baked into the packaged `package.json`
(`corehubChannel: "test"`, read by `src/main/channel.ts`); test builds skip the
new-version notice and never touch the stable app or its data.

## Install

Download the latest macOS, Windows, or Linux installer for your CPU
architecture from the project
[GitHub Releases](https://github.com/EKKOLearnAI/hermes-studio/releases/latest).

The desktop app bundles the Web UI runtime and launches it locally from the
native shell app.

## Command shims

After the packaged desktop app starts, it installs managed command shims:

| Command | Description |
| --- | --- |
| `ekko-studio` | Open the Ekko Studio desktop app |
| `ekko-studio cli ...` | Run the bundled Hermes Agent CLI |
| `ekko-studio web ...` | Run the bundled `hermes-web-ui` command |
| `ekko-studio -h` | Show wrapper help |
| `ekko-studio-mcp` | Run the managed Web UI MCP bridge |

The desktop command is `ekko-studio`; the previous managed `hermes-studio`
command is removed when the new shim is installed. No compatibility alias is created.

Use `ekko-studio cli -h` for Hermes Agent CLI help and
`ekko-studio web -h` for Web UI CLI help.

## Data directories

On Windows, the first packaged launch after the rename updates the existing
Studio startup entry from `Hermes Studio.exe` to `Ekko Studio.exe` in the same
installation directory. Its Task Manager enabled/disabled state is preserved.
No entry is created if startup was never enabled; custom entries and machine-wide
entries are left alone. The migration is safe to retry on later launches.

Hermes Agent data is stored in `~/.hermes` on Windows, macOS, and Linux.

The desktop wrapper's own Web UI state is stored separately in
`~/.hermes-web-ui` unless `HERMES_WEB_UI_HOME` is set.

## Connection mode

The app has two connection modes (see `docs/DESKTOP-SERVER-MODE.md`):

- **Local** (default, unchanged): the bundled Web UI, Hermes, models and data
  run on this machine. Existing installs stay in this mode and are never asked.
- **Linked to a server**: the window loads the Web UI of a Core Hub server you
  own and signs in with that server's account, like a browser would. Nothing
  local is started: no Python, no Hermes, no local Web UI server, no command
  shims. Local-only controls (Runtime directory picker, login reset, the
  desktop agent browser) are hidden.

Open **Connection mode…** from the tray menu, or **Settings → Display → App
connection**, to switch. The page checks `/health/ready` (then `/health`) on
the address before saving to `desktop-mode.json` under the Electron userData
directory and restarting the app. Automation can pin the mode with
`HERMES_DESKTOP_MODE=local|server` and `HERMES_DESKTOP_SERVER_URL=https://…`;
when set, the page is read-only.

## Device access (linked mode)

In the linked-server mode the app can let Hermes on your server operate this
machine through the server's existing device tools (`ekko_studio_devices`):

1. Tray → **Device access…** (also reachable from the connection mode page).
2. On the server open **Devices → Copy pairing link**, paste it into the page and
   send the pairing request; approve it on the server under **Devices → Requests**.
3. Share at least one folder and enable **Run commands** and/or **Read and write
   files**. Commands start in a shared folder without a shell (they can still reach anything your
   account can, so keep approval on), file transfers stay inside shared folders, and each command
   asks for approval (Allow / Allow for this session / Deny) unless you choose
   "Always allow". Interactive terminals are not available on the device.

Two more capabilities can be shared from the same page:

- **Agent browser**: the server's bundled browser tools drive this app's agent
  browser through a gateway on the server; nothing else changes on the server.
- **Screen**: screenshots plus mouse/keyboard actions (AppleScript on macOS,
  PowerShell on Windows, `xdotool` on Linux). The first use in a connection asks
  for approval; a red banner with a **Stop** button shows while it is active.
  macOS asks for Screen Recording and Accessibility permissions the first time.

- **App connections** (sidebar entry in the linked app): MCP servers that other assistants
  already have on this computer (Claude Desktop and its extensions, Claude Code, Codex,
  Cursor, Windsurf — macOS, Windows, Linux paths) can be shared one by one. The app runs
  the program's MCP server locally and pipes it over the device channel; the server turns
  it into a managed MCP server of the allowed Hermes/Ekko profiles automatically. The
  first use per connection asks for approval on this computer.
  In local mode (no linked server) the same page works too: shared apps are
  handed to the local Core Hub server (`PUT /api/desktop/local-apps`, loopback +
  desktop token) and become managed MCP servers of every local profile, run
  directly with the app's own command.

On the server, **Devices → Linked devices** lists connected devices and lets you
restrict each one to specific Hermes profiles (empty = every profile).

The device keeps an outbound WebSocket to `/api/devices/peer-socket` declaring
`controllable=1`; it never listens on a port. Settings live in `device-agent.json`
and the local activity log in `device-agent-audit.jsonl` under the Electron
userData directory; the device identity is `device-identity.json` there.

## Desktop and tray icons

Regenerate the rounded Windows desktop icon and macOS, Windows, and Linux tray
icons from `build/icon.png` by running this command from the repository root:

```sh
node packages/desktop/scripts/generate-rounded-icons.mjs
```

The script preserves the original artwork and applies a transparent rounded-square
mask at each output size (16% corner radius for Windows, 26% for macOS/Linux trays).
It writes `iconWindows.png`, the multi-resolution
`icon.ico`, and the platform tray PNGs. Linux uses a separate `trayLinux.png` asset.

## China mirror environment

These mirrors are optional and are not required in CI:

```sh
export NPM_CONFIG_REGISTRY=https://registry.npmmirror.com
export ELECTRON_MIRROR=https://npmmirror.com/mirrors/electron/
export ELECTRON_BUILDER_BINARIES_MIRROR=https://npmmirror.com/mirrors/electron-builder-binaries/
```

If GitHub release downloads are slow, `fetch-python.mjs` can also use a compatible
python-build-standalone release mirror:

```sh
export PBS_BASE_URL=https://github.com/astral-sh/python-build-standalone/releases/download
```
