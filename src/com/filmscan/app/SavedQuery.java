package com.filmscan.app;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Search, date filter and sort for Saved scans (plain Java, so it is tested on the desktop). */
public final class SavedQuery {
    private SavedQuery() {}

    public static final int ALL = 0, TODAY = 1, YESTERDAY = 2, LAST_7 = 3, LAST_30 = 4, THIS_MONTH = 5, THIS_YEAR = 6, CUSTOM = 7;
    public static final String[] DATE_NAMES = {"All time", "Today", "Yesterday", "Last 7 days", "Last 30 days", "This month", "This year", "Custom range…"};
    public static final int NEWEST = 0, OLDEST = 1, NAME_AZ = 2, NAME_ZA = 3;
    public static final String[] SORT_NAMES = {"Newest first", "Oldest first", "Name A\u2013Z", "Name Z\u2013A"};

    public interface Access<T> {
        String name(T t);

        long time(T t);
    }

    private static Calendar dayStart(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    /** Time window {from, toExclusive} for a date filter in local time, or null for "All time". */
    public static long[] range(int filter, long now, long customFrom, long customTo) {
        Calendar d = dayStart(now);
        long today = d.getTimeInMillis();
        Calendar end = (Calendar) d.clone();
        end.add(Calendar.DAY_OF_MONTH, 1);
        long tomorrow = end.getTimeInMillis();
        switch (filter) {
            case TODAY: return new long[]{today, tomorrow};
            case YESTERDAY: {
                Calendar y = (Calendar) d.clone();
                y.add(Calendar.DAY_OF_MONTH, -1);
                return new long[]{y.getTimeInMillis(), today};
            }
            case LAST_7:
            case LAST_30: {
                Calendar f = (Calendar) d.clone();
                f.add(Calendar.DAY_OF_MONTH, filter == LAST_7 ? -6 : -29);   // today counts as one of the days
                return new long[]{f.getTimeInMillis(), tomorrow};
            }
            case THIS_MONTH: {
                Calendar f = (Calendar) d.clone();
                f.set(Calendar.DAY_OF_MONTH, 1);
                Calendar t = (Calendar) f.clone();
                t.add(Calendar.MONTH, 1);
                return new long[]{f.getTimeInMillis(), t.getTimeInMillis()};
            }
            case THIS_YEAR: {
                Calendar f = (Calendar) d.clone();
                f.set(Calendar.DAY_OF_YEAR, 1);
                Calendar t = (Calendar) f.clone();
                t.add(Calendar.YEAR, 1);
                return new long[]{f.getTimeInMillis(), t.getTimeInMillis()};
            }
            case CUSTOM: {
                long a = Math.min(customFrom, customTo), b = Math.max(customFrom, customTo);
                Calendar t = dayStart(b);
                t.add(Calendar.DAY_OF_MONTH, 1);                              // "to" day is included
                return new long[]{dayStart(a).getTimeInMillis(), t.getTimeInMillis()};
            }
            default: return null;
        }
    }

    /** Every word of the search must appear in the name (any order, any case). */
    public static boolean matches(String name, String query) {
        if (query == null) return true;
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        for (String w : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            if (w.length() > 0 && !n.contains(w)) return false;
        }
        return true;
    }

    public static <T> List<T> apply(List<T> all, final Access<T> acc, String query, int filter,
                                    long customFrom, long customTo, int sort, long now) {
        long[] r = range(filter, now, customFrom, customTo);
        List<T> out = new ArrayList<T>();
        for (T t : all) {
            if (!matches(acc.name(t), query)) continue;
            long time = acc.time(t);
            if (r != null && (time < r[0] || time >= r[1])) continue;
            out.add(t);
        }
        final int s = sort;
        Collections.sort(out, new Comparator<T>() {
            @Override
            public int compare(T a, T b) {
                long ta = acc.time(a), tb = acc.time(b);
                int byTime = ta < tb ? -1 : ta > tb ? 1 : 0;
                if (s == OLDEST) return byTime;
                if (s == NAME_AZ || s == NAME_ZA) {
                    int c = String.CASE_INSENSITIVE_ORDER.compare(acc.name(a) == null ? "" : acc.name(a), acc.name(b) == null ? "" : acc.name(b));
                    if (c != 0) return s == NAME_AZ ? c : -c;
                }
                return -byTime;   // newest first, and the tie-break for equal names
            }
        });
        return out;
    }
}
