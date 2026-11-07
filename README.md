# Planner

Plan the materials for a build in the units you actually store them in. Make a project, add
what it needs as chests, double chests, shulker chests or shulker double chests, and press **+**
each time you fill one. Planner shows how many of each you still need, what that comes to in
stacks and shulker boxes, and how long the rest takes at your farm's rate.

Part of the [GoH mod family](https://github.com/users/omeldar/projects/2). Built for
**Minecraft 1.21.11 / Fabric**. Client-side only: nothing on the server, works anywhere.

---

## How it works

**Projects** are one list across all your worlds and servers, each labelled with where it was
made. Plan a build in a creative copy of the world, then open the same project on the server and
fill it there. **Copy** makes a fresh copy of a project's materials list with nothing filled, for
planning a variant. **B** opens it, as does `/plan`; the key is rebindable in Controls, or on the last page of the GoH setup walkthrough. (0.1.0 kept a list per world; those are folded in on first
start.)

**Materials are planned in a container you pick per material:**

| Container | Holds |
| --- | --- |
| Chest | 27 stacks |
| Double chest | 54 stacks |
| Shulker chest | a chest of 27 full shulker boxes = 729 stacks |
| Shulker double chest | a double chest of 54 full shulker boxes = 1,458 stacks |
| Shulker box, stack, item | for the small stuff |

Every amount uses the item's real stack size: 64 for most blocks, 16 for ender pearls and signs,
1 for tools. So "2 double chests of ender pearls" is 1,728 pearls, not 6,912.

**Each row** shows have / needed in that material's container ("1.1 / 1.5 double chests"), a
progress bar, and **−** / **+**: press **+** when you have filled one more, **−** if you took one
out. Nobody counts 3,456 items; they count chests. Select a row for the full picture - what one
container holds, the total in stacks, shulkers and double chests, and the time left if you set
a farm rate. The header totals the whole project.

**Edit** a material to change its container (the numbers convert, so 2 double chests becomes 4
chests), set needed and filled outright, or give a farm rate in items per hour. **Count inv.**
sets "have" for every material to what you carry, shulker box contents included; it replaces
rather than adds, so pressing it twice never counts twice.

**Adding** a material: search, click the icon, type an amount, click the unit until it says the
container you want. The line above the buttons shows what it comes to before you add it.

The screens are laid out from the bottom up, so they fit the default window at the default GUI
scale; lists scroll.

## Commands

```
B                                  the open project, or the project list (rebindable)
/plan                              the same, for anyone who prefers typing
/plan new <name>                   create a project and open it
/plan open <name>
/plan add <item> <amount> [unit]   unit: chests, double chests (default), shulker chests,
                                   shulker double chests, shulker boxes, stacks, items
/plan filled <item> [n]            filled n more containers (default 1, negative to undo)
/plan have <item> <items>          set what you have, in items
/plan rate <item> <per hour>       your farm's rate
/plan remove <item>
/plan count                        count your inventory and shulkers into "have"
/plan show                         the project in chat
```

## Lumi

With [Lumi](https://github.com/omeldar/lumi) installed, a session gets the project tools under a
`plan_` prefix through Lumi Link (port 25596, or the next free one): list, read and create
projects, add and change materials, `add_filled`, count the inventory, open a project on screen,
and a `storage_math` tool that converts any amount of any item into containers. So "how many
shulker double chests is a 200 x 200 platform of deepslate tiles" or "plan me a castle in stone
bricks" are one question; Lumi is told to say when amounts are estimates.

## Cost

Nothing while you play: no HUD, no rendering outside its own screens, no network traffic. One
tick listener only opens a screen a command asked for.

## Ideas

On the [project board](https://github.com/users/omeldar/projects/2): a count button on open
chests, importing Litematica material lists, sharing projects through the GoH server, and a
simple task list for general project planning.

## Licence

MIT. Not made or endorsed by Mojang, Microsoft or Anthropic.
