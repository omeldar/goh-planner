package com.omeldar.planner;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A build and the materials it needs. Plain fields, so Gson writes it as it is. */
public final class Project {
    public String name = "";
    public String notes = "";
    /** Where it was made, "World Castle copy" or "Server goh.example.net"; shown, never enforced. */
    public String world = "";
    public String created = "";
    public List<Entry> entries = new ArrayList<>();

    /** One material: how many the project needs, how many are already put by, how fast it farms. */
    public static final class Entry {
        public String item = "";
        public long target;
        public long have;
        /** Items per hour the player's farm makes, 0 when unknown. */
        public double ratePerHour;
        /** The container this material is planned and counted in, a {@link Storage.Unit} name. */
        public String unit = Storage.Unit.DOUBLE_CHESTS.name();

        public Storage.Unit unit() {
            Storage.Unit u = Storage.Unit.parse(unit);
            return u == null ? Storage.Unit.DOUBLE_CHESTS : u;
        }

        /** "1.5 / 3 double chests" in this material's own container. */
        public String inUnits() {
            int size = Items.stackSize(item);
            Storage.Unit u = unit();
            // Five pickaxes are not "0.1 double chests": below half a container, count items.
            if (u != Storage.Unit.ITEMS && u.of(target, size) < 0.5) u = Storage.Unit.ITEMS;
            return num(u.of(this.have, size)) + " / " + u.count(u.of(target, size));
        }

        private static String num(double d) {
            if (d > 0 && d < 0.1) return "<0.1";
            return d == Math.rint(d) ? Storage.fmt((long) d) : String.format(java.util.Locale.ROOT, "%.1f", d);
        }

        public long missing() {
            return Math.max(0, target - have);
        }
    }

    public Entry find(String itemId) {
        for (Entry e : entries) if (e.item.equals(itemId)) return e;
        return null;
    }

    /** Overall progress, by stacks rather than items, so 64 dirt does not count like 64 beacons. */
    public double progress() {
        double need = 0;
        double got = 0;
        for (Entry e : entries) {
            int size = Items.stackSize(e.item);
            need += (double) e.target / size;
            got += (double) Math.min(e.have, e.target) / size;
        }
        return need == 0 ? 0 : got / need;
    }

    public String key() {
        return name.toLowerCase(Locale.ROOT);
    }
}
