package com.gathin.carhome;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.os.Environment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Finds how this head unit's CAN app might share car data, from the unit itself, for the owner to photograph
 * and send: the CAN-looking apps with their receivers, services and content providers, and the files in a
 * CanBus folder in storage (names, sizes and the first lines of small text files). Reads only.
 */
final class CanFinder {
    private static final String[] WORDS = {"can", "mcu", "syu", "fyt", "vehicle", "carinfo", "carservice",
            "hiworld", "raise", "xbs", "simple", "microntek", "hct", "ts10", "obd", "car."};
    private static final String[] FOLDERS = {"CanBus", "can"};

    /** Visible app names that mean the unit's car or CAN app (e.g. "Car Set" on the owner's A30). */
    static boolean labelLooksLikeCan(CharSequence label) {
        if (label == null) return false;
        String l = label.toString().toLowerCase(Locale.ROOT).replace(" ", "");
        for (String w : new String[]{"carset", "carsetting", "carinfo", "canbus", "canbox", "vehicle", "carstatus",
                "originalcar", "carservice", "obd"}) {
            if (l.contains(w)) return true;
        }
        return false;
    }

    private CanFinder() {
    }

    /** True when a package name looks like part of the car's CAN or MCU side. */
    static boolean looksLikeCan(String pkg) {
        String p = pkg.toLowerCase(Locale.ROOT);
        for (String w : WORDS) {
            if (w.equals("can")) {
                // "can" alone matches too much (scanner, candy...): only as its own word in the name.
                if (p.matches(".*(^|[._])can([._]|bus|box|$).*")) return true;
            } else if (p.contains(w)) {
                return true;
            }
        }
        return false;
    }

    static String report(Context ctx) {
        StringBuilder sb = new StringBuilder();
        PackageManager pm = ctx.getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        List<String> found = new ArrayList<>();
        for (ApplicationInfo a : apps) {
            boolean match = looksLikeCan(a.packageName);
            if (!match) {
                try {
                    match = labelLooksLikeCan(a.loadLabel(pm));
                } catch (RuntimeException ignored) {
                    // A label that can't be read: the package name decided.
                }
            }
            if (match) found.add(a.packageName);
        }
        java.util.Collections.sort(found);
        sb.append("CAN-LOOKING APPS (").append(found.size()).append(")\n");
        int flags = PackageManager.GET_RECEIVERS | PackageManager.GET_SERVICES | PackageManager.GET_PROVIDERS;
        for (String pkg : found) {
            try {
                PackageInfo pi = pm.getPackageInfo(pkg, flags);
                CharSequence label = pi.applicationInfo.loadLabel(pm);
                sb.append("\n• ").append(label).append("  [").append(pkg).append("] v").append(pi.versionName).append('\n');
                if (pi.providers != null) {
                    for (ProviderInfo p : pi.providers) {
                        sb.append("   provider: ").append(p.authority).append(p.exported ? "" : " (private)").append('\n');
                    }
                }
                if (pi.receivers != null) {
                    for (ActivityInfo r : pi.receivers) {
                        sb.append("   receiver: ").append(shortName(pkg, r.name)).append(r.exported ? "" : " (private)").append('\n');
                    }
                }
                if (pi.services != null) {
                    for (ServiceInfo s : pi.services) {
                        sb.append("   service: ").append(shortName(pkg, s.name)).append(s.exported ? "" : " (private)").append('\n');
                    }
                }
            } catch (PackageManager.NameNotFoundException | RuntimeException e) {
                sb.append("\n• ").append(pkg).append(" (couldn't read)\n");
            }
        }

        File root = Environment.getExternalStorageDirectory();
        boolean any = false;
        // Storage ignores letter case, so list what is really there and match names without case.
        File[] top = root.listFiles();
        List<File> dirs = new ArrayList<>();
        if (top != null) {
            for (File f : top) {
                for (String name : FOLDERS) {
                    if (f.isDirectory() && f.getName().equalsIgnoreCase(name) && !dirs.contains(f)) dirs.add(f);
                }
            }
        }
        for (File dir : dirs) {
            any = true;
            sb.append("\nFOLDER ").append(dir.getPath()).append('\n');
            listFolder(sb, dir, "   ", 0);
        }
        if (!any) sb.append("\nNo CanBus folder readable in ").append(root.getPath())
                .append(" (allow Files & media / Storage for Glass Car and open this again)\n");
        return sb.toString();
    }

    private static String shortName(String pkg, String cls) {
        return cls.startsWith(pkg) ? cls.substring(pkg.length()) : cls;
    }

    private static void listFolder(StringBuilder sb, File dir, String indent, int depth) {
        File[] files = dir.listFiles();
        if (files == null) {
            sb.append(indent).append("(can't read: allow Storage for Glass Car)\n");
            return;
        }
        Arrays.sort(files);
        int shown = 0;
        for (File f : files) {
            if (shown++ >= 40) {
                sb.append(indent).append("… and more\n");
                break;
            }
            if (f.isDirectory()) {
                sb.append(indent).append(f.getName()).append("/\n");
                if (depth < 2) listFolder(sb, f, indent + "   ", depth + 1);
                continue;
            }
            sb.append(indent).append(f.getName()).append("  (").append(f.length()).append(" bytes)\n");
            if (f.length() > 0 && f.length() < 16 * 1024 && isText(f.getName())) {
                try (BufferedReader in = new BufferedReader(new FileReader(f))) {
                    String line;
                    int n = 0;
                    while ((line = in.readLine()) != null && n++ < 6) {
                        sb.append(indent).append("   | ").append(line.length() > 90 ? line.substring(0, 90) + "…" : line).append('\n');
                    }
                } catch (Exception ignored) {
                    // Unreadable: the name and size are enough.
                }
            }
        }
    }

    private static boolean isText(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".txt") || n.endsWith(".ini") || n.endsWith(".cfg") || n.endsWith(".conf")
                || n.endsWith(".json") || n.endsWith(".xml") || n.endsWith(".log") || n.endsWith(".properties")
                || !n.contains(".");
    }
}
