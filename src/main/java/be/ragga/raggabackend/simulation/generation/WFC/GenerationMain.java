// GenerationMain.java
package be.ragga.raggabackend.simulation.generation.WFC;

import java.util.*;

public class GenerationMain {
    public static void main(String[] args) {
        Map<String, Tile> tiles = TileDefinition.createSimpleUrbanTileSet();

        // Option 1: Use specific seed for reproducible generation
        long seed = 678L; // Change this to get different cities
        boolean success = false;
        WaveFunctionCollapse wfc = new WaveFunctionCollapse(64, 40, tiles, seed);

        // Option 2: Random seed
        // WaveFunctionCollapse wfc = new WaveFunctionCollapse(64, 40, tiles);

        while (!success) {
            wfc = new WaveFunctionCollapse(64, 40, tiles, seed);
            System.out.println("Generating city with seed: " + wfc.getSeed());
            success = wfc.generate();
            seed = seed + 1;
        }


        if (success) {
            System.out.println("Generation successful!");
            System.out.println("Save this seed to regenerate: " + wfc.getSeed());
            CityVisualizer.display(wfc);
        } else {
            System.out.println("Generation failed - contradiction detected");
            System.out.println("Try a different seed");
        }
    }
}