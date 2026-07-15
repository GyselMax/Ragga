package be.ragga.raggabackend.simulation.building;

import be.ragga.raggabackend.simulation.owner.Owner;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class Building {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    // Economics filled by the market-clearing pass (null until then). For a
    // residential building these are PER-DWELLING figures: `price` is one
    // dwelling's cleared market value and `rent` its monthly rent, so a
    // subdivided block of `householdCapacity` dwellings is worth
    // price x householdCapacity and grosses rent x occupiedDwellings for its
    // owner. `desirability` is the hedonic quality score Q in [0,1].
    private BigDecimal price;
    private BigDecimal rent;
    private BigDecimal desirability;

    // Who holds title to this building, as a mutable relationship (see Owner):
    // a Household (owner-occupier, or a private landlord who holds several), a
    // Company (commercial/industrial/agrarian stock and rental residential
    // blocks), or later a player. Null means no modelled owner holds title - an
    // off-model institutional owner, or simply vacant/unclaimed stock.
    @ManyToOne
    @JoinColumn(name = "owner_id")
    private Owner owner;

    private int sizeX;
    private int sizeY;

    // Vertical extent in floors, copied from the blueprint at creation time.
    // Footprint (sizeX/sizeY) rotates with placement; floors never do.
    private int floors;

    // Authored blueprint prestige, valid range 1..5, common to every building
    // function (a tier-5 villa, boutique, or estate farm all outclass a tier-1
    // one at equal location). Feeds the structural term of market valuation.
    // Out-of-range inputs (e.g. a 0 from a catalog row not yet backfilled) are
    // clamped rather than rejected: unlike capacity/floors this never causes a
    // division, so a soft fallback beats crashing generation.
    public static final int MIN_QUALITY_TIER = 1;
    public static final int MAX_QUALITY_TIER = 5;
    private int qualityTier;

    protected Building() {
    }

    protected Building(int sizeX, int sizeY, int floors, int qualityTier) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.floors = floors;
        this.qualityTier = Math.clamp(qualityTier, MIN_QUALITY_TIER, MAX_QUALITY_TIER);
    }

    public long getId() {
        return id;
    }
    public BigDecimal getPrice() {
        return price;
    }
    public void setPrice(BigDecimal price) {
        this.price = price;
    }
    public BigDecimal getRent() {
        return rent;
    }
    public void setRent(BigDecimal rent) {
        this.rent = rent;
    }
    public BigDecimal getDesirability() {
        return desirability;
    }
    public void setDesirability(BigDecimal desirability) {
        this.desirability = desirability;
    }
    public Owner getOwner() {
        return owner;
    }
    public void setOwner(Owner owner) {
        this.owner = owner;
    }
    public int getSizeX() {
        return sizeX;
    }
    public int getSizeY() {
        return sizeY;
    }
    public int getFloors() {
        return floors;
    }
    public int getQualityTier() {
        return qualityTier;
    }
}
