package be.ragga.raggabackend.simulation.grid.persistence.web;

import be.ragga.raggabackend.simulation.grid.generation.GenerationConfig;

/**
 * Query-param carrier for the generation tuner endpoint. Every field is a boxed
 * type so an omitted query param stays null and falls back to the matching
 * default in {@link #toConfig()} - the whole point of the tuner is to override
 * one or two knobs and leave the rest alone.
 *
 * Field names match {@link GenerationConfig}'s record components exactly, which
 * is how Spring binds {@code ?arterialSpacing=40} to {@code arterialSpacing} and
 * how the tuner page (which reads the defaults JSON) knows the param names.
 *
 * Deliberately a plain JavaBean, not a record: Spring's @ModelAttribute binding
 * simply skips the setter for an absent param, so partial requests never fail.
 */
public class GenerationParams {

    // Generous ceiling matching the standalone generator's large-map workflow
    // (e.g. 5000x5000) - only an absurd request is clamped, so a map size the
    // user typed is honored rather than silently shrunk. In-memory preview can
    // afford this; the persist path applies its own (lower) limit, since a
    // 5000x5000 map is 25M grid rows.
    private static final int MAX_DIM = 5000;
    private static final int MIN_DIM = 10;
    private static final int DEFAULT_DIM = 400;

    private Integer width;
    private Integer height;
    private Integer arterialSpacing;
    private Integer arterialJitter;
    private Integer arterialMaxDrift;
    private Integer minBlockSizeForSplit;
    private Integer maxLocalRoadDepth;
    private Double blockSplitChance;
    private Integer minBlockArea;
    private Integer stripDepth;
    private Integer minLotWidth;
    private Integer maxLotWidth;
    private Integer industrialMinLotArea;
    private Double industrialRatio;
    private Double publicRatio;
    private Double coreVacantRatio;
    private Double edgeVacantRatio;
    private Boolean smoothingEnabled;
    private Double coreRadiusFraction;
    private Double edgeDensity;
    private Double coreCenterJitter;
    private Boolean riverEnabled;
    private Integer riverWidth;
    private Integer riverMaxDrift;
    private Integer maxCityRadius;
    private Boolean forestsEnabled;
    private Double forestDensity;
    private Double farmlandDensityThreshold;
    private Double farmlandSizeMultiplier;
    private Integer cityCount;
    private Double satelliteMinScale;
    private Double satelliteMaxScale;
    private Double satellitePeakDensity;
    private Integer hamletCount;
    private Integer hamletRadius;
    private Double hamletPeakDensity;
    private Integer minSettlementSpacing;
    private Integer settlementConnectionCount;
    private Integer cityConnectionCount;
    private Integer edgeExitCount;

    /**
     * Builds a full {@link GenerationConfig}: dimensions clamped to the preview
     * range, every other knob taken from the request or, if omitted, from
     * {@code GenerationConfig.defaults} at the resolved dimensions. The config's
     * own compact constructor still validates the combination, so an impossible
     * mix (e.g. cityCount &gt; 1 with maxCityRadius 0) throws
     * IllegalArgumentException for the controller to turn into a 400.
     */
    public GenerationConfig toConfig() {
        int w = Math.clamp(width != null ? width : DEFAULT_DIM, MIN_DIM, MAX_DIM);
        int h = Math.clamp(height != null ? height : DEFAULT_DIM, MIN_DIM, MAX_DIM);
        GenerationConfig d = GenerationConfig.defaults(w, h);
        return new GenerationConfig(
                w, h,
                i(arterialSpacing, d.arterialSpacing()),
                i(arterialJitter, d.arterialJitter()),
                i(arterialMaxDrift, d.arterialMaxDrift()),
                i(minBlockSizeForSplit, d.minBlockSizeForSplit()),
                i(maxLocalRoadDepth, d.maxLocalRoadDepth()),
                dbl(blockSplitChance, d.blockSplitChance()),
                i(minBlockArea, d.minBlockArea()),
                i(stripDepth, d.stripDepth()),
                i(minLotWidth, d.minLotWidth()),
                i(maxLotWidth, d.maxLotWidth()),
                i(industrialMinLotArea, d.industrialMinLotArea()),
                dbl(industrialRatio, d.industrialRatio()),
                dbl(publicRatio, d.publicRatio()),
                dbl(coreVacantRatio, d.coreVacantRatio()),
                dbl(edgeVacantRatio, d.edgeVacantRatio()),
                b(smoothingEnabled, d.smoothingEnabled()),
                dbl(coreRadiusFraction, d.coreRadiusFraction()),
                dbl(edgeDensity, d.edgeDensity()),
                dbl(coreCenterJitter, d.coreCenterJitter()),
                b(riverEnabled, d.riverEnabled()),
                i(riverWidth, d.riverWidth()),
                i(riverMaxDrift, d.riverMaxDrift()),
                i(maxCityRadius, d.maxCityRadius()),
                b(forestsEnabled, d.forestsEnabled()),
                dbl(forestDensity, d.forestDensity()),
                dbl(farmlandDensityThreshold, d.farmlandDensityThreshold()),
                dbl(farmlandSizeMultiplier, d.farmlandSizeMultiplier()),
                i(cityCount, d.cityCount()),
                dbl(satelliteMinScale, d.satelliteMinScale()),
                dbl(satelliteMaxScale, d.satelliteMaxScale()),
                dbl(satellitePeakDensity, d.satellitePeakDensity()),
                i(hamletCount, d.hamletCount()),
                i(hamletRadius, d.hamletRadius()),
                dbl(hamletPeakDensity, d.hamletPeakDensity()),
                i(minSettlementSpacing, d.minSettlementSpacing()),
                i(settlementConnectionCount, d.settlementConnectionCount()),
                i(cityConnectionCount, d.cityConnectionCount()),
                i(edgeExitCount, d.edgeExitCount()));
    }

    private static int i(Integer v, int fallback) {
        return v != null ? v : fallback;
    }

    private static double dbl(Double v, double fallback) {
        return v != null ? v : fallback;
    }

    private static boolean b(Boolean v, boolean fallback) {
        return v != null ? v : fallback;
    }

    public void setWidth(Integer width) { this.width = width; }
    public void setHeight(Integer height) { this.height = height; }
    public void setArterialSpacing(Integer v) { this.arterialSpacing = v; }
    public void setArterialJitter(Integer v) { this.arterialJitter = v; }
    public void setArterialMaxDrift(Integer v) { this.arterialMaxDrift = v; }
    public void setMinBlockSizeForSplit(Integer v) { this.minBlockSizeForSplit = v; }
    public void setMaxLocalRoadDepth(Integer v) { this.maxLocalRoadDepth = v; }
    public void setBlockSplitChance(Double v) { this.blockSplitChance = v; }
    public void setMinBlockArea(Integer v) { this.minBlockArea = v; }
    public void setStripDepth(Integer v) { this.stripDepth = v; }
    public void setMinLotWidth(Integer v) { this.minLotWidth = v; }
    public void setMaxLotWidth(Integer v) { this.maxLotWidth = v; }
    public void setIndustrialMinLotArea(Integer v) { this.industrialMinLotArea = v; }
    public void setIndustrialRatio(Double v) { this.industrialRatio = v; }
    public void setPublicRatio(Double v) { this.publicRatio = v; }
    public void setCoreVacantRatio(Double v) { this.coreVacantRatio = v; }
    public void setEdgeVacantRatio(Double v) { this.edgeVacantRatio = v; }
    public void setSmoothingEnabled(Boolean v) { this.smoothingEnabled = v; }
    public void setCoreRadiusFraction(Double v) { this.coreRadiusFraction = v; }
    public void setEdgeDensity(Double v) { this.edgeDensity = v; }
    public void setCoreCenterJitter(Double v) { this.coreCenterJitter = v; }
    public void setRiverEnabled(Boolean v) { this.riverEnabled = v; }
    public void setRiverWidth(Integer v) { this.riverWidth = v; }
    public void setRiverMaxDrift(Integer v) { this.riverMaxDrift = v; }
    public void setMaxCityRadius(Integer v) { this.maxCityRadius = v; }
    public void setForestsEnabled(Boolean v) { this.forestsEnabled = v; }
    public void setForestDensity(Double v) { this.forestDensity = v; }
    public void setFarmlandDensityThreshold(Double v) { this.farmlandDensityThreshold = v; }
    public void setFarmlandSizeMultiplier(Double v) { this.farmlandSizeMultiplier = v; }
    public void setCityCount(Integer v) { this.cityCount = v; }
    public void setSatelliteMinScale(Double v) { this.satelliteMinScale = v; }
    public void setSatelliteMaxScale(Double v) { this.satelliteMaxScale = v; }
    public void setSatellitePeakDensity(Double v) { this.satellitePeakDensity = v; }
    public void setHamletCount(Integer v) { this.hamletCount = v; }
    public void setHamletRadius(Integer v) { this.hamletRadius = v; }
    public void setHamletPeakDensity(Double v) { this.hamletPeakDensity = v; }
    public void setMinSettlementSpacing(Integer v) { this.minSettlementSpacing = v; }
    public void setSettlementConnectionCount(Integer v) { this.settlementConnectionCount = v; }
    public void setCityConnectionCount(Integer v) { this.cityConnectionCount = v; }
    public void setEdgeExitCount(Integer v) { this.edgeExitCount = v; }
}
