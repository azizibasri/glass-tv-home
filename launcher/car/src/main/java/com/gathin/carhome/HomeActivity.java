package com.gathin.carhome;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Glass Car's home screen, laid out for a car's touchscreen: a glass rail on the left with the time, the
 * dock and the Clean button, and pages of large rounded app icons on the right, swiped sideways. Everything
 * is built in code (no layouts), sized from the real screen, so 1024x600, 1280x720 and 1920x720 units
 * all fill properly.
 */
public final class HomeActivity extends Activity {
    private static final int MAX_DOCK = 4;
    private static final String[][] DOCK_GUESSES = {
            {"com.waze", "com.google.android.apps.maps", "com.here.app.maps", "com.sygic.aura", "ru.yandex.yandexnavi"},
            {"com.spotify.music", "com.google.android.apps.youtube.music", "com.apple.android.music",
                    "deezer.android.app", "com.amazon.mp3", "com.joox.music", "com.google.android.youtube"},
    };
    private static final String[] PROJECTION_PARTS = {"zlink", "carplay", "autokit", "carlink", "easyconn", "carbit"};
    private static final String[] PHONE_PARTS = {"btphone", "bt.phone", "bluetooth", "dialer", "nfore"};

    static final class App {
        final String pkg, label;
        final ComponentName component;
        Bitmap icon;

        App(String pkg, String label, ComponentName component) {
            this.pkg = pkg;
            this.label = label;
            this.component = component;
        }
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Glass.Source glass = new Glass.Source();
    private final List<View> glassViews = new ArrayList<>();
    private final Map<String, Bitmap> iconCache = new HashMap<>();
    private List<App> apps = new ArrayList<>();
    private CarConfig cfg;
    private SharedPreferences prefs;
    private float density;

    private FrameLayout root;
    private View backdrop;
    private Pager pager;
    private LinearLayout dots;
    private TextView clock, date, toast;
    private RamRing ring;
    private int builtW, builtH;
    private boolean resumed;
    private CarData carData;
    private BigSpeedView bigSpeed;
    private InfoTile coolantTile, rangeTile;
    private static final int REQUEST_WALLPAPER = 11;
    private final TripMeter trip = new TripMeter();
    private TextView tripValue, tripNote, tripReset;
    private boolean tripResetArmed;
    private long tripSavedAt;
    /** "color" or "glass": the person's choice in the settings menu, else config.json's. */
    private String iconStyle;
    /** Clock font and size (%), and the car data source: the ⚙ menu's choices, else config.json's. */
    private String clockFont, dataSource, obdAddress;
    private int clockSize, clockHeight;
    private boolean clockGlow, clockRainbow;
    private float clockBasePx;
    private int lastObdState = ObdLink.OFF;
    private TorqueBar torqueBar;
    private RainbowClock bigClock;
    private TextView bigDate;
    private NowPlaying nowPlaying;

    private final Runnable dataTick = new Runnable() {
        @Override
        public void run() {
            if (carData != null) carData.publish();
            if (resumed) main.postDelayed(this, 1_000);
        }
    };

    private final Runnable ramTick = new Runnable() {
        @Override
        public void run() {
            if (ring != null) ring.setUsed(Cleaner.usedFraction(HomeActivity.this), cfg.accent);
            if (resumed) main.postDelayed(this, 15_000);
        }
    };

    private final Runnable autoClean = () -> {
        if (!resumed || cfg.autoCleanMinutes <= 0) return;
        long last = prefs.getLong("auto_clean", 0), now = SystemClock.elapsedRealtime();
        if (last != 0 && last < now && now - last < cfg.autoCleanMinutes * 60_000L) return;
        prefs.edit().putLong("auto_clean", now).apply();
        Cleaner.cleanRam(this, launchable(), cfg.keepRunning, (closed, freed, used) -> {
            if (ring != null) ring.setUsed(used, cfg.accent);
        });
    };

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            updateClock();
        }
    };

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            loadApps();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        density = getResources().getDisplayMetrics().density;
        prefs = getSharedPreferences("car", MODE_PRIVATE);
        cfg = CarConfig.load(this);
        // Stay inside the system bars: head units often keep a status bar and a navigation bar on screen.
        getWindow().setNavigationBarColor(0xFF000000);

        root = new FrameLayout(this);
        glass.root = root;
        backdrop = new View(this) {
            @Override
            protected void onDraw(Canvas c) {
                Wallpaper wp = glass.wallpaper;
                if (wp != null) c.drawBitmap(wp.sharp, null, new RectF(0, 0, getWidth(), getHeight()), null);
            }
        };
        backdrop.setBackgroundColor(0xFF070B1E);
        root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != builtW || b - t != builtH) main.post(this::build);
        });
        setContentView(root);

        IntentFilter time = new IntentFilter(Intent.ACTION_TIME_TICK);
        time.addAction(Intent.ACTION_TIME_CHANGED);
        time.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(timeReceiver, time);
        IntentFilter pkgs = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        pkgs.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pkgs.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pkgs.addDataScheme("package");
        registerReceiver(packageReceiver, pkgs);
        carData = new CarData(this, cfg, this::showCarData);
        iconStyle = prefs.getString("icon_style", cfg.iconStyle);
        clockFont = prefs.getString("clock_font", cfg.clockFont);
        clockSize = prefs.getInt("clock_size", cfg.clockSize);
        clockHeight = prefs.getInt("clock_height", cfg.clockHeight);
        clockGlow = prefs.getBoolean("clock_glow", cfg.clockGlow);
        clockRainbow = prefs.getBoolean("clock_rainbow", cfg.clockRainbow);
        dataSource = prefs.getString("data_source", cfg.dataSource);
        obdAddress = prefs.getString("obd_address", cfg.obdAddress);
        carData.setSource(dataSource, obdAddress);
        trip.baseOdometer = prefs.getFloat("trip_base", Float.NaN);
        trip.accumulated = prefs.getFloat("trip_acc", 0f);
        loadApps();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(timeReceiver);
        unregisterReceiver(packageReceiver);
        main.removeCallbacksAndMessages(null);
        worker.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        updateClock();
        main.removeCallbacks(ramTick);
        main.post(ramTick);
        main.removeCallbacks(autoClean);
        main.postDelayed(autoClean, 4_000);
        if (cfg.dashboard) {
            if (!carData.hasGpsPermission() && !prefs.getBoolean("asked_gps", false)) {
                prefs.edit().putBoolean("asked_gps", true).apply();
                requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 3);
            }
            carData.start();
            main.removeCallbacks(dataTick);
            main.post(dataTick);
        }
        if (bigClock != null) bigClock.setRunning(true);
        if (nowPlaying != null) nowPlaying.start();
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] perms, int[] results) {
        super.onRequestPermissionsResult(request, perms, results);
        if (request == 6) {
            // Music files allowed: play them now (or say there are none).
            if (results.length > 0 && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) openMusicApp();
            return;
        }
        if (resumed && cfg.dashboard) {
            carData.stop();
            carData.start();
        }
    }

    @Override
    protected void onPause() {
        resumed = false;
        main.removeCallbacks(ramTick);
        main.removeCallbacks(autoClean);
        main.removeCallbacks(dataTick);
        carData.stop();
        saveTrip();
        if (bigClock != null) bigClock.setRunning(false);
        if (nowPlaying != null) nowPlaying.stop();
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while already home: back to the first page.
        if (pager != null) pager.snapTo(0);
    }

    @Override
    public void onBackPressed() {
        if (pager != null && pager.page() > 0) pager.snapTo(0);
        // Home never closes itself.
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == Cleaner.REQUEST_CLEAR_CACHE && result == RESULT_OK) showToast(getString(R.string.clean) + " ✓");
        if (request == REQUEST_WALLPAPER && result == RESULT_OK && data != null && data.getData() != null) {
            copyPicture(data.getData(), "wallpaper.jpg", () -> {
                cfg.wallpaper = true;
                refreshWallpaper();
                showToast(getString(R.string.wallpaper_set));
            });
        }
    }

    // ---- Building the screen ----

    private int dp(float v) {
        return Math.round(v * density);
    }

    private void build() {
        int w = root.getWidth(), h = root.getHeight();
        if (w == 0 || h == 0) return;
        builtW = w;
        builtH = h;
        if (root.getChildCount() > 1) root.removeViews(1, root.getChildCount() - 1);
        glassViews.clear();

        Insets in = insets();
        int pad = dp(12);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setPadding(pad + in.left, pad + in.top, pad + in.right, pad + in.bottom);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        int railW = Math.max(dp(84), Math.min(dp(104), w / 11));
        content.addView(buildRail(h - 2 * pad - in.top - in.bottom, railW), new LinearLayout.LayoutParams(railW, -1));

        FrameLayout area = new FrameLayout(this);
        LinearLayout.LayoutParams areaLp = new LinearLayout.LayoutParams(0, -1, 1f);
        areaLp.leftMargin = pad;
        content.addView(area, areaLp);

        pager = new Pager(this);
        FrameLayout.LayoutParams pagerLp = new FrameLayout.LayoutParams(-1, -1);
        pagerLp.bottomMargin = dp(22);
        area.addView(pager, pagerLp);
        dots = new LinearLayout(this);
        dots.setGravity(Gravity.CENTER);
        area.addView(dots, new FrameLayout.LayoutParams(-1, dp(18), Gravity.BOTTOM));
        pager.setListener(this::updateDots);

        toast = new TextView(this);
        toast.setTextColor(0xFFFFFFFF);
        toast.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        toast.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        toast.setPadding(dp(18), dp(10), dp(18), dp(10));
        toast.setBackground(glassFor(toast, dp(22)));
        toast.setAlpha(0f);
        toast.setVisibility(View.GONE);
        area.addView(toast, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        fillPages();
        updateClock();
        if (glass.wallpaper == null || glass.wallpaper.sharp.getWidth() != w || glass.wallpaper.sharp.getHeight() != h) {
            worker.execute(() -> {
                Wallpaper wp = Wallpaper.make(this, cfg, w, h);
                main.post(() -> {
                    glass.wallpaper = wp;
                    backdrop.invalidate();
                    for (View v : glassViews) v.invalidate();
                });
            });
        }
    }

    private static final class Insets {
        int left, top, right, bottom;
    }

    private Insets insets() {
        // The window already stops at the system bars, so nothing more to leave out.
        return new Insets();
    }

    private Glass glassFor(View owner, float radius) {
        glassViews.add(owner);
        return new Glass(glass, owner, radius, density);
    }

    private View buildRail(int railH, int railW) {
        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER_HORIZONTAL);
        rail.setPadding(0, dp(14), 0, dp(12));
        rail.setBackground(glassFor(rail, dp(26)));

        clock = text(22, true);
        rail.addView(clock, new LinearLayout.LayoutParams(-2, -2));
        date = text(12, false);
        date.setAlpha(0.75f);
        rail.addView(date, new LinearLayout.LayoutParams(-2, -2));

        // Dock: as many slots as fit, up to four, each icon at most 64 dp.
        int fixed = dp(14 + 30 + 18 + 12 + 12) + dp(76) + dp(48);
        // Dock icons shrink to fit short screens instead of being cut off.
        int slot = Math.min(dp(80), Math.max(dp(36), (railH - fixed) / MAX_DOCK));
        int icon = Math.min(Math.min(dp(64), railW - dp(20)), slot - dp(slot < dp(56) ? 6 : 12));
        List<App> dock = dockApps();
        LinearLayout dockBox = new LinearLayout(this);
        dockBox.setOrientation(LinearLayout.VERTICAL);
        dockBox.setGravity(Gravity.CENTER);
        for (App a : dock) {
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(a.icon);
            iv.setContentDescription(a.label);
            pressable(iv, a);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(icon, icon);
            lp.topMargin = lp.bottomMargin = (slot - icon) / 2;
            dockBox.addView(iv, lp);
        }
        LinearLayout.LayoutParams dockLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        dockLp.topMargin = dp(18);
        rail.addView(dockBox, dockLp);

        ImageView gear = new ImageView(this);
        gear.setImageResource(R.drawable.ic_settings);
        gear.setAlpha(0.85f);
        gear.setPadding(dp(8), dp(8), dp(8), dp(8));
        gear.setContentDescription(getString(R.string.settings));
        gear.setOnClickListener(this::settingsMenu);
        squish(gear);
        LinearLayout.LayoutParams gearLp = new LinearLayout.LayoutParams(dp(40), dp(40));
        gearLp.bottomMargin = dp(8);
        rail.addView(gear, gearLp);

        ring = new RamRing(this, cfg.accent);
        ring.setContentDescription(getString(R.string.clean));
        ring.setOnClickListener(v -> clean());
        ring.setOnLongClickListener(v -> {
            open(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS));
            return true;
        });
        squish(ring);
        rail.addView(ring, new LinearLayout.LayoutParams(dp(54), dp(54)));
        TextView label = text(11, true);
        label.setText(R.string.clean);
        label.setAlpha(0.85f);
        rail.addView(label, new LinearLayout.LayoutParams(-2, -2));
        ring.setUsed(Cleaner.usedFraction(this), cfg.accent);
        return rail;
    }

    private void fillPages() {
        if (pager == null || pager.getWidth() == 0 && builtW == 0) return;
        int keepPage = pager.page();
        pager.removeAllViews();
        int areaW = builtW - dp(24) - Math.max(dp(84), Math.min(dp(104), builtW / 11)) - dp(12);
        int areaH = builtH - dp(24) - dp(22);
        Grid g = Grid.fit(areaW, areaH, density, cfg.columns);
        List<App> list = gridApps();
        int dash = cfg.dashboard ? 1 : 0;
        int pages = g.pages(list.size()) + dash;
        bigSpeed = null;
        coolantTile = null;
        rangeTile = null;
        torqueBar = null;
        if (nowPlaying != null) nowPlaying.stop();
        bigClock = null;
        bigDate = null;
        nowPlaying = null;
        if (dash == 1) pager.addView(dashboard(areaW, areaH));
        int cellW = areaW / g.columns, cellH = areaH / g.rows;
        int labelH = cfg.showLabels ? dp(22) : 0;
        int icon = Math.min(dp(88), Math.min(Math.round(cellW * 0.66f), cellH - labelH - dp(16)));
        for (int p = 0; p < pages - dash; p++) {
            Page page = new Page(this, g.columns, g.rows);
            for (int i = p * g.perPage(); i < Math.min(list.size(), (p + 1) * g.perPage()); i++) {
                page.addView(cell(list.get(i), icon));
            }
            pager.addView(page);
        }
        pager.requestLayout();
        if (keepPage > 0) main.post(() -> pager.snapTo(keepPage));
        updateDots(Math.min(keepPage, pages - 1), pages);
    }

    /** The first page: a large clock and date with engine torque under it, and the speedometer. */
    private View dashboard(int areaW, int areaH) {
        LinearLayout d = new LinearLayout(this);
        d.setOrientation(LinearLayout.HORIZONTAL);
        int gap = dp(12);
        d.setPadding(0, 0, 0, gap);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.CENTER_HORIZONTAL);
        // Narrow screens (dense or small units): less padding so the music widget and date fit.
        int leftW = Math.round((areaW - dp(12)) / 2.25f);
        boolean compact = leftW < dp(340);
        int lp2 = compact ? dp(12) : dp(24);
        left.setPadding(lp2, compact ? dp(12) : dp(20), lp2, compact ? dp(12) : dp(20));
        left.setBackground(glassFor(left, dp(26)));
        bigClock = new RainbowClock(this, cfg.clockRainbow);
        clockBasePx = Math.min(areaH * 0.24f, areaW * 0.12f);
        applyClockStyle();
        // The clock sits in the same dark glass box as the music widget below it.
        FrameLayout clockBox = new FrameLayout(this);
        clockBox.setBackground(NowPlaying.darkPanel(this));
        clockBox.addView(bigClock, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams cblp = new LinearLayout.LayoutParams(-1, 0, 1f);
        cblp.bottomMargin = dp(10);
        left.addView(clockBox, cblp);
        bigDate = text(18, false);
        bigDate.setAlpha(0.8f);
        // Shrinks to fit rather than cutting the date short.
        bigDate.setAutoSizeTextTypeUniformWithConfiguration(11, 18, 1, TypedValue.COMPLEX_UNIT_SP);
        left.addView(bigDate, new LinearLayout.LayoutParams(-1, dp(compact ? 22 : 28)));
        if (cfg.musicWidget) {
            int npH = Math.max(dp(64), Math.min(dp(84), Math.round(areaH * 0.17f)));
            nowPlaying = new NowPlaying(this, npH, leftW - 2 * lp2, this::openMusicApp);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-1, npH);
            nlp.topMargin = dp(14);
            left.addView(nowPlaying, nlp);
            if (resumed) nowPlaying.start();
        }
        torqueBar = new TorqueBar(this, cfg.torqueMax);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, dp(48));
        tlp.topMargin = dp(14);
        left.addView(torqueBar, tlp);
        d.addView(left, new LinearLayout.LayoutParams(0, -1, 1f));

        // Right: the big digital speedometer with turbo boost, trip, coolant and fuel range.
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(dp(12), dp(12), dp(12), dp(12));
        right.setBackground(glassFor(right, dp(26)));
        bigSpeed = new BigSpeedView(this, cfg.mph ? "mph" : "km/h", cfg.boostPsi, cfg.boostMaxBar);
        bigSpeed.setTypeface(ClockFonts.typeface(this, clockFont));
        LinearLayout.LayoutParams speedLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        speedLp.leftMargin = speedLp.rightMargin = dp(6);
        right.addView(bigSpeed, speedLp);
        LinearLayout.LayoutParams tripLp = new LinearLayout.LayoutParams(-1, dp(52));
        tripLp.topMargin = dp(8);
        right.addView(tripRow(), tripLp);
        LinearLayout tiles = new LinearLayout(this);
        tiles.setOrientation(LinearLayout.HORIZONTAL);
        coolantTile = new InfoTile(this, R.drawable.ic_coolant, getString(R.string.coolant));
        rangeTile = new InfoTile(this, R.drawable.ic_fuel, getString(R.string.fuel_range));
        tiles.addView(coolantTile, new LinearLayout.LayoutParams(0, -1, 1f));
        LinearLayout.LayoutParams rangeLp = new LinearLayout.LayoutParams(0, -1, 1f);
        rangeLp.leftMargin = dp(10);
        tiles.addView(rangeTile, rangeLp);
        LinearLayout.LayoutParams tilesLp = new LinearLayout.LayoutParams(-1,
                Math.max(dp(64), Math.min(dp(84), Math.round(areaH * 0.18f))));
        tilesLp.topMargin = dp(10);
        right.addView(tiles, tilesLp);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, -1, 1.25f);
        rlp.leftMargin = gap;
        d.addView(right, rlp);
        updateClock();
        bigClock.setRunning(resumed);
        return d;
    }

    /** The trip row under the dials: TRIP, the distance, where it comes from, and Reset. */
    private View tripRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(6), dp(8), dp(6));
        row.setBackground(NowPlaying.darkPanel(this));
        TextView label = text(11, true);
        label.setText(R.string.trip);
        label.setLetterSpacing(0.12f);
        label.setAlpha(0.65f);
        row.addView(label, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout values = new LinearLayout(this);
        values.setOrientation(LinearLayout.VERTICAL);
        values.setPadding(dp(12), 0, dp(8), 0);
        tripValue = text(20, true);
        tripValue.setGravity(Gravity.START);
        tripValue.setFontFeatureSettings("tnum");
        tripNote = text(10, false);
        tripNote.setGravity(Gravity.START);
        tripNote.setAlpha(0.6f);
        values.addView(tripValue);
        values.addView(tripNote);
        row.addView(values, new LinearLayout.LayoutParams(0, -2, 1f));
        tripReset = text(13, true);
        tripReset.setText(R.string.trip_reset);
        tripReset.setPadding(dp(16), dp(8), dp(16), dp(8));
        android.graphics.drawable.GradientDrawable pill = new android.graphics.drawable.GradientDrawable();
        pill.setColor(0x24FFFFFF);
        pill.setCornerRadius(dp(18));
        pill.setStroke(Math.max(1, dp(1)), 0x4DFFFFFF);
        tripReset.setBackground(pill);
        tripReset.setOnClickListener(v -> resetTrip());
        squish(tripReset);
        row.addView(tripReset, new LinearLayout.LayoutParams(-2, -2));
        tripResetArmed = false;
        showTrip();
        return row;
    }

    private final Runnable disarmTrip = () -> {
        tripResetArmed = false;
        if (tripReset != null) {
            tripReset.setText(R.string.trip_reset);
            tripReset.setTextColor(0xFFFFFFFF);
        }
    };

    /** First tap arms it ("Tap again"), a second tap within 3 seconds resets: no accidental resets. */
    private void resetTrip() {
        if (!tripResetArmed) {
            tripResetArmed = true;
            tripReset.setText(R.string.trip_confirm);
            tripReset.setTextColor(0xFFFF9F0A);
            main.removeCallbacks(disarmTrip);
            main.postDelayed(disarmTrip, 3_000);
            return;
        }
        main.removeCallbacks(disarmTrip);
        disarmTrip.run();
        trip.reset();
        saveTrip();
        showTrip();
        showToast(getString(R.string.trip_was_reset));
    }

    private void showTrip() {
        if (tripValue == null) return;
        tripValue.setText(TripMeter.format(trip.distance()) + (cfg.mph ? " mi" : " km"));
        tripNote.setText(trip.fromOdometer() ? R.string.trip_from_odometer : R.string.trip_from_speed);
    }

    private void saveTrip() {
        tripSavedAt = SystemClock.elapsedRealtime();
        prefs.edit().putFloat("trip_base", trip.baseOdometer).putFloat("trip_acc", trip.accumulated).apply();
    }

    // ---- Settings menu: wallpaper and icon style ----

    /** The settings panel: every option as a big glass button, two to a row, in the middle of the screen. */
    private void settingsMenu(View anchor) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        android.app.Dialog[] holder = new android.app.Dialog[1];
        Object[][] items = {
                {R.string.wallpaper_pick, (Runnable) () -> pickPicture(REQUEST_WALLPAPER)},
                {R.string.wallpaper_reset, (Runnable) this::resetWallpaper},
                {"glass".equals(iconStyle) ? R.string.icons_color : R.string.icons_glass,
                        (Runnable) () -> setIconStyle("glass".equals(iconStyle) ? "color" : "glass")},
                {R.string.clock_style, (Runnable) this::clockDialog},
                {R.string.data_source, (Runnable) this::dataSourceDialog},
        };
        LinearLayout row = null;
        for (int i = 0; i < items.length; i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
                rlp.bottomMargin = dp(10);
                body.addView(row, rlp);
            }
            TextView b = text(15, true);
            b.setText((int) items[i][0]);
            b.setSingleLine(false);
            b.setMinHeight(dp(52));
            b.setPadding(dp(14), dp(12), dp(14), dp(12));
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setColor(0x1FFFFFFF);
            g.setCornerRadius(dp(16));
            g.setStroke(Math.max(1, dp(1)), 0x40FFFFFF);
            b.setBackground(g);
            Runnable action = (Runnable) items[i][1];
            b.setOnClickListener(v -> {
                if (holder[0] != null) holder[0].dismiss();
                action.run();
            });
            squish(b);
            b.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i % 2 == 1) blp.leftMargin = dp(10);
            row.addView(b, blp);
        }
        // An odd number of options: keep the last one half width.
        if (items.length % 2 == 1 && row != null) {
            LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(0, dp(1), 1f);
            flp.leftMargin = dp(10);
            row.addView(new View(this), flp);
        }
        holder[0] = panelDialog(R.string.settings, body);
        holder[0].show();
    }

    private void pickPicture(int request) {
        Intent pick = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        Intent any = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
        for (Intent i : new Intent[]{pick, any}) {
            try {
                startActivityForResult(i, request);
                return;
            } catch (RuntimeException ignored) {
                // No app for this one: try the next.
            }
        }
        showToast(getString(R.string.no_gallery));
    }

    /** Copies the chosen picture next to config.json as {@code name}, checks it is a picture, then runs done. */
    private void copyPicture(Uri uri, String name, Runnable done) {
        java.io.File dir = CarConfig.dir(this);
        if (dir == null) {
            showToast(getString(R.string.wallpaper_failed));
            return;
        }
        worker.execute(() -> {
            java.io.File tmp = new java.io.File(dir, name + ".tmp"), dst = new java.io.File(dir, name);
            boolean ok = false;
            try (java.io.InputStream in = getContentResolver().openInputStream(uri);
                 java.io.OutputStream out = new java.io.FileOutputStream(tmp)) {
                if (in != null) {
                    byte[] buf = new byte[64 * 1024];
                    long total = 0;
                    int n;
                    while ((n = in.read(buf)) > 0 && total < 40L * 1024 * 1024) {
                        out.write(buf, 0, n);
                        total += n;
                    }
                    ok = total > 0;
                }
            } catch (Exception e) {
                ok = false;
            }
            // Check it really is a picture before it replaces the old one.
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            if (ok) android.graphics.BitmapFactory.decodeFile(tmp.getPath(), o);
            ok = ok && o.outWidth > 0 && tmp.renameTo(dst);
            if (!ok) tmp.delete();
            final boolean copied = ok;
            main.post(() -> {
                if (!copied) {
                    showToast(getString(R.string.wallpaper_failed));
                    return;
                }
                done.run();
            });
        });
    }

    private void resetWallpaper() {
        java.io.File dir = CarConfig.dir(this);
        if (dir != null) new java.io.File(dir, "wallpaper.jpg").delete();
        refreshWallpaper();
    }

    private void refreshWallpaper() {
        glass.wallpaper = null;
        backdrop.invalidate();
        builtW = 0;
        build();
    }

    private void setIconStyle(String style) {
        iconStyle = style;
        prefs.edit().putString("icon_style", style).apply();
        synchronized (iconCache) {
            iconCache.clear();
        }
        loadApps();
    }

    private static String obdStateLabel(int state) {
        switch (state) {
            case ObdLink.CONNECTING:
                return "OBD CONNECTING";
            case ObdLink.CONNECTED:
                return "OBD-II";
            case ObdLink.NO_BLUETOOTH:
                return "BLUETOOTH OFF";
            case ObdLink.NOT_PAIRED:
                return "OBD NOT PAIRED";
            case ObdLink.FAILED:
                return "OBD NO CONNECTION";
            default:
                return "OBD OFF";
        }
    }

    // ---- Clock style dialog ----

    private void applyClockStyle() {
        if (bigSpeed != null) bigSpeed.setTypeface(ClockFonts.typeface(this, clockFont));
        if (bigClock == null) return;
        bigClock.setTypeface(ClockFonts.typeface(this, clockFont));
        bigClock.setTextSizePx(clockBasePx * clockSize / 100f * ClockFonts.widthFactor(clockFont));
        bigClock.setStretch(clockHeight / 100f);
        bigClock.setGlow(clockGlow);
        bigClock.setRainbow(clockRainbow);
    }

    /** A dark glass panel dialog with a title, used by the clock and data source settings. */
    private android.app.Dialog panelDialog(int title, LinearLayout body) {
        android.app.Dialog d = new android.app.Dialog(this);
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(18), dp(22), dp(14));
        // Clear glass: the blurred wallpaper under the panel, a dark veil for reading, a bright rim.
        box.setBackground(new Glass(glass, box, dp(24), density, 0x8C1C1E2A));
        TextView t = text(18, true);
        t.setText(title);
        t.setGravity(Gravity.START);
        box.addView(t, new LinearLayout.LayoutParams(-1, -2));
        // The body scrolls when the screen is short, so Done always stays on screen.
        int maxBody = Math.max(dp(120), (builtH > 0 ? builtH : getResources().getDisplayMetrics().heightPixels) - dp(150));
        android.widget.ScrollView scroll = new android.widget.ScrollView(this) {
            @Override
            protected void onMeasure(int wSpec, int hSpec) {
                super.onMeasure(wSpec, MeasureSpec.makeMeasureSpec(maxBody, MeasureSpec.AT_MOST));
            }
        };
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(body, new android.widget.FrameLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.topMargin = dp(12);
        box.addView(scroll, blp);
        TextView done = text(16, true);
        done.setText(R.string.done);
        done.setTextColor(0xFF64D2FF);
        done.setPadding(dp(16), dp(10), dp(4), dp(4));
        done.setGravity(Gravity.END);
        done.setOnClickListener(v -> d.dismiss());
        box.addView(done, new LinearLayout.LayoutParams(-1, -2));
        d.setContentView(box);
        glassViews.add(box);
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
            d.getWindow().setLayout(Math.min(builtW - dp(48), dp(620)), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        return d;
    }

    /** A glass slider: thin frosted track, white glass knob. */
    private void glassSeek(android.widget.SeekBar bar) {
        android.graphics.drawable.GradientDrawable knob = new android.graphics.drawable.GradientDrawable();
        knob.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        knob.setColors(new int[]{0xFFFFFFFF, 0xFFD5D9E1});
        knob.setGradientType(android.graphics.drawable.GradientDrawable.RADIAL_GRADIENT);
        knob.setGradientRadius(dp(14));
        knob.setStroke(Math.max(1, dp(1)), 0xB3FFFFFF);
        knob.setSize(dp(24), dp(24));
        bar.setThumb(knob);
        bar.setThumbOffset(dp(12));
        // A thin rounded track centred in the slider whatever its height, filled up to the knob.
        bar.setProgressDrawable(new GlassTrack(dp(6), dp(1)));
        bar.setPadding(dp(14), 0, dp(14), 0);
    }

    /** An iPhone-style switch: white glass knob, green track when on, frosted when off. */
    private void glassSwitch(android.widget.Switch sw) {
        int[][] states = {{android.R.attr.state_checked}, {}};
        sw.setThumbTintList(new android.content.res.ColorStateList(states, new int[]{0xFFFFFFFF, 0xFFF2F4F8}));
        sw.setTrackTintList(new android.content.res.ColorStateList(states, new int[]{0xFF30D158, 0x59FFFFFF}));
        sw.setTrackTintMode(android.graphics.PorterDuff.Mode.SRC);
    }

    private void clockDialog() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(chips);
        List<TextView> sample = new ArrayList<>();
        Runnable mark = () -> {
            for (int i = 0; i < chips.getChildCount(); i++) {
                View chip = chips.getChildAt(i);
                boolean on = ClockFonts.KEYS[i].equals(clockFont);
                android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
                g.setCornerRadius(dp(16));
                g.setColor(on ? 0x400A84FF : 0x1AFFFFFF);
                g.setStroke(dp(on ? 2 : 1), on ? 0xFF0A84FF : 0x33FFFFFF);
                chip.setBackground(g);
            }
        };
        for (int i = 0; i < ClockFonts.KEYS.length; i++) {
            String key = ClockFonts.KEYS[i];
            if (key.equals("custom") && ClockFonts.customFile(this) == null) continue;
            LinearLayout chip = new LinearLayout(this);
            chip.setOrientation(LinearLayout.VERTICAL);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(14), dp(8), dp(14), dp(8));
            TextView digits = text(30, false);
            digits.setText(DateFormat.format(DateFormat.is24HourFormat(this) ? "H:mm" : "h:mm", new Date()));
            digits.setTypeface(ClockFonts.typeface(this, key));
            TextView name = text(12, false);
            name.setText(ClockFonts.NAMES[i]);
            name.setAlpha(0.75f);
            chip.addView(digits);
            chip.addView(name);
            chip.setOnClickListener(v -> {
                clockFont = key;
                prefs.edit().putString("clock_font", key).apply();
                applyClockStyle();
                mark.run();
            });
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
            clp.rightMargin = dp(10);
            chips.addView(chip, clp);
            sample.add(digits);
        }
        mark.run();
        body.addView(scroll, new LinearLayout.LayoutParams(-1, -2));

        TextView sizeLabel = text(14, false);
        sizeLabel.setGravity(Gravity.START);
        sizeLabel.setText(getString(R.string.clock_size, clockSize));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.topMargin = dp(16);
        body.addView(sizeLabel, slp);
        android.widget.SeekBar bar = new android.widget.SeekBar(this);
        glassSeek(bar);
        bar.setMax(80);
        bar.setProgress(clockSize - 60);
        bar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar b, int p, boolean user) {
                clockSize = 60 + p;
                sizeLabel.setText(getString(R.string.clock_size, clockSize));
                applyClockStyle();
            }

            @Override
            public void onStartTrackingTouch(android.widget.SeekBar b) {
            }

            @Override
            public void onStopTrackingTouch(android.widget.SeekBar b) {
                prefs.edit().putInt("clock_size", clockSize).apply();
            }
        });
        body.addView(bar, new LinearLayout.LayoutParams(-1, dp(40)));

        // Height: stretches the digits up and down, the width stays.
        TextView heightLabel = text(14, false);
        heightLabel.setGravity(Gravity.START);
        heightLabel.setText(getString(R.string.clock_height, clockHeight));
        body.addView(heightLabel, new LinearLayout.LayoutParams(-1, -2));
        android.widget.SeekBar tall = new android.widget.SeekBar(this);
        glassSeek(tall);
        tall.setMax(150);
        tall.setProgress(clockHeight - 100);
        tall.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar b, int p, boolean user) {
                clockHeight = 100 + p;
                heightLabel.setText(getString(R.string.clock_height, clockHeight));
                applyClockStyle();
            }

            @Override
            public void onStartTrackingTouch(android.widget.SeekBar b) {
            }

            @Override
            public void onStopTrackingTouch(android.widget.SeekBar b) {
                prefs.edit().putInt("clock_height", clockHeight).apply();
            }
        });
        body.addView(tall, new LinearLayout.LayoutParams(-1, dp(40)));

        android.widget.Switch rainbowSwitch = new android.widget.Switch(this);
        rainbowSwitch.setText(R.string.clock_rainbow);
        rainbowSwitch.setTextColor(0xFFFFFFFF);
        rainbowSwitch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        rainbowSwitch.setChecked(clockRainbow);
        glassSwitch(rainbowSwitch);
        rainbowSwitch.setOnCheckedChangeListener((v, on) -> {
            clockRainbow = on;
            prefs.edit().putBoolean("clock_rainbow", on).apply();
            applyClockStyle();
            if (bigClock != null) bigClock.setRunning(resumed);
        });
        LinearLayout.LayoutParams rlp2 = new LinearLayout.LayoutParams(-1, -2);
        rlp2.bottomMargin = dp(6);
        body.addView(rainbowSwitch, rlp2);
        android.widget.Switch glowSwitch = new android.widget.Switch(this);
        glowSwitch.setText(R.string.clock_glow);
        glowSwitch.setTextColor(0xFFFFFFFF);
        glowSwitch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        glowSwitch.setChecked(clockGlow);
        glassSwitch(glowSwitch);
        glowSwitch.setOnCheckedChangeListener((v, on) -> {
            clockGlow = on;
            prefs.edit().putBoolean("clock_glow", on).apply();
            applyClockStyle();
        });
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-1, -2);
        glp.bottomMargin = dp(8);
        body.addView(glowSwitch, glp);
        TextView hint = text(11, false);
        hint.setGravity(Gravity.START);
        hint.setSingleLine(false);
        hint.setAlpha(0.6f);
        hint.setText(R.string.clock_custom_hint);
        body.addView(hint, new LinearLayout.LayoutParams(-1, -2));
        if (pager != null) pager.snapTo(0);
        panelDialog(R.string.clock_style, body).show();
    }

    // ---- Car data source dialog: CAN bus + GPS, or Bluetooth OBD-II ----

    @android.annotation.SuppressLint("MissingPermission")
    private void dataSourceDialog() {
        if (Build.VERSION.SDK_INT >= 31
                && checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.BLUETOOTH_CONNECT}, 4);
        }
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        android.app.Dialog[] holder = new android.app.Dialog[1];
        List<View> rows = new ArrayList<>();
        String[] chosen = {dataSource, obdAddress};
        Runnable refresh = () -> {
            for (View row : rows) {
                String[] tag = (String[]) row.getTag();
                boolean on = tag[0].equals(chosen[0]) && (tag[0].equals("can") || tag[1].equals(chosen[1]));
                android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
                g.setCornerRadius(dp(14));
                g.setColor(on ? 0x330A84FF : 0x14FFFFFF);
                g.setStroke(dp(on ? 2 : 1), on ? 0xFF0A84FF : 0x26FFFFFF);
                row.setBackground(g);
            }
        };
        java.util.function.BiConsumer<String[], String[]> addRow = (tag, lines) -> {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(14), dp(10), dp(14), dp(10));
            row.setTag(tag);
            TextView a = text(15, true);
            a.setGravity(Gravity.START);
            a.setText(lines[0]);
            TextView b = text(12, false);
            b.setGravity(Gravity.START);
            b.setSingleLine(false);
            b.setAlpha(0.7f);
            b.setText(lines[1]);
            row.addView(a);
            row.addView(b);
            row.setOnClickListener(v -> {
                chosen[0] = tag[0];
                if (tag[0].equals("obd")) chosen[1] = tag[1];
                refresh.run();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(8);
            body.addView(row, lp);
            rows.add(row);
        };
        addRow.accept(new String[]{"can", ""}, new String[]{getString(R.string.source_can), getString(R.string.source_can_hint)});
        TextView obdTitle = text(13, true);
        obdTitle.setGravity(Gravity.START);
        obdTitle.setAlpha(0.7f);
        obdTitle.setText(R.string.source_obd);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(-1, -2);
        olp.topMargin = dp(8);
        olp.bottomMargin = dp(6);
        body.addView(obdTitle, olp);
        java.util.Set<android.bluetooth.BluetoothDevice> paired = null;
        try {
            android.bluetooth.BluetoothAdapter bt = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
            if (bt != null) paired = bt.getBondedDevices();
        } catch (SecurityException ignored) {
        }
        if (paired != null) {
            for (android.bluetooth.BluetoothDevice dev : paired) {
                String name = dev.getName() == null ? dev.getAddress() : dev.getName();
                boolean likely = name.toUpperCase(Locale.ROOT).matches(".*(OBD|ELM|CARISTA|VLINK|V-LINK|OBDII|KONNWEI|VGATE).*");
                addRow.accept(new String[]{"obd", dev.getAddress()},
                        new String[]{name + (likely ? "  ·  OBD" : ""), dev.getAddress()});
            }
        }
        TextView help = text(12, false);
        help.setGravity(Gravity.START);
        help.setSingleLine(false);
        help.setAlpha(0.65f);
        help.setText(R.string.source_obd_hint);
        body.addView(help, new LinearLayout.LayoutParams(-1, -2));
        TextView btSettings = text(14, true);
        btSettings.setGravity(Gravity.START);
        btSettings.setTextColor(0xFF64D2FF);
        btSettings.setText(R.string.open_bluetooth);
        btSettings.setPadding(0, dp(10), 0, 0);
        btSettings.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            open(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
        });
        body.addView(btSettings, new LinearLayout.LayoutParams(-2, -2));
        TextView finder = text(14, true);
        finder.setGravity(Gravity.START);
        finder.setTextColor(0xFF64D2FF);
        finder.setText(R.string.can_finder);
        finder.setPadding(0, dp(12), 0, 0);
        finder.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            canFinderDialog();
        });
        body.addView(finder, new LinearLayout.LayoutParams(-2, -2));
        refresh.run();
        android.app.Dialog d = panelDialog(R.string.data_source, body);
        holder[0] = d;
        d.setOnDismissListener(x -> {
            if (chosen[0].equals("obd") && chosen[1].isEmpty()) chosen[0] = "can";
            if (chosen[0].equals(dataSource) && chosen[1].equals(obdAddress)) return;
            dataSource = chosen[0];
            obdAddress = chosen[1];
            prefs.edit().putString("data_source", dataSource).putString("obd_address", obdAddress).apply();
            carData.stop();
            carData.setSource(dataSource, obdAddress);
            if (resumed) carData.start();
            showToast(getString("obd".equals(dataSource) ? R.string.source_now_obd : R.string.source_now_can));
        });
        d.show();
    }

    /** Shows what the CAN finder sees, for the owner to photograph and send. */
    private void canFinderDialog() {
        if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, 5);
        }
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        TextView hint = text(12, false);
        hint.setGravity(Gravity.START);
        hint.setSingleLine(false);
        hint.setAlpha(0.7f);
        hint.setText(R.string.can_finder_hint);
        body.addView(hint, new LinearLayout.LayoutParams(-1, -2));
        TextView out = new TextView(this);
        out.setTextColor(0xFFFFFFFF);
        out.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        out.setTypeface(Typeface.MONOSPACE);
        out.setTextIsSelectable(true);
        out.setText(R.string.can_finder_looking);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(-1, -2);
        olp.topMargin = dp(10);
        body.addView(out, olp);
        worker.execute(() -> {
            String r = CanFinder.report(this);
            main.post(() -> out.setText(r));
        });
        panelDialog(R.string.can_finder, body).show();
    }

        /** Puts one snapshot of car data on the dashboard. */
    private void showCarData(CarData.Reading r) {
        trip.odometer(r.odometer);
        trip.speed(r.speed, SystemClock.elapsedRealtime());
        showTrip();
        if (SystemClock.elapsedRealtime() - tripSavedAt > 15_000) saveTrip();
        if (bigSpeed == null) return;
        if (r.obdState != lastObdState) {
            if (r.obdState == ObdLink.CONNECTED) showToast(getString(R.string.obd_connected));
            else if (r.obdState == ObdLink.FAILED && lastObdState != ObdLink.FAILED) showToast(getString(R.string.obd_failed));
            lastObdState = r.obdState;
        }
        int src = r.speedSource;
        String missing = carData.usesObd() ? obdStateLabel(r.obdState) : "NO DATA";
        bigSpeed.set(r.speed, src == SpeedMath.CAN ? r.carLabel : src == SpeedMath.GPS ? "GPS"
                : carData.usesObd() ? missing : carData.hasGpsPermission() ? "NO SIGNAL" : "NO GPS PERMISSION", r.boost);
        if (torqueBar != null) torqueBar.set(r.torque, r.torqueEstimated);
        if (Float.isNaN(r.coolant)) {
            coolantTile.set("-- °C", Float.NaN, 0x80FFFFFF, getString(R.string.no_data));
        } else {
            int level = SpeedMath.coolantLevel(r.coolant);
            coolantTile.set(Math.round(r.coolant) + " °C", SpeedMath.coolantFraction(r.coolant),
                    level == SpeedMath.COLD ? 0xFF64D2FF : level == SpeedMath.HOT ? 0xFFFF453A : 0xFF30D158,
                    getString(level == SpeedMath.COLD ? R.string.coolant_cold
                            : level == SpeedMath.HOT ? R.string.coolant_hot : R.string.coolant_normal));
        }
        if (Float.isNaN(r.range)) {
            rangeTile.set("-- km", Float.NaN, 0x80FFFFFF, getString(R.string.no_data));
        } else {
            boolean low = r.range < SpeedMath.RANGE_LOW_KM, veryLow = r.range < SpeedMath.RANGE_LOW_KM / 3f;
            rangeTile.set(Math.round(r.range) + " km", SpeedMath.rangeFraction(r.range),
                    veryLow ? 0xFFFF453A : low ? 0xFFFF9F0A : 0xFF30D158,
                    getString(low ? R.string.range_low : R.string.range_ok));
        }
    }

    /** The music widget's play button when nothing is playing: the dock's music app, else the first one. */
    /**
     * The music widget's play button when nothing is playing: the music files on the unit and USB sticks,
     * with Glass Car's own player; with none, the dock's music app.
     */
    private void openMusicApp() {
        String perm = Build.VERSION.SDK_INT >= 33 ? "android.permission.READ_MEDIA_AUDIO"
                : android.Manifest.permission.READ_EXTERNAL_STORAGE;
        if (checkSelfPermission(perm) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{perm}, 6);
            return;
        }
        worker.execute(() -> {
            boolean any = !LocalMusic.songs(this).isEmpty();
            main.post(() -> {
                if (any) LocalPlayerService.play(this);
                else openOtherMusicApp();
            });
        });
    }

    private void openOtherMusicApp() {
        showToast(getString(R.string.no_local_music));
        for (String pkg : DOCK_GUESSES[1]) {
            App a = first(pkg);
            if (a != null) {
                launch(a, nowPlaying != null ? nowPlaying : root);
                return;
            }
        }
        showToast(getString(R.string.nothing_playing));
    }

    private View cell(App a, int iconPx) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        c.setClipChildren(false);
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(a.icon);
        c.addView(iv, new LinearLayout.LayoutParams(iconPx, iconPx));
        if (cfg.showLabels) {
            TextView t = text(13, false);
            t.setText(a.label);
            t.setMaxWidth(Math.round(iconPx * 1.5f));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.topMargin = dp(6);
            c.addView(t, lp);
        }
        c.setContentDescription(a.label);
        pressable(c, a);
        return c;
    }

    private TextView text(float sp, boolean bold) {
        TextView t = new TextView(this);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setGravity(Gravity.CENTER);
        t.setShadowLayer(dp(3), 0, dp(1), 0x99000000);
        return t;
    }

    /** Shrinks a little while pressed, like a real button under a finger. */
    private void squish(View v) {
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.88f).scaleY(0.88f).setDuration(90).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    private void pressable(View v, App a) {
        squish(v);
        v.setOnClickListener(view -> launch(a, view));
        v.setOnLongClickListener(view -> {
            appMenu(a, view);
            return true;
        });
    }

    private void updateDots(int page, int count) {
        if (dots == null) return;
        dots.removeAllViews();
        if (count <= 1) return;
        for (int i = 0; i < count; i++) {
            View d = new View(this);
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            g.setColor(i == page ? 0xFFFFFFFF : 0x59FFFFFF);
            d.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(7), dp(7));
            lp.leftMargin = lp.rightMargin = dp(4);
            dots.addView(d, lp);
        }
    }

    private void updateClock() {
        if (clock == null) return;
        Date now = new Date();
        String pattern = DateFormat.is24HourFormat(this) ? "H:mm" : "h:mm";
        clock.setText(DateFormat.format(pattern, now));
        date.setText(DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEd"), now));
        if (bigClock != null) {
            bigClock.setText(DateFormat.format(pattern, now));
            bigDate.setText(DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM"), now));
        }
    }

    private void showToast(CharSequence msg) {
        if (toast == null) return;
        toast.setText(msg);
        toast.setVisibility(View.VISIBLE);
        toast.animate().cancel();
        toast.setTranslationY(-dp(12));
        toast.animate().alpha(1f).translationY(0).setStartDelay(0).setDuration(200).withEndAction(() ->
                toast.animate().alpha(0f).setStartDelay(2600).setDuration(300)
                        .withEndAction(() -> toast.setVisibility(View.GONE)).start()).start();
    }

    // ---- Apps ----

    private void loadApps() {
        worker.execute(() -> {
            LauncherApps la = (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
            List<LauncherActivityInfo> infos = la.getActivityList(null, Process.myUserHandle());
            List<App> list = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            int iconPx = dp(96);
            for (LauncherActivityInfo info : infos) {
                String pkg = info.getComponentName().getPackageName();
                if (pkg.equals(getPackageName()) || !seen.add(info.getComponentName().flattenToString())) continue;
                App a = new App(pkg, String.valueOf(info.getLabel()), info.getComponentName());
                String key = a.component.flattenToString();
                Bitmap cached;
                synchronized (iconCache) {
                    cached = iconCache.get(key);
                }
                if (cached == null) {
                    try {
                        cached = renderIcon(info.getIcon(0), iconPx, "glass".equals(iconStyle));
                    } catch (RuntimeException | OutOfMemoryError e) {
                        cached = renderIcon(getDrawable(R.drawable.ic_car_home), iconPx, "glass".equals(iconStyle));
                    }
                    synchronized (iconCache) {
                        iconCache.put(key, cached);
                    }
                }
                a.icon = cached;
                list.add(a);
            }
            Collator col = Collator.getInstance();
            Collections.sort(list, (x, y) -> col.compare(x.label, y.label));
            main.post(() -> {
                apps = list;
                if (builtW > 0) {
                    builtW = 0;
                    build();
                }
            });
        });
    }

    private Set<String> launchable() {
        Set<String> s = new HashSet<>();
        for (App a : apps) s.add(a.pkg);
        return s;
    }

    private List<App> gridApps() {
        List<App> out = new ArrayList<>();
        for (App a : apps) if (!cfg.hiddenApps.contains(a.pkg)) out.add(a);
        return out;
    }

    private App first(String pkg) {
        for (App a : apps) if (a.pkg.equals(pkg)) return a;
        return null;
    }

    private App firstContaining(String[] parts) {
        for (App a : apps) {
            String p = a.pkg.toLowerCase(Locale.ROOT);
            for (String part : parts) if (p.contains(part)) return a;
        }
        return null;
    }

    /** The person's own dock, else config.json's, else a guess: maps, music, CarPlay, phone. */
    private List<App> dockApps() {
        LinkedHashSet<App> out = new LinkedHashSet<>();
        String saved = prefs.getString("dock", null);
        List<String> wanted = saved != null ? Arrays.asList(saved.split(",")) : cfg.dockApps;
        for (String pkg : wanted) {
            App a = first(pkg.trim());
            if (a != null) out.add(a);
        }
        if (saved == null && wanted.isEmpty()) {
            for (String[] group : DOCK_GUESSES) {
                for (String pkg : group) {
                    App a = first(pkg);
                    if (a != null) {
                        out.add(a);
                        break;
                    }
                }
            }
            App proj = firstContaining(PROJECTION_PARTS);
            if (proj != null) out.add(proj);
            App phone = firstContaining(PHONE_PARTS);
            if (phone != null) out.add(phone);
            for (App a : apps) {
                if (out.size() >= MAX_DOCK) break;
                out.add(a);
            }
        }
        List<App> list = new ArrayList<>(out);
        return list.size() > MAX_DOCK ? list.subList(0, MAX_DOCK) : list;
    }

    private void saveDock(List<App> dock) {
        StringBuilder sb = new StringBuilder();
        for (App a : dock) {
            if (sb.length() > 0) sb.append(',');
            sb.append(a.pkg);
        }
        prefs.edit().putString("dock", sb.toString()).apply();
        builtW = 0;
        build();
    }

    private void appMenu(App a, View anchor) {
        PopupMenu m = new PopupMenu(this, anchor);
        List<App> dock = new ArrayList<>(dockApps());
        boolean inDock = dock.contains(a);
        m.getMenu().add(0, 1, 0, inDock ? R.string.remove_from_dock : R.string.move_to_dock);
        m.getMenu().add(0, 2, 1, R.string.app_info);
        m.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                if (inDock) dock.remove(a);
                else {
                    if (dock.size() >= MAX_DOCK) dock.remove(dock.size() - 1);
                    dock.add(0, a);
                }
                saveDock(dock);
            } else {
                open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg)));
            }
            return true;
        });
        m.show();
    }

    private void launch(App a, View from) {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        i.setComponent(a.component);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            Bundle opts = ActivityOptions.makeScaleUpAnimation(from, 0, 0, from.getWidth(), from.getHeight()).toBundle();
            startActivity(i, opts);
        } catch (RuntimeException e) {
            Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show();
        }
    }

    private void open(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show();
        }
    }

    // ---- Clean ----

    private void clean() {
        showToast(getString(R.string.cleaning));
        prefs.edit().putLong("auto_clean", SystemClock.elapsedRealtime()).apply();
        Cleaner.cleanRam(this, launchable(), cfg.keepRunning, (closed, freed, used) -> {
            if (ring != null) ring.setUsed(used, cfg.accent);
            String free = android.text.format.Formatter.formatShortFileSize(this, freed);
            showToast(getString(R.string.clean_done, closed, free));
            if (Cleaner.askClearCaches(this)) return;
            // Android 9/10 (and 11+ without "All files access"): trim through the debugging service.
            Cleaner.trimCaches(this, true, (result, freedCache) -> main.postDelayed(() -> {
                if (result == Cleaner.TRIM_DONE) {
                    showToast(getString(R.string.cache_done,
                            android.text.format.Formatter.formatShortFileSize(this, freedCache)));
                } else if (result == Cleaner.TRIM_NOT_ALLOWED) {
                    showToast(getString(R.string.cache_allow));
                } else {
                    showToast(getString(R.string.cache_no_adb));
                }
            }, 1_500));
        });
    }

    // ---- Icons ----

    /**
     * Draws an app icon as a full-bleed rounded square with a faint glass rim. Adaptive icons fill the
     * square edge to edge; older icons sit on a dark tile, so every icon has the same shape.
     */
    static Bitmap renderIcon(Drawable d, int size, boolean glassStyle) {
        if (glassStyle) return renderGlassIcon(d, size);
        Bitmap content = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(content);
        if (Build.VERSION.SDK_INT >= 26 && d instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable a = (AdaptiveIconDrawable) d;
            int pad = size / 4;
            Drawable bg = a.getBackground(), fg = a.getForeground();
            if (bg != null) {
                bg.setBounds(-pad, -pad, size + pad, size + pad);
                bg.draw(c);
            } else {
                c.drawColor(0xFFFFFFFF);
            }
            if (fg != null) {
                fg.setBounds(-pad, -pad, size + pad, size + pad);
                fg.draw(c);
            }
        } else {
            Paint tile = new Paint(Paint.ANTI_ALIAS_FLAG);
            tile.setShader(new LinearGradient(0, 0, 0, size, 0xFF3A3A3C, 0xFF1C1C1E, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, size, size, tile);
            int inset = Math.round(size * 0.14f);
            if (d != null) {
                d.setBounds(inset, inset, size - inset, size - inset);
                d.draw(c);
            }
        }
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas oc = new Canvas(out);
        float r = size * 0.225f;
        RectF rect = new RectF(0, 0, size, size);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setShader(new BitmapShader(content, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        oc.drawRoundRect(rect, r, r, p);
        Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Math.max(1f, size / 96f));
        rim.setShader(new LinearGradient(0, 0, size, size, 0x55FFFFFF, 0x10FFFFFF, Shader.TileMode.CLAMP));
        float h = rim.getStrokeWidth() / 2;
        oc.drawRoundRect(new RectF(h, h, size - h, size - h), r, r, rim);
        content.recycle();
        return out;
    }

    /**
     * The clear glass icon style: a see-through frosted tile (the wallpaper shows through it), a soft shine
     * at the top and a bright rim, with the app's symbol in white. Adaptive icons give their symbol layer;
     * older icons are drawn in soft grey-white so they stay recognisable.
     */
    static Bitmap renderGlassIcon(Drawable d, int size) {
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        float r = size * 0.225f;
        RectF rect = new RectF(0, 0, size, size);
        Paint tile = new Paint(Paint.ANTI_ALIAS_FLAG);
        tile.setShader(new LinearGradient(0, 0, 0, size, 0x47FFFFFF, 0x14FFFFFF, Shader.TileMode.CLAMP));
        c.drawRoundRect(rect, r, r, tile);
        Paint shine = new Paint(Paint.ANTI_ALIAS_FLAG);
        shine.setShader(new LinearGradient(0, 0, 0, size * 0.5f, 0x33FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(size * 0.04f, size * 0.03f, size * 0.96f, size * 0.5f), r * 0.85f, r * 0.85f, shine);

        Bitmap glyph = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas g = new Canvas(glyph);
        boolean adaptive = Build.VERSION.SDK_INT >= 26 && d instanceof AdaptiveIconDrawable
                && ((AdaptiveIconDrawable) d).getForeground() != null;
        if (adaptive) {
            int pad = size / 4;
            Drawable fg = ((AdaptiveIconDrawable) d).getForeground();
            fg.setBounds(-pad, -pad, size + pad, size + pad);
            fg.draw(g);
        } else if (d != null) {
            int inset = Math.round(size * 0.18f);
            d.setBounds(inset, inset, size - inset, size - inset);
            d.draw(g);
        }
        Paint glyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        if (adaptive) {
            // The symbol's shape in white.
            glyphPaint.setColorFilter(new android.graphics.PorterDuffColorFilter(0xF2FFFFFF,
                    android.graphics.PorterDuff.Mode.SRC_IN));
        } else {
            android.graphics.ColorMatrix cm = new android.graphics.ColorMatrix();
            cm.setSaturation(0f);
            android.graphics.ColorMatrix lift = new android.graphics.ColorMatrix(new float[]{
                    0.6f, 0, 0, 0, 100, 0, 0.6f, 0, 0, 100, 0, 0, 0.6f, 0, 100, 0, 0, 0, 0.9f, 0});
            cm.postConcat(lift);
            glyphPaint.setColorFilter(new android.graphics.ColorMatrixColorFilter(cm));
        }
        glyphPaint.setShadowLayer(size * 0.03f, 0, size * 0.01f, 0x40000000);
        c.drawBitmap(glyph, 0, 0, glyphPaint);
        glyph.recycle();

        Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Math.max(1f, size / 64f));
        rim.setShader(new LinearGradient(0, 0, size, size, new int[]{0xB3FFFFFF, 0x1AFFFFFF, 0x66FFFFFF},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        float h = rim.getStrokeWidth() / 2;
        c.drawRoundRect(new RectF(h, h, size - h, size - h), r, r, rim);
        return out;
    }
}
