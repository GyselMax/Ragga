package be.ragga.raggabackend.simulation.grid.generation;

/**
 * All tuning knobs for city generation, as a plain record so the pipeline
 * can run without Spring or a database (e.g. from GridVisualizer.main()).
 * Phase B maps ragga.grid.* properties onto this.
 *
 * @param width                Map width in tiles. Generation cost grows roughly linearly
 *                             with the tile count; the visualizer renders 10 pixels per
 *                             tile, so very large maps produce very large PNGs.
 * @param height               Map height in tiles.
 * @param arterialSpacing      Target distance in tiles between neighboring arterial roads
 *                             (the wandering main streets that cross the whole map). The
 *                             first arterial sits at half this distance from the border.
 *                             Bigger = fewer main roads and larger districts between them,
 *                             which also means bigger blocks and more room for parks;
 *                             smaller = a tight downtown-style mesh everywhere. Sensible
 *                             range roughly 12-30.
 * @param arterialJitter       Maximum random offset in tiles applied to each arterial's
 *                             base position and to every block-split line. This controls
 *                             the SPACING rhythm only, not how bent a road is (that is
 *                             arterialMaxDrift): 0 = perfectly even spacing, higher = more
 *                             irregular. Keep it below about a quarter of arterialSpacing
 *                             or roads start crowding.
 * @param arterialMaxDrift     How far, in tiles, a wandering arterial may stray sideways
 *                             from its base line as it crosses the map. This is what bends
 *                             the roads: 0 = perfectly straight arterials, 1-2 = gentle
 *                             organic bends, 3+ = visibly meandering streets (produces
 *                             more odd-shaped blocks and roadside park slivers).
 * @param minBlockSizeForSplit A block only qualifies for a collector/local road cut when
 *                             its largest inscribed rectangle's longer side is at least
 *                             this many tiles. Lower = even small blocks keep getting cut
 *                             (tiny blocks everywhere); higher = larger blocks survive,
 *                             giving longer lot strips and more interior parks. There is
 *                             an implicit floor of 2*(minLotWidth+1)+1: blocks below it
 *                             are never split regardless of this setting, because both
 *                             halves must be able to hold a strip of lots.
 * @param maxLocalRoadDepth    How many rounds of block splitting run. Round 1 cuts
 *                             COLLECTOR roads, every later round cuts LOCAL roads. Each
 *                             round can halve every qualifying block, so depth 2 means a
 *                             district can be quartered. More depth = finer street mesh.
 * @param blockSplitChance     Base probability that a qualifying block actually gets cut.
 *                             Density-scaled at runtime: guaranteed (1.0) in the city core,
 *                             sliding down to HALF this value at the map edge. Lower base =
 *                             more unsplit blocks = more big parks, mostly near the edges.
 * @param minBlockArea         Blocks smaller than this many tiles are too small to ever
 *                             hold a lot; they become roadside park strips instead (bent
 *                             arterials produce a lot of these slivers).
 * @param stripDepth           How deep, in tiles, the lot strips are cut inward from each
 *                             road edge - effectively the depth of every lot. Also decides
 *                             where interior parks appear: a block deeper than twice this
 *                             value in both axes keeps a road-less interior, which becomes
 *                             a park.
 * @param minLotWidth          Narrowest lot the subdivider may cut along a road. The last
 *                             lot in a strip absorbs the remainder when it would fall
 *                             below this, so some lots come out wider than maxLotWidth.
 * @param maxLotWidth          Widest lot the subdivider normally cuts. Wider = occasional
 *                             large parcels that fit the biggest templates.
 * @param industrialMinLotArea Only lots with at least this area (width x depth, in tiles)
 *                             compete for the industrial quota - factories need room.
 * @param industrialRatio      Fraction of all lots the industrial quota targets. These are
 *                             picked from the largest eligible lots and locked so the
 *                             smoothing pass can't flip them. The weighted random rolls add
 *                             a little industry on top, so the real share drifts slightly
 *                             above this.
 * @param publicRatio          Fraction of parcels converted into public-use sites (school,
 *                             station, library, ...). Only well-connected parcels qualify:
 *                             arterial frontage or a corner lot with two road sides.
 * @param coreVacantRatio      Fraction of zoned lots left vacant (no building) in the dense
 *                             core - how much the downtown zoning is actually built out.
 *                             0 = every core lot gets a building (packed, dense); higher =
 *                             more empty lots even downtown (sparser). In [0, 1].
 * @param edgeVacantRatio      Same, at the city edge. The per-lot vacancy chance
 *                             interpolates from edgeVacantRatio at the rim to coreVacantRatio
 *                             in the core by local density, so this pair independently sets
 *                             how dense the core and the outskirts feel. Vacant lots are the
 *                             cheap parcels players buy and build on later. In [0, 1].
 * @param smoothingEnabled     Runs the majority-neighbor smoothing pass after zoning: a lot
 *                             flips to a different zone only when 3+ neighboring lots share
 *                             it AND it outnumbers the lot's own zone among the neighbors.
 *                             Clusters zones into districts; off = noisier, salt-and-pepper
 *                             zoning.
 * @param coreRadiusFraction   Size of the full-density downtown, as a fraction of the
 *                             distance from the map center to the nearest edge. 0.35 means
 *                             the inner 35% of that distance is max density; beyond it,
 *                             density fades linearly to edgeDensity at the border.
 * @param edgeDensity          Density floor at the map border, between 0 and 1. Lower =
 *                             starker downtown-vs-outskirts contrast: more vacancy, fewer
 *                             road splits, bigger parks and smaller buildings at the rim.
 * @param coreCenterJitter     How far the city core may sit away from the exact map
 *                             center, as a fraction of each map dimension, randomized per
 *                             seed. 0 = always dead center (mirror-symmetric city), 0.2 =
 *                             the core lands anywhere in the middle 40% of the map. Must
 *                             stay below 0.5 or the core could leave the map.
 * @param riverEnabled         Carves a meandering river across the map before any road
 *                             exists. Its base line runs through the city core (offset by
 *                             up to half the core radius); arterials crossing it become
 *                             bridges, and a 1-tile park bank traces both shores. Maps too
 *                             narrow for the river plus banks silently stay dry.
 * @param riverWidth           Nominal river width in tiles; the carved width breathes
 *                             about ±2 tiles around it per stretch so the banks aren't
 *                             ruler-straight. 4-8 reads well at city scale.
 * @param riverMaxDrift        How far, in tiles, the river may meander sideways from its
 *                             base line - the river's equivalent of arterialMaxDrift but
 *                             meant to be much larger (20-50): big lazy curves that break
 *                             the grid instead of a road-like wiggle.
 * @param maxCityRadius        Caps the main city's size in ABSOLUTE tiles, independent of
 *                             map size. 0 = uncapped (legacy behavior: the city scales
 *                             with the map). When set, the density falloff normalizes
 *                             against min(center-to-nearest-edge, maxCityRadius), so a
 *                             5000x5000 map holds a constant-size city in a sea of
 *                             countryside instead of a proportionally gigantic one. With
 *                             default falloff knobs the urban land (density above the
 *                             farmland threshold) ends at roughly 0.96 x this radius.
 *                             Maps smaller than ~2x the cap are unaffected.
 * @param forestsEnabled       Carves noise-shaped woodland where density is low - the map
 *                             corners and the rural rim, never downtown. Roads route
 *                             around it (arterials cut through); no lots form inside.
 * @param forestDensity        How much woodland the rural land carries, in [0, 1]: higher =
 *                             more forest (it lowers the noise cutoff for a FOREST tile).
 *                             Only bites when forestsEnabled is true, and never adds forest
 *                             downtown - the density falloff still keeps the core clear.
 *                             0 = almost none, 1 = the countryside is nearly all trees. The
 *                             default (0.42) reproduces the previous fixed cutoff.
 * @param farmlandDensityThreshold Below this local density the map turns rural: blocks
 *                             stop being split by collector/local roads, lot strips and
 *                             widths triple (huge field parcels), and every lot is zoned
 *                             FARMLAND. With the default falloff this claims the map rim,
 *                             reaching deepest on the side facing away from the core.
 *                             0 = no farmland. Must stay clear of the core: < 1.
 * @param farmlandSizeMultiplier Scales how big farm parcels get, on top of the rural belt's
 *                             built-in enlargement (8x strip depth, 3x lot width vs town
 *                             lots). 1.0 = that default; higher makes the fields deeper and
 *                             raises the widest-field ceiling, so a big value yields some
 *                             really large farms (widths still vary, so smaller fields
 *                             remain too). Self-limited by the rural block size. In (0, 10].
 * @param cityCount            Number of city cores. 1 = a single main city. Above 1 adds
 *                             that many satellite cities, rejection-placed so their urban
 *                             extents never touch each other or the main city (a farm gap
 *                             always separates them). Requires maxCityRadius > 0 (a
 *                             map-filling main city leaves no room for satellites). The
 *                             countryside road graph is what keeps them connected.
 * @param satelliteMinScale    Smallest satellite radius as a fraction of the main city
 *                             radius (satellites are always smaller towns). 0 < min <= max.
 * @param satelliteMaxScale    Largest satellite radius as a fraction of the main city
 *                             radius. Must be < 1 so a satellite never rivals downtown.
 * @param satellitePeakDensity Peak density at a satellite center, an absolute value in
 *                             (farmlandDensityThreshold, 1]. Below 1 the satellite tops out
 *                             thinner than the main core: a proper town, not a second
 *                             downtown.
 * @param hamletCount          Target number of hamlets: tiny villages scattered in the
 *                             countryside as small density bumps, so the normal pipeline
 *                             builds each one automatically. 0 = none. Only as many as fit
 *                             the rural land are placed (rejection sampling), so uncapped
 *                             maps whose city fills the map get few or none.
 * @param hamletRadius         Hamlet density-bump radius in tiles (village ~2x this
 *                             across). Small: think 12-24.
 * @param hamletPeakDensity    Peak density at a hamlet center, in (farmlandDensityThreshold,
 *                             satellitePeakDensity). A village grain, denser than farmland
 *                             but far below any city.
 * @param minSettlementSpacing Minimum center-to-center distance between hamlets, in tiles,
 *                             so villages don't clump. >= 2x hamletRadius.
 * @param settlementConnectionCount How many nearest neighbors each settlement (city, hamlet)
 *                             links to by road. 1-6; a union pass ALREADY guarantees the
 *                             whole network is one connected component, so this only controls
 *                             how dense the extra countryside links are, not whether things
 *                             connect. Clamped per settlement to the number of other
 *                             settlements, so on a map with few of them a high value does
 *                             nothing.
 * @param cityConnectionCount  Extra direct road links between the actual cities (main +
 *                             satellites only, never hamlets), on top of the general
 *                             settlement graph above. Each city links to its N nearest OTHER
 *                             cities, so a higher value meshes the cities together with more
 *                             organic cross-country roads instead of a bare spanning tree.
 *                             0 = cities connect only through the general graph. Clamped to
 *                             the number of other cities, so it does nothing with one city.
 * @param edgeExitCount        Number of roads that leave the map at the border toward the
 *                             wider world, each linked to its nearest settlement. Replaces
 *                             the old random rural-highway roll. 0 = none.
 */
public record GenerationConfig(
        int width,
        int height,
        int arterialSpacing,
        int arterialJitter,
        int arterialMaxDrift,
        int minBlockSizeForSplit,
        int maxLocalRoadDepth,
        double blockSplitChance,
        int minBlockArea,
        int stripDepth,
        int minLotWidth,
        int maxLotWidth,
        int industrialMinLotArea,
        double industrialRatio,
        double publicRatio,
        double coreVacantRatio,
        double edgeVacantRatio,
        boolean smoothingEnabled,
        double coreRadiusFraction,
        double edgeDensity,
        double coreCenterJitter,
        boolean riverEnabled,
        int riverWidth,
        int riverMaxDrift,
        int maxCityRadius,
        boolean forestsEnabled,
        double forestDensity,
        double farmlandDensityThreshold,
        double farmlandSizeMultiplier,
        int cityCount,
        double satelliteMinScale,
        double satelliteMaxScale,
        double satellitePeakDensity,
        int hamletCount,
        int hamletRadius,
        double hamletPeakDensity,
        int minSettlementSpacing,
        int settlementConnectionCount,
        int cityConnectionCount,
        int edgeExitCount
) {

    // Fails fast on configs that would otherwise crash mid-generation
    // (nextInt on an empty range, NaN density normalization) or silently
    // misbehave - a typo in a knob should be an error message, not a weird
    // city.
    public GenerationConfig {
        if (width < 10 || height < 10) {
            throw new IllegalArgumentException("map must be at least 10x10 tiles");
        }
        if (minLotWidth < 1 || minLotWidth > maxLotWidth) {
            throw new IllegalArgumentException("need 1 <= minLotWidth <= maxLotWidth");
        }
        if (stripDepth < 1 || arterialSpacing < 4 || arterialJitter < 0 || arterialMaxDrift < 0) {
            throw new IllegalArgumentException("stripDepth >= 1, arterialSpacing >= 4, jitter/drift >= 0 required");
        }
        if (edgeDensity < 0 || edgeDensity >= 1 || coreRadiusFraction < 0 || coreRadiusFraction >= 1) {
            throw new IllegalArgumentException("edgeDensity and coreRadiusFraction must be in [0, 1)");
        }
        if (coreCenterJitter < 0 || coreCenterJitter >= 0.5) {
            throw new IllegalArgumentException("coreCenterJitter must be in [0, 0.5)");
        }
        if (riverEnabled && (riverWidth < 2 || riverMaxDrift < 0)) {
            throw new IllegalArgumentException("riverWidth >= 2 and riverMaxDrift >= 0 required when the river is enabled");
        }
        if (farmlandDensityThreshold < 0 || farmlandDensityThreshold >= 1) {
            throw new IllegalArgumentException("farmlandDensityThreshold must be in [0, 1)");
        }
        if (forestDensity < 0 || forestDensity > 1) {
            throw new IllegalArgumentException("forestDensity must be in [0, 1]");
        }
        if (farmlandSizeMultiplier <= 0 || farmlandSizeMultiplier > 10) {
            throw new IllegalArgumentException("farmlandSizeMultiplier must be in (0, 10]");
        }
        if (maxCityRadius != 0 && maxCityRadius < 2 * arterialSpacing) {
            throw new IllegalArgumentException("maxCityRadius must be 0 (uncapped) or at least 2x arterialSpacing");
        }
        if (maxCityRadius > 0 && edgeDensity >= farmlandDensityThreshold) {
            // Without this, a capped city sits in an endless plain that never
            // turns FARMLAND but never urbanizes either.
            throw new IllegalArgumentException("a capped city needs edgeDensity < farmlandDensityThreshold so countryside exists");
        }
        if (blockSplitChance < 0 || blockSplitChance > 1
                || industrialRatio < 0 || publicRatio < 0) {
            throw new IllegalArgumentException("chances/ratios may not be negative (blockSplitChance at most 1)");
        }
        if (coreVacantRatio < 0 || coreVacantRatio > 1 || edgeVacantRatio < 0 || edgeVacantRatio > 1) {
            throw new IllegalArgumentException("coreVacantRatio and edgeVacantRatio must be in [0, 1]");
        }
        if (cityCount < 1) {
            throw new IllegalArgumentException("cityCount must be at least 1");
        }
        if (cityCount > 1 && maxCityRadius <= 0) {
            throw new IllegalArgumentException("cityCount > 1 needs maxCityRadius > 0 so satellites have room");
        }
        if (satelliteMinScale <= 0 || satelliteMinScale > satelliteMaxScale || satelliteMaxScale >= 1) {
            throw new IllegalArgumentException("need 0 < satelliteMinScale <= satelliteMaxScale < 1");
        }
        if (satellitePeakDensity <= farmlandDensityThreshold || satellitePeakDensity > 1) {
            throw new IllegalArgumentException("satellitePeakDensity must be in (farmlandDensityThreshold, 1]");
        }
        if (hamletCount < 0 || edgeExitCount < 0) {
            throw new IllegalArgumentException("hamletCount and edgeExitCount may not be negative");
        }
        if (hamletCount > 0) {
            if (hamletRadius < 8) {
                throw new IllegalArgumentException("hamletRadius must be at least 8");
            }
            if (hamletPeakDensity <= farmlandDensityThreshold || hamletPeakDensity >= satellitePeakDensity) {
                throw new IllegalArgumentException("hamletPeakDensity must be in (farmlandDensityThreshold, satellitePeakDensity)");
            }
            if (minSettlementSpacing < 2 * hamletRadius) {
                throw new IllegalArgumentException("minSettlementSpacing must be at least 2x hamletRadius");
            }
        }
        if (settlementConnectionCount < 1 || settlementConnectionCount > 6) {
            throw new IllegalArgumentException("settlementConnectionCount must be in [1, 6]");
        }
        if (cityConnectionCount < 0) {
            throw new IllegalArgumentException("cityConnectionCount may not be negative");
        }
    }

    /**
     * The same config with rural lot dimensions - what the subdivider uses
     * inside the farm belt: fields reach far deeper from the road than town
     * lots (x8 strip depth) and are several times wider, so the few highways
     * leaving the city carry broad bands of fields with open meadow beyond.
     */
    public GenerationConfig ruralVariant() {
        return new GenerationConfig(width, height,
                arterialSpacing, arterialJitter, arterialMaxDrift,
                minBlockSizeForSplit, maxLocalRoadDepth, blockSplitChance,
                minBlockArea,
                // farmlandSizeMultiplier scales the belt's already-enlarged depth
                // and widest-field ceiling; min width stays so small fields remain.
                Math.max(1, (int) (stripDepth * 8 * farmlandSizeMultiplier)),
                minLotWidth * 3,
                Math.max(minLotWidth * 3, (int) (maxLotWidth * 3 * farmlandSizeMultiplier)),
                industrialMinLotArea, industrialRatio,
                publicRatio, coreVacantRatio, edgeVacantRatio, smoothingEnabled,
                coreRadiusFraction, edgeDensity, coreCenterJitter,
                riverEnabled, riverWidth, riverMaxDrift,
                maxCityRadius, forestsEnabled, forestDensity, farmlandDensityThreshold, farmlandSizeMultiplier,
                cityCount, satelliteMinScale, satelliteMaxScale, satellitePeakDensity,
                hamletCount, hamletRadius, hamletPeakDensity,
                minSettlementSpacing, settlementConnectionCount, cityConnectionCount, edgeExitCount);
    }

    /**
     * The same config with the lot width range narrowed toward the minimum -
     * what the subdivider uses in the dense core, so downtown reads as fine
     * grain: many small parcels instead of the suburban mix.
     */
    public GenerationConfig coreVariant() {
        int narrowedMax = Math.max(minLotWidth, minLotWidth + (maxLotWidth - minLotWidth) / 2);
        return new GenerationConfig(width, height,
                arterialSpacing, arterialJitter, arterialMaxDrift,
                minBlockSizeForSplit, maxLocalRoadDepth, blockSplitChance,
                minBlockArea,
                stripDepth, minLotWidth, narrowedMax,
                industrialMinLotArea, industrialRatio,
                publicRatio, coreVacantRatio, edgeVacantRatio, smoothingEnabled,
                coreRadiusFraction, edgeDensity, coreCenterJitter,
                riverEnabled, riverWidth, riverMaxDrift,
                maxCityRadius, forestsEnabled, forestDensity, farmlandDensityThreshold, farmlandSizeMultiplier,
                cityCount, satelliteMinScale, satelliteMaxScale, satellitePeakDensity,
                hamletCount, hamletRadius, hamletPeakDensity,
                minSettlementSpacing, settlementConnectionCount, cityConnectionCount, edgeExitCount);
    }

    public static GenerationConfig defaults(int width, int height) {
        return new GenerationConfig(
                width, height,
                50, 2, 2,       // arterialSpacing, arterialJitter, arterialMaxDrift
                12, 4, 0.94,    // minBlockSizeForSplit, maxLocalRoadDepth, blockSplitChance
                6,              // minBlockArea
                4, 3, 10,        // stripDepth, minLotWidth, maxLotWidth
                18, 0.10,       // industrialMinLotArea, industrialRatio
                0.05,           // publicRatio
                0.0, 0.10,      // coreVacantRatio, edgeVacantRatio
                true,           // smoothingEnabled
                0.35, 0.2,      // coreRadiusFraction, edgeDensity
                0.2,            // coreCenterJitter
                true, 12, 90,   // riverEnabled, riverWidth, riverMaxDrift
                350,              // maxCityRadius (0 = city scales with the map)
                true,           // forestsEnabled
                0.42,           // forestDensity (0.42 reproduces the old fixed forest cutoff)
                0.25,           // farmlandDensityThreshold
                1.0,            // farmlandSizeMultiplier (1.0 = the rural belt's default field size)
                3,              // cityCount (1 = single city, no satellites)
                0.5, 0.9, 0.85, // satelliteMinScale, satelliteMaxScale, satellitePeakDensity
                10, 100, 0.65,    // hamletCount, hamletRadius, hamletPeakDensity
                200, 3, 2, 3     // minSettlementSpacing, settlementConnectionCount, cityConnectionCount, edgeExitCount
        );
    }
}

/*
                OG VALUES
                18, 2,
                12, 2, 0.8,
                6,
                4, 3, 6,
                18, 0.10,
                0.05,
                0.08,
                true,
                0.35, 0.35
 */

/*
  "width": 5000,
  "height": 5000,
  "arterialSpacing": 50,
  "arterialJitter": 2,
  "arterialMaxDrift": 2,
  "minBlockSizeForSplit": 12,
  "maxLocalRoadDepth": 4,
  "blockSplitChance": 0.94,
  "minBlockArea": 6,
  "stripDepth": 4,
  "minLotWidth": 3,
  "maxLotWidth": 10,
  "industrialMinLotArea": 18,
  "industrialRatio": 0.10,
  "publicRatio": 0.05,
  "coreVacantRatio": 0.0,
  "edgeVacantRatio": 0.10,
  "smoothingEnabled": true,
  "coreRadiusFraction": 0.35,
  "edgeDensity": 0.2,
  "coreCenterJitter": 0.2,
  "riverEnabled": true,
  "riverWidth": 12,
  "riverMaxDrift": 90,
  "maxCityRadius": 1000,
  "forestsEnabled": true,
  "farmlandDensityThreshold": 0.25,
  "cityCount": 3,
  "satelliteMinScale": 0.5,
  "satelliteMaxScale": 0.9,
  "satellitePeakDensity": 0.85,
  "hamletCount": 5,
  "hamletRadius": 25,
  "hamletPeakDensity": 0.6,
  "minSettlementSpacing": 60,
  "settlementConnectionCount": 3,
  "edgeExitCount": 3
 */