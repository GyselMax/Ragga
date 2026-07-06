package be.ragga.raggabackend.simulation.grid.generation;

import be.ragga.raggabackend.simulation.grid.ZoneType;
import be.ragga.raggabackend.simulation.grid.lot.Direction;
import be.ragga.raggabackend.simulation.grid.road.RoadClass;

import java.util.EnumMap;
import java.util.Map;

/**
 * In-memory draft of a lot during generation. Mutable on purpose: the
 * pipeline stages progressively fill in zone, vacancy and building. Phase B
 * maps finished drafts onto Lot entities.
 *
 * A lot is always a solid axis-aligned rectangle: origin (x,y) is its
 * top-left cell, spanning width tiles along x and depth tiles along y.
 */
public class LotDraft {

    private final int x;
    private final int y;
    private final int width;
    private final int depth;
    private final Map<Direction, Frontage> frontages;

    private ZoneType zone;
    // Zones set by a hard rule (industrial quota, arterial commercial) are
    // locked so the smoothing pass can't overwrite them.
    private boolean zoneLocked;
    private boolean publicSite;
    private boolean vacant;
    private BuildingDraft building;

    public LotDraft(int x, int y, int width, int depth, Map<Direction, Frontage> frontages) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.depth = depth;
        this.frontages = new EnumMap<>(frontages);
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public int getDepth() {
        return depth;
    }

    public int area() {
        return width * depth;
    }

    public Map<Direction, Frontage> getFrontages() {
        return frontages;
    }

    public boolean hasFrontage(RoadClass roadClass) {
        return frontages.values().stream().anyMatch(f -> f.roadClass() == roadClass);
    }

    public ZoneType getZone() {
        return zone;
    }

    public void setZone(ZoneType zone) {
        this.zone = zone;
    }

    public boolean isZoneLocked() {
        return zoneLocked;
    }

    public void lockZone() {
        this.zoneLocked = true;
    }

    public boolean isPublicSite() {
        return publicSite;
    }

    public void setPublicSite(boolean publicSite) {
        this.publicSite = publicSite;
    }

    public boolean isVacant() {
        return vacant;
    }

    public void setVacant(boolean vacant) {
        this.vacant = vacant;
    }

    public BuildingDraft getBuilding() {
        return building;
    }

    public void setBuilding(BuildingDraft building) {
        this.building = building;
    }

    /** True when the two lots touch or are separated by at most `gap` tiles (e.g. a 1-wide road). */
    public boolean isNear(LotDraft other, int gap) {
        return this.x - gap <= other.x + other.width - 1
                && other.x - gap <= this.x + this.width - 1
                && this.y - gap <= other.y + other.depth - 1
                && other.y - gap <= this.y + this.depth - 1;
    }
}
