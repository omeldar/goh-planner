package com.omeldar.planner;

import java.util.Locale;

/**
 * The arithmetic of putting things away.
 *
 * <p>Everything is counted in stacks of the item's real stack size - 64 for most blocks, 16 for
 * ender pearls and signs, 1 for tools and potions - because that is what a slot holds. A
 * shulker box has 27 slots, a double chest 54, and a double chest full of shulker boxes holds
 * 54 x 27 = 1,458 stacks.
 */
public final class Storage {
    public static final int SHULKER = 27;
    public static final int DOUBLE_CHEST = 54;
    public static final int DC_OF_SHULKERS = DOUBLE_CHEST * SHULKER;

    private Storage() {}

    /**
     * The containers a material is planned and counted in. A project says "3 double chests of
     * stone bricks", and filling one is a single press of +.
     *
     * <p>A shulker chest is a chest filled with 27 full shulker boxes; a shulker double chest is
     * a double chest filled with 54 of them. Ordered by size, which is also the cycle order on
     * the unit buttons.
     */
    public enum Unit {
        CHESTS("chest", "chests", 27),
        DOUBLE_CHESTS("double chest", "double chests", DOUBLE_CHEST),
        SHULKER_CHESTS("shulker chest", "shulker chests", 27 * SHULKER),
        SHULKER_DOUBLE_CHESTS("shulker double chest", "shulker double chests", Storage.DC_OF_SHULKERS),
        SHULKERS("shulker box", "shulker boxes", SHULKER),
        STACKS("stack", "stacks", 1),
        ITEMS("item", "items", 0);

        public final String one;
        public final String label;
        /** Stacks per unit; 0 means single items. */
        private final int stacks;

        Unit(String one, String label, int stacks) {
            this.one = one;
            this.label = label;
            this.stacks = stacks;
        }

        public long toItems(double amount, int stackSize) {
            return stacks == 0 ? Math.round(amount) : Math.round(amount * stacks * stackSize);
        }

        /** Items in one full container of this kind. */
        public long perUnit(int stackSize) {
            return toItems(1, stackSize);
        }

        /** How many of this unit the items make, e.g. 2.4 double chests. */
        public double of(long items, int stackSize) {
            return (double) items / perUnit(stackSize);
        }

        /** "1 double chest", "2.5 double chests". */
        public String count(double n) {
            String num = n == Math.rint(n) ? fmt((long) n) : String.format(Locale.ROOT, "%.1f", n);
            return num + " " + (n == 1 ? one : label);
        }

        public Unit next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public static Unit parse(String s) {
            if (s == null) return null;
            String t = s.toLowerCase(Locale.ROOT).replace("_", " ").trim();
            return switch (t) {
                case "chest", "chests", "c" -> CHESTS;
                case "dc", "double chest", "double chests", "doublechest", "doublechests" -> DOUBLE_CHESTS;
                case "sc", "shulker chest", "shulker chests", "chest of shulkers", "chests of shulkers" -> SHULKER_CHESTS;
                case "sdc", "dcs", "shulker double chest", "shulker double chests", "dc of shulkers",
                     "double chest of shulkers", "double chests of shulkers" -> SHULKER_DOUBLE_CHESTS;
                case "shulker", "shulkers", "shulker box", "shulker boxes", "sb" -> SHULKERS;
                case "stack", "stacks", "s" -> STACKS;
                case "item", "items", "i" -> ITEMS;
                default -> {
                    try {
                        yield Unit.valueOf(s.trim().toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        yield null;
                    }
                }
            };
        }
    }

    public static long stacks(long items, int stackSize) {
        return ceilDiv(items, stackSize);
    }

    public static long shulkers(long items, int stackSize) {
        return ceilDiv(stacks(items, stackSize), SHULKER);
    }

    public static long doubleChests(long items, int stackSize) {
        return ceilDiv(stacks(items, stackSize), DOUBLE_CHEST);
    }

    public static long doubleChestsOfShulkers(long items, int stackSize) {
        return ceilDiv(shulkers(items, stackSize), DOUBLE_CHEST);
    }

    /** "12,345 items = 193 stacks = 8 shulkers = 4 double chests", trimmed to what matters. */
    public static String summary(long items, int stackSize) {
        StringBuilder b = new StringBuilder(fmt(items)).append(items == 1 ? " item" : " items");
        long st = stacks(items, stackSize);
        if (stackSize > 1 && items > stackSize) b.append(" = ").append(count(st, "stack"));
        if (st > 1) {
            b.append(" = ").append(count(shulkers(items, stackSize), "shulker"));
            b.append(" = ").append(count(doubleChests(items, stackSize), "double chest"));
        }
        if (shulkers(items, stackSize) > DOUBLE_CHEST / 2) {
            long dcs = doubleChestsOfShulkers(items, stackSize);
            b.append(" = ").append(fmt(dcs)).append(dcs == 1 ? " double chest" : " double chests").append(" of shulkers");
        }
        return b.toString();
    }

    /** Short form for a list row: the largest unit that is at least one, with one decimal. */
    public static String brief(long items, int stackSize) {
        double st = (double) items / stackSize;
        if (st >= DC_OF_SHULKERS) return dec(st / DC_OF_SHULKERS) + " DC of shulkers";
        if (st >= DOUBLE_CHEST) return dec(st / DOUBLE_CHEST) + " DC";
        if (st >= SHULKER) return dec(st / SHULKER) + " shulkers";
        if (stackSize > 1 && items >= stackSize) return dec(st) + " stacks";
        return count(items, "item");
    }

    /** How to actually put it away: loose in double chests, or boxed in shulkers. */
    public static String howToStore(long items, int stackSize) {
        long st = stacks(items, stackSize);
        long fullDc = st / DOUBLE_CHEST;
        long restStacks = st - fullDc * DOUBLE_CHEST;
        String loose = (fullDc > 0 ? fmt(fullDc) + " full double chest" + (fullDc == 1 ? "" : "s") : "")
                + (fullDc > 0 && restStacks > 0 ? " + " : "")
                + (restStacks > 0 ? fmt(restStacks) + " stack" + (restStacks == 1 ? "" : "s") : "");
        long boxes = shulkers(items, stackSize);
        long boxDc = ceilDiv(boxes, DOUBLE_CHEST);
        return "loose: " + (loose.isEmpty() ? "nothing" : loose) + "; boxed: " + fmt(boxes)
             + " shulker" + (boxes == 1 ? "" : "s") + (boxes > 1 ? ", which fill " + fmt(boxDc)
             + " double chest" + (boxDc == 1 ? "" : "s") : "");
    }

    public static String duration(double hours) {
        if (hours <= 0) return "done";
        long minutes = Math.round(hours * 60);
        if (minutes < 60) return Math.max(1, minutes) + " min";
        if (minutes < 48 * 60) return (minutes / 60) + " h " + (minutes % 60) + " min";
        return String.format(Locale.ROOT, "%.1f days", minutes / 1440.0);
    }

    /** "1 shulker", "3 shulkers". */
    public static String count(long n, String word) {
        return fmt(n) + " " + word + (n == 1 ? "" : "s");
    }

    public static String fmt(long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    private static String dec(double d) {
        return d >= 100 ? String.format(Locale.ROOT, "%,.0f", d) : String.format(Locale.ROOT, "%.1f", d);
    }

    private static long ceilDiv(long a, long b) {
        return a <= 0 ? 0 : (a + b - 1) / b;
    }
}
