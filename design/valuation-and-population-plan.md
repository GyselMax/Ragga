# Plan — Realistic Property Valuation & Population Placement (European basis)

> **Status:** draft plan for review, not yet implemented. Sits ahead of / inside Roadmap
> Step 4 (household generation & placement) and borrows from Steps 8–10 (rent, land value).
> Goal: give every building and the ground under it a *realistic, location-driven value*, then
> use that value + a realistic household income distribution to place people who could
> plausibly **afford to rent or own** each home — reproducing how real European cities sort.

---

## 1. Why & what

Right now buildings are physical shells: `Building.price`, `Building.rent`, `Building.desirability`
exist but are **null**, and residential buildings carry only `householdCapacity` / `bedroomsPerHousehold`
(the Step 3 slice). To populate the city believably we need three linked things:

1. **Land value** — the ground is worth wildly different amounts by *location* (central vs rural).
2. **Property value & rent** — land value + the building on it → a sale price and a monthly rent.
3. **Affordability matching** — generate households with a realistic income spread, then place each
   into a home it can actually afford, biased so the well-off end up in the pricey central buildings
   (the European sorting pattern) — with a knob to flip it.

This is the economic backbone that makes Step 4's "put households in residential zones" *realistic*
rather than random.

---

## 2. Research grounding (European base example)

Full findings + sources are in this session's research; the load-bearing facts:

- **Value = land value + structure value.** Structure (build) cost is roughly *flat* everywhere
  (~€1,500–2,500/m²); land is what varies. Land as a share of total value: **~70% city centre,
  ~50% mid, ~35% outer, ~15% rural** ([Davis–Palumbo](http://morris.marginalq.com/papers/2007-02-Davis-Palumbo.paper.pdf)).
- **Price gradient** (residential €/m²): prime centre is **3–4× the outer ring**, and centre-to-rural
  spans **~10–20×**. Base example ≈ Paris/Munich core scaling down to French/Spanish rural
  ([Sotheby's Paris](https://www.parisouest-sothebysrealty.com/en/articles-presse/details/2193/),
  [Investropa Munich](https://investropa.com/blogs/news/average-price-sqm-apartment-munich)).
- **Rent = price × gross yield**, and yield is **inverse to price**: ~**3.5% in expensive centres →
  ~5–6% in cheap/rural markets** ([Global Property Guide](https://www.globalpropertyguide.com/europe/rent-yields)).
- **Amenities move land value**: park within 100 m **+~3%**, water proximity larger, transit within
  ~500 m positive (but immediate rail adjacency a noise disamenity); industry/pollution negative.
- **By zone**: prime **retail ≫ office > residential > industrial** per m²; retail value spikes *only*
  downtown, **industrial is cheap and nearly flat** and prefers peripheral land.
- **Affordability**: rent should be **≤30%** of income; Eurostat flags **>40%** as "housing cost
  overburden" (EU avg ~8%, Greece ~29%). Buying needs **savings ≥ down-payment** (LTV ~80% → 20%
  down) **and** mortgage payment **≤~35%** of income ([Eurostat](https://ec.europa.eu/eurostat/statistics-explained/index.php/Glossary:Housing_cost_overburden_rate), [ECB LTV/DSTI](https://www.ecb.europa.eu/press/financial-stability-publications/macroprudential-bulletin/focus/2023/html/ecb.mpbu202307_focus2.en.html)).
- **Income distribution**: right-skewed — **lognormal** bulk + **Pareto** top tail; EU median
  equivalised income ≈ €21k, quintile ratio S80/S20 ≈ 4.7.
- **THE key modelling point — European sorting is inverted vs the US.** Brueckner, Thisse & Zenou
  (1999): when the centre has a strong **amenity advantage** (historic core, architecture,
  riverfront), the **rich outbid for the centre and the poor go to the periphery** — opposite the
  American "rich suburbs" gradient ([EER 1999](https://ideas.repec.org/a/eee/eecrev/v43y1999i1p91-107.html)).
  We expose a `centralAmenityStrength` knob so the city can be European (default) or flipped.

### Calibration table (starting point — synthesized from the cited ranges, all tunable)

| Location | Res €/m² | Res rent €/m²/mo | Comm €/m² | Ind €/m² | Land share |
|---|---|---|---|---|---|
| Centre | 12,000 | 28 | 20,000 | 2,500 | ~70% |
| Mid | 6,500 | 20 | 8,000 | 2,000 | ~50% |
| Outer | 3,500 | 14 | 3,500 | 1,500 | ~35% |
| Rural | 1,200 | 7 | 1,200 | 900 | ~15% |

Structure build cost ≈ flat €1,800/m² everywhere; yield ~3.5% centre → ~6% rural.

---

## 3. What already exists to build on

- **`DensityField.at(x, y) → [edgeDensity, 1]`** — the multi-core centrality surface already threaded
  through generation (downtown ≈ 1, countryside ≈ edgeDensity, with the river lobe). **This is our
  land-value gradient**, ready-made and deterministic per seed. It is *not* currently in
  `GenerationResult`, so exposing it is the one generation-side change needed (below).
- **`Building.price` / `.rent` / `.desirability` (BigDecimal)** — currently null; this plan fills them.
  `desirability` becomes the normalized location score (0–1) that drives both value and sorting.
- **`SimulatedResidential.householdCapacity` / `bedroomsPerHousehold`** and **`floors`** on every
  building — the structure inputs for floor-area valuation.
- **Income fields already modelled**: `Household.totalGrossIncome`/`sharedSavings`,
  `Inhabitant.grossIncome`/`personalSavings`, `Occupation.averageSalary`/`standardDeviation`.
- **Amenity signals already on the map**: `TileType.PARK`, `WATER`, `PUBLIC`, road classes, and
  `INDUSTRIAL` zoning — all readable from the persisted grid for amenity adjustments.

---

## 4. The valuation model

### 4.1 Tile ↔ m² scale (a decision — see §8)
Generation tiles are abstract; valuation is in €/m². Introduce **`SQUARE_METERS_PER_TILE`** (proposed
~15 m²: a 2×2 house = 60 m²/floor, feels right). Floor area of a building:
`floorArea_m² = sizeX × sizeY × floors × SQUARE_METERS_PER_TILE`.

### 4.2 Location score → €/m²
For each building, sample centrality `d = DensityField.at(centre)` ∈ [edgeDensity, 1], then apply
amenity adjustments from the surrounding tiles (park/water bonus, industry/heavy-road penalty) to get
a **`desirability` ∈ [0,1]**. Map desirability to a per-m² land price by interpolating the calibration
table (centre↔rural), per zone. `pricePerSqm = structureCostPerSqm + landPricePerSqm(desirability, zone)`
where land's share rises with desirability (70% central … 15% rural).

### 4.3 Sale price & rent
- `salePrice = floorArea_m² × pricePerSqm` (per household unit: divide by `householdCapacity`).
- `yield = lerp(0.06 → 0.035, desirability)` (cheap places yield more).
- `monthlyRent = salePrice × yield / 12`.

This **supersedes the old sketch** `Rent = (LandValue + ZoneType×Level) × LotSize × SpaceMultiplier`
from design-1.0 — the floor-area × €/m² × yield form is directly calibratable against real figures and
was the sketch's intent anyway. (Flagging the divergence honestly.)

### 4.4 Zones
Residential uses the table above. Commercial multiplies the central premium hard (retail spikes
downtown) and decays fast outward; industrial is low and nearly flat and effectively *prefers* cheap
peripheral land. Commercial/industrial valuation can land in a later slice — residential first, since
that's what population needs.

---

## 5. Population placement (the payoff)

### 5.1 Generate a realistic income distribution
Draw household gross income as **`lognormal(μ, σ)`** (median ≈ configurable regional median, σ ≈ 0.6)
with a **Pareto tail above P90** (α ≈ 2.5), calibrated to hit **S80/S20 ≈ 4.5–5**. Derive savings as an
increasing function of income (and later, age): `savings ≈ income × k` with k rising for higher
brackets — the down-payment constraint is what gates ownership.

### 5.2 Own vs rent (per household)
```
canBuy = savings ≥ (1 − LTV) × unitSalePrice           // LTV ≈ 0.80 → 20% down
         AND mortgagePayment(unitSalePrice) ≤ 0.35 × monthlyIncome
tenure = canBuy ? OWN : RENT
```
`mortgagePayment` is the standard amortization `L·r/(1−(1+r)⁻ⁿ)`, n = 360, r = monthly rate (a config
knob). Renters must satisfy `monthlyRent ≤ 0.30 × monthlyIncome`; assignments between 30–40% are marked
**overburdened** (so we can check we reproduce ~8% EU stress).

### 5.3 Assignment that reproduces European sorting
1. Compute every residential unit's `desirability`, `salePrice`, `rent`, and capacity.
2. Sort households by income (desc) and units by desirability (desc).
3. Walk richest→poorest, placing each household into the **most desirable unit it can afford**
   (rent-≤-30% or buy) that still has capacity. With `centralAmenityStrength` high (European default),
   this puts the rich centrally and the poor peripherally; set it low to bias the rich outward (US
   pattern). Unhoused households (can't afford the cheapest available) become the seed of the
   homelessness mechanic (Roadmap Step 11) or spill to vacant rural stock.

This is deliberately a *matching* pass, not a market simulation — good enough to populate a plausible
starting city; the turn-based economy (Steps 8–11) can let it drift afterwards.

---

## 6. Entities & services

**New/changed entities**
- `Building.price/rent/desirability` — populated (no schema change; already present).
- `Household` — add `tenure` (`OWN`/`RENT` enum), a `@ManyToOne` residence link to its building, and
  (closing a Known Gap) accessors + a repository. This is the household-side Step 1 plumbing, finally
  needed for real.
- Optional `Lot.landValue` (BigDecimal) if we want per-parcel land value queryable; otherwise it's
  implicit in the building's price. Recommend *derive, don't store separately* to start.

**New services**
- `ValuationService` — building → (desirability, salePrice, rent), using `DensityField` + amenity scan
  + the calibration config. Pure, deterministic.
- `PopulationService` (a.k.a. Step 4) — generates the income-distributed households and runs the
  affordability assignment.
- Both are post-generation economic steps; generation stays economics-free.

**One generation-side seam:** expose the built `DensityField` (or a per-lot desirability sample) on
`GenerationResult` so `ValuationService` can read centrality per location without recomputing the
upgraded field. Small, additive.

---

## 7. Config knobs (new `ragga.economy.*` block)
`squareMetersPerTile`, `structureCostPerSqm`, the 4×zone €/m² calibration anchors, `yieldCentre`/
`yieldRural`, `centralAmenityStrength`, park/water/industry amenity weights, income `medianIncome`/
`sigma`/`paretoAlpha`, savings-rate curve, `mortgageLtv`/`mortgageRate`/`dstiCap` (0.35), rent-burden
cap (0.30) — all exposed so the future tuner/admin UI can balance the economy the same way it now
tunes generation.

---

## 8. Open decisions (my recommendations first)

1. **Currency scale** — *Realistic € via the existing BigDecimal fields* (recommended: numbers are
   grounded and BigDecimal handles the magnitude) vs a scaled "game credit". → **Realistic €.**
2. **`SQUARE_METERS_PER_TILE`** — proposed **15**; affects every absolute figure. Easy to retune.
3. **Land value storage** — *derive per building from `DensityField`* (recommended, no 250k-row cost)
   vs a stored `Lot.landValue` vs per-`GridCell` (design-1.0's old idea — too heavy). → **Derive.**
4. **Default sorting** — *European (rich centre), `centralAmenityStrength` high* (recommended, matches
   your "European base" ask) vs US default. → **European.**
5. **Scope of the first slice** — *residential valuation + population only* (recommended: it's the
   stated goal — put affordable people in homes) with commercial/industrial valuation and the
   turn-based market deferred. → **Residential first.**
6. **Relationship to Roadmap Step 4** — this plan effectively *is* Step 4 done properly; recommend
   merging them (valuation is the prerequisite that makes placement realistic).

---

## 9. Build order (once approved)
1. Household-side plumbing (accessors, `HouseholdRepository`, `tenure`, residence link) — the Step 1 gap.
2. Expose `DensityField` on `GenerationResult`.
3. `ragga.economy.*` config + a `EconomyConfig` bean.
4. `ValuationService` (+ unit tests: gradient monotonic centre→rural, yield inverse, amenity bumps).
5. Populate `Building.price/rent/desirability` at the persistence-mapping stage (extends the residential
   bridge already in `GenerationResultMapper`).
6. `PopulationService`: income distribution + savings + own/rent + sorted affordability assignment
   (tests: income S80/S20 in range; ~8% overburden; rich-centre correlation; no one housed above 40%).
7. Visualize it: a new render mode / overlay (land-value heatmap, or colour households by tenure/income)
   — reuses the PNG renderer + tuner you now have.
8. Update `design-2.0.md` (Steps 3/4/8 status, Known Gaps) + a new `ECONOMY.md`.

## 10. Verification
- **Value gradient**: assert `price/m²` decreases monotonically from centre to rim on a fixed seed.
- **Affordability realism**: generated cohort hits S80/S20 ≈ 4.5–5 and ≈5–15% overburden — matches
  Eurostat.
- **Sorting**: positive correlation between household income and building desirability under European
  default; correlation weakens/flips as `centralAmenityStrength` drops.
- **Eyeball**: a land-value heatmap render should look like a real city — hot core, warm along the
  river/parks, cold rim.
