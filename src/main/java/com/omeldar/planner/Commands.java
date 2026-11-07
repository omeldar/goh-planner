package com.omeldar.planner;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.omeldar.planner.ui.ProjectListScreen;
import com.omeldar.planner.ui.ProjectScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.argument.ItemStackArgumentType;

import java.util.function.Function;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * {@code /plan}. Commands act on the current project - the last one created or opened - so a
 * player can keep typing {@code /plan add} without naming it every time.
 */
final class Commands {
    private Commands() {}

    static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
                literal("plan")
                        .executes(ctx -> open())
                        .then(literal("new").then(argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> run(() -> {
                                    String failed = Store.create(StringArgumentType.getString(ctx, "name"));
                                    return failed != null ? failed : "created " + Store.current().name
                                            + "; add materials with /plan add <item> <amount> [unit]";
                                }))))
                        .then(literal("open").then(argument("name", StringArgumentType.greedyString())
                                .suggests((ctx, b) -> {
                                    if (Store.ready()) Store.all().forEach(p -> b.suggest(p.name));
                                    return b.buildFuture();
                                })
                                .executes(ctx -> {
                                    if (!Store.ready()) return say("not in a world");
                                    Project p = Store.find(StringArgumentType.getString(ctx, "name"));
                                    if (p == null) return say("no project with that name");
                                    Store.setCurrent(p);
                                    PlannerClient.openLater(new ProjectScreen(null, p));
                                    return 1;
                                })))
                        .then(literal("add").then(argument("item", ItemStackArgumentType.itemStack(access))
                                .then(argument("amount", DoubleArgumentType.doubleArg(0.001))
                                        .executes(ctx -> add(ctx, Storage.Unit.DOUBLE_CHESTS))
                                        .then(argument("unit", StringArgumentType.greedyString())
                                                .suggests((ctx, b) -> {
                                                    for (Storage.Unit u : Storage.Unit.values()) b.suggest(u.label);
                                                    return b.buildFuture();
                                                })
                                                .executes(ctx -> {
                                                    Storage.Unit u = Storage.Unit.parse(StringArgumentType.getString(ctx, "unit"));
                                                    if (u == null) return say("units are chests, double chests, shulker chests, shulker double chests, shulker boxes, stacks or items");
                                                    return add(ctx, u);
                                                })))))
                        .then(literal("remove").then(argument("item", ItemStackArgumentType.itemStack(access))
                                .executes(ctx -> withProject(p -> Plans.remove(p, itemId(ctx))))))
                        .then(literal("have").then(argument("item", ItemStackArgumentType.itemStack(access))
                                .then(argument("amount", DoubleArgumentType.doubleArg(0))
                                        .executes(ctx -> withProject(p -> Plans.setHave(p, itemId(ctx),
                                                Math.round(DoubleArgumentType.getDouble(ctx, "amount"))))))))
                        .then(literal("rate").then(argument("item", ItemStackArgumentType.itemStack(access))
                                .then(argument("perHour", DoubleArgumentType.doubleArg(0))
                                        .executes(ctx -> withProject(p -> Plans.setRate(p, itemId(ctx),
                                                DoubleArgumentType.getDouble(ctx, "perHour")))))))
                        .then(literal("filled").then(argument("item", ItemStackArgumentType.itemStack(access))
                                .executes(ctx -> withProject(p -> Plans.addFilled(p, itemId(ctx), 1)))
                                .then(argument("n", com.mojang.brigadier.arguments.IntegerArgumentType.integer(-1000, 1000))
                                        .executes(ctx -> withProject(p -> Plans.addFilled(p, itemId(ctx),
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "n")))))))
                        .then(literal("count").executes(ctx -> withProject(Plans::countInventory)))
                        .then(literal("show").executes(ctx -> {
                            if (!Store.ready()) return say("not in a world");
                            Project p = Store.pick(null);
                            if (p == null) return say("no project open; /plan new <name> or /plan open <name>");
                            Plans.describe(p).forEach(PlannerClient::say);
                            return 1;
                        }))));
    }

    private static int open() {
        if (!Store.ready()) return say("not in a world");
        Project p = Store.current();
        PlannerClient.openLater(p != null ? new ProjectScreen(new ProjectListScreen(), p) : new ProjectListScreen());
        return 1;
    }

    private static int add(CommandContext<FabricClientCommandSource> ctx, Storage.Unit unit) {
        double amount = DoubleArgumentType.getDouble(ctx, "amount");
        return withProject(p -> Plans.add(p, itemId(ctx), amount, unit));
    }

    private static String itemId(CommandContext<FabricClientCommandSource> ctx) {
        return Items.id(ItemStackArgumentType.getItemStackArgument(ctx, "item").getItem());
    }

    private static int withProject(Function<Project, String> action) {
        if (!Store.ready()) return say("not in a world");
        Project p = Store.pick(null);
        if (p == null) return say("no project open; /plan new <name> or /plan open <name> first");
        return say(action.apply(p));
    }

    private static int run(java.util.function.Supplier<String> action) {
        if (!Store.ready()) return say("not in a world");
        return say(action.get());
    }

    private static int say(String message) {
        PlannerClient.say(message);
        return 1;
    }
}
