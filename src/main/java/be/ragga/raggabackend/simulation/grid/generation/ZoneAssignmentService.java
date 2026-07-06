package be.ragga.raggabackend.simulation.grid.generation;

import be.ragga.raggabackend.simulation.grid.ZoneType;
import be.ragga.raggabackend.simulation.grid.road.RoadClass;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Stage 4: assigns a zone to every lot from its context instead of the old
 * Voronoi blobs:
 * - a capped quota of the largest lots becomes INDUSTRIAL (factories need room)
 * - arterial-fronting lots lean COMMERCIAL (main-street feel)
 * - everything else leans RESIDENTIAL
 * - a small quota of well-connected parcels is reserved for public buildings
 * - one majority-neighbor smoothing pass afterwards clusters zones so the
 *   result doesn't look like salt-and-pepper noise
 * - a config fraction of lots is left vacant: zoned land with no building,
 *   the cheap parcels players can buy and build on later
 */
@Component
public class ZoneAssignmentService {

    public void assign(List<LotDraft> lots, GenerationConfig config, DensityField density, Random random) {
        assignIndustrialQuota(lots, config, random);
        reservePublicSites(lots, config, random);
        assignWeightedZones(lots, config, random);
        if (config.smoothingEnabled()) {
            smooth(lots);
        }
        markVacancies(lots, config, density, random);
    }

    /** The N largest eligible lots become industrial, N derived from the configured ratio. */
    private void assignIndustrialQuota(List<LotDraft> lots, GenerationConfig config, Random random) {
        int quota = (int) Math.round(config.industrialRatio() * lots.size());
        if (quota == 0) {
            return;
        }
        List<LotDraft> eligible = new ArrayList<>(lots.stream()
                .filter(lot -> lot.area() >= config.industrialMinLotArea())
                .toList());
        eligible.sort((a, b) -> Integer.compare(b.area(), a.area()));

        // Shuffle within the top candidates so it isn't always exactly the
        // biggest lots - keeps industry scattered rather than fully sorted.
        List<LotDraft> pool = new ArrayList<>(eligible.subList(0, Math.min(eligible.size(), quota * 2)));
        Collections.shuffle(pool, random);
        for (LotDraft lot : pool.subList(0, Math.min(pool.size(), quota))) {
            lot.setZone(ZoneType.INDUSTRIAL);
            lot.lockZone();
        }
    }

    /** Well-connected parcels (arterial frontage or corner lots) are reserved for public buildings. */
    private void reservePublicSites(List<LotDraft> lots, GenerationConfig config, Random random) {
        int quota = Math.max(1, (int) Math.round(config.publicRatio() * lots.size()));
        List<LotDraft> candidates = new ArrayList<>(lots.stream()
                .filter(lot -> lot.getZone() == null)
                .filter(lot -> lot.hasFrontage(RoadClass.ARTERIAL) || lot.getFrontages().size() >= 2)
                .toList());
        Collections.shuffle(candidates, random);
        for (LotDraft lot : candidates.subList(0, Math.min(candidates.size(), quota))) {
            lot.setPublicSite(true);
            // Still gets a zone below: if no public template fits at placement
            // time, the parcel gracefully falls back to being a normal lot.
        }
    }

    private void assignWeightedZones(List<LotDraft> lots, GenerationConfig config, Random random) {
        for (LotDraft lot : lots) {
            if (lot.getZone() != null) {
                continue;
            }
            boolean arterial = lot.hasFrontage(RoadClass.ARTERIAL);
            double commercial = arterial ? 0.28 : 0.08;
            double residential = arterial ? 0.66 : 0.89;

            double roll = random.nextDouble();
            if (roll < commercial) {
                lot.setZone(ZoneType.COMMERCIAL);
                if (arterial) {
                    // Main-street commercial is a deliberate look - protect it
                    // from being smoothed away.
                    lot.lockZone();
                }
            } else if (roll < commercial + residential) {
                lot.setZone(ZoneType.RESIDENTIAL);
            } else {
                lot.setZone(ZoneType.INDUSTRIAL);
            }
        }
    }

    /**
     * One synchronous majority-neighbor pass: a lot flips to the zone shared
     * by 3+ of its neighbors. Flips are computed against a snapshot first so
     * iteration order can't cascade.
     */
    private void smooth(List<LotDraft> lots) {
        Map<LotDraft, ZoneType> flips = new java.util.HashMap<>();

        for (LotDraft lot : lots) {
            if (lot.isZoneLocked()) {
                continue;
            }
            Map<ZoneType, Integer> counts = new EnumMap<>(ZoneType.class);
            for (LotDraft other : lots) {
                // Gap 2 also counts neighbors across a 1-wide road.
                if (other != lot && lot.isNear(other, 2)) {
                    counts.merge(other.getZone(), 1, Integer::sum);
                }
            }
            counts.entrySet().stream()
                    .filter(e -> e.getKey() != lot.getZone() && e.getValue() >= 3)
                    .max(Map.Entry.comparingByValue())
                    .ifPresent(e -> flips.put(lot, e.getKey()));
        }
        flips.forEach(LotDraft::setZone);
    }

    private void markVacancies(List<LotDraft> lots, GenerationConfig config, DensityField density, Random random) {
        for (LotDraft lot : lots) {
            if (lot.isPublicSite()) {
                continue;
            }
            // Vacancy follows the density gradient: essentially no empty lots
            // downtown, progressively more toward the outskirts.
            double lotDensity = density.at(lot.getX() + lot.getWidth() / 2, lot.getY() + lot.getDepth() / 2);
            double chance = config.vacantLotRatio() * (1.0 - lotDensity) * 3.5;
            if (random.nextDouble() < chance) {
                lot.setVacant(true);
            }
        }
    }
}
