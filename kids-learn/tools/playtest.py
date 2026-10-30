#!/usr/bin/env python3
"""Plays Belajar Ceria on a connected emulator or device the way a child would, records the screen, and checks
that every game reacted (the app logs each event under the tag "Ceria").

    python3 kids-learn/tools/playtest.py <out dir>      (the APK must already be installed)

Writes <out>/playtest.mp4, a screenshot after each game, and <out>/report.txt. Exits 1 if a check failed or the
app crashed.
"""
import os
import re
import subprocess
import sys
import time

PKG = "com.belajarceria.kids"
OUT = sys.argv[1] if len(sys.argv) > 1 else "playtest"
checks = []


def adb(*args, timeout=60):
    """Runs one adb command; one that hangs (it happens on a busy emulator) is given up on, not waited for."""
    try:
        return subprocess.run(["adb", *args], capture_output=True, text=True, timeout=timeout).stdout
    except subprocess.TimeoutExpired:
        print(f"  ! adb {' '.join(args)[:60]} timed out")
        return ""


def sh(cmd):
    return adb("shell", cmd)


def nodes():
    """Every labelled piece on screen as (label, (left, top, right, bottom)). The app logs them each time a screen
    or round settles (uiautomator can't be used: the animated background never lets the screen go idle)."""
    lines = events()
    if "layout" not in lines:
        return []
    last = len(lines) - 1 - lines[::-1].index("layout")
    found = []
    for e in lines[last + 1:]:
        m = re.match(r"view (.+) (-?\d+) (-?\d+) (-?\d+) (-?\d+)$", e)
        if m:
            found.append((m.group(1), tuple(int(m.group(i)) for i in range(2, 6))))
    return found


def find(desc, exact=False):
    return [b for d, b in nodes() if (d == desc if exact else d.startswith(desc))]


def center(b):
    return (b[0] + b[2]) // 2, (b[1] + b[3]) // 2


def tap(desc, wait=3.0):
    hits = find(desc, exact=True)
    if not hits:
        print(f"  ! nothing on screen called {desc!r}")
        return False
    x, y = center(hits[0])
    sh(f"input tap {x} {y}")
    time.sleep(wait)
    return True


def swipe(a, b, ms):
    sh(f"input swipe {a[0]} {a[1]} {b[0]} {b[1]} {ms}")


def back(wait=2.5):
    sh("input keyevent 4")
    time.sleep(wait)


def shot(name):
    sh("screencap -p /sdcard/play.png")
    adb("pull", "/sdcard/play.png", os.path.join(OUT, name + ".png"))
    print(f"  screenshot {name}.png")


def events():
    return [l.split(": ", 1)[-1].strip() for l in adb("logcat", "-d", "-s", "Ceria:I").splitlines() if "Ceria" in l]


def count(prefix, since=0):
    return sum(1 for e in events()[since:] if e.startswith(prefix))


def check(name, ok, detail=""):
    checks.append((name, ok, detail))
    print(("  PASS " if ok else "  FAIL ") + name + (f"  ({detail})" if detail else ""))


def play_learn():
    print("Learn: swipe through the flash cards")
    tap("Learn", 3)
    before = len(events())
    card = find("card", exact=True)
    if card:
        x, y = center(card[0])
        for _ in range(2):
            swipe((x + 250, y), (x - 450, y), 220)  # a quick flick to the left: next card
            time.sleep(2)
        sh(f"input tap {x} {y}")  # a tap reads it again and wobbles
        time.sleep(1.5)
    shot("p1-learn")
    check("Learn: flicking shows the next cards", count("card ", before) >= 2, f"{count('card ', before)} cards")
    back()


def play_drag():
    print("Drag & drop: one wrong drop, then every tile into its slot")
    tap("Drag", 4.5)
    before = len(events())
    tiles = [(d[5:], b) for d, b in nodes() if d.startswith("tile:")]
    slots = {d[5:]: b for d, b in nodes() if d.startswith("slot:")}
    if len(tiles) >= 2 and len(slots) >= 2:
        word, tb = tiles[0]
        other = next(w for w in slots if w != word)
        swipe(center(tb), center(slots[other]), 1200)  # the wrong slot: it should shake and bounce back
        time.sleep(2)
        for word, tb in tiles:
            if word in slots:
                swipe(center(tb), center(slots[word]), 1200)
                time.sleep(1.6)
        time.sleep(0.2)
        shot("p2-drag-done")
    check("Drag: a wrong drop is refused", count("drag wrong", before) == 1, f"{count('drag wrong', before)}")
    check("Drag: every tile lands in its slot", count("drag right", before) == len(tiles) > 0,
          f"{count('drag right', before)} of {len(tiles)}")
    time.sleep(1.5)
    check("Drag: the round ends with a celebration", count("celebrate", before) >= 1)
    time.sleep(3)
    back()


def play_match(tap_mode=False):
    print("Match: " + ("tap one, then its partner" if tap_mode else "one wrong line, then a line for every pair"))
    tap("Match", 4.5)
    before = len(events())
    lefts = [(d[5:], b) for d, b in nodes() if d.startswith("left:")]
    rights = {d[6:]: b for d, b in nodes() if d.startswith("right:")}
    if lefts and rights:
        if not tap_mode:
            word, lb = lefts[0]
            other = next(w for w in rights if w != word)
            swipe(center(lb), center(rights[other]), 900)
            time.sleep(1.5)
        for word, lb in lefts:
            if word not in rights:
                continue
            if tap_mode:
                sh("input tap %d %d" % center(lb))
                time.sleep(0.8)
                sh("input tap %d %d" % center(rights[word]))
            else:
                swipe(center(lb), center(rights[word]), 900)
            time.sleep(1.4)
        time.sleep(0.2)
        shot("p4-match-tap" if tap_mode else "p3-match-done")
    if not tap_mode:
        check("Match: a wrong line is refused", count("match wrong", before) == 1, f"{count('match wrong', before)}")
    check("Match: every pair joins" + (" by tapping" if tap_mode else " by drawing a line"),
          count("match right", before) == len(lefts) > 0, f"{count('match right', before)} of {len(lefts)}")
    time.sleep(1.5)
    check("Match: the round ends with a celebration", count("celebrate", before) >= 1)
    time.sleep(3)
    back()


def play_trace():
    print("Draw: trace Alif with a finger")
    tap("Draw", 4)
    before = len(events())
    board = find("board", exact=True)
    if board:
        l, t, r, b = board[0]
        x, h = (l + r) // 2, b - t
        # Alif is one tall stroke in the middle: draw it top to bottom, twice, like a careful child.
        swipe((x, t + int(h * 0.12)), (x + 10, t + int(h * 0.88)), 900)
        time.sleep(1.2)
        if count("traced", before) == 0:
            swipe((x + 12, t + int(h * 0.12)), (x + 18, t + int(h * 0.88)), 900)
            time.sleep(1.2)
        shot("p5-trace-done")
    scores = [e for e in events()[before:] if e.startswith("trace check")]
    check("Draw: tracing the letter is recognised", count("traced", before) == 1, "; ".join(scores))
    time.sleep(2.5)
    check("Draw: tracing ends with a celebration", count("celebrate", before) >= 1)
    time.sleep(2.5)
    shot("p6-trace-next")
    back()


def wait_question(i, timeout=15):
    """Waits until question i is on screen and its buttons are logged; returns the right answer's index."""
    end = time.time() + timeout
    while time.time() < end:
        ev = events()
        mark = [k for k, e in enumerate(ev) if e.startswith(f"exam q {i} ")]
        if mark and "layout" in ev[mark[-1]:]:
            return int(ev[mark[-1]].split()[-1])
        time.sleep(0.5)
    return None


def play_exam():
    print("Ujian: the Bahasa Melayu part, 24 right and one wrong on purpose")
    tap("Ujian", 3)
    before = len(events())
    tap("start:0", 1)
    shot_taken = False
    for i in range(25):
        right = wait_question(i)
        if right is None:
            print(f"  ! question {i + 1} never appeared")
            break
        if i == 2 and not shot_taken:
            shot("p7-exam-question")
            shot_taken = True
        opts = {d: b for d, b in nodes() if d.startswith("opt:")}
        pick = right if i != 3 else (right + 1) % len(opts)  # question 4 answered wrongly
        b = opts.get(f"opt:{pick}")
        if b:
            sh("input tap %d %d" % center(b))
        time.sleep(0.4)
    time.sleep(2.5)
    shot("p8-exam-result")
    ev = events()[before:]
    check("Ujian: all 25 questions answered", count("exam answer", before) == 25, f"{count('exam answer', before)}")
    check("Ujian: the mark is 24/25 (one wrong on purpose)", "exam result 0 24/25" in ev,
          next((e for e in ev if e.startswith("exam result")), "no result"))
    check("Ujian: the result is celebrated", count("celebrate", before) >= 1)
    time.sleep(2)
    if tap("Lihat jawapan", 2):
        shot("p9-exam-review")
    back()


def play_heroes():
    print("Hero Squad: drag tiles in the hero theme; heroes zap right answers and fly by")
    sh(f"am start -S -W -n {PKG}/.MainActivity --es theme hero --es open 0,2")
    time.sleep(6)
    before = len(events())
    tiles = [(d[5:], b) for d, b in nodes() if d.startswith("tile:")]
    slots = {d[5:]: b for d, b in nodes() if d.startswith("slot:")}
    for k, (word, tb) in enumerate(tiles):
        if word in slots:
            swipe(center(tb), center(slots[word]), 1000)
            time.sleep(1.0 if k else 0.9)
            if k == 0:
                shot("p10-hero-zap")
            time.sleep(1.4)
    time.sleep(0.6)
    shot("p11-hero-squad")
    check("Heroes: a hero zaps each right answer", count("hero zap", before) == len(tiles) > 0,
          f"{count('hero zap', before)} of {len(tiles)}")
    time.sleep(16)  # long enough for a hero to run or fly by on its own
    sh(f"am start -S -W -n {PKG}/.MainActivity --es theme classic")
    time.sleep(3)


def record(name):
    """Starts a screen recording (Android stops each one after 3 minutes)."""
    return subprocess.Popen(["adb", "shell", "screenrecord", "--time-limit", "180", "--bit-rate", "6000000",
                             f"/sdcard/{name}.mp4"])


def stop(rec, name):
    sh("pkill -INT screenrecord")
    try:
        rec.wait(timeout=20)
    except subprocess.TimeoutExpired:
        rec.kill()
    time.sleep(3)
    adb("pull", f"/sdcard/{name}.mp4", os.path.join(OUT, f"{name}.mp4"))


def main():
    os.makedirs(OUT, exist_ok=True)
    sh("settings put secure immersive_mode_confirmations confirmed")
    adb("logcat", "-c")
    sh(f"am start -S -W -n {PKG}/.MainActivity")
    time.sleep(6)
    rec = record("playtest")
    time.sleep(2)
    shot("p0-home")

    print("Home: open ABC")
    tap("ABC", 3)
    play_learn()
    play_drag()
    play_match()
    back()

    print("Home: open Alif Ba Ta")
    tap("Alif Ba Ta", 3)
    play_trace()
    back()

    print("Home: open Islamic Words")
    tap("Islamic Words", 3)
    play_match(tap_mode=True)
    back()

    stop(rec, "playtest")

    rec = record("exam")
    time.sleep(1)
    play_exam()
    stop(rec, "exam")

    rec = record("heroes")
    play_heroes()
    stop(rec, "heroes")

    crash = adb("logcat", "-d", "-b", "crash")
    check("No crash", PKG not in crash, crash.strip()[:300])
    with open(os.path.join(OUT, "report.txt"), "w") as f:
        for name, ok, detail in checks:
            f.write(("PASS  " if ok else "FAIL  ") + name + (f"  ({detail})" if detail else "") + "\n")
        f.write("\nEvents logged by the app:\n" + "\n".join(events()) + "\n")
    failed = [c for c in checks if not c[1]]
    print(f"\n{len(checks) - len(failed)} of {len(checks)} checks passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
