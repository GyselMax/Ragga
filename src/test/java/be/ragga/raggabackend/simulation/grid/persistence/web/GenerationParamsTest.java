package be.ragga.raggabackend.simulation.grid.persistence.web;

import be.ragga.raggabackend.simulation.grid.generation.GenerationConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the tuner's param-to-config resolution: omitted knobs fall back to
 * defaults, provided knobs win, and preview dimensions are clamped.
 */
class GenerationParamsTest {

    @Test
    void emptyParamsYieldTheDefaults() {
        GenerationConfig config = new GenerationParams().toConfig();
        // width/height default to 400 inside GenerationParams.
        assertEquals(GenerationConfig.defaults(400, 400), config,
                "no overrides must reproduce the default config exactly");
    }

    @Test
    void providedKnobsOverrideDefaultsAndRestStay() {
        GenerationParams params = new GenerationParams();
        params.setWidth(300);
        params.setHeight(300);
        params.setCityCount(1);          // single city (avoids satellite constraints)
        params.setEdgeDensity(0.05);
        params.setRiverEnabled(false);

        GenerationConfig config = params.toConfig();
        GenerationConfig baseline = GenerationConfig.defaults(300, 300);

        assertEquals(1, config.cityCount(), "overridden knob must win");
        assertEquals(0.05, config.edgeDensity(), "overridden knob must win");
        assertEquals(false, config.riverEnabled(), "overridden boolean must win");
        // An untouched knob keeps the default for the resolved dimensions.
        assertEquals(baseline.arterialSpacing(), config.arterialSpacing(),
                "untouched knob must stay at its default");
        assertEquals(baseline.stripDepth(), config.stripDepth(),
                "untouched knob must stay at its default");
    }

    @Test
    void dimensionsAreClampedToThePreviewRange() {
        GenerationParams tooBig = new GenerationParams();
        tooBig.setWidth(99999);
        tooBig.setHeight(99999);
        GenerationConfig big = tooBig.toConfig();
        assertEquals(5000, big.width(), "width must clamp to the 5000 sanity ceiling");
        assertEquals(5000, big.height(), "height must clamp to the 5000 sanity ceiling");

        GenerationParams tooSmall = new GenerationParams();
        tooSmall.setWidth(1);
        tooSmall.setHeight(1);
        GenerationConfig small = tooSmall.toConfig();
        assertEquals(10, small.width(), "width must clamp up to the 10-tile minimum");
        assertEquals(10, small.height(), "height must clamp up to the 10-tile minimum");
    }
}
