# City Generation Pipeline

How a city map is generated, stage by stage. All classes live in this
package and run fully in memory — no Spring context, no database — which is
why `GridVisualizer` can render a city standalone. Persistence (Phase B)
maps the finished `GenerationResult` onto JPA entities afterwards.

## How to run / judge the output

Open [GridVisualizer](../GridVisualizer.java) in IntelliJ and hit the green
arrow next to `main`. It writes `grid.png` in the project root and prints:

- a **sanity check** (PASS/FAIL per invariant — see bottom of this file)
- a **summary** (lot counts per zone, vacancy, parks, roads)
- the **seed** used — pass it back as the first program argument
  (Run Configuration → Program arguments) to reproduce the exact same city.

The visualizer uses `StubTemplateCatalog`, a hardcoded starter library —
**not** the real building catalog, which will be DB-seeded in Phase B so it
can grow during a live game without redeploys.

### Color legend

| Color | Meaning |
|---|---|
| near-black / dark gray / gray | road: arterial / collector / local |
| light green fill | residential lot |
| light blue fill | commercial lot |
| light orange fill | industrial lot |
| tan fill | vacant lot (zoned, no building — purchasable) |
| bright green | park tiles |
| river blue | `WATER` tiles (river; arterial-colored tiles crossing it are bridges) |
| dark woodland green | `FOREST` tiles (noise-carved, rim and corners only) |
| wheat fill | farmland lot (rural rim; small brown farm buildings) |
| light purple | public-use parcel ground |
| strong green/blue/orange block | placed building (residential/commercial/industrial) |
| dark purple block | public building (school, station, ...) |
| white notch on a building edge | its front — which road it faces (shows rotation) |
| light gray | `UNUSED` tiles (normally absent — slivers become parks) |

## The two-layer type model

- **`TileType`** (physical map layer, per cell): `ROAD, LOT, PARK, PUBLIC, UNUSED, WATER, FOREST`.
  `ROAD`, `PARK`, `PUBLIC`, `WATER` and `FOREST` are immutable to building
  placement — a building footprint may only ever occupy `LOT` cells
  (`TileType.isBuildable()`).
- **`ZoneType`** (per lot): `RESIDENTIAL, COMMERCIAL, INDUSTRIAL, FARMLAND, UNDER_CONSTRUCTION`.
  Only player-ownable development kinds. Parks/public land are not zones and
  not lots. A vacant lot is a lot with no building — vacancy is not a zone.

## Stages (in order — terrain first, then roads, everything follows from them)

### 0. `TerrainGenerator` — natural terrain
Also carves **forests**: two octaves of seeded value noise thresholded
against a density-raised cutoff, so woodland blobs survive only where
density is low — the rim and the corners, never downtown, exactly where real
forests survive urbanization. Roads route around them (arterials cut
through); no lots ever form inside.

Carves a meandering **river** across the map before any road exists, using
the same random-walk-with-momentum as the wandering arterials but with far
longer drift holds and a much wider drift bound (`riverMaxDrift`) — big lazy
curves instead of a road-like wiggle. The width (`riverWidth`) breathes ±2
tiles so the banks aren't ruler-straight. The river's base line runs through
the **city core** (offset by up to half the core radius): cities grow around
their river, so it cuts through or grazes downtown, never some random strip
of suburbs. Every water tile gets a 1-tile `PARK` bank — a green ribbon
tracing the river, and a guarantee no lot borders water directly.

Because the terrain is pre-seeded into the tile grid the road generator
draws into, the rest of the pipeline needs **zero water handling**: block
flood-fill, road splitting and lot subdivision only ever operate on null
tiles. Only arterials overwrite water — those tiles are the **bridges**.
Arterials that would run lengthwise *inside* the river corridor (parallel
orientation, base line within the meander band) are dropped; the riverside
is served by the collector/local mesh instead, and the connectivity
invariant (check 6) guarantees every bank stays reachable.

### 0.5 `SettlementPlanner` — hamlets and the countryside road graph
Between terrain and roads. Everything here is **destination-driven**: there
are no random rural highways, so a countryside road exists only because it
connects two settlements or reaches a map-edge exit.

- **Hamlets** — tiny villages rejection-sampled onto genuinely rural,
  buildable, spaced-out land. Each is just a small density bump
  (`hamletRadius`, `hamletPeakDensity`) appended to the field via
  `DensityField.withSettlements`, so the normal road/block/lot/zone pipeline
  builds each village on its own — the same way it builds the main city,
  because every stage keys off the density field. Uncapped maps whose city
  fills the map get few or none (little rural land); capped/multi-city maps
  get a proper scattering.
- **Connection roads** — a graph over every settlement center (main city,
  satellites, hamlets): each links to its `settlementConnectionCount` nearest
  neighbors, then a union-find pass adds the shortest cross-component edges
  until the whole set is ONE connected network. This is what guarantees the
  road network stays 100% connected.
- **Edge exits** — `edgeExitCount` roads from the border to their nearest
  settlement: the region's links to the wider world. Replaces the old random
  rural-highway roll.

The plan is handed to the road generator, which draws the connection roads as
ARTERIAL doglegs (see stage 1).

### 1. `RoadNetworkGenerator` — roads
Arterials are **wandering roads**: they cross the whole map but drift
sideways as they go — a random walk with momentum, bounded around their base
line — giving the organic, gently bent main streets of a real city instead
of a monotone checkerboard. (This is the simple version of organic city
generation; the full-fat versions use L-systems or tensor fields, but the
meander is the cheap 90% of the visual effect.) First arterial sits at half
spacing from the edge so the border keeps only a thin green band.

The arterial wander is **density-scaled**: streets bend visibly inside the
old core (medieval irregularity) and straighten toward the planned outskirts.

The grid arterials are drawn on **urban land only** (city cores and hamlet
bumps). The countryside is left entirely to the settlement plan's connection
roads (see stage 0.5) — a rural road always goes *somewhere* rather than
being a random highway to nowhere. Not every walked arterial tile is drawn:
- **Urban-only** — a walked arterial deposits road tiles only where local
  density ≥ `farmlandDensityThreshold`. The full-map walk still runs so
  parallel arterials keep their spacing rhythm; it just draws nothing over
  farmland.
- **Bridges** — only arterials PERPENDICULAR to the river draw over water;
  parallel arterials break where the meander crosses their line and resume
  beyond, so the riverside keeps full road density with no roads running
  lengthwise in the water.
- **Grid gaps** — inside the city, each arterial may skip up to two
  stretches of one to two block lengths, merging the neighboring blocks
  into longer rectangles: not every square gets all four roads. Gaps never
  apply on bridges.

Then the **connection roads** from the settlement plan are drawn as ARTERIAL
L-doglegs (one straight leg per axis, sharing the corner tile). These are the
only roads in the countryside; where they cross the city they merge into the
grid, where they cross water they bridge/causeway. Because the settlement
graph is a single connected component (stage 0.5), drawing them guarantees
every city, satellite, hamlet and edge exit is reachable — sanity invariant 6
doubles as the regression guard.

The space between arterials is then split by straight collector/local
roads, **region by region**: flood-fill the untyped space, and any region
still large enough gets a road cut through the middle of its largest
inscribed rectangle. Split probability follows the **density field** — a
guaranteed split in the city core (downtown has no big roadless blocks),
sliding down to half the configured chance at the map edge, and **zero
below the farmland threshold** (fields are not street grids). One extra
split round runs for the dense core only, so downtown reads as fine grain.
The unsplit outskirt blocks are where the large parks come from.

Connectivity is guaranteed by construction (cuts run wall-to-wall inside
their region), and a flood-fill pass afterwards demotes any road tile that
somehow isn't reachable — a disconnected "road to nowhere" granting lots
frontage would be a silent lie.

### 2. `BlockSubdivisionService` — blocks
Flood fill of all non-road space → contiguous blocks. Blocks below
`minBlockArea` become roadside `PARK` strips (bent roads produce many small
leftovers; green pockets read far better than dead gray slivers).

### 3. `LotSubdivisionService` — lots (guillotine cuts)
From every road-facing edge of a block, a strip of `stripDepth` tiles is cut
inward, then split perpendicular into lots of random width
(`minLotWidth`..`maxLotWidth`). Lot grain follows density, decided per
block: below the farmland threshold strips reach 8x deeper and lots are 3x
wider (broad field bands along the rural highways, open meadow beyond);
above 0.8 the width range narrows toward the minimum (fine downtown grain). Which side a lot was cut from — and what
actually borders it — is measured per lot into its **frontages** (direction +
tiles of actual road contact + most major road class). Corner lots naturally
get two frontages.

Non-rectangular blocks (partial road crossings, map corners) are handled by
carving the largest inscribed rectangle first (maximal-rectangle DP) and
re-queueing the leftover pieces as smaller regions.

Whatever interior remains when no road-facing edge is left has no road
access by definition → those cells become `PARK`. Lots that end up with zero
actual road contact (e.g. cut along a map-edge stretch) also fall back to
park, as do strips too short to hold a legal lot and degenerate slivers —
green pockets read better than dead gray. `UNUSED` still exists as a tile
type but the generator normally emits none.

### 4. `ZoneAssignmentService` — zoning from context
Zoning follows the **density field**, so the city reads as core / ring / rim
instead of uniform confetti:
- Everything below `farmlandDensityThreshold` becomes `FARMLAND`, locked, so
  neither the weighted rolls nor smoothing can urbanize the rim.
- A quota (`industrialRatio`) of large lots (≥ `industrialMinLotArea`)
  becomes `INDUSTRIAL`, scored by `area × (1 − density)` — factories claim
  roomy rim parcels and never sit downtown (core lots score 0).
- A quota (`publicRatio`) of **well-connected** parcels (arterial frontage or
  corner lots) is reserved as public sites, ranked by density with a random
  jitter so civic buildings cluster around the core.
- The rest rolls weighted-random: commercial weight grows with density
  **squared** (downtown is dominantly commercial), arterial frontage adds the
  main-street bonus everywhere, loose industry only sprinkles near the rim
  (`(1 − density)²`), and residential takes the remainder.
- One majority-neighbor **smoothing pass** clusters zones so the result
  doesn't look like salt-and-pepper noise. Quota/arterial picks are locked so
  smoothing can't erase them. Toggle: `smoothingEnabled`.
- Vacancy (how built-out the zoning is) interpolates by **density** from
  `edgeVacantRatio` at the rim to `coreVacantRatio` downtown — the two knobs
  set independently how dense the core and the outskirts feel. Vacant lots are
  zoned land without a building: the cheap parcels players buy and build on
  later in the game.

### 5. `BuildingPlacementService` — buildings from context
**The lot's context defines the building, never the reverse.** Candidates
are templates matching the lot's zone whose footprint fits the lot in some
orientation facing one of the lot's actual road frontages. Templates are
authored front-facing-NORTH; placement rotates (width/depth swap on 90°/270°)
so the front faces the road, then sets the building flush against that edge.
Selection is a **frontage-first fit**: fill the street frontage as fully as
possible, and only then fill the depth. Each candidate is scored by
(frontage gap, depth gap) and the lexicographically smallest wins — so a
perfect fill beats a shallower building that still spans the whole street,
which in turn beats a narrower one that leaves a hole in the street; narrowing
the frontage is the last resort. One of the equally-best fits is picked at
random for variety. The core packs tight because its small lots make perfect
fills easy; big edge lots get big buildings for the same reason, so the
core→edge size gradient falls out of the **lot** sizes (fine downtown, coarse
at the rim) rather than any fill target. If nothing in the library fits, the
lot simply stays vacant — generation never fails on a sparse catalog.

Public sites get a public template the same way; their whole parcel turns
into `PUBLIC` tiles (building + surrounding plaza). If no public template
fits, the parcel gracefully falls back to a normal zoned lot.

### 6. `GenerationPipeline` — orchestration
Runs terrain → settlement planning → roads → blocks → lots → zoning →
buildings, tags every tile, and returns a `GenerationResult`. Deterministic
per seed: same seed + same config = the exact same city.

It builds ONE `DensityField` and upgrades it in place, threading the single
instance through every stage (stages must not construct their own, or the
jittered centers would disagree):
- **`DensityField.of`** — the field is a set of `Core` blobs; density at a
  tile is the MAX over all cores, floored at `edgeDensity`. `cores[0]` is the
  main city (off-center-jittered, peak 1.0). `cityCount > 1` adds satellite
  cities (smaller radius, lower `satellitePeakDensity`), rejection-placed so
  their urban extents never touch. One core with peak 1.0 reproduces the
  original single-radial-core behavior exactly.
- **`withRiver`** — after terrain, adds the main core's **river lobe**: a
  second density blob smeared along the river centerline (squeezed across the
  flow, stretched ~1.6x along it, closing quadratically before the map
  border), so the main city is an elongated river-hugging oval, not a flat
  circle.
- **`withSettlements`** — after settlement planning, appends the hamlet
  cores, so the pipeline builds each village.

`maxCityRadius` caps the main city in absolute tiles (0 = scales with the
map), which is what lets a big map hold a constant-size city in a sea of
countryside rather than a proportionally gigantic one — and what makes room
for satellites and hamlets.

## Config knobs (`GenerationConfig`)

Every knob has a detailed javadoc on the record itself — that (and
`defaults(...)`) is the source of truth; the defaults below reflect a
snapshot and drift during tuning.

| Knob | Effect | Default |
|---|---|---|
| `width` / `height` | map size in tiles (passed by the caller, e.g. `GridVisualizer.main`) | — |
| `arterialSpacing` | distance between arterials | 25 |
| `arterialJitter` | random offset on arterial/split *positions* (spacing rhythm, not straightness) | 3 |
| `arterialMaxDrift` | how far a road may bend sideways — 0 = perfectly straight arterials | 2 |
| `minBlockSizeForSplit` | blocks with a longer side ≥ this may get bisected | 12 |
| `maxLocalRoadDepth` | rounds of region splitting | 2 |
| `blockSplitChance` | base split chance — scaled up to 1.0 in the core and down to half at the edge | 0.95 |
| `minBlockArea` | smaller blocks become roadside park strips | 6 |
| `stripDepth` | lot depth cut inward from roads | 4 |
| `minLotWidth` / `maxLotWidth` | lot width range | 3–6 |
| `industrialMinLotArea` | minimum lot area for the industrial quota | 18 |
| `industrialRatio` | target fraction of industrial lots | 0.10 |
| `publicRatio` | target fraction of public parcels (0 = none; any positive value reserves at least one) | 0.05 |
| `coreVacantRatio` / `edgeVacantRatio` | fraction of zoned lots left vacant in the core / at the edge — lower = denser, more built-out | 0.0 / 0.15 |
| `smoothingEnabled` | zone-clustering smoothing pass (true majority: challenger needs 3+ neighbors and must outnumber the lot's own zone) | true |
| `coreRadiusFraction` | radius of the full-density core, as a fraction of center-to-edge distance | 0.35 |
| `edgeDensity` | density left at the map border — lower = emptier, greener outskirts | 0.2 |
| `coreCenterJitter` | max per-seed offset of the core from the map center, as a fraction of each dimension — 0 = always dead center | 0.2 |
| `riverEnabled` | carve a meandering river through/past the core (off = dry map) | true |
| `riverWidth` | nominal river width in tiles (breathes ±2) | 12 |
| `riverMaxDrift` | how far the river may meander from its base line — the big lazy curves | 60 |
| `maxCityRadius` | absolute cap on the main city radius in tiles (0 = scales with the map) | 0 |
| `forestsEnabled` | noise-carved woodland at the rim and corners | true |
| `farmlandDensityThreshold` | below this density the map turns rural: no splits, tripled parcels, locked FARMLAND | 0.25 |
| `cityCount` | number of city cores; > 1 adds satellite cities (needs `maxCityRadius` > 0) | 1 |
| `satelliteMinScale` / `satelliteMaxScale` | satellite radius as a fraction of the main city radius | 0.45 / 0.75 |
| `satellitePeakDensity` | peak density at a satellite center (a town, not a second downtown) | 0.85 |
| `hamletCount` | target villages scattered in the countryside (only as many as fit) | 5 |
| `hamletRadius` | hamlet density-bump radius in tiles | 18 |
| `hamletPeakDensity` | peak density at a hamlet center | 0.45 |
| `minSettlementSpacing` | minimum hamlet center-to-center distance | 60 |
| `settlementConnectionCount` | nearest neighbors each settlement links to by road | 2 |
| `edgeExitCount` | roads leaving the map toward the wider world | 2 |

## Sanity check invariants (`GenerationSanityCheck`)

1. every lot has at least one road frontage
2. every non-vacant lot has a building
3. every footprint stays inside its own lot
4. no two footprints overlap
5. no footprint cell sits on a road/park tile (immutability rule)
6. the road network is 100% flood-fill connected

These run after every visualizer render; a FAIL next to a weird-looking PNG
tells you immediately whether you're looking at a tuning issue or a bug.
