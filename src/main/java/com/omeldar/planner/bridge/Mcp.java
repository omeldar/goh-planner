package com.omeldar.planner.bridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.omeldar.planner.Items;
import com.omeldar.planner.Plans;
import com.omeldar.planner.PlannerClient;
import com.omeldar.planner.ui.ProjectListScreen;
import com.omeldar.planner.ui.ProjectScreen;
import com.omeldar.planner.Project;
import com.omeldar.planner.Storage;
import com.omeldar.planner.Store;

import java.util.function.Function;

/**
 * The tools Lumi gets, under the {@code plan_} prefix. Each goes through {@link Plans}, the
 * same code as the screens and {@code /plan}, and answers in a sentence or plain JSON.
 */
final class Mcp {
    private Mcp() {}

    private static final String UNIT = "\"unit\":{\"type\":\"string\",\"enum\":[\"chests\",\"double_chests\","
            + "\"shulker_chests\",\"shulker_double_chests\",\"shulker_boxes\",\"stacks\",\"items\"],"
            + "\"description\":\"the container the amounts are in. A shulker chest is a chest of 27 full shulker "
            + "boxes, a shulker double chest a double chest of 54. For add_material it also becomes the container the "
            + "material is planned and counted in. Default items for amounts.\"}";
    private static final String PROJECT = "\"project\":{\"type\":\"string\",\"description\":\"name; "
            + "omit for the project the player has open\"}";

    static JsonArray toolList() {
        JsonArray t = new JsonArray();
        t.add(tool("list_projects", "The player's projects in this world, with material count and progress.",
                "{\"type\":\"object\",\"properties\":{},\"required\":[]}"));
        t.add(tool("get_project", "One project in full: each material with needed, have, stack size, the "
              + "amount in stacks / shulkers / double chests / double chests of shulkers, farm rate and time left.",
                "{\"type\":\"object\",\"properties\":{" + PROJECT + "},\"required\":[]}"));
        t.add(tool("create_project", "Create a project and make it the open one.",
                "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}},\"required\":[\"name\"]}"));
        t.add(tool("add_material", "Add an amount of an item to a project's needs (adds to what is already "
              + "listed). item is an id like minecraft:stone_bricks or a display name.",
                "{\"type\":\"object\",\"properties\":{" + PROJECT + ",\"item\":{\"type\":\"string\"},"
              + "\"amount\":{\"type\":\"number\",\"exclusiveMinimum\":0}," + UNIT + "},\"required\":[\"item\",\"amount\"]}"));
        t.add(tool("set_material", "Set a material's needed amount, have amount and/or farm rate (items per hour) outright.",
                "{\"type\":\"object\",\"properties\":{" + PROJECT + ",\"item\":{\"type\":\"string\"},"
              + "\"needed\":{\"type\":\"number\",\"minimum\":0},\"have\":{\"type\":\"number\",\"minimum\":0},"
              + UNIT + ",\"rate_per_hour\":{\"type\":\"number\",\"minimum\":0}},\"required\":[\"item\"]}"));
        t.add(tool("add_filled", "The player filled n more containers of a material (negative to take some "
              + "out). Uses the container the material is planned in. This is how progress is usually kept.",
                "{\"type\":\"object\",\"properties\":{" + PROJECT + ",\"item\":{\"type\":\"string\"},"
              + "\"n\":{\"type\":\"integer\",\"description\":\"default 1\"}},\"required\":[\"item\"]}"));
        t.add(tool("remove_material", "Take a material off a project.",
                "{\"type\":\"object\",\"properties\":{" + PROJECT + ",\"item\":{\"type\":\"string\"}},\"required\":[\"item\"]}"));
        t.add(tool("count_inventory", "Set have for every material in a project to what the player carries, "
              + "shulker box contents included. Replaces, so it never counts twice.",
                "{\"type\":\"object\",\"properties\":{" + PROJECT + "},\"required\":[]}"));
        t.add(tool("show_project", "Open a project's screen in front of the player, so they can see it. "
              + "Only when they ask to see it.",
                "{\"type\":\"object\",\"properties\":{" + PROJECT + "},\"required\":[]}"));
        t.add(tool("storage_math", "Convert an amount of any item into stacks, shulkers, double chests and "
              + "double chests of shulkers, using its real stack size. No project needed.",
                "{\"type\":\"object\",\"properties\":{\"item\":{\"type\":\"string\"},"
              + "\"amount\":{\"type\":\"number\",\"exclusiveMinimum\":0}," + UNIT + "},\"required\":[\"item\",\"amount\"]}"));
        return t;
    }

    static String call(String name, JsonObject a) throws Exception {
        return ClientQueue.on(mc -> {
            return switch (name) {
                case "list_projects" -> listProjects();
                case "get_project" -> withProject(a, Mcp::projectJson);
                case "create_project" -> {
                    String failed = Store.create(str(a, "name"));
                    yield failed != null ? failed : "created " + str(a, "name").trim() + " and opened it";
                }
                case "add_material" -> withProject(a, p -> {
                    Storage.Unit u = unit(a);
                    return u == null ? badUnit() : Plans.add(p, str(a, "item"), num(a, "amount"), u);
                });
                case "set_material" -> withProject(a, p -> setMaterial(p, a));
                case "remove_material" -> withProject(a, p -> Plans.remove(p, str(a, "item")));
                case "add_filled" -> withProject(a, p -> Plans.addFilled(p, str(a, "item"),
                        a.has("n") ? a.get("n").getAsInt() : 1));
                case "count_inventory" -> withProject(a, Plans::countInventory);
                case "show_project" -> withProject(a, p -> {
                    PlannerClient.openLater(new ProjectScreen(new ProjectListScreen(), p));
                    return "opened " + p.name + " on the player's screen";
                });
                case "storage_math" -> {
                    String id = Items.resolve(str(a, "item"));
                    Storage.Unit u = unit(a);
                    if (id == null) yield "there is no item called \"" + str(a, "item") + "\"";
                    if (u == null) yield badUnit();
                    int size = Items.stackSize(id);
                    long items = u.toItems(num(a, "amount"), size);
                    yield Items.name(id) + " (stacks of " + size + "): " + Storage.summary(items, size) + ". "
                        + Storage.howToStore(items, size) + ".";
                }
                default -> "unknown tool " + name;
            };
        });
    }

    private static String setMaterial(Project p, JsonObject a) {
        String id = Items.resolve(str(a, "item"));
        if (id == null) return "there is no item called \"" + str(a, "item") + "\"";
        Storage.Unit u = unit(a);
        if (u == null) return badUnit();
        if (p.find(id) == null) Plans.add(p, id, Math.max(1, a.has("needed") ? num(a, "needed") : 1), u);
        int size = Items.stackSize(id);
        StringBuilder out = new StringBuilder();
        if (a.has("needed")) out.append(Plans.setTarget(p, id, u.toItems(num(a, "needed"), size))).append(". ");
        if (a.has("have")) out.append(Plans.setHave(p, id, u.toItems(num(a, "have"), size))).append(". ");
        if (a.has("rate_per_hour")) out.append(Plans.setRate(p, id, num(a, "rate_per_hour"))).append(". ");
        if (a.has("unit") && u != Storage.Unit.ITEMS) out.append(Plans.setUnit(p, id, u)).append(". ");
        return out.length() == 0 ? "nothing to change: give needed, have or rate_per_hour" : out.toString().trim();
    }

    private static String listProjects() {
        JsonArray out = new JsonArray();
        Project current = Store.current();
        for (Project p : Store.all()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", p.name);
            o.addProperty("madeIn", p.world);
            o.addProperty("materials", p.entries.size());
            o.addProperty("progressPercent", Math.round(p.progress() * 100));
            o.addProperty("open", p == current);
            out.add(o);
        }
        return out.isEmpty() ? "no projects in this world yet" : out.toString();
    }

    private static String projectJson(Project p) {
        JsonObject o = new JsonObject();
        o.addProperty("name", p.name);
        o.addProperty("notes", p.notes);
        o.addProperty("progressPercent", Math.round(p.progress() * 100));
        JsonArray list = new JsonArray();
        for (Project.Entry e : p.entries) {
            int size = Items.stackSize(e.item);
            JsonObject m = new JsonObject();
            m.addProperty("item", e.item);
            m.addProperty("name", Items.name(e.item));
            m.addProperty("stackSize", size);
            m.addProperty("plannedIn", e.unit().label);
            m.addProperty("progress", e.inUnits());
            m.addProperty("needed", e.target);
            m.addProperty("have", e.have);
            m.addProperty("missing", e.missing());
            m.addProperty("neededStorage", Storage.summary(e.target, size));
            m.addProperty("missingStorage", Storage.summary(e.missing(), size));
            m.addProperty("ratePerHour", e.ratePerHour);
            if (e.ratePerHour > 0) m.addProperty("timeLeft", Storage.duration(e.missing() / e.ratePerHour));
            list.add(m);
        }
        o.add("materials", list);
        return o.toString();
    }

    private static String withProject(JsonObject a, Function<Project, String> f) {
        String name = str(a, "project");
        Project p = Store.pick(name);
        if (p == null) {
            return name.isBlank() ? "no project is open; name one (plan_list_projects) or create one"
                    : "there is no project called \"" + name + "\"";
        }
        Store.setCurrent(p);
        return f.apply(p);
    }

    private static Storage.Unit unit(JsonObject a) {
        String u = str(a, "unit");
        return u.isBlank() ? Storage.Unit.ITEMS : Storage.Unit.parse(u);
    }

    private static String badUnit() {
        return "unit must be chests, double_chests, shulker_chests, shulker_double_chests, shulker_boxes, stacks or items";
    }

    private static double num(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsDouble() : 0;
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    private static JsonObject tool(String name, String description, String schema) {
        JsonObject t = new JsonObject();
        t.addProperty("name", name);
        t.addProperty("description", description);
        t.add("inputSchema", JsonParser.parseString(schema));
        return t;
    }
}
