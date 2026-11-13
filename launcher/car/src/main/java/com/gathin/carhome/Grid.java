package com.gathin.carhome;

/** How many app tiles fit on one page. Plain Java so it is unit tested on the JVM. */
final class Grid {
    final int columns, rows;

    private Grid(int columns, int rows) {
        this.columns = columns;
        this.rows = rows;
    }

    int perPage() {
        return columns * rows;
    }

    /**
     * Fits tiles of about 150 x 170 dp into the area, 3 to 6 columns and 1 to 3 rows: big enough to hit
     * with a finger in a moving car. A 1024x600 unit at mdpi gets 5 x 3, a 1280x720 one at 1.5x gets
     * 5 x 2. {@code forcedColumns} above 0 wins over the fit.
     */
    static Grid fit(int widthPx, int heightPx, float density, int forcedColumns) {
        if (density <= 0) density = 1;
        float w = widthPx / density, h = heightPx / density;
        int cols = forcedColumns > 0 ? forcedColumns : (int) (w / 150f);
        int rows = (int) (h / 170f);
        return new Grid(clamp(cols, 3, 6 + (forcedColumns > 6 ? 2 : 0)), clamp(rows, 1, 3));
    }

    /** Number of pages needed for {@code count} apps; always at least one. */
    int pages(int count) {
        return Math.max(1, (count + perPage() - 1) / perPage());
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
