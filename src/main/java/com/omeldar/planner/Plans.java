package com.omeldar.planner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Everything that changes a project, used by the screens, {@code /plan} and Lumi alike, so
 * they all follow the same rules. Each returns a sentence for the player, or for the model.
 */
public final class Plans {
    private Plans() {}

    public static String add(Project p, String itemTyped, double amount, Storage.Unit unit) {
        String id = Items.resolve(itemTyped);
        if (id == null) return "there is no item called \"" + itemTyped + "\"";
        if (amount <= 0) return "the amount has to be more than zero";
        long items = unit.toItems(amount, Items.stackSize(id));
        Project.Entry e = p.find(id);
        boolean added = e == null;
        if (added) {
            e = new Project.Entry();
            e.item = id;
            e.unit = unit.name();
            p.entries.add(e);
        }
        e.target = added ? items : e.target + items;
        Store.save();
        int size = Items.stackSize(id);
        return (added ? "added " : "now ") + e.unit().count(e.unit().of(e.target, size)) + " of "
             + Items.name(id) + " to " + p.name + " (" + Storage.fmt(e.target) + " items)";
    }

    public static String setTarget(Project p, String itemTyped, long items) {
        Project.Entry e = entry(p, itemTyped);
        if (e == null) return notIn(p, itemTyped);
        e.target = Math.max(0, items);
        Store.save();
        return Items.name(e.item) + " target is now " + Storage.fmt(e.target);
    }

    public static String setHave(Project p, String itemTyped, long items) {
        Project.Entry e = entry(p, itemTyped);
        if (e == null) return notIn(p, itemTyped);
        e.have = Math.max(0, items);
        Store.save();
        return Items.name(e.item) + ": " + Storage.fmt(e.have) + " of " + Storage.fmt(e.target);
    }

    public static String setRate(Project p, String itemTyped, double perHour) {
        Project.Entry e = entry(p, itemTyped);
        if (e == null) return notIn(p, itemTyped);
        e.ratePerHour = Math.max(0, perHour);
        Store.save();
        return Items.name(e.item) + " farms at " + Storage.fmt(Math.round(e.ratePerHour)) + " an hour"
             + (e.ratePerHour > 0 ? ", " + Storage.duration(e.missing() / e.ratePerHour) + " to go" : "");
    }

    /**
     * The player filled (or emptied, with a negative n) whole containers of this material.
     * This is the everyday button: nobody counts 3,456 items, they count chests.
     */
    public static String addFilled(Project p, String itemTyped, int n) {
        Project.Entry e = entry(p, itemTyped);
        if (e == null) return notIn(p, itemTyped);
        int size = Items.stackSize(e.item);
        e.have = Math.max(0, e.have + n * e.unit().perUnit(size));
        Store.save();
        return Items.name(e.item) + ": " + e.inUnits() + (e.missing() == 0 ? ", all there" : "");
    }

    /** Plan this material in a different container; the amounts stay the same. */
    public static String setUnit(Project p, String itemTyped, Storage.Unit unit) {
        Project.Entry e = entry(p, itemTyped);
        if (e == null) return notIn(p, itemTyped);
        e.unit = unit.name();
        Store.save();
        return Items.name(e.item) + " is now planned in " + unit.label + ": " + e.inUnits();
    }

    public static String remove(Project p, String itemTyped) {
        Project.Entry e = entry(p, itemTyped);
        if (e == null) return notIn(p, itemTyped);
        p.entries.remove(e);
        Store.save();
        return "removed " + Items.name(e.item) + " from " + p.name;
    }

    /**
     * Set "have" for every material to what the player carries right now, shulker contents
     * included. A replace, not an add, so pressing it twice cannot count anything twice.
     */
    public static String countInventory(Project p) {
        Map<String, Long> carried = Items.carried();
        int changed = 0;
        for (Project.Entry e : p.entries) {
            long n = carried.getOrDefault(e.item, 0L);
            if (n != e.have) changed++;
            e.have = n;
        }
        Store.save();
        return "counted your inventory and shulker boxes: " + changed + " of " + p.entries.size()
             + " materials changed";
    }

    /** A few plain chat lines describing the project; no lists or tables. */
    public static List<String> describe(Project p) {
        List<String> out = new ArrayList<>();
        out.add(p.name + ": " + p.entries.size() + " materials, " + Math.round(p.progress() * 100) + "% gathered.");
        long stacks = 0;
        long shulkers = 0;
        for (Project.Entry e : p.entries) {
            int size = Items.stackSize(e.item);
            stacks += Storage.stacks(e.missing(), size);
            shulkers += Storage.shulkers(e.missing(), size);
        }
        if (!p.entries.isEmpty()) {
            out.add("Still missing " + Storage.fmt(stacks) + " stacks, which is " + Storage.fmt(shulkers)
                  + " shulkers if each material gets its own boxes.");
        }
        for (Project.Entry e : p.entries) {
            int size = Items.stackSize(e.item);
            String line = Items.name(e.item) + ": " + e.inUnits() + " (" + Storage.fmt(e.have)
                        + " of " + Storage.fmt(e.target) + " items)";
            if (e.missing() > 0 && e.ratePerHour > 0) {
                line += ", " + Storage.duration(e.missing() / e.ratePerHour) + " of farming left";
            }
            out.add(line + ".");
        }
        return out;
    }

    private static Project.Entry entry(Project p, String itemTyped) {
        String id = Items.resolve(itemTyped);
        return id == null ? null : p.find(id);
    }

    private static String notIn(Project p, String itemTyped) {
        return p.name + " has no " + itemTyped;
    }
}
