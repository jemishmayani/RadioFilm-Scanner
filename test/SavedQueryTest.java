import com.filmscan.app.SavedQuery;
import java.util.*;
/** Saved scans: search + date filters + sorting, including day/month/year boundaries. */
public class SavedQueryTest {
  static int fails = 0;
  static void check(boolean ok, String m) { System.out.println((ok ? "PASS " : "FAIL ") + m); if (!ok) fails++; }
  static long at(int y, int mo, int d, int h, int mi) { Calendar c = Calendar.getInstance(); c.clear(); c.set(y, mo - 1, d, h, mi); return c.getTimeInMillis(); }
  static class S { String n; long t; S(String n, long t) { this.n = n; this.t = t; } public String toString() { return n; } }
  static final SavedQuery.Access<S> A = new SavedQuery.Access<S>() { public String name(S s) { return s.n; } public long time(S s) { return s.t; } };
  static List<String> names(List<S> l) { List<String> o = new ArrayList<>(); for (S s : l) o.add(s.n); return o; }
  public static void main(String[] a) {
    long now = at(2026, 9, 26, 10, 0);                       // Saturday 26 Sep 2026, 10:00
    List<S> all = Arrays.asList(
      new S("MRI Brain Plain", at(2026, 9, 26, 0, 0)),      // today, first second of the day
      new S("CT Chest Contrast", at(2026, 9, 25, 23, 59)),  // yesterday, last minute
      new S("X-ray Knee", at(2026, 9, 20, 9, 0)),           // 6 days ago -> inside last 7 days
      new S("USG Abdomen", at(2026, 9, 19, 23, 59)),        // 7 days ago -> outside last 7 days
      new S("mri spine", at(2026, 9, 1, 0, 0)),             // first moment of this month
      new S("Old report", at(2026, 8, 31, 23, 59)),         // last month
      new S("New year scan", at(2026, 1, 1, 0, 0)),         // first moment of this year
      new S("Last year", at(2025, 12, 31, 23, 59)));
    check(names(SavedQuery.apply(all, A, "", SavedQuery.TODAY, 0, 0, 0, now)).equals(Arrays.asList("MRI Brain Plain")), "Today starts at midnight");
    check(names(SavedQuery.apply(all, A, "", SavedQuery.YESTERDAY, 0, 0, 0, now)).equals(Arrays.asList("CT Chest Contrast")), "Yesterday ends at midnight");
    check(SavedQuery.apply(all, A, "", SavedQuery.LAST_7, 0, 0, 0, now).size() == 3, "Last 7 days = today + 6 days before");
    check(SavedQuery.apply(all, A, "", SavedQuery.LAST_30, 0, 0, 0, now).size() == 6, "Last 30 days");
    check(SavedQuery.apply(all, A, "", SavedQuery.THIS_MONTH, 0, 0, 0, now).size() == 5, "This month starts on the 1st at 00:00");
    check(SavedQuery.apply(all, A, "", SavedQuery.THIS_YEAR, 0, 0, 0, now).size() == 7, "This year starts on 1 Jan");
    check(SavedQuery.apply(all, A, "", SavedQuery.ALL, 0, 0, 0, now).size() == 8, "All time");
    check(names(SavedQuery.apply(all, A, "", SavedQuery.CUSTOM, at(2026, 9, 25, 15, 0), at(2026, 9, 20, 8, 0), 0, now))
          .equals(Arrays.asList("CT Chest Contrast", "X-ray Knee")), "Custom range includes both whole days, even if picked in reverse");
    check(names(SavedQuery.apply(all, A, "mri", SavedQuery.ALL, 0, 0, 0, now)).equals(Arrays.asList("MRI Brain Plain", "mri spine")), "Search ignores case");
    check(names(SavedQuery.apply(all, A, "brain  mri", SavedQuery.ALL, 0, 0, 0, now)).equals(Arrays.asList("MRI Brain Plain")), "Every search word must match, in any order");
    check(SavedQuery.apply(all, A, "mri", SavedQuery.TODAY, 0, 0, 0, now).size() == 1, "Search and date filter work together");
    check(SavedQuery.apply(all, A, "zzz", SavedQuery.ALL, 0, 0, 0, now).isEmpty(), "No match gives an empty list");
    check(names(SavedQuery.apply(all, A, "", SavedQuery.ALL, 0, 0, SavedQuery.NEWEST, now)).get(0).equals("MRI Brain Plain"), "Newest first");
    check(names(SavedQuery.apply(all, A, "", SavedQuery.ALL, 0, 0, SavedQuery.OLDEST, now)).get(0).equals("Last year"), "Oldest first");
    List<String> az = names(SavedQuery.apply(all, A, "", SavedQuery.ALL, 0, 0, SavedQuery.NAME_AZ, now));
    check(az.get(0).equals("CT Chest Contrast") && az.get(1).equals("Last year") && az.indexOf("mri spine") > az.indexOf("MRI Brain Plain"), "Name A-Z ignores case: " + az);
    List<String> za = names(SavedQuery.apply(all, A, "", SavedQuery.ALL, 0, 0, SavedQuery.NAME_ZA, now));
    check(za.get(0).equals("X-ray Knee") && za.get(za.size() - 1).equals("CT Chest Contrast"), "Name Z-A");
    // month and year roll-over: 'now' on 1 Jan must not include last year's December in 'this month'
    long jan1 = at(2027, 1, 1, 0, 30);
    long[] r = SavedQuery.range(SavedQuery.YESTERDAY, jan1, 0, 0);
    check(r[0] == at(2026, 12, 31, 0, 0) && r[1] == at(2027, 1, 1, 0, 0), "Yesterday across a year boundary");
    check(SavedQuery.range(SavedQuery.THIS_MONTH, jan1, 0, 0)[0] == at(2027, 1, 1, 0, 0), "This month on 1 January");
    System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
  }
}
