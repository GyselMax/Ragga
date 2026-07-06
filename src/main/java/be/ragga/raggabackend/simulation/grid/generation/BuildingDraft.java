package be.ragga.raggabackend.simulation.grid.generation;

import be.ragga.raggabackend.simulation.grid.lot.Direction;

/**
 * In-memory placed building: which template was stamped, where its top-left
 * corner sits, its effective (possibly rotated) footprint, and which way its
 * front faces. sizeX/sizeY differ from the template's canonical width/depth
 * when the building was rotated 90/270 degrees to face its road.
 */
public record BuildingDraft(TemplateSpec template, int x, int y, int sizeX, int sizeY, Direction facing) {
}
