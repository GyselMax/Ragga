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
| light purple | public-use parcel ground |
| strong green/blue/orange block | placed building (residential/commercial/industrial) |
| dark purple block | public building (school, station, ...) |
| white notch on a building edge | its front — which road it faces (shows rotation) |
| light gray | unused sliver |

## The two-layer type model

- **`TileType`** (physical map layer, per cell): `ROAD, LOT, PARK, PUBLIC, UNUSED`.
  `ROAD`, `PARK` and `PUBLIC` are immutable to building placement — a
  building footprint may only ever occupy `LOT` cells (`TileType.isBuildable()`).
- **`ZoneType`** (per lot): `RESIDENTIAL, COMMERCIAL, INDUSTRIAL, UNDER_CONSTRUCTION`.
  Only player-ownable development kinds. Parks/public land are not zones and
  not lots. A vacant lot is a lot with no building — vacancy is not a zone.

## Stages (in order — roads first, everything follows from them)

### 1. `RoadNetworkGenerator` — roads
Arterials are **wandering roads**: they cross the whole map but drift
sideways as they go — a random walk with momentum, bounded around their base
line — giving the organic, gently bent main streets of a real city instead
of a monotone checkerboard. (This is the simple version of organic city
generation; the full-fat versions use L-systems or tensor fields, but the
meander is the cheap 90% of the visual effect.) First arterial sits at half
spacing from the edge so the border keeps only a thin green band.

The space between arterials is then split by straight collector/local
roads, **region by region**: flood-fill the untyped space, and any region
still large enough gets a road cut through the middle of its largest
inscribed rectangle. Split probability follows the **density field** — a
guaranteed split in the city core (downtown has no big roadless blocks),
sliding down to half the configured chance at the map edge. The unsplit
outskirt blocks are where the large parks come from.

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
(`minLotWidth`..`maxLotWidth`). Which side a lot was cut from — and what
actually borders it — is measured per lot into its **frontages** (direction +
tiles of actual road contact + most major road class). Corner lots naturally
get two frontages.

Non-rectangular blocks (partial road crossings, map corners) are handled by
carving the largest inscribed rectangle first (maximal-rectangle DP) and
re-queueing the leftover pieces as smaller regions.

Whatever interior remains when no road-facing edge is left has no road
access by definition → those cells become `PARK`. Lots that end up with zero
actual road contact (e.g. cut along a map-edge stretch) also fall back to
park. Degenerate slivers become `UNUSED`.

### 4. `ZoneAssignmentService` — zoning from context
- A quota (`industrialRatio`) of the **largest** lots (≥ `industrialMinLotArea`)
  becomes `INDUSTRIAL` — factories need room.
- A quota (`publicRatio`) of **well-connected** parcels (arterial frontage or
  corner lots) is reserved as public sites.
- The rest rolls weighted-random: arterial frontage leans `COMMERCIAL`
  (main-street feel), everything else leans `RESIDENTIAL`.
- One majority-neighbor **smoothing pass** clusters zones so the result
  doesn't look like salt-and-pepper noise. Quota/arterial picks are locked so
  smoothing can't erase them. Toggle: `smoothingEnabled`.
- Vacancy follows the **density field**: essentially no vacant lots downtown,
  progressively more toward the outskirts (base rate `vacantLotRatio`,
  scaled by `1 - density`). Vacant lots are zoned land without a building —
  the cheap parcels players buy and build on later in the game.

### 5. `BuildingPlacementService` — buildings from context
**The lot's context defines the building, never the reverse.** Candidates
are templates matching the lot's zone whose footprint fits the lot in some
orientation facing one of the lot's actual road frontages. Templates are
authored front-facing-NORTH; placement rotates (width/depth swap on 90°/270°)
so the front faces the road, then sets the building flush against that edge.
Selection **packs the lot**: only candidates near the best achievable fill
are kept (how near depends on local density — downtown demands near-perfect
fill, the outskirts tolerate a small house on a big parcel), then one is
picked at random for variety. If nothing in the library fits, the lot simply
stays vacant — generation never fails on a sparse catalog.

Public sites get a public template the same way; their whole parcel turns
into `PUBLIC` tiles (building + surrounding plaza). If no public template
fits, the parcel gracefully falls back to a normal zoned lot.

### 6. `GenerationPipeline` — orchestration
Runs 1–5, tags every tile, and returns a `GenerationResult`. Deterministic
per seed: same seed + same config = the exact same city.

## Config knobs (`GenerationConfig`)

| Knob | Effect | Default |
|---|---|---|
| `width` / `height` | map size in tiles | 50×50 |
| `arterialSpacing` | distance between arterials | 18 |
| `arterialJitter` | random offset on arterial/split positions | 2 |
| `minBlockSizeForSplit` | blocks with a longer side ≥ this may get bisected | 12 |
| `maxLocalRoadDepth` | rounds of region splitting | 2 |
| `blockSplitChance` | base split chance — scaled up to 1.0 in the core and down to half at the edge | 0.8 |
| `minBlockArea` | smaller blocks become roadside park strips | 6 |
| `stripDepth` | lot depth cut inward from roads | 4 |
| `minLotWidth` / `maxLotWidth` | lot width range | 3–6 |
| `industrialMinLotArea` | minimum lot area for the industrial quota | 18 |
| `industrialRatio` | target fraction of industrial lots | 0.10 |
| `publicRatio` | target fraction of public parcels | 0.05 |
| `vacantLotRatio` | base vacancy rate (density-scaled: ~0 downtown, ~2.3× at the edge) | 0.08 |
| `smoothingEnabled` | zone-clustering smoothing pass | true |
| `coreRadiusFraction` | radius of the full-density core, as a fraction of center-to-edge distance | 0.35 |
| `edgeDensity` | density left at the map border — lower = emptier, greener outskirts | 0.35 |

## Sanity check invariants (`GenerationSanityCheck`)

1. every lot has at least one road frontage
2. every non-vacant lot has a building
3. every footprint stays inside its own lot
4. no two footprints overlap
5. no footprint cell sits on a road/park tile (immutability rule)
6. the road network is 100% flood-fill connected

These run after every visualizer render; a FAIL next to a weird-looking PNG
tells you immediately whether you're looking at a tuning issue or a bug.
