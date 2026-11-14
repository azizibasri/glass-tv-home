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
import android.view.WindowInsets;
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
    private Gauge gauge, rpmGauge;
    private InfoTile coolantTile, rangeTile;
    private static final int REQUEST_WALLPAPER = 11;
    private final TripMeter trip = new TripMeter();
    private TextView tripValue, tripNote, tripReset;
    private boolean tripResetArmed;
    private long tripSavedAt;
    /** "color" or "glass": the person's choice in the settings menu, else config.json's. */
    private String iconStyle;
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
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

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
        if (gauge != null && pager != null && pager.page() == 0) {
            gauge.sweep();
            rpmGauge.sweep();
        }
        if (nowPlaying != null) nowPlaying.start();
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] perms, int[] results) {
        super.onRequestPermissionsResult(request, perms, results);
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
            setWallpaperFrom(data.getData());
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
        Insets in = new Insets();
        WindowInsets wi = root.getRootWindowInsets();
        if (wi != null) {
            in.left = wi.getSystemWindowInsetLeft();
            in.top = wi.getSystemWindowInsetTop();
            in.right = wi.getSystemWindowInsetRight();
            in.bottom = wi.getSystemWindowInsetBottom();
        }
        return in;
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
        int slot = Math.min(dp(80), Math.max(dp(48), (railH - fixed) / MAX_DOCK));
        int icon = Math.min(Math.min(dp(64), railW - dp(20)), slot - dp(12));
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
        gauge = null;
        rpmGauge = null;
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
        left.setPadding(dp(24), dp(20), dp(24), dp(20));
        left.setBackground(glassFor(left, dp(26)));
        bigClock = new RainbowClock(this, cfg.clockRainbow);
        bigClock.setTextSizePx(Math.min(areaH * 0.24f, areaW * 0.12f));
        // The clock sits in the same dark glass box as the music widget below it.
        FrameLayout clockBox = new FrameLayout(this);
        clockBox.setBackground(NowPlaying.darkPanel(this));
        clockBox.addView(bigClock, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams cblp = new LinearLayout.LayoutParams(-1, 0, 1f);
        cblp.bottomMargin = dp(10);
        left.addView(clockBox, cblp);
        bigDate = text(18, false);
        bigDate.setAlpha(0.8f);
        left.addView(bigDate, new LinearLayout.LayoutParams(-2, -2));
        if (cfg.musicWidget) {
            int npH = Math.max(dp(64), Math.min(dp(84), Math.round(areaH * 0.17f)));
            nowPlaying = new NowPlaying(this, npH, this::openMusicApp);
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

        // Right: speedometer (top left) and rev counter (top right), coolant and fuel range under them.
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(dp(12), dp(12), dp(12), dp(12));
        right.setBackground(glassFor(right, dp(26)));
        LinearLayout dials = new LinearLayout(this);
        dials.setOrientation(LinearLayout.HORIZONTAL);
        gauge = Gauge.speed(this, cfg.speedMax, cfg.mph ? "mph" : "km/h");
        rpmGauge = Gauge.rpm(this, cfg.rpmMax, cfg.rpmRed);
        dials.addView(gauge, new LinearLayout.LayoutParams(0, -1, 1f));
        LinearLayout.LayoutParams rpmLp = new LinearLayout.LayoutParams(0, -1, 1f);
        rpmLp.leftMargin = dp(8);
        dials.addView(rpmGauge, rpmLp);
        right.addView(dials, new LinearLayout.LayoutParams(-1, 0, 1f));
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

    private void settingsMenu(View anchor) {
        PopupMenu m = new PopupMenu(this, anchor);
        m.getMenu().add(0, 1, 0, R.string.wallpaper_pick);
        m.getMenu().add(0, 2, 1, R.string.wallpaper_reset);
        m.getMenu().add(0, 3, 2, "glass".equals(iconStyle) ? R.string.icons_color : R.string.icons_glass);
        m.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) pickWallpaper();
            else if (item.getItemId() == 2) resetWallpaper();
            else setIconStyle("glass".equals(iconStyle) ? "color" : "glass");
            return true;
        });
        m.show();
    }

    private void pickWallpaper() {
        Intent pick = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        Intent any = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
        for (Intent i : new Intent[]{pick, any}) {
            try {
                startActivityForResult(i, REQUEST_WALLPAPER);
                return;
            } catch (RuntimeException ignored) {
                // No app for this one: try the next.
            }
        }
        showToast(getString(R.string.no_gallery));
    }

    /** Copies the chosen picture next to config.json as wallpaper.jpg, then redraws everything with it. */
    private void setWallpaperFrom(Uri uri) {
        java.io.File dir = CarConfig.dir(this);
        if (dir == null) {
            showToast(getString(R.string.wallpaper_failed));
            return;
        }
        worker.execute(() -> {
            java.io.File tmp = new java.io.File(dir, "wallpaper.tmp"), dst = new java.io.File(dir, "wallpaper.jpg");
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
            final boolean done = ok;
            main.post(() -> {
                if (!done) {
                    showToast(getString(R.string.wallpaper_failed));
                    return;
                }
                cfg.wallpaper = true;
                refreshWallpaper();
                showToast(getString(R.string.wallpaper_set));
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

    /** Puts one snapshot of car data on the dashboard. */
    private void showCarData(CarData.Reading r) {
        trip.odometer(r.odometer);
        trip.speed(r.speed, SystemClock.elapsedRealtime());
        showTrip();
        if (SystemClock.elapsedRealtime() - tripSavedAt > 15_000) saveTrip();
        if (gauge == null) return;
        int src = r.speedSource;
        gauge.set(r.speed, src, src == SpeedMath.CAN ? "CAN BUS" : src == SpeedMath.GPS ? "GPS"
                : carData.hasGpsPermission() ? "NO SIGNAL" : "NO GPS PERMISSION");
        boolean hasRpm = !Float.isNaN(r.rpm);
        rpmGauge.set(r.rpm, hasRpm ? SpeedMath.CAN : SpeedMath.NONE, hasRpm ? "CAN BUS" : "NO DATA");
        if (torqueBar != null) torqueBar.set(r.torque);
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
    private void openMusicApp() {
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
