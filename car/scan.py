#!/usr/bin/env python3
"""Back up a car head unit and sort its apps into "keep", "candidates" and "ask", without changing anything.

Usage (from the repository root):
    CAR_HOST=192.168.43.20 tools/venv/bin/python -I car/scan.py

Writes backup/car-<time>/ (package lists, getprop, settings, who owns Home) and
backup/car-<time>/proposal.txt: a debloat list to read through with the owner before tools/disable.sh.
Only reads from the unit. The keep rules are the same ones the Clean button uses (Keep.java).
"""
import os
import re
import sys
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
os.environ.setdefault("TV_HOST", os.environ.get("CAR_HOST", ""))
if not os.environ["TV_HOST"]:
    sys.exit("Set CAR_HOST to the head unit's IP address, e.g. CAR_HOST=192.168.43.20")
sys.path.insert(0, os.path.join(ROOT, "tools"))
import tv  # noqa: E402

KEEP_JAVA = os.path.join(ROOT, "launcher", "car", "src", "main", "java", "com", "gathin", "carhome", "Keep.java")
KEEP_PARTS = re.findall(r'"([^"]+)"', open(KEEP_JAVA).read().split("BUILT_IN = {", 1)[1].split("};", 1)[0])

# Safe to disable without asking (rule: telemetry, ads, background services nobody uses in a car).
CANDIDATES = {
    "com.google.android.feedback": "crash and feedback reports",
    "com.android.printspooler": "printing",
    "com.android.bips": "printing",
    "com.google.android.printservice.recommendation": "printing",
    "com.android.traceur": "system tracing (developer tool)",
    "com.android.egg": "Android version easter egg",
    "com.android.dreams.basic": "screensaver",
    "com.android.dreams.phototable": "screensaver",
    "com.android.wallpaper.livepicker": "live wallpapers",
    "com.android.calllogbackup": "call log cloud backup",
    "com.google.android.apps.wellbeing": "Digital Wellbeing",
    "com.google.android.apps.restore": "phone-to-phone restore",
    "com.android.managedprovisioning": "work profile setup",
    "com.android.cellbroadcastreceiver": "emergency SIM alerts (only without a SIM card)",
    "com.android.stk": "SIM toolkit (only without a SIM card)",
    "com.google.android.apps.turbo": "battery tips (no battery in a head unit)",
    "com.google.android.apps.tachyon": "Google Meet / Duo",
    "com.google.android.apps.docs": "Google Drive",
    "com.google.android.apps.photos": "Google Photos",
    "com.google.android.videos": "Google TV / Play Movies",
    "com.google.android.apps.magazines": "Google News",
    "com.google.android.gm": "Gmail",
    "com.android.email": "Email",
}
# Words in vendor package names that usually mean ads, app stores, analytics or updaters.
BLOAT_WORDS = ("analytics", "statistic", "report", "ads", "advert", "promo", "push", "appstore", "market",
               "store", "ota", "update", "upgrade", "feedback", "log.", "logger", "telemetry", "track", "cloud")


def run(dev, cmd):
    return dev.shell(cmd, read_timeout_s=60) or ""


def main():
    dev = tv.connect()
    out = os.path.join(ROOT, "backup", "car-" + time.strftime("%Y%m%d-%H%M%S"))
    os.makedirs(out, exist_ok=True)
    dumps = {
        "packages-all.txt": "pm list packages -f",
        "packages-system.txt": "pm list packages -s",
        "packages-third-party.txt": "pm list packages -3",
        "packages-disabled.txt": "pm list packages -d",
        "getprop.txt": "getprop",
        "settings-system.txt": "settings list system",
        "settings-secure.txt": "settings list secure",
        "settings-global.txt": "settings list global",
        "home-owner.txt": "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME",
        "home-claimants.txt": "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME",
        "meminfo.txt": "cat /proc/meminfo",
        "storage.txt": "df -h /data",
    }
    text = {}
    for name, cmd in dumps.items():
        text[name] = run(dev, cmd)
        with open(os.path.join(out, name), "w") as f:
            f.write(text[name])
    for name in dumps:
        if not text[name].strip():
            print(f"WARNING: {name} came back empty")

    def pkgs(name):
        return sorted(line.split(":", 1)[1].strip().rsplit("=", 1)[-1]
                      for line in text[name].splitlines() if line.startswith("package:"))

    system, third = pkgs("packages-system.txt"), set(pkgs("packages-third-party.txt"))
    keep, cand, ask = [], [], []
    for p in system:
        low = p.lower()
        if p == "android" or any(k in low for k in KEEP_PARTS):
            keep.append(p)
        elif p in CANDIDATES:
            cand.append(f"{p:<55} # {CANDIDATES[p]}")
        elif any(w in low for w in BLOAT_WORDS):
            ask.append(f"# {p:<53} # name suggests ads/store/updater: check `dumpsys package {p}` first")
        else:
            ask.append(f"# {p}")

    # CAN bus discovery for the dashboard: what the CAN apps declare, and what speed/torque they log.
    can_words = ("can", "mcu", "syu", "fyt", "vehicle", "carinfo", "carservice", "hiworld", "raise", "xbs", "simple")
    can_pkgs = [p for p in system + sorted(third) if any(w in p.lower() for w in can_words)]
    with open(os.path.join(out, "can-discovery.txt"), "w") as f:
        f.write("# CAN-related packages and what they declare. Look for actions or extras with SPEED / TORQUE /\n"
                "# RPM / TEMP / COOLANT / RANGE / FUEL / VEHICLE in them; they go in config.json as can_speed, can_rpm,\n"
                "# can_torque, can_coolant, can_range (car/README.md).\n\n")
        for p in can_pkgs:
            dump = run(dev, f"dumpsys package {p}")
            keep_lines = [l.rstrip() for l in dump.splitlines()
                          if re.search(r"Action:|Receiver|Provider|Service|authority|permission", l, re.I)]
            f.write(f"===== {p} =====\n" + "\n".join(keep_lines[:300]) + "\n\n")
        log = run(dev, "logcat -d -v brief -t 4000")
        hits = [l for l in log.splitlines() if re.search(r"speed|torque|rpm|coolant|water|temp|range|mileage|fuel|oil|vehicle|canbus|can_", l, re.I)]
        f.write("===== logcat lines mentioning speed / rpm / torque / temperature / range / fuel / CAN (drive a little first) =====\n")
        f.write("\n".join(hits[-300:]) + "\n")
    print(f"  CAN apps found: {', '.join(can_pkgs) or 'none'}  (details in can-discovery.txt)")

    prop = dict(re.findall(r"\[([^\]]+)\]: \[([^\]]*)\]", text["getprop.txt"]))
    head = [
        f"# {prop.get('ro.product.manufacturer', '?')} {prop.get('ro.product.model', '?')}"
        f" ({prop.get('ro.product.device', '?')}), Android {prop.get('ro.build.version.release', '?')}"
        f" (SDK {prop.get('ro.build.version.sdk', '?')}), {prop.get('ro.board.platform', prop.get('ro.hardware', '?'))}",
        f"# Proposal made by car/scan.py on {time.strftime('%Y-%m-%d')}. NOTHING HERE IS TESTED ON THIS UNIT YET.",
        "# Read every line with the owner. Apply in batches of about 10:",
        f"#   TV_HOST=<ip> tools/disable.sh {os.path.relpath(out, ROOT)}/proposal.txt",
        f"# Undo: TV_HOST=<ip> tools/restore.sh {os.path.relpath(out, ROOT)}/proposal.txt",
        "# After each batch check: reverse camera, steering-wheel buttons, radio, Bluetooth calls, CarPlay/Android",
        "# Auto, the keyboard, Wi-Fi, sound, and that Home still opens.",
        "",
        "# ---- Candidates (telemetry, printing, screensavers, apps a car doesn't need) ----",
        *cand,
        "",
        "# ---- Ask the owner: uncomment only what they never use and you have checked ----",
        *ask,
        "",
        "# ---- KEEP: the car needs these (camera, CAN bus, MCU, radio, Bluetooth, CarPlay, system). Never disable ----",
        *[f"#KEEP {p}" for p in keep],
        "",
        f"# Third-party apps installed ({len(third)}): not listed, the owner uses them. Hide one in Glass Car",
        "# with \"hidden_apps\" instead of disabling it.",
    ]
    with open(os.path.join(out, "proposal.txt"), "w") as f:
        f.write("\n".join(head) + "\n")
    print(f"Backup and proposal in {os.path.relpath(out, ROOT)}/")
    print(f"  keep: {len(keep)}  candidates: {len(cand)}  ask: {len(ask)}  third-party: {len(third)}")
    print(f"  Home is: {text['home-owner.txt'].strip().splitlines()[-1] if text['home-owner.txt'].strip() else '?'}")


if __name__ == "__main__":
    main()
