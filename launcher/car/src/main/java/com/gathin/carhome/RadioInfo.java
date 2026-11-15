package com.gathin.carhome;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads what the unit's own radio app shows (its media title, its notification, or a broadcast) for the
 * music widget: the frequency ("FM 98.8", "AM 1035"), the preset channel number ("CH 3") and the station
 * name. Plain Java, so it is unit tested. Head-unit radio apps word it many ways; this looks for the
 * numbers themselves.
 */
final class RadioInfo {
    final String band;      // "FM" or "AM", or null
    final String frequency; // "98.8" (MHz for FM, kHz for AM), or null
    final int channel;      // preset number, or 0
    final String station;   // station name, or null

    RadioInfo(String band, String frequency, int channel, String station) {
        this.band = band;
        this.frequency = frequency;
        this.channel = channel;
        this.station = station;
    }

    /** "FM 98.8", or "CH 3" when only the channel is known, or null. */
    String headline() {
        if (frequency != null) return (band != null ? band + " " : "") + frequency;
        if (channel > 0) return "CH " + channel;
        return null;
    }

    /** "CH 3 · Hitz FM" from what is known besides the headline, or null. */
    String detail() {
        StringBuilder b = new StringBuilder();
        if (frequency != null && channel > 0) b.append("CH ").append(channel);
        if (station != null) {
            if (b.length() > 0) b.append(" · ");
            b.append(station);
        }
        return b.length() > 0 ? b.toString() : null;
    }

    boolean isEmpty() {
        return frequency == null && channel <= 0;
    }

    // FM 87.5 to 108.0 MHz, written 98.8 or 98,80 or 98.80MHz.
    private static final Pattern FM = Pattern.compile("(?<![\\d.,])(8[7-9]|9\\d|10[0-8])[.,](\\d{1,2})(?![\\d])");
    // AM 522 to 1710 kHz, only when the text says AM or kHz.
    private static final Pattern AM = Pattern.compile("(?<!\\d)(5[2-9]\\d|[6-9]\\d\\d|1[0-6]\\d\\d|170\\d|1710)(?!\\d)\\s*(?:k\\s*hz)?",
            Pattern.CASE_INSENSITIVE);
    // A preset: "CH 3", "CH3", "P3", "Preset 3", "Channel 12", "M3".
    private static final Pattern CHANNEL = Pattern.compile("(?i)(?<![a-z])(?:ch(?:annel)?|preset|p|m)\\s*[.:#]?\\s*(\\d{1,2})(?!\\d)");

    /** What the texts say; texts may be null. The first text is taken as the most important one. */
    static RadioInfo parse(CharSequence... texts) {
        String band = null, freq = null, station = null;
        int channel = 0;
        for (CharSequence cs : texts) {
            if (cs == null) continue;
            String t = cs.toString().trim();
            if (t.isEmpty()) continue;
            String upper = t.toUpperCase(Locale.ROOT);
            if (freq == null) {
                Matcher fm = FM.matcher(t);
                if (fm.find()) {
                    band = "FM";
                    String dec = fm.group(2);
                    if (dec.length() == 2 && dec.endsWith("0")) dec = dec.substring(0, 1);
                    freq = fm.group(1) + "." + dec;
                } else if (upper.contains("AM") || upper.contains("KHZ")) {
                    Matcher am = AM.matcher(t);
                    if (am.find()) {
                        band = "AM";
                        freq = am.group(1);
                    }
                }
            }
            if (channel == 0) {
                Matcher ch = CHANNEL.matcher(t);
                if (ch.find()) {
                    int n = Integer.parseInt(ch.group(1));
                    if (n > 0) channel = n;
                }
            }
            // A text that is not just numbers and band words is taken as the station's name.
            if (station == null) {
                String rest = t.replaceAll("(?i)\\b(fm|am|mhz|khz|ch(annel)?|preset|radio|stereo|st)\\b", " ")
                        .replaceAll("[\\d.,:#·|\\-]+", " ").trim();
                if (rest.length() >= 2 && !looksLikeAppName(t)) station = t;
            }
        }
        if (station != null && freq != null && station.contains(freq)) station = null;
        return new RadioInfo(band, freq, channel, station);
    }

    private static boolean looksLikeAppName(String t) {
        String l = t.toLowerCase(Locale.ROOT);
        return l.equals("radio") || l.equals("fm radio") || l.equals("fm") || l.contains("is running")
                || l.contains("tap for more") || l.contains("playing");
    }

    /** From a raw broadcast number: 9880 or 98800 (10 kHz or kHz) or 98.8 (MHz) to "98.8"; AM stays kHz. */
    static RadioInfo fromNumber(float value, float scale) {
        float v = value * scale;
        if (v >= 87.5f && v <= 108.05f) {
            String s = String.format(Locale.ROOT, "%.2f", v);
            if (s.endsWith("0")) s = s.substring(0, s.length() - 1);
            return new RadioInfo("FM", s, 0, null);
        }
        if (v >= 522 && v <= 1710) return new RadioInfo("AM", String.valueOf(Math.round(v)), 0, null);
        return new RadioInfo(null, null, 0, null);
    }

    /** True for the unit's radio app: its package name says radio or FM. */
    static boolean isRadioPackage(String pkg) {
        if (pkg == null) return false;
        String p = pkg.toLowerCase(Locale.ROOT);
        return p.contains("radio") || p.contains(".fm") || p.endsWith("fm") || p.contains("tuner");
    }
}
