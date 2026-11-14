# Glass Car: for Android car head units

A simple touch home screen for Android car players, in a glass style inspired by in-car phone projection:
a frosted rail on the left with the time, four dock apps and a **Clean** button, and pages of large
rounded app icons you swipe sideways. Built for the A30-V2-A01-CPL (1024x600 / 1280x720 class units), and
for any Android 8+ head unit.

> **Status: not yet run on a real head unit.** The code is written and its logic is unit tested, but nobody
> has installed it on a car yet. Treat the first install as a test, with the car parked.

| | |
|---|---|
| App | `com.gathin.carhome`, source in [`launcher/car/`](../launcher/car/) |
| Needs | Android 8 or newer, landscape touchscreen |
| Keeps | the unit's own launcher installed and enabled (it often also runs car features) |

**A30-V2-A01-CPL, as seen on its About screen (2026):** Settings says "Android 16.0", but it is really
**Android 9**: build `android-trunk-p0` (p = Pie), security patch February 2020, kernel 4.14.116. Many
head units show a made-up version. What it means here: Glass Car runs (it needs 8+), RAM clean works, and
caches are cleared through the debugging service rather than the Android 11 dialog (below).

## Dashboard: clock, speed and torque

The first page shows, on the left, a large digital clock, the date, a music widget and engine torque; on the
right, the speedometer (top left), the rev counter (top right), and tiles for coolant temperature and fuel
range under them.
Swipe left for the apps.

- **Clock**: in a dark glass box like the music widget; thin large digits filled with a rainbow that flows across them, with a soft glow that shifts
  colour and breathes. It only animates while Home is on screen, and stands still when Android's animations
  are turned off. `"clock_rainbow": false` makes it plain white.
- **Music widget**: cover art, song, artist, a progress line, and previous / play-pause / next. It follows
  whatever is playing: Spotify, YouTube Music, the radio app or Bluetooth audio from a phone, if that app
  shares its playback with Android (most do). Tap the song to open the app; with nothing playing, play opens
  your music app. It needs notification access, which `car/install.sh` grants; it reads no notifications.
  `"music_widget": false` hides it.

- **Speedometer**: a clear frosted-glass dial that lets the background show through, a glowing rainbow arc that
  fills with the speed (green, yellow, blue, purple, pink, red), a thin bright glass rim,
  clean white numbers, a red zone from 200 km/h and a red needle with a glowing tip that sweeps up and back
  each time Home opens.
- **Your car and its doors**: under the dials, your own picture of the car (⚙ → **Car picture from
  gallery**; it stays on the unit as `car_picture.jpg`, **Remove car picture** takes it off) with the door
  status under it: "Front left door open", "Front left door, Boot open", "All doors closed". It turns orange
  while anything is open. The status needs door data from the CAN bus (`can_doors`); OBD-II has no door
  data, so in OBD-II mode it says *Door status: no data*.
- **Trip**: under the car picture, the trip in km with a **Reset** button (tap Reset, then *Tap again* within
  3 seconds). With the car's odometer from the CAN bus (`can_odometer`) it counts every km, whatever is on
  screen. Without it, it adds up distance from the speed, and only while Home is open: the row says which.
- **Rev counter**: the same glass dial, 0 to 7 (x1000 rpm), red from 6000.
- **Coolant**: °C with a bar and a word: *Warming up* under 70 °C (blue), *Normal* (green), *Hot* over
  110 °C (red). A VW normally sits near 90 °C.
- **Fuel range**: km left, as the car's own trip computer works it out. Orange *Refuel soon* under 80 km,
  red under about 27 km.
- **Speed** comes from the car's CAN bus when the unit's CAN app shares it, otherwise from GPS. The dial
  says which one (`CAN BUS`, `GPS`, or `NO SIGNAL` in a tunnel). GPS speed works on any unit with no setup.
- **Rpm, torque, coolant and range** can only come from the CAN bus. Each shows `--` and *No data* until set
  up.
- The car's own instrument cluster stays the speed to drive by. GPS speed lags by about a second.

**Why the CAN bus needs one more step.** Android head units have no standard CAN interface. A small CAN box
behind the radio (Raise, Hiworld, XBS, Simple Soft…) talks to the maker's own app, and each maker passes the
data on differently, if at all. On a 2017 Jetta the box sits on the comfort network: it normally knows doors,
lights, outside temperature and steering angle, and often speed. Many VW boxes also pass on what the
instrument cluster shows, which can include rpm, coolant temperature and fuel range: the scan will tell.
**Engine torque is on the engine network, which these boxes usually don't read.** If yours doesn't send it, torque needs an OBD-II Bluetooth adapter
(ELM327 type) in the port under the dashboard. Glass Car doesn't read OBD-II yet.

How to wire it up: drive a short way with the unit on, then run `car/scan.py` (step 3). It writes
`can-discovery.txt`: the CAN apps, the broadcasts they declare, and log lines that mention speed or torque.
When one carries the value, add it to `config.json`:

```json
{
  "can_speed":   { "action": "<broadcast action>", "extra": "<extra name>", "scale": 1 },
  "can_rpm":     { "action": "<broadcast action>", "extra": "<extra name>", "scale": 1 },
  "can_torque":  { "action": "<broadcast action>", "extra": "<extra name>", "scale": 1 },
  "can_coolant": { "action": "<broadcast action>", "extra": "<extra name>", "scale": 1 },
  "can_range":   { "action": "<broadcast action>", "extra": "<extra name>", "scale": 1 },
  "can_odometer":{ "action": "<broadcast action>", "extra": "<extra name>", "scale": 1 },
  "can_doors":   { "action": "<broadcast action>", "extra": "<extra name>", "scale": 1 },
  "door_bits":   { "front_left": 1, "front_right": 2, "rear_left": 4, "rear_right": 8, "boot": 16, "bonnet": 32 }
}
```

`can_doors` is one number with a bit per door; `door_bits` says which bit is which (the values above are the
default; CAN boxes differ, so open each door once with the scan running and watch which bit changes).
`scale` converts the raw number (e.g. `0.1` when the box sends tenths). A CAN speed older than 3 seconds
falls back to GPS; rpm and torque older than 3 seconds, and coolant and range older than 30, show `--`.

## OBD-II Bluetooth adapter (Carista and others)

Instead of the CAN box, every value can come from an OBD-II adapter in the port under the dashboard: speed,
rpm, coolant, torque (actual % of the engine's reference torque), the odometer where the car has one
(mostly 2019 and newer), and fuel range worked out from the tank level (`fuel_tank_l`, default 55 L for the
Jetta, and `fuel_l_per_100km`, default 7.5).

1. Plug the adapter in and switch the ignition on.
2. Pair it in Android's Bluetooth settings (PIN usually `1234` or `0000`).
3. ⚙ → **Car data source** → pick the adapter → Done. The dials say `OBD-II` once it answers.

What might not work, and why:

- **Carista.** It is made for Carista's own app. Older Carista adapters (Bluetooth Classic) are ELM327-type
  and should answer standard OBD-II requests; the newer **Carista EVO** uses Bluetooth Low Energy, which Glass
  Car does not speak yet. A generic ELM327 Bluetooth adapter (Vgate, Konnwei, OBDLink…) is the safe choice.
- **The head unit's Bluetooth.** On many units the Bluetooth chip is reserved for calls and music and Android
  apps can't open a serial connection on it; then the adapter never shows up as paired, or the dials say
  `OBD NO CONNECTION`. Some units have their own "OBD" page in the Bluetooth app instead.
- Like everything on the dashboard, the adapter is only asked while Home is on screen.

## What the Clean button does

- **RAM**: closes apps sitting in the background, the same as swiping them away. It never closes the app on
  screen, music or navigation that is playing (they run a foreground service), or anything the car needs:
  reverse and 360 cameras, CAN bus, MCU, steering-wheel buttons, radio, Bluetooth calls, CarPlay / Android
  Auto bridges (zLink, AutoKit, CarbitLink…), keyboard, Google services. See `Keep.java`; add your own with
  `keep_running`. The ring shows how much RAM is in use; it turns orange at 75% and red at 90%.
- **Automatically**: when Home shows and the last clean was more than 30 minutes ago (`auto_clean_minutes`).
- **Cache**: after the RAM clean. On Android 11+ it shows Android's own "clear the cache of all apps?"
  dialog. On Android 9 and 10 (the A30) it runs `pm trim-caches` through the unit's own network debugging
  service, like Glass TV does: the **first** tap shows "Allow USB debugging?" on the screen; tick **Always
  allow** and OK, and tap Clean again. It needs network ADB left on in Developer options. Either way it
  deletes cache files only: no sign-ins, settings or downloads.
- **Long press** Clean: opens Android's storage settings.

## Set it up

Do it parked, engine running or ignition on so the unit doesn't sleep. Connect the unit and your computer to
the same Wi-Fi (a phone hotspot works).

1. **Developer options**: Settings → About → tap **Build number** 7 times. Many head units ask for a code
   first (often `8888`, `3368`, `1234` or `0000`, depending on the maker): look it up for your exact model
   before you try. Then Developer options → **USB debugging** on, and **Wireless / network ADB** if shown.
2. **Connect**: `python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt`, then
   `TV_HOST=<unit ip> tools/venv/bin/python -I tools/tv.py --probe`. Tick **Always allow** on the screen.
   Check `ro.build.version.sdk`: 26 or higher is needed. Many cheap units show a made-up Android version in
   Settings; this number is the real one.
3. **Back up and scan** (changes nothing): `CAR_HOST=<ip> tools/venv/bin/python -I car/scan.py`. It saves
   the package lists, settings and Home owner to `backup/car-<time>/`, and writes `proposal.txt`: a
   debloat list with candidates, an "ask" section and a `#KEEP` section the car needs.
4. **Debloat**: read `proposal.txt` with the owner, then apply about 10 lines at a time with
   `TV_HOST=<ip> tools/disable.sh backup/car-<time>/proposal.txt`. After **every** batch check: reverse
   camera (shift into R), steering-wheel buttons, radio, Bluetooth call, CarPlay / Android Auto, sound,
   Wi-Fi, keyboard, and Home. Anything broken: `pm enable <pkg>` for that batch, one by one. Undo all:
   `TV_HOST=<ip> tools/restore.sh backup/car-<time>/proposal.txt <stock launcher package>`.
5. **Build and install**: `cd launcher && ./gradlew :car:testReleaseUnitTest :car:assembleRelease`, then
   `CAR_HOST=<ip> car/install.sh`. It installs, checks Glass Car stays running and in front, and only then
   makes it Home. If a check fails it uninstalls it and puts the old Home back.
6. **Choose the dock**: hold an app → **Put in dock**, or push a config:
   `CAR_HOST=<ip> car/theme.sh car/themes/default`.

Undo Glass Car: `CAR_HOST=<ip> car/restore-home.sh <original home printed by install.sh>`.

## The ⚙ menu

The gear on the left rail, above Clean:

- **Wallpaper from gallery**: pick any photo with the unit's gallery. Glass Car keeps a copy, turns it the
  right way up and blurs it behind every glass panel. **Default wallpaper** goes back to the built-in one.
- **Clock style**: seven fonts modelled on the iOS lock-screen clock styles (Thin, Light, Regular, Heavy,
  Condensed, Serif, and Inter, an open font close to Apple's) and a size slider (60 to 140 %). Apple's own
  font (SF Pro) is licensed for Apple devices only, so it isn't included; to use another font you are allowed
  to use, copy it to `Android/data/com.gathin.carhome/files/clock.ttf` and choose **Custom**.
- **Car data source**: **CAN bus + GPS** (the CAN box, GPS for speed when it has none) or an **OBD-II
  Bluetooth adapter** (see below).
- **Clear glass icons / Colour icons**: switches every app icon between its own colours and a clear
  frosted-glass tile with the app's symbol in white.

## Settings (`config.json`)

All keys optional; a bad file is ignored as a whole.

| Key | Type | Meaning |
|---|---|---|
| `dock_apps` | list of packages | Dock apps, up to 4, in order. Empty: maps, music, CarPlay app and phone, guessed. Holding an app → Put in dock wins over this |
| `hidden_apps` | list of packages | Never shown in the grid |
| `keep_running` | list of name parts | More apps Clean must never close, e.g. `"com.mycar.dvr"` |
| `columns` | 3 to 8, or 0 | Icons per row; 0 fits them to the screen (5 x 3 on 1024x600, 5 x 2 on 1280x720 at 1.5x) |
| `auto_clean_minutes` | 0 to 1440 | Close background apps by itself when Home shows this long after the last clean; 0 = only on tap. Default 30 |
| `show_labels` | bool | App names under the icons |
| `wallpaper` | bool | Use `wallpaper.jpg` from the theme folder (or the one picked from the gallery); otherwise the built-in dark aurora |
| `clock_font` | `thin`, `light`, `regular`, `heavy`, `condensed`, `serif`, `inter`, `custom` | Clock font. The ⚙ menu wins over this |
| `clock_size` | 60 to 140 | Clock size in %. Default 100 |
| `data_source` | `"can"` or `"obd"` | Car data from the CAN box (and GPS) or a Bluetooth OBD-II adapter |
| `obd_address` | Bluetooth address | The paired OBD-II adapter, e.g. `"00:1D:A5:68:98:8B"`; easier to pick in the ⚙ menu |
| `fuel_tank_l`, `fuel_l_per_100km` | numbers | For fuel range over OBD-II. Default 55 and 7.5 |
| `icon_style` | `"color"` or `"glass"` | App icons as the apps draw them, or clear frosted glass with a white symbol. The ⚙ menu wins over this |
| `accent` | `#RRGGBB` | Colour of the RAM ring |
| `dashboard` | bool | The first page with clock, music, speedometer, rev counter, torque, coolant and range. Default `true` |
| `clock_rainbow` | bool | Flowing, glowing rainbow clock. Default `true`; `false` for plain white |
| `music_widget` | bool | Music widget under the clock. Default `true` |
| `speed_unit` | `"km/h"` or `"mph"` | Speedometer unit |
| `speed_max` | 60 to 400 | Top of the dial. Default 240, like the Jetta's cluster |
| `torque_max` | 50 to 2000 | End of the torque bar in Nm, about the engine's peak (1.4 TSI: 250, 1.5 MPI: 145) |
| `rpm_max`, `rpm_red` | 3000 to 12000 | Top of the rev counter and start of its red zone. Default 7000 and 6000 |
| `door_bits` | `{front_left, front_right, rear_left, rear_right, boot, bonnet}` | Which bit of `can_doors` is which door |
| `can_speed`, `can_rpm`, `can_torque`, `can_coolant`, `can_range`, `can_odometer`, `can_doors` | `{action, extra, scale}` | The CAN app's broadcast that carries each value; see Dashboard above |

## Safety

- Never disable anything in the `#KEEP` section, and never anything you haven't checked with
  `dumpsys package <pkg>`. On head units the reverse camera, the CAN bus link and the steering-wheel buttons
  are ordinary apps with names that don't say so.
- Don't set it up while driving, and don't use the screen while driving beyond what the law allows.
- Firmware updates for these units are rare and maker-specific; Glass Car doesn't touch them.

## Credits

The Inter typeface (`launcher/car/src/main/res/font/inter.ttf`) is by the Inter Project Authors, under the
SIL Open Font License (`launcher/car/FONT-LICENSE-OFL.txt`).
