package be.ragga.raggabackend.simulation.grid.generation;

import be.ragga.raggabackend.simulation.grid.ZoneType;
import be.ragga.raggabackend.simulation.grid.road.RoadClass;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Stage 4: assigns a zone to every lot from its context instead of the old
 * Voronoi blobs. Zoning follows the density gradient so the city reads as
 * core / ring / rim instead of uniform confetti:
 * - a capped quota of large lots becomes INDUSTRIAL, scored toward the low-
 *   density rim (factories need room and never sit downtown)
 * - commercial weight grows with density squared, so downtown is dominantly
 *   commercial; arterial frontage adds the main-street bonus everywhere
 * - everything else leans RESIDENTIAL, strongest away from the core
 * - a small quota of well-connected parcels is reserved for public buildings,
 *   biased toward the core so civic life clusters downtown
 * - one majority-neighbor smoothing pass afterwards clusters zones so the
 *   result doesn't look like salt-and-pepper noise
 * - a config fraction of lots is left vacant: zoned land with no building,
 *   the cheap parcels players can buy and build on later
 */
@Component
public class ZoneAssignmentService {

    public void assign(List<LotDraft> lots, GenerationConfig config, DensityField density, Random random) {
        assignFarmland(lots, config, density);
        assignIndustrialQuota(lots, config, density, random);
        reservePublicSites(lots, config, density, random);
        assignWeightedZones(lots, density, random);
        if (config.smoothingEnabled()) {
            smooth(lots);
        }
        markVacancies(lots, config, density, random);
    }

    /**
     * Everything below the farmland density threshold is FARMLAND, locked so
     * neither the weighted rolls nor smoothing can urbanize the rim. The
     * subdivider already carved these blocks into huge field parcels; this
     * pass gives them their zone.
     */
    private void assignFarmland(List<LotDraft> lots, GenerationConfig config, DensityField density) {
        for (LotDraft lot : lots) {
            if (lotDensity(density, lot) < config.farmlandDensityThreshold()) {
                lot.setZone(ZoneType.FARMLAND);
                lot.lockZone();
            }
        }
    }

    /**
     * A quota of large lots becomes industrial, scored by area x (1 - density)
     * so factories claim the roomy rim parcels and never appear downtown
     * (core lots score exactly 0 and are filtered out).
     */
    private void assignIndustrialQuota(List<LotDraft> lots, GenerationConfig config, DensityField density, Random random) {
        int quota = (int) Math.round(config.industrialRatio() * lots.size());
        if (quota == 0) {
            return;
        }
        Map<LotDraft, Double> scores = new HashMap<>();
        List<LotDraft> eligible = new ArrayList<>(lots.stream()
                .filter(lot -> lot.getZone() == null)
                .filter(lot -> lot.area() >= config.industrialMinLotArea())
                .filter(lot -> {
                    double score = lot.area() * (1.0 - lotDensity(density, lot));
                    scores.put(lot, score);
                    return score > 0;
                })
                .toList());
        eligible.sort((a, b) -> Double.compare(scores.get(b), scores.get(a)));

        // Shuffle within the top candidates so it isn't always exactly the
        // best-scoring lots - keeps industry scattered rather than fully sorted.
        List<LotDraft> pool = new ArrayList<>(eligible.subList(0, Math.min(eligible.size(), quota * 2)));
        Collections.shuffle(pool, random);
        for (LotDraft lot : pool.subList(0, Math.min(pool.size(), quota))) {
            lot.setZone(ZoneType.INDUSTRIAL);
            lot.lockZone();
        }
    }

    /**
     * Well-connected parcels (arterial frontage or corner lots) are reserved
     * for public buildings. Candidates are ranked by density with a random
     * jitter, so civic sites cluster around the core - the biggest downtown
     * public parcel renders as the plaza anchoring the city - while a few
     * still land further out.
     */
    private void reservePublicSites(List<LotDraft> lots, GenerationConfig config, DensityField density, Random random) {
        if (config.publicRatio() <= 0) {
            return; // knob fully off - no minimum-one floor
        }
        int quota = Math.max(1, (int) Math.round(config.publicRatio() * lots.size()));
        Map<LotDraft, Double> scores = new HashMap<>();
        List<LotDraft> candidates = new ArrayList<>(lots.stream()
                .filter(lot -> lot.getZone() == null)
                .filter(lot -> lot.hasFrontage(RoadClass.ARTERIAL) || lot.getFrontages().size() >= 2)
                .toList());
        for (LotDraft lot : candidates) {
            scores.put(lot, lotDensity(density, lot) * (0.5 + random.nextDouble()));
        }
        candidates.sort((a, b) -> Double.compare(scores.get(b), scores.get(a)));
        for (LotDraft lot : candidates.subList(0, Math.min(candidates.size(), quota))) {
            lot.setPublicSite(true);
            // Still gets a zone below: if no public template fits at placement
            // time, the parcel gracefully falls back to being a normal lot.
        }
    }

    private void assignWeightedZones(List<LotDraft> lots, DensityField density, Random random) {
        for (LotDraft lot : lots) {
            if (lot.getZone() != null) {
                continue;
            }
            boolean arterial = lot.hasFrontage(RoadClass.ARTERIAL);
            double d = lotDensity(density, lot);
            // Commercial rises with density SQUARED so it dominates only the
            // true core; industrial fills in toward the rim; residential takes
            // the remainder, peaking in the ring between them.
            double commercial = (arterial ? 0.18 : 0.05) + 0.40 * d * d;
            // (1-d) squared keeps loose industry out of the mid-density ring:
            // it only sprinkles near the rim, on top of the locked quota.
            double industrial = 0.02 + 0.14 * (1.0 - d) * (1.0 - d);

            double roll = random.nextDouble();
            if (roll < commercial) {
                lot.setZone(ZoneType.COMMERCIAL);
                if (arterial) {
                    // Main-street commercial is a deliberate look - protect it
                    // from being smoothed away.
                    lot.lockZone();
                }
            } else if (roll < commercial + industrial) {
                lot.setZone(ZoneType.INDUSTRIAL);
            } else {
                lot.setZone(ZoneType.RESIDENTIAL);
            }
        }
    }

    private double lotDensity(DensityField density, LotDraft lot) {
        return density.at(lot.getX() + lot.getWidth() / 2, lot.getY() + lot.getDepth() / 2);
    }

    // Gap 2 also counts neighbors across a 1-wide road.
    private static final int NEIGHBOR_GAP = 2;

    /**
     * One synchronous majority-neighbor pass: a lot flips to a different zone
     * only when at least 3 nearby lots share it AND it outnumbers the lot's
     * own zone among the neighbors (a true majority, not just any cluster).
     * Flips are computed against a snapshot first so iteration order can't
     * cascade.
     *
     * Neighbor lookup uses a coarse spatial hash instead of comparing every
     * lot against every lot - the naive quadratic version dominated the whole
     * generation time on large maps (~90% at 1000x1000).
     */
    private void smooth(List<LotDraft> lots) {
        if (lots.isEmpty()) {
            return;
        }

        int maxDimension = 1;
        for (LotDraft lot : lots) {
            maxDimension = Math.max(maxDimension, Math.max(lot.getWidth(), lot.getDepth()));
        }
        int bucketSize = maxDimension + NEIGHBOR_GAP;

        Map<Long, List<LotDraft>> buckets = new HashMap<>();
        for (LotDraft lot : lots) {
            long key = bucketKey(lot.getX() / bucketSize, lot.getY() / bucketSize);
            buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(lot);
        }

        Map<LotDraft, ZoneType> flips = new HashMap<>();
        for (LotDraft lot : lots) {
            if (lot.isZoneLocked()) {
                continue;
            }
            Map<ZoneType, Integer> counts = new EnumMap<>(ZoneType.class);
            // Buckets are keyed by lot origin, so the scan range must reach
            // far enough left/up to catch neighbors whose origin is up to
            // (their size + gap) away.
            int fromBx = Math.floorDiv(lot.getX() - NEIGHBOR_GAP - maxDimension, bucketSize);
            int toBx = Math.floorDiv(lot.getX() + lot.getWidth() - 1 + NEIGHBOR_GAP, bucketSize);
            int fromBy = Math.floorDiv(lot.getY() - NEIGHBOR_GAP - maxDimension, bucketSize);
            int toBy = Math.floorDiv(lot.getY() + lot.getDepth() - 1 + NEIGHBOR_GAP, bucketSize);

            for (int bx = fromBx; bx <= toBx; bx++) {
                for (int by = fromBy; by <= toBy; by++) {
                    List<LotDraft> bucket = buckets.get(bucketKey(bx, by));
                    if (bucket == null) {
                        continue;
                    }
                    for (LotDraft other : bucket) {
                        if (other != lot && lot.isNear(other, NEIGHBOR_GAP)) {
                            counts.merge(other.getZone(), 1, Integer::sum);
                        }
                    }
                }
            }

            int ownCount = counts.getOrDefault(lot.getZone(), 0);
            counts.entrySet().stream()
                    .filter(e -> e.getKey() != lot.getZone() && e.getValue() >= 3 && e.getValue() > ownCount)
                    .max(Map.Entry.comparingByValue())
                    .ifPresent(e -> flips.put(lot, e.getKey()));
        }
        flips.forEach(LotDraft::setZone);
    }

    private long bucketKey(int bx, int by) {
        return ((long) bx << 32) | (by & 0xFFFFFFFFL);
    }

    private void markVacancies(List<LotDraft> lots, GenerationConfig config, DensityField density, Random random) {
        for (LotDraft lot : lots) {
            if (lot.isPublicSite()) {
                continue;
            }
            // Vacancy (unbuilt zoned land) interpolates by local density from
            // edgeVacantRatio at the rim to coreVacantRatio downtown, so the
            // two knobs set independently how built-out - how dense - the core
            // and the outskirts feel.
            double norm = Math.clamp(
                    (lotDensity(density, lot) - config.edgeDensity()) / (1.0 - config.edgeDensity()),
                    0.0, 1.0);
            double chance = config.edgeVacantRatio()
                    + (config.coreVacantRatio() - config.edgeVacantRatio()) * norm;
            if (random.nextDouble() < chance) {
                lot.setVacant(true);
            }
        }
    }
}
