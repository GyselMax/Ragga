# City Persistence (Phase B)

How a generated city is stored. Generation (see
[GENERATION.md](../generation/GENERATION.md)) runs fully in memory and produces one
`GenerationResult`; this package maps that result onto JPA entities and saves it. The
`grid/generation` package stays **JPA-free** — nothing here leaks back into it, so
`GridVisualizer` keeps running standalone.

## How to run

The catalog is DB-seeded on first boot, so just start the app and call the API (Swagger
UI at `/swagger-ui/index.html`):

- `POST /cities/generate` — generate + store a new city, returns a summary (including the
  seed). Accepts every `GenerationConfig` knob as a query param (same set as the tuner;
  omitted ones keep their default), so a tuned city can be persisted exactly as previewed.
  `?seed=<n>` reproduces a specific map; omit it for a random (returned) seed. An impossible
  knob combination returns a 400.
- `GET /cities` — list every stored city (to find an id).
- `GET /cities/{id}` — read a stored city's summary.
- `GET /cities/{id}/render` — the stored city as a PNG (same color legend as
  `GridVisualizer`, see GENERATION.md). `?cellSize=<px per tile>` (1-10, default 4),
  `?showFloors=true` overlays each building's floor count.

**Interactive tuner** (no persistence — pure in-memory preview):

- Open **`/tuner.html`** in a browser: a slider/number panel for every
  `GenerationConfig` knob, live-rendering the map as you tweak. The page builds
  its form from `GET /cities/preview/defaults` (so new knobs appear automatically)
  and renders via `GET /cities/preview/render` (all knobs as query params;
  omitted ones keep their default; an impossible combination returns a 400 with
  the reason). Preview handles maps up to 5000 tiles/side (matching the standalone
  generator's large-map workflow); saving via `/cities/generate` is limited to 1000/side
  since it stores one row per tile. Note `maxCityRadius` is in absolute tiles, so on a
  small map a large radius fills it and leaves no room for satellite cities.
- **💾 Generate & Save** on the tuner POSTs the current knobs + seed to
  `/cities/generate`, persisting the previewed city and linking to its render.

The full store→reload path is covered by `CityPersistenceRoundTripTest` (runs on in-memory
H2, no live DB needed).

## What gets stored

The generator emits in-memory drafts; the mapper (`GenerationResultMapper`) turns each into
an entity:

| Generation (in memory) | Persisted entity | Notes |
|---|---|---|
| `tiles[x][y]` (`TileType`) | `GridCell` (one row per tile) | the physical raster of record |
| `LotDraft` | `Lot` | frontages & zone-lock dropped (generation-time only) |
| `RoadDraft` | `RoadSegment` | |
| `BuildingDraft` | `PlacedBuilding` | physical placement; links to its `Lot` + `BuildingTemplate`, and (residential only) to its economic `Building` instance |
| `BuildingDraft` (residential) | `SimulatedLowRise` / `SimulatedHighRise` | the economic bridge: floors + householdCapacity from the blueprint, bedrooms derived; split by `SimulatedResidential.HIGH_RISE_MIN_FLOORS` |
| `TemplateSpec` | `BuildingTemplate` | the DB catalog, seeded from `StubTemplateCatalog`; carries footprint + floors + householdCapacity |
| `GenerationConfig` | JSON column on `City` | provenance — reproduces the exact map with the seed |

`roadClasses[x][y]` is **not** stored: it is rebuildable from the `RoadSegment`s.

### Deliberate design choices

- **`GridCell` is physical-only** (`tileType`). Zoning lives on `Lot`, never on a cell —
  matching the domain rule in `TileType`/`ZoneType`.
- **`PlacedBuilding` stays the physical record; the `Building` hierarchy is the economic one.**
  A placement records *what was stamped where*; the nullable `PlacedBuilding.building` link
  attaches the economic instance. Today only residential placements are bridged (to
  `SimulatedLowRise`/`SimulatedHighRise`, split by the blueprint's floors); price/rent/
  desirability stay null until the land-value/rent-formula phase, and commercial/industrial/
  farm/public placements stay physical-only.
- **The catalog seeder also backfills.** `ddl-auto=update` adds new columns (floors,
  householdCapacity) with 0 on rows seeded before the column existed; on boot the seeder
  fills stub-known rows still at 0 from `StubTemplateCatalog`, never touching hand-edited
  nonzero values.
- **Zoning per cell is not duplicated.** A cell only knows its `TileType`; to find a
  tile's zone, look up the `Lot` covering it.

## Cost of the per-cell grid

A 500×500 map is **250k `GridCell` rows per city** — the price of storing the raster
directly instead of deriving it. Two things keep the write sane, both in
`application.properties`:

- `hibernate.jdbc.batch_size=100` + `order_inserts/order_updates=true` — turns the insert
  storm into batched multi-row statements.
- All child collections cascade from `City`, so one `save(city)` writes the whole graph.

Note the trade-off: the whole grid lives in the persistence context during a save, so very
large maps are memory-heavy. The `CitySummary` DTO deliberately does **not** serialize the
grid (it reports `width*height` instead of loading every cell); a full grid export, if
ever needed, belongs on its own endpoint.

## Schema lifecycle

`ddl-auto=update` (not `create-drop`) so stored cities survive a reboot. `update` only adds
tables/columns — during active schema churn you may still need a manual `DROP` in dev.
