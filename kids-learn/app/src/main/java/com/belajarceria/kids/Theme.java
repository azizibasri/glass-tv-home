package com.belajarceria.kids;

import android.content.Context;

/**
 * A look for the whole app, picked on the home screen: the subjects' colours, the background and its decoration,
 * a mascot on every game screen, and how a win is celebrated. All three are original designs.
 */
final class Theme {
    static final int DECO_NONE = 0, DECO_CITY = 1, DECO_BUBBLES = 2;

    final String id, icon, label;
    /** A gradient pair per subject, in the order of {@link Content#SUBJECTS}. */
    final int[][] subjects;
    /** Home background: top, bottom, then four drifting blobs. */
    final int[] home;
    /** The two extra blob colours behind a subject's own pair. */
    final int[] extras;
    final int[] title;
    final int[] confetti;
    /** Hearts among the confetti. */
    final boolean hearts;
    /** Rows of {shown, spoken}. */
    final String[][] praise;
    final String badge;
    /** One mascot per subject, or null for none. */
    final String[] mascots;
    final int deco;

    private Theme(String id, String icon, String label, int[][] subjects, int[] home, int[] extras, int[] title,
                  int[] confetti, boolean hearts, String[][] praise, String badge, String[] mascots, int deco) {
        this.id = id;
        this.icon = icon;
        this.label = label;
        this.subjects = subjects;
        this.home = home;
        this.extras = extras;
        this.title = title;
        this.confetti = confetti;
        this.hearts = hearts;
        this.praise = praise;
        this.badge = badge;
        this.mascots = mascots;
        this.deco = deco;
    }

    static final Theme CLASSIC = new Theme("classic", "🌈", "Rainbow",
            new int[][]{{0xFFFF4F86, 0xFFFFA45B}, {0xFF4F7BFF, 0xFF3FD0FF}, {0xFF0FB57F, 0xFF7BE495},
                    {0xFF8A5CFF, 0xFFD16BFF}, {0xFFFF8A1C, 0xFFFFC93F}},
            new int[]{0xFFFFF3FA, 0xFFE9F3FF, 0xFFFF9AC1, 0xFF8EC5FF, 0xFFFFD36E, 0xFF9BF6C5},
            new int[]{0xFFFFE08A, 0xFFA8E6FF},
            new int[]{0xFFFF4F86, 0xFF8A5CFF, 0xFF3FA8FF},
            new int[]{0xFFFF4F86, 0xFFFFA45B, 0xFFFFD23F, 0xFF0FB57F, 0xFF3FD0FF, 0xFF4F7BFF, 0xFF8A5CFF, 0xFFD16BFF},
            false,
            new String[][]{{"Great job!", "Great job!"}, {"Well done!", "Well done!"}, {"Awesome!", "Awesome!"},
                    {"You did it!", "You did it!"}, {"Masya-Allah!", "Masha Allah! Well done!"}, {"Hebat!", "Super!"}},
            "🌟", null, DECO_NONE);

    /** Bold comic-book colours, a city at dusk, and heroes (boys and girls) cheering the child on. */
    static final Theme HERO = new Theme("hero", "🦸", "Hero Squad",
            new int[][]{{0xFFE53935, 0xFFFF7043}, {0xFF1E5EFF, 0xFF00B0FF}, {0xFFF57C00, 0xFFFFB300},
                    {0xFF00A86B, 0xFF26C6DA}, {0xFF6A1B9A, 0xFFAB47BC}},
            new int[]{0xFFE6EEFF, 0xFFFFF0DE, 0xFF4F7BFF, 0xFFFF5252, 0xFFFFD54F, 0xFF7C4DFF},
            new int[]{0xFFFFD54F, 0xFF82B1FF},
            new int[]{0xFFE53935, 0xFFFF9100, 0xFF1E5EFF},
            new int[]{0xFFE53935, 0xFF1E5EFF, 0xFFFFC400, 0xFFFFFFFF, 0xFF1A237E, 0xFFFF7043},
            false,
            new String[][]{{"POW! Super job!", "Super job, hero!"}, {"ZAP! Amazing!", "Amazing, hero!"},
                    {"Hero power!", "You have hero power!"}, {"BOOM! You did it!", "You did it, hero!"},
                    {"Masya-Allah, hero!", "Masha Allah! Well done, hero!"}},
            "💥", new String[]{"🦸‍♂️", "🦸‍♀️", "🦸", "🦸‍♀️", "🦸‍♂️"}, DECO_CITY);

    /** Soft nursery pastels, rising bubbles, heart confetti, and four babies who play along (the person's artwork). */
    static final Theme BABY = new Theme("baby", "👶", "Kawan Comel",
            new int[][]{{0xFFFF6FA5, 0xFFFFA48A}, {0xFF4F9BFF, 0xFF7FD3FF}, {0xFF2EC4A6, 0xFF8BE3B5},
                    {0xFFB57BFF, 0xFFFF9BD2}, {0xFFFF9F43, 0xFFFFD166}},
            new int[]{0xFFFFF5F8, 0xFFFFF9EC, 0xFFFFC2D9, 0xFFBDE0FE, 0xFFFFE5A3, 0xFFCDEAC0},
            new int[]{0xFFFFE0EC, 0xFFE3F2FF},
            new int[]{0xFFFF6FA5, 0xFFB57BFF, 0xFF4F9BFF},
            new int[]{0xFFFF8FB8, 0xFFFFC2D9, 0xFF9CC9FF, 0xFFFFD166, 0xFFB5E8C8, 0xFFD7B8FF},
            true,
            new String[][]{{"Yay! Pandai!", "Yay! Well done!"}, {"Comelnya, bijak!", "So clever!"},
                    {"Tepuk tangan!", "Clap clap! Great job!"}, {"Hebat, sayang!", "Great job, sweetie!"},
                    {"Masya-Allah!", "Masha Allah! Well done!"}},
            "🧸", new String[]{"👶", "👶", "👶", "👶", "👶"}, DECO_BUBBLES);

    static final Theme[] ALL = {CLASSIC, HERO, BABY};

    static Theme current = CLASSIC;

    /** Restores the theme picked last time. */
    static void load(Context c) {
        String id = c.getSharedPreferences("ceria", Context.MODE_PRIVATE).getString("theme", CLASSIC.id);
        use(byId(id));
    }

    static void save(Context c, Theme t) {
        c.getSharedPreferences("ceria", Context.MODE_PRIVATE).edit().putString("theme", t.id).apply();
        use(t);
    }

    static Theme byId(String id) {
        if ("ice".equals(id)) return BABY; // the Ice Kingdom became Kawan Comel
        for (Theme t : ALL) if (t.id.equals(id)) return t;
        return CLASSIC;
    }

    private static void use(Theme t) {
        current = t;
        for (int i = 0; i < Content.SUBJECTS.length; i++) Content.SUBJECTS[i].colors = t.subjects[i];
    }

    /** In the Hero Squad theme, the hero who looks after each subject (the whole squad cheers together). */
    private static final int[] HERO_FOR_SUBJECT = {0, 3, 2, 1, 0};

    HeroArt.Hero hero(Content.Subject s) {
        if (this != HERO) return null;
        for (int i = 0; i < Content.SUBJECTS.length; i++) {
            if (Content.SUBJECTS[i] == s) return HeroArt.SQUAD[HERO_FOR_SUBJECT[i]];
        }
        return HeroArt.SQUAD[2]; // the test is looked after by Wira Khaizuran
    }

    /** In the Kawan Comel theme, the baby who looks after each subject. */
    private static final int[] BABY_FOR_SUBJECT = {0, 1, 2, 3, 1};

    int baby(Content.Subject s) {
        if (this != BABY) return -1;
        for (int i = 0; i < Content.SUBJECTS.length; i++) if (Content.SUBJECTS[i] == s) return BABY_FOR_SUBJECT[i];
        return 2;
    }

    String mascot(Content.Subject s) {
        if (mascots == null) return null;
        for (int i = 0; i < Content.SUBJECTS.length; i++) if (Content.SUBJECTS[i] == s) return mascots[i];
        return mascots[0];
    }
}
