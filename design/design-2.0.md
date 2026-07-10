# Ragga Backend — Design Document (v2.0)

> Supersedes [design-1.0.md](design-1.0.md) (the original design doc, kept as a frozen
> historical record — don't edit it). This is the **living** design doc: update it as the
> project evolves so both the user and any AI assistant picking this up in a future session
> can get oriented quickly. See especially **Current Status** and **Known Gaps** below before
> starting new work.

## Project Philosophy

The core idea is a **city-first simulation**. A fully functioning city is generated and runs
on its own before players are ever allowed to interact with it. The city should be able to
sustain itself — economy, jobs, spending, housing — without any player input. Players are
participants in an already-living world, not the reason it exists.

This means the simulation has integrity before player input can distort it.

---

## Developer Context (for AI assistants)

- Bachelor-level Java developer
- Using Spring Boot with JPA/Hibernate and MySQL
- Wants step-by-step explanations for any code provided
- Wants the full file every time code is given
- Prefers a senior-developer tone: honest feedback, flag issues early, suggest improvements
- **QA requirement: after ANY code change, a dedicated quality-assurance review agent must
  be run over the changed code before presenting the result.** The QA pass checks for
  bugs, doc/behavior mismatches (e.g. a config value whose javadoc promises something the
  code doesn't do), broken invariants, and inconsistencies with this design document. Its
  findings are reported honestly, including the ones that were not fixed.
  **Scope rule: a QA pass covers ONLY the classes changed (and those directly affected by
  the change) — never a full re-review of already-verified code.** Verified work is not
  redone; where possible the same QA agent is resumed with a follow-up instead of starting
  a fresh full review.
- **Testing requirement: completed systems get regression test classes so future work
  cannot silently break older code.** The QA pass includes writing/updating these tests —
  cheap, deterministic, no Spring context where possible. For the generation pipeline this
  means **golden-seed tests**: generation is deterministic per seed, so a test locks one
  seed's summary statistics (lot counts per zone, road/building/park counts) as exact
  expected values plus the sanity-check invariants. If a deliberate generation change
  breaks the golden numbers, update the constants in the same commit — the test exists to
  catch *accidental* changes, not to forbid evolution.

---

## Companion Docs

Two package-level docs go deeper than this file on already-built systems — read them instead
of re-deriving the details:

- [`src/main/java/be/ragga/raggabackend/simulation/grid/generation/GENERATION.md`](../src/main/java/be/ragga/raggabackend/simulation/grid/generation/GENERATION.md) —
  how the city generation pipeline works stage by stage, how to run `GridVisualizer`, all
  `GenerationConfig` knobs.
- [`src/main/java/be/ragga/raggabackend/simulation/grid/persistence/PERSISTENCE.md`](../src/main/java/be/ragga/raggabackend/simulation/grid/persistence/PERSISTENCE.md) —
  how a generated city is mapped onto JPA entities and stored, the DB-seeded template
  catalog, batching trade-offs of the per-cell grid.

---

## Architecture Overview

### Package Structure (current, as of this revision)
```
be.ragga.raggabackend
├── simulation
│   ├── City.java                              (root entity: seed, config snapshot, grid + lots + roads + buildings)
│   ├── building
│   │   ├── Building.java                       (abstract base, JOINED inheritance)
│   │   ├── simulated
│   │   │   ├── SimulatedBuilding.java          (abstract, NPC-owned)
│   │   │   ├── SimulatedResidential.java       (abstract)
│   │   │   ├── SimulatedLowRise.java
│   │   │   ├── SimulatedHighRise.java
│   │   │   ├── SimulatedCommercial.java
│   │   │   └── SimulatedIndustrial.java
│   │   ├── player
│   │   │   ├── PlayerBuilding.java             (abstract, player-owned — NOT yet @Entity, see Known Gaps)
│   │   │   ├── PlayerResidential.java
│   │   │   ├── PlayerCommercial.java
│   │   │   └── PlayerIndustrial.java
│   │   └── modifier
│   │       ├── PublicBuilding.java             (abstract — not yet a Building subtype or JPA entity)
│   │       ├── BusStop.java
│   │       ├── MetroStop.java
│   │       └── TrainStop.java
│   ├── household
│   │   ├── Household.java
│   │   ├── Inhabitant.java
│   │   ├── Occupation.java
│   │   ├── EducationLevel.java
│   │   └── HouseholdRole.java
│   └── grid
│       ├── CityGridConfig.java                 (ragga.grid.* properties: width, height, zoneAnchorCount)
│       ├── CityRepository.java
│       ├── GridCell.java                       (physical raster: one row per tile, TileType only)
│       ├── GridPosition.java                   (@Embeddable x/y, reused across every positioned entity)
│       ├── GridVisualizer.java                 (standalone PNG renderer, no Spring/DB — debug tool)
│       ├── TileType.java / ZoneType.java / lot/Direction.java / road/RoadClass.java
│       ├── generation/                         (JPA-free pipeline — see GENERATION.md)
│       │   ├── GenerationPipeline.java, GenerationConfig.java, GenerationResult.java
│       │   ├── TerrainGenerator, SettlementPlanner, RoadNetworkGenerator,
│       │   │   BlockSubdivisionService, LotSubdivisionService, ZoneAssignmentService,
│       │   │   BuildingPlacementService                       (the pipeline stages, in order)
│       │   ├── LotDraft, RoadDraft, BuildingDraft, TemplateSpec  (in-memory drafts)
│       │   └── StubTemplateCatalog.java         (hardcoded starter library, standalone use only)
│       └── persistence/                         (JPA mapping layer — see PERSISTENCE.md)
│           ├── CityPersistenceService.java      (generateAndSave(seed) / getSummary(id))
│           ├── entity/    Lot.java, RoadSegment.java, PlacedBuilding.java
│           ├── catalog/   BuildingTemplate.java (DB-seeded catalog), DbTemplateCatalog,
│           │              BuildingTemplateSeeder, TemplateCatalog (interface)
│           ├── mapping/   GenerationResultMapper.java, GenerationConfigConverter.java
│           ├── repository/ LotRepository, RoadSegmentRepository, PlacedBuildingRepository
│           └── web/       CityGenerationController.java, CitySummary.java
```

### Key Abstraction Note
`PlayerBuilding` and `SimulatedBuilding` both extend `Building` but will eventually need
a shared interface or abstraction point to support the **building conversion mechanic**
(see below). This is not urgent but should be kept in mind to avoid painful refactoring later.
**Update:** `PlayerBuilding` currently isn't even a JPA entity yet (see Known Gaps) — that's
the more immediate blocker, ahead of the conversion-mechanic abstraction.

Separately: the generation pipeline's `PlacedBuilding` (physical placement: template, footprint,
facing, lot) is a **deliberately separate** entity from the `Building` hierarchy (economic:
price, rent, desirability). The generator only knows physical placement; bridging a
`PlacedBuilding` to a real `SimulatedResidential`/`SimulatedCommercial`/etc. instance with
economic values is Roadmap Step 3's remaining work (see Current Status).

---

## Core Systems

### 1. Grid and Zoning
- The city is built on a **grid**. Coordinates are relevant from day one.
- `Building` and `Household` both need grid coordinates immediately.
- Grid is generated first, then filled with **zones** (residential, commercial, industrial, etc.)
- Buildings are then placed within appropriate zones.
- **Implemented**: see [GENERATION.md](../src/main/java/be/ragga/raggabackend/simulation/grid/generation/GENERATION.md)
  for the full pipeline and [PERSISTENCE.md](../src/main/java/be/ragga/raggabackend/simulation/grid/persistence/PERSISTENCE.md)
  for how it's stored. `Building`/`Household` themselves don't carry coordinates yet — see
  Known Gaps.

### 2. City Generation Order
1. Generate the grid
2. Apply zoning to the grid
3. Place simulated (NPC) buildings within zones — they get coordinates at this point
4. Generate households and place them in residential zones
5. Generate inhabitants inside those households

### 3. Job Matching (NPC, proximity-weighted)
- Happens at generation time, before any player interaction
- Jobs (`Occupation` slots) are tied to `SimulatedBuilding` instances (commercial/industrial)
- Matching is weighted by:
  - **Proximity** (distance between `Household` and `Building` on the grid)
  - **Education match** (`Inhabitant.educationLevel` vs `Occupation.requiredEducation`)
  - **Salary** (`Occupation.averageSalary` + standard deviation)
- Goal is realistic job distribution, not purely nearest-job
- An inhabitant always prefers a job that matches their exact education level. They will accept
  a lower-level job if nothing better is available, but this is tracked — an overqualified
  inhabitant is more likely to voluntarily job-hop when a better match appears (see Life Events)

### 4. Company Class and Building Ownership
- A `Company` is a first-class entity with its own persistence and stats. It is not just a
  building — a single company can own multiple buildings.
- Each `SimulatedCommercial` or `SimulatedIndustrial` building belongs to a `Company`.
- The company tracks:
  - **Financial reserves** — current cash on hand
  - **Monthly revenue and expenses** — used to evaluate profitability each turn
  - **Ideal employee count** — the number of filled roles needed to operate at full capacity
  - **Actual employee count** — how many roles are currently filled
  - **Efficiency rating** — starts at 100%, reduced when roles are unfilled or filled by
    underqualified workers. An overqualified worker filling a lower-level role does not
    improve efficiency beyond that role's ceiling.
  - **Seniority-adjusted wage bill** — total wages paid, factoring in tenure bonuses
- Company health drives layoffs, hiring, and eventually bankruptcy. A company that cannot
  pay its wages and upkeep will begin shedding employees, then eventually collapse.

### 5. Simulation Turns (Pre-Player)
- After generation, the city runs for N turns with **no player involvement**
- Purpose: validate the city doesn't collapse (economy holds, people stay employed, spending works)
- Only after this validation phase are players allowed in

---

## Player Mechanics

### Player Entry
- Players enter an already-functioning city
- Players can **hire inhabitants** who are looking for work
- Players must pay a **fair, inflation-adjusted market wage** (based on `Occupation.averageSalary`)
- Players can view where their employee lives and basic life details

### Building Acquisition (Takeover Mechanic)
- Players can buy NPC-owned (`Simulated`) buildings
- Purchase options: **full cash** or **loan**
- Conversion is **instant**: `SimulatedCommercial` → `PlayerCommercial`, etc.
- Benefits of takeover (vs building new):
  - **Customer loyalty** carries over
  - **Existing workers** stay (unless they quit)
  - **Proven operational settings** transfer — the building works as before from day one

### Player-Owned vs NPC-Owned Shops
- NPC shops exist at generation and function independently
- Player shops are either bought up from NPC stock or built new
- Both service inhabitants based on proximity and budget

---

## Economy

### Income Processing Order (per turn, per inhabitant)
1. Receive gross salary (base pay + seniority bonus)
2. Apply taxation
3. Service personal debt (interest + repayment)
4. Escalate remaining debt to household level if needed
5. Pay rent
6. Spend at shops (see below)
7. Remainder goes to savings (personal or shared household)

### Shop Spending
- Which shops an inhabitant spends at is determined by:
  - Proximity (grid distance)
  - Budget available (`foodBudget`, `RetailBudget`, `LuxuryBudget`)
  - `Inhabitant` modifiers: `distanceTolerance`, `substitutionTendency`, `priceSensitivity`
- Inhabitants must cover rent and any debt obligations before any discretionary spending is
  possible. Spending is what drives commercial revenue, which drives company health, which
  drives employment — the loop must be stable for the city to sustain itself.
- Commercial demand scales with how much households are actually consuming. More housed,
  employed inhabitants = more spending = more demand for commercial zones.

### Rent
- Rent is calculated per building using the formula (inspired by Cities: Skylines II):
  `Rent = (LandValue + (ZoneType × BuildingLevel)) × LotSize × SpaceMultiplier`
- `LandValue` on a `GridCell` is a future field, initially defaulting to a configurable base.
- A household only triggers a "can't afford rent" condition when its income is genuinely
  insufficient — temporary savings dips do not immediately cause eviction.

### Fair Wage and Seniority
- Base market rate is `Occupation.averageSalary` with `standardDeviation`
- Each turn an employee remains at a company, they accumulate **seniority**, which applies a
  small incremental pay increase (configurable rate, e.g. +1% per N turns)
- This means long-tenured employees cost more but provide stability — companies must weigh
  the value of keeping experienced workers against the wage bill
- Players underpaying vs market rate leads to turnover (see Life Events)
- Adjusted for inflation over time

---

## Life Events

These are mid-simulation events that can happen to an `Inhabitant` or `Household` during the
turn loop. They are what make the city feel alive over time rather than frozen in its initial
generated state. Each event is driven by existing simulation data — no exotic mechanics needed.

### Layoffs
- Each turn, a company evaluates its financial health (revenue vs. expenses, reserves vs. wage bill)
- If the company is unprofitable and reserves are below a configurable threshold, it begins
  shedding employees to cut costs
- Layoff priority: least senior employees first, then the most overqualified for their role
  (they are the most expensive relative to what they contribute at that level)
- A laid-off inhabitant transitions to **Unemployed** and re-enters the job matching pool,
  using the same education-match and proximity logic as initial generation
- Companies recovering financially will begin hiring again, pulling from the unemployed pool

### Voluntary Job Changes
- Each turn, an employed inhabitant has a small chance of evaluating the job market
- If a better-matching job exists — higher pay *and* a closer education fit — the inhabitant
  will consider leaving
- Before leaving, the inhabitant first gives their **current company the opportunity to match
  the competing offer**. If the company's finances allow it, it matches the pay and the
  inhabitant stays. If not, the inhabitant resigns and takes the new job.
- Seniority bonuses are designed to make this less likely over time — long-tenured employees
  have a higher bar for what counts as "meaningfully better"
- This prevents the city's job assignments from calcifying after generation

### Homelessness and Personal Bankruptcy
- If a household cannot pay rent for a configurable number of consecutive turns, it loses its
  housing and becomes **Homeless**
- Homeless inhabitants carry heavy penalties to reflect the instability:
  - First in line to be laid off (companies treat them as a liability)
  - Ineligible for seniority bonuses until housed again
  - Small pay penalty vs. equivalent housed workers (reflects reduced reliability)
  - Locked out of certain spending categories (no luxury or retail budget)
- These penalties are deliberately punishing — the incentive is to get housed again quickly,
  not to persist as homeless
- Once a household secures new housing and pays the first turn of rent successfully, all
  homelessness penalties are lifted
- **Personal Bankruptcy:** a homeless inhabitant with zero savings and no realistic path to
  re-housing can declare personal bankruptcy. This allows them to dissolve their household
  and rejoin an existing family household (e.g. a parent, sibling) as a dependent. The
  receiving household absorbs the cost of an extra member. The bankrupt inhabitant restarts
  from scratch: no savings, no seniority, but penalties are cleared once housed again.

### Aging and Death
- Every `Inhabitant` has an `age` field (integer, in simulation years)
- Age increments each configurable number of turns
- At a configurable **retirement age**, the inhabitant stops working. Their job slot is freed
  for the company to fill. Retired inhabitants still consume resources and pay rent, funded
  from savings.
- Death is determined by a **per-age death probability table** — a preconfigured curve where
  the chance of dying is low in young adulthood and increases with age. No illness system
  is required; the probability curve handles the natural population shape.
- On death, the inhabitant is removed from their household. If the household becomes empty,
  the housing unit is freed and becomes available for new inhabitants.

---

## Developer Tooling

### Visualizer API (planned)
The current `GridVisualizer` renders a generated city to a PNG and is run manually from
the IDE. The goal is to replace/extend this with a **visualizer API consumable from a
browser**:

- An endpoint that runs city generation with **all tweakable values exposed as request
  parameters** (grid size, arterial spacing, lot sizes, zone ratios, vacancy, density,
  seed, ...) and returns a rendered image or renderable data.
- A simple browser page with sliders/inputs for each knob so tuning is interactive:
  change a value, see the new city immediately — no IDE, no recompile.
- Not just generation: **balance values** (economy rates, buff magnitudes, wages, rent
  factors, ...) should become visualizable the same way once those systems exist, so
  tuning the live game's balance uses the same workflow.
- This pairs with the DB-seeded template catalog and the future admin layer: the same
  browser tooling direction eventually serves admins tuning a running world.
- **Update:** the API + browser tooling now exist
  ([CityGenerationController](../src/main/java/be/ragga/raggabackend/simulation/grid/persistence/web/CityGenerationController.java)).
  `POST /cities/generate` + `GET /cities/{id}` (persist/read), `GET /cities/{id}/render`
  (stored city → PNG, `CityPngRenderer`), and the **interactive tuner at `/tuner.html`**:
  a live slider panel over every `GenerationConfig` knob, backed by
  `GET /cities/preview/render` (in-memory, non-persisting) and `GET /cities/preview/defaults`.
  This delivers the "change a value, see the city immediately — no IDE, no recompile" goal
  above. Still future: exposing **balance** values the same way once economy systems exist,
  and real 3D renders (blueprints now carry `floors` for those).

---

## Roadmap

| Step | Description | Status |
|------|-------------|--------|
| 1 | Finish Household/Inhabitant wiring — JPA mappings, `HouseholdRole` on `Inhabitant`, add grid coordinates to `Building` and `Household` | ✅ Done¹ |
| 2 | Grid generation + Zoning system | ✅ Done |
| 3 | Building placement on the grid (Simulated buildings get coordinates) | 🟡 In progress³ |
| 4 | Household generation + placement in residential zones | Not started |
| 5 | Inhabitant generation inside households (including `age` field and death probability table) | Not started |
| 6 | NPC job matching (proximity + education + salary weighted; overqualified fill-down allowed) | Not started |
| 7 | Simulate N turns without players — validate city stability | Not started |
| 8 | Income processing (salary → tax → debt → rent → shops → savings) | Not started |
| 9 | Shop/spending system (which inhabitants spend where; demand drives commercial health) | Not started |
| 10 | `Company` class — owns buildings, tracks financial stats, drives layoffs and hiring | Not started |
| 11 | Life events loop — layoffs, voluntary job changes (with counter-offer), aging, death, homelessness, personal bankruptcy | Not started |
| 12 | Player entry (hire workers, buy buildings, take loans) | Not started |

¹ `HouseholdRole` on `Inhabitant` is in place, and generation + persistence cover the grid
side. The literal per-entity polish (grid coordinates on `Building`/`Household`, accessors,
the `PlayerBuilding` entity bug, missing repositories) is tracked as outstanding in
**Known Gaps** below rather than blocking this checkbox.

² The *physical* half is done: generation places building footprints and persistence stores
them (`PlacedBuilding` + `Lot`, see PERSISTENCE.md).

³ The economic bridge has started with a residential-only slice: every generated residential
`PlacedBuilding` links to a real `SimulatedLowRise` **or** `SimulatedHighRise` instance —
blueprints now carry a `floors` axis (the vertical dimension, authored per template) and the
subtype splits at `SimulatedResidential.HIGH_RISE_MIN_FLOORS` (7). Each instance carries
`householdCapacity` (authored, scaled to floors) and a derived `bedroomsPerHousehold`
(clamped floor-area formula: `sizeX×sizeY×floors / capacity / 4 tiles-per-bedroom`, 1–8) —
the data Roadmap Step 4 will consume to generate and place households. Floors also give
future 3D/browser renders their box heights. `price`/`rent`/`desirability` remain null (no
land-value/rent-formula system yet) and commercial/industrial/farm placements stay
physical-only.

---

## Current Status — Where We Left Off

*(Keep this section current — it's the fastest way for a new session to get oriented.)*

**What's built and verified:**
- Full city generation pipeline (terrain → roads → blocks → lots → parks → zoning →
  buildings), deterministic per seed. Golden-seed regression test:
  `GenerationRegressionTest`. Standalone renderer: `GridVisualizer` → `grid.png`.
- Full persistence layer: `GenerationResult` → JPA entity graph → MySQL (or H2 in tests).
  DB-seeded building-template catalog. `POST /cities/generate` / `GET /cities/{id}`
  endpoints (Swagger at `/swagger-ui/index.html`). Round-trip test:
  `CityPersistenceRoundTripTest`.
- `ddl-auto=update` (schema persists across restarts) + Hibernate batch inserts tuned for
  the ~250k-row per-city grid.
- Step 3's economic bridge, residential slice: blueprints (`TemplateSpec`/`BuildingTemplate`)
  carry `floors` (vertical axis; feeds future 3D render heights) and an authored
  `householdCapacity`; every generated residential `PlacedBuilding` links to a
  `SimulatedLowRise`/`SimulatedHighRise` (split at 7+ floors) with derived
  `bedroomsPerHousehold`. The seeder backfills floors/capacity on catalog rows that predate
  those columns (`ddl-auto=update` leaves them at 0). Tests: `SimulatedResidentialTest`
  (formula goldens + guards), `BuildingTemplateSeederTest` (seed/backfill/no-clobber),
  extended `CityPersistenceRoundTripTest` (bridge + subtype consistency). See
  `GenerationResultMapper.mapBuilding`/`residentialFor` for the bridge point.
- Golden-seed constants re-derived (400×400, seed 42) after the multi-core generation
  defaults and enlarged catalog landed — `GenerationRegressionTest` is green again.
- `GET /cities/{id}/render` — stored city as a PNG straight from Swagger UI
  (`CityPngRenderer`, mirrors `GridVisualizer`'s legend; road classes repainted from
  `RoadSegment`s since the raster doesn't store them). Test: `CityPngRendererTest`.
- Interactive generation tuner at `/tuner.html` (`CityPreviewService` + `GenerationParams`
  + `/cities/preview/{defaults,render}`): live in-memory preview of every knob, no
  persistence. Tests: `GenerationParamsTest`, `CityPreviewServiceTest`.

**What's next (not yet decided as of this revision):** commercial/industrial bridging (mirror
of the residential slice, job-slot capacity instead of household capacity — Roadmap Step 6
territory), Roadmap Step 4 (actually generate/place `Household` entities into the new
`householdCapacity` slots), or closing out the remaining Known Gaps below. Check with the user
before assuming which.

**Known scaling cost, not yet addressed:** `City.simulatedBuildings` sits on the `Building`
JOINED-inheritance hierarchy (6 mapped levels). Loading it issues a query LEFT JOINing every
`Simulated*` subtype table regardless of which are actually populated — confirmed in the
round-trip test's SQL log. Worth watching as more subtypes gain real rows.

**How to verify the current state works:** `./mvnw test` runs the full suite including the
two regression/round-trip tests above; `./mvnw compile` catches JPA mapping errors early via
`RaggaBackendApplicationTests`' context-load check.

---

## Known Gaps / Tech Debt

Found during a Step 1 audit; not yet fixed. Listed here so they aren't lost across sessions.

- **No accessors in `household`, and only partial coverage in `building`.** `Household`,
  `Inhabitant`, `Occupation` still have private fields and zero getters/setters. `Building` and
  the full `Simulated*` residential branch (`SimulatedBuilding`/`SimulatedResidential`/
  `SimulatedLowRise`/`SimulatedHighRise`) gained accessors + constructors as part of the Step 3
  residential slice; `SimulatedCommercial`/`SimulatedIndustrial`/`Player*` remain untouched.
- **`Household` still has no grid coordinates.** `Building` doesn't need its own position field
  after all — it's reachable via its `PlacedBuilding` link, which already has `origin`. Every
  positioned entity (`GridCell`, `Lot`, `RoadSegment`, `PlacedBuilding`) reuses the
  `@Embeddable GridPosition` — the same pattern would apply to `Household` once it's tackled.
- **`PlayerBuilding` is not a JPA entity.** `Building` declares
  `@Inheritance(strategy = InheritanceType.JOINED)` and `SimulatedBuilding` correctly carries
  `@Entity`, but `PlayerBuilding` has no `@Entity` annotation. Its leaf subtypes
  (`PlayerResidential`, `PlayerCommercial`, `PlayerIndustrial`) are currently unpersistable.
  Will bite at Step 12 (building takeover) if left as-is.
- **No repositories for `Household`, `Inhabitant`, or `Occupation`** — `BuildingRepository` was
  added alongside the Step 3 residential slice; the household package still has none.
- **`building/modifier` (`PublicBuilding`, `BusStop`, `MetroStop`, `TrainStop`) isn't wired
  into the `Building` hierarchy at all** — no fields, no JPA, doesn't extend `Building`.
  Not urgent (not on the current roadmap step), but will need attention once public-building
  gameplay (transit bonuses, etc.) is designed.

---

## Open Questions / Future Decisions
- Exact tax model (flat, bracketed, municipal?)
- Loan system mechanics for players (interest rate, repayment terms, default consequences)
- How building inventory/stock works for shops (needed before step 9 is meaningful)
- Configurable values that need tuning: retirement age, death probability curve, seniority rate,
  layoff threshold, homelessness penalty duration, turns-per-simulation-year
- Which family relationships are valid targets for personal bankruptcy rejoining (parents only?
  any household with a blood relation? configurable?)
- What happens to a building if its company goes bankrupt before a player buys it
- `LandValue` calculation details — what factors raise or lower it (proximity to parks,
  pollution, services, zone density) — needed before rent formula is fully meaningful
