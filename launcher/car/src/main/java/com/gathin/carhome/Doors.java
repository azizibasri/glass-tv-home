package com.gathin.carhome;

/**
 * The car's openings, and how a CAN door value says which are open. Plain Java: unit tested.
 */
final class Doors {
    static final int FRONT_LEFT = 1, FRONT_RIGHT = 2, REAR_LEFT = 3, REAR_RIGHT = 4, BOOT = 5, BONNET = 6;
    static final int COUNT = 7;   // index 0 unused, so the constants are the indexes

    /** Door bits as most CAN boxes send them, unless config.json's door_bits says otherwise. */
    static final int[] DEFAULT_BITS = {0, 1, 2, 4, 8, 16, 32};

    private Doors() {
    }

    /** Which openings are open, from a CAN door value and the bit for each (index = opening). */
    static boolean[] open(int mask, int[] bits) {
        boolean[] open = new boolean[COUNT];
        for (int p = 1; p < COUNT; p++) open[p] = bits[p] != 0 && (mask & bits[p]) != 0;
        return open;
    }
}
