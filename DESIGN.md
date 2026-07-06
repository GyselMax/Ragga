# Ragga Backend — Design Document

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

---

## Architecture Overview

### Package Structure
```
be.ragga.raggabackend
├── simulation
│   ├── City.java
│   ├── building
│   │   ├── Building.java                  (abstract base)
│   │   ├── simulated
│   │   │   ├── SimulatedBuilding.java     (abstract, NPC-owned)
│   │   │   ├── SimulatedResidential.java  (abstract)
│   │   │   ├── SimulatedLowRise.java
│   │   │   ├── SimulatedHighRise.java
│   │   │   ├── SimulatedCommercial.java
│   │   │   └── SimulatedIndustrial.java
│   │   ├── player
│   │   │   ├── PlayerBuilding.java        (abstract, player-owned)
│   │   │   ├── PlayerResidential.java
│   │   │   ├── PlayerCommercial.java
│   │   │   └── PlayerIndustrial.java
│   │   └── modifier
│   │       ├── PublicBuilding.java        (abstract)
│   │       ├── BusStop.java
│   │       ├── MetroStop.java
│   │       └── TrainStop.java
│   └── household
│       ├── Household.java
│       ├── Inhabitant.java
│       ├── Occupation.java
│       ├── EducationLevel.java
│       └── HouseholdRole.java
```

### Key Abstraction Note
`PlayerBuilding` and `SimulatedBuilding` both extend `Building` but will eventually need
a shared interface or abstraction point to support the **building conversion mechanic**
(see below). This is not urgent but should be kept in mind to avoid painful refactoring later.

---

## Core Systems

### 1. Grid and Zoning
- The city is built on a **grid**. Coordinates are relevant from day one.
- `Building` and `Household` both need grid coordinates immediately.
- Grid is generated first, then filled with **zones** (residential, commercial, industrial, etc.)
- Buildings are then placed within appropriate zones.

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

---

## Roadmap

| Step | Description |
|------|-------------|
| 1 | Finish Household/Inhabitant wiring — JPA mappings, `HouseholdRole` on `Inhabitant`, add grid coordinates to `Building` and `Household` |
| 2 | Grid generation + Zoning system |
| 3 | Building placement on the grid (Simulated buildings get coordinates) |
| 4 | Household generation + placement in residential zones |
| 5 | Inhabitant generation inside households (including `age` field and death probability table) |
| 6 | NPC job matching (proximity + education + salary weighted; overqualified fill-down allowed) |
| 7 | Simulate N turns without players — validate city stability |
| 8 | Income processing (salary → tax → debt → rent → shops → savings) |
| 9 | Shop/spending system (which inhabitants spend where; demand drives commercial health) |
| 10 | `Company` class — owns buildings, tracks financial stats, drives layoffs and hiring |
| 11 | Life events loop — layoffs, voluntary job changes (with counter-offer), aging, death, homelessness, personal bankruptcy |
| 12 | Player entry (hire workers, buy buildings, take loans) |

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
