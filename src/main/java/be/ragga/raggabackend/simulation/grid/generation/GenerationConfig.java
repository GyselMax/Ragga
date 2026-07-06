package be.ragga.raggabackend.simulation.grid.generation;

/**
 * All tuning knobs for city generation, as a plain record so the pipeline
 * can run without Spring or a database (e.g. from GridVisualizer.main()).
 * Phase B maps ragga.grid.* properties onto this.
 *
 * @param width                Map width in tiles. Generation cost grows linearly with the
 *                             tile count; the visualizer renders 10 pixels per tile.
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
 *                             giving longer lot strips and more interior parks.
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
 * @param vacantLotRatio       Base vacancy rate. The effective chance per lot is
 *                             base x (1 - local density) x 3.5, so downtown has virtually
 *                             no vacant lots and the map border reaches roughly 2.3x this
 *                             value. Vacant lots are the cheap parcels players buy later.
 * @param smoothingEnabled     Runs the majority-neighbor smoothing pass after zoning: a lot
 *                             flips to the zone 3+ of its neighbors share, which clusters
 *                             zones into districts. Off = noisier, salt-and-pepper zoning.
 * @param coreRadiusFraction   Size of the full-density downtown, as a fraction of the
 *                             distance from the map center to the nearest edge. 0.35 means
 *                             the inner 35% of that distance is max density; beyond it,
 *                             density fades linearly to edgeDensity at the border.
 * @param edgeDensity          Density floor at the map border, between 0 and 1. Lower =
 *                             starker downtown-vs-outskirts contrast: more vacancy, fewer
 *                             road splits, bigger parks and smaller buildings at the rim.
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
        double vacantLotRatio,
        boolean smoothingEnabled,
        double coreRadiusFraction,
        double edgeDensity
) {

    public static GenerationConfig defaults(int width, int height) {
        return new GenerationConfig(
                width, height,
                25, 3, 2,       // arterialSpacing, arterialJitter, arterialMaxDrift
                12, 2, 0.95,    // minBlockSizeForSplit, maxLocalRoadDepth, blockSplitChance
                6,              // minBlockArea
                4, 3, 6,        // stripDepth, minLotWidth, maxLotWidth
                18, 0.10,       // industrialMinLotArea, industrialRatio
                0.05,           // publicRatio
                0.08,           // vacantLotRatio
                true,           // smoothingEnabled
                0.35, 0.2      // coreRadiusFraction, edgeDensity
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
