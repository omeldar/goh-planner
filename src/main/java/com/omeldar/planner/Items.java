package com.omeldar.planner;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Item lookups: ids, names, stack sizes, search, and counting what the player carries. */
public final class Items {
    private Items() {}

    /** The item for an id, or null. Accepts "stone" as well as "minecraft:stone". */
    public static Item item(String id) {
        Identifier ident = Identifier.tryParse(id.contains(":") ? id : "minecraft:" + id);
        if (ident == null || !Registries.ITEM.containsId(ident)) return null;
        Item item = Registries.ITEM.get(ident);
        return item == net.minecraft.item.Items.AIR ? null : item;
    }

    public static String id(Item item) {
        return Registries.ITEM.getId(item).toString();
    }

    public static ItemStack stack(String id) {
        Item item = item(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    public static int stackSize(String id) {
        Item item = item(id);
        return item == null ? 64 : Math.max(1, new ItemStack(item).getMaxCount());
    }

    public static String name(String id) {
        Item item = item(id);
        return item == null ? id : new ItemStack(item).getName().getString();
    }

    /**
     * Resolve what a player or a model typed: an exact id, an id without namespace, or an
     * item's display name ignoring case. Null when nothing matches.
     */
    public static String resolve(String typed) {
        if (typed == null || typed.isBlank()) return null;
        String t = typed.trim();
        Item direct = item(t.toLowerCase(Locale.ROOT).replace(' ', '_'));
        if (direct != null) return id(direct);
        for (Item item : Registries.ITEM) {
            if (item == net.minecraft.item.Items.AIR) continue;
            if (new ItemStack(item).getName().getString().equalsIgnoreCase(t)) return id(item);
        }
        return null;
    }

    /** Items whose name or id contains the query, best matches (name starts with it) first. */
    public static List<Item> search(String query, int limit) {
        String q = query.toLowerCase(Locale.ROOT).trim();
        List<Item> starts = new ArrayList<>();
        List<Item> contains = new ArrayList<>();
        for (Item item : Registries.ITEM) {
            if (item == net.minecraft.item.Items.AIR) continue;
            String name = new ItemStack(item).getName().getString().toLowerCase(Locale.ROOT);
            String path = Registries.ITEM.getId(item).getPath();
            if (q.isEmpty() || name.startsWith(q) || path.startsWith(q)) starts.add(item);
            else if (name.contains(q) || path.contains(q)) contains.add(item);
            if (starts.size() >= limit) break;
        }
        starts.addAll(contains);
        return starts.size() > limit ? starts.subList(0, limit) : starts;
    }

    /**
     * Everything the player carries, by item id, including the contents of shulker boxes (and
     * bundles' cousins - anything with a container component) in their inventory.
     */
    public static Map<String, Long> carried() {
        Map<String, Long> out = new HashMap<>();
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p == null) return out;
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) add(out, inv.getStack(i));
        return out;
    }

    private static void add(Map<String, Long> out, ItemStack stack) {
        if (stack.isEmpty()) return;
        out.merge(id(stack.getItem()), (long) stack.getCount(), Long::sum);
        ContainerComponent inside = stack.get(DataComponentTypes.CONTAINER);
        if (inside != null) {
            for (ItemStack s : inside.iterateNonEmpty()) add(out, s);
        }
    }
}
