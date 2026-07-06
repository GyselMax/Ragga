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
                                     TileType[][] tiles, DensityField density, Random random) {
        List<TemplateSpec> publicTemplates = catalog.stream().filter(TemplateSpec::publicUse).toList();
        List<BuildingDraft> buildings = new ArrayList<>();

        for (LotDraft lot : lots) {
            if (lot.isPublicSite()) {
                BuildingDraft building = tryPlace(lot, publicTemplates, density, random);
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
            BuildingDraft building = tryPlace(lot, zoneTemplates, density, random);
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

    private BuildingDraft tryPlace(LotDraft lot, List<TemplateSpec> templates, DensityField density, Random random) {
        record Candidate(TemplateSpec template, Direction facing, int sizeX, int sizeY) {
        }

        List<Candidate> candidates = new ArrayList<>();
        for (TemplateSpec template : templates) {
            for (Direction facing : lot.getFrontages().keySet()) {
                // Facing north/south keeps the canonical footprint; east/west
                // rotates it 90 degrees, swapping the extents.
                boolean rotated = facing == Direction.EAST || facing == Direction.WEST;
                int sizeX = rotated ? template.depth() : template.width();
                int sizeY = rotated ? template.width() : template.depth();
                if (sizeX <= lot.getWidth() && sizeY <= lot.getDepth()) {
                    candidates.add(new Candidate(template, facing, sizeX, sizeY));
                }
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }

        // Pack the lot: only candidates close to the best achievable fill are
        // kept, then one is picked at random for variety. How close depends
        // on local density - downtown demands near-perfect fill, the
        // outskirts tolerate a small house on a big parcel.
        double lotDensity = density.at(lot.getX() + lot.getWidth() / 2, lot.getY() + lot.getDepth() / 2);
        double acceptFraction = 0.45 + 0.45 * lotDensity;
        int bestArea = candidates.stream().mapToInt(c -> c.template().area()).max().orElseThrow();
        List<Candidate> tight = candidates.stream()
                .filter(c -> c.template().area() >= bestArea * acceptFraction)
                .toList();
        Candidate chosen = tight.get(random.nextInt(tight.size()));

        // Flush against the road-facing edge, random slide along it.
        int x;
        int y;
        switch (chosen.facing()) {
            case NORTH -> {
                y = lot.getY();
                x = lot.getX() + random.nextInt(lot.getWidth() - chosen.sizeX() + 1);
            }
            case SOUTH -> {
                y = lot.getY() + lot.getDepth() - chosen.sizeY();
                x = lot.getX() + random.nextInt(lot.getWidth() - chosen.sizeX() + 1);
            }
            case WEST -> {
                x = lot.getX();
                y = lot.getY() + random.nextInt(lot.getDepth() - chosen.sizeY() + 1);
            }
            default -> {
                x = lot.getX() + lot.getWidth() - chosen.sizeX();
                y = lot.getY() + random.nextInt(lot.getDepth() - chosen.sizeY() + 1);
            }
        }
        return new BuildingDraft(chosen.template(), x, y, chosen.sizeX(), chosen.sizeY(), chosen.facing());
    }

    private void fillLotTiles(LotDraft lot, TileType type, TileType[][] tiles) {
        for (int x = lot.getX(); x < lot.getX() + lot.getWidth(); x++) {
            for (int y = lot.getY(); y < lot.getY() + lot.getDepth(); y++) {
                tiles[x][y] = type;
            }
        }
    }
}
