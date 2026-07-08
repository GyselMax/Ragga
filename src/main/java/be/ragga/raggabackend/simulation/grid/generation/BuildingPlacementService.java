package be.ragga.raggabackend.simulation.grid.generation;

import be.ragga.raggabackend.simulation.grid.TileType;
import be.ragga.raggabackend.simulation.grid.lot.Direction;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Stage 5: picks a building template per lot from the lot's own context -
 * its zone, its size, and which sides face road. The template library never
 * drives lot creation; the lot's situation narrows the candidate set and one
 * candidate is stamped out.
 *
 * Templates are authored with their front facing NORTH. Placement rotates
 * the footprint so the front faces an actual road frontage of the lot
 * (width/depth swap on 90/270 degrees) and sets the building flush against
 * that road-facing edge.
 */
@Component
public class BuildingPlacementService {

    public List<BuildingDraft> place(List<LotDraft> lots, List<TemplateSpec> catalog,
                                     TileType[][] tiles, Random random) {
        List<TemplateSpec> publicTemplates = catalog.stream().filter(TemplateSpec::publicUse).toList();
        List<BuildingDraft> buildings = new ArrayList<>();

        for (LotDraft lot : lots) {
            if (lot.isPublicSite()) {
                BuildingDraft building = tryPlace(lot, publicTemplates, tiles, random);
                if (building != null) {
                    // The whole parcel becomes public ground (building +
                    // surrounding plaza), immutable to player building.
                    fillLotTiles(lot, TileType.PUBLIC, tiles);
                    lot.setBuilding(building);
                    buildings.add(building);
                    continue;
                }
                // No public template fits - fall back to a normal zoned lot.
                lot.setPublicSite(false);
            }

            if (lot.isVacant()) {
                continue;
            }

            List<TemplateSpec> zoneTemplates = catalog.stream()
                    .filter(t -> !t.publicUse() && t.zone() == lot.getZone())
                    .toList();
            BuildingDraft building = tryPlace(lot, zoneTemplates, tiles, random);
            if (building == null) {
                // Nothing in the library fits this lot - it stays vacant
                // (zoned, purchasable) rather than failing generation.
                lot.setVacant(true);
            } else {
                lot.setBuilding(building);
                buildings.add(building);
            }
        }
        return buildings;
    }

    private BuildingDraft tryPlace(LotDraft lot, List<TemplateSpec> templates, TileType[][] tiles,
                                   Random random) {
        // frontageGap = how much of the street the building leaves unfilled;
        // depthGap = how far short of the lot's depth it stops.
        record Candidate(TemplateSpec template, Direction facing, int sizeX, int sizeY,
                         int frontageGap, int depthGap) {
        }

        List<Candidate> candidates = new ArrayList<>();
        for (TemplateSpec template : templates) {
            for (Direction facing : lot.getFrontages().keySet()) {
                // Facing north/south keeps the canonical footprint; east/west
                // rotates it 90 degrees, swapping the extents.
                boolean rotated = facing == Direction.EAST || facing == Direction.WEST;
                int sizeX = rotated ? template.depth() : template.width();
                int sizeY = rotated ? template.width() : template.depth();
                if (sizeX > lot.getWidth() || sizeY > lot.getDepth()) {
                    continue;
                }
                // The front runs along the road the building faces; depth is
                // perpendicular, into the lot. Measure how far each falls
                // short of filling the lot.
                boolean alongX = facing == Direction.NORTH || facing == Direction.SOUTH;
                int frontExtent = alongX ? sizeX : sizeY;
                int depthExtent = alongX ? sizeY : sizeX;
                int frontageSpan = alongX ? lot.getWidth() : lot.getDepth();
                int depthSpan = alongX ? lot.getDepth() : lot.getWidth();
                candidates.add(new Candidate(template, facing, sizeX, sizeY,
                        frontageSpan - frontExtent, depthSpan - depthExtent));
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }

        // Frontage-first fit: fill the street frontage as fully as possible
        // (smallest frontage gap), and only then fill the depth (smallest
        // depth gap). So a perfect fill wins; failing that, a shallower
        // building that still spans the whole street beats a narrower one that
        // leaves a hole; narrowing the frontage is the last resort. Downtown's
        // small lots make perfect fills easy, so the core packs tight. One of
        // the equally-best fits is picked at random for variety.
        int minFrontageGap = candidates.stream().mapToInt(Candidate::frontageGap).min().orElseThrow();
        int minDepthGap = candidates.stream()
                .filter(c -> c.frontageGap() == minFrontageGap)
                .mapToInt(Candidate::depthGap).min().orElseThrow();
        List<Candidate> best = candidates.stream()
                .filter(c -> c.frontageGap() == minFrontageGap && c.depthGap() == minDepthGap)
                .toList();
        Candidate chosen = best.get(random.nextInt(best.size()));

        // Flush against the road-facing edge, random slide along it - but
        // constrained so the footprint overlaps the stretch of the edge that
        // actually borders road (frontage can cover only part of a side,
        // e.g. next to a map edge or a leftover region).
        int x;
        int y;
        boolean alongX = chosen.facing() == Direction.NORTH || chosen.facing() == Direction.SOUTH;
        int[] contact = contactRange(lot, chosen.facing(), tiles);

        if (alongX) {
            y = chosen.facing() == Direction.NORTH ? lot.getY() : lot.getY() + lot.getDepth() - chosen.sizeY();
            x = slide(lot.getX(), lot.getX() + lot.getWidth() - chosen.sizeX(), chosen.sizeX(), contact, random);
        } else {
            x = chosen.facing() == Direction.WEST ? lot.getX() : lot.getX() + lot.getWidth() - chosen.sizeX();
            y = slide(lot.getY(), lot.getY() + lot.getDepth() - chosen.sizeY(), chosen.sizeY(), contact, random);
        }
        return new BuildingDraft(chosen.template(), x, y, chosen.sizeX(), chosen.sizeY(), chosen.facing());
    }

    /** Random position in [low, high], narrowed so the footprint overlaps the road-contact interval when one exists. */
    private int slide(int low, int high, int size, int[] contact, Random random) {
        if (contact != null) {
            int narrowedLow = Math.max(low, contact[0] - size + 1);
            int narrowedHigh = Math.min(high, contact[1]);
            // Contact tiles lie along the lot's own side, so the narrowed
            // range can't actually be empty - this check is belt-and-braces
            // for future callers, not a reachable branch today.
            if (narrowedLow <= narrowedHigh) {
                low = narrowedLow;
                high = narrowedHigh;
            }
        }
        return low + random.nextInt(high - low + 1);
    }

    /** First and last coordinate along the facing edge whose outside neighbor is a road tile, or null if none. */
    private int[] contactRange(LotDraft lot, Direction facing, TileType[][] tiles) {
        int gridW = tiles.length;
        int gridH = tiles[0].length;
        boolean alongX = facing == Direction.NORTH || facing == Direction.SOUTH;
        int span = alongX ? lot.getWidth() : lot.getDepth();

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < span; i++) {
            int cx = alongX ? lot.getX() + i : (facing == Direction.WEST ? lot.getX() - 1 : lot.getX() + lot.getWidth());
            int cy = alongX ? (facing == Direction.NORTH ? lot.getY() - 1 : lot.getY() + lot.getDepth()) : lot.getY() + i;
            if (cx >= 0 && cx < gridW && cy >= 0 && cy < gridH && tiles[cx][cy] == TileType.ROAD) {
                int along = alongX ? cx : cy;
                min = Math.min(min, along);
                max = Math.max(max, along);
            }
        }
        return min == Integer.MAX_VALUE ? null : new int[]{min, max};
    }

    private void fillLotTiles(LotDraft lot, TileType type, TileType[][] tiles) {
        for (int x = lot.getX(); x < lot.getX() + lot.getWidth(); x++) {
            for (int y = lot.getY(); y < lot.getY() + lot.getDepth(); y++) {
                tiles[x][y] = type;
            }
        }
    }
}
