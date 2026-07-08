package be.ragga.raggabackend.simulation.grid.generation;

import java.util.List;

/**
 * Output of the settlement-planning stage (between terrain and roads):
 * the hamlet density bumps to fold into the field, and the connection roads
 * that stitch every settlement - main city, satellites, hamlets - plus the
 * map-edge exits into one network.
 *
 * @param hamletCores density bumps for the scattered villages; appended to
 *                    the field via DensityField.withSettlements so the normal
 *                    pipeline builds each village on its own
 * @param roads       axis-aligned connection roads as {ax, ay, bx, by}
 *                    center-to-center pairs, drawn by RoadNetworkGenerator as
 *                    ARTERIAL doglegs
 */
public record SettlementPlan(List<DensityField.Core> hamletCores, List<int[]> roads) {

    public static SettlementPlan empty() {
        return new SettlementPlan(List.of(), List.of());
    }
}
