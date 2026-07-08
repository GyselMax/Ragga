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
  seed). Pass `?seed=<n>` to reproduce a specific map.
- `GET /cities/{id}` — read a stored city's summary.

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
| `BuildingDraft` | `PlacedBuilding` | physical placement only; links to its `Lot` + `BuildingTemplate` |
| `TemplateSpec` | `BuildingTemplate` | the DB catalog, seeded from `StubTemplateCatalog` |
| `GenerationConfig` | JSON column on `City` | provenance — reproduces the exact map with the seed |

`roadClasses[x][y]` is **not** stored: it is rebuildable from the `RoadSegment`s.

### Deliberate design choices

- **`GridCell` is physical-only** (`tileType`). Zoning lives on `Lot`, never on a cell —
  matching the domain rule in `TileType`/`ZoneType`.
- **`PlacedBuilding` is separate from the `Building` economic hierarchy.** It records only
  *what was stamped where*; price/rent/desirability belong to a later economic phase that
  can attach to a placement.
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
