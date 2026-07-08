package be.ragga.raggabackend.simulation.grid.generation;

import be.ragga.raggabackend.simulation.grid.ZoneType;

import java.util.List;

import static be.ragga.raggabackend.simulation.grid.ZoneType.COMMERCIAL;
import static be.ragga.raggabackend.simulation.grid.ZoneType.FARMLAND;
import static be.ragga.raggabackend.simulation.grid.ZoneType.INDUSTRIAL;
import static be.ragga.raggabackend.simulation.grid.ZoneType.RESIDENTIAL;

/**
 * Hardcoded starter template catalog.
 *
 * NOT the real building library: in the live game templates are DB rows
 * (Phase B's BuildingTemplate entity, seeded from this list at boot) so the
 * catalog can grow over a months-long game without redeploys. This class
 * exists so the generation pipeline and GridVisualizer can run standalone,
 * without Spring or MySQL.
 *
 * Size rule: most buildings are multi-tile - 2x2/3x3 and rectangles like
 * 2x4; industrial goes 4x4 and up; 1x1 is the deliberate exception.
 */
public final class StubTemplateCatalog {

    private StubTemplateCatalog() {
    }

    public static List<TemplateSpec> standard() {
        return List.of(
                residential("RES_HOUSE_2X2", 2, 2),
                residential("RES_HOUSE_2X3", 2, 3),
                residential("RES_ROWHOUSE_2X4", 2, 4),
                residential("RES_LOWRISE_3X3", 3, 3),
                residential("RES_MIDRISE_3X4", 3, 4),
                residential("RES_APARTMENT_4X4", 4, 4),
                residential("RES_TERRACE_5X4", 5, 4),
                residential("RES_BLOCK_6X4", 6, 4),
                residential("RES_HIGHRISE_6X6", 6, 6),
                residential("RES_MANSION_8X8", 8, 8),
                residential("RES_BIG_MANSION_10X10", 10, 10),

                commercial("COM_KIOSK_1X1", 1, 1),
                commercial("COM_SHOP_2X2", 2, 2),
                commercial("COM_STORE_3X2", 3, 2),
                commercial("COM_SUPERMARKET_3X3", 3, 3),
                commercial("COM_MALL_4X3", 4, 3),
                commercial("COM_OFFICE_4X4", 4, 4),
                commercial("COM_CENTER_5X4", 5, 4),
                commercial("COM_MEGAMALL_5X4", 8, 8),

                industrial("IND_WORKSHOP_3X3", 3, 3),
                industrial("IND_FACTORY_4X4", 4, 4),
                industrial("IND_WAREHOUSE_5X4", 5, 4),
                industrial("IND_PLANT_4X6", 4, 6),
                industrial("IND_HEAVY_6X4", 6, 4),
                industrial("IND_MEGAFACTORY_10X10", 8, 8),

                // Farm buildings are deliberately tiny relative to their huge
                // field parcels - the field IS the lot, the farmhouse sits at
                // its road edge.
                farm("FARM_HOUSE_2X2", 2, 2),
                farm("FARM_BARN_3X2", 3, 2),
                farm("FARM_STABLE_4X2", 4, 2),

                publicUse("PUB_BUSSTOP_1X1", 1, 1),
                publicUse("PUB_LIBRARY_2X2", 2, 2),
                publicUse("PUB_SCHOOL_3X3", 3, 3),
                publicUse("PUB_STATION_4X2", 4, 2)
        );
    }

    private static TemplateSpec residential(String code, int width, int depth) {
        return template(code, RESIDENTIAL, width, depth);
    }

    private static TemplateSpec commercial(String code, int width, int depth) {
        return template(code, COMMERCIAL, width, depth);
    }

    private static TemplateSpec industrial(String code, int width, int depth) {
        return template(code, INDUSTRIAL, width, depth);
    }

    private static TemplateSpec farm(String code, int width, int depth) {
        return template(code, FARMLAND, width, depth);
    }

    private static TemplateSpec template(String code, ZoneType zone, int width, int depth) {
        return new TemplateSpec(code, zone, false, width, depth);
    }

    private static TemplateSpec publicUse(String code, int width, int depth) {
        return new TemplateSpec(code, null, true, width, depth);
    }
}
