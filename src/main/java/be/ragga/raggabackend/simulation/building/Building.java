package be.ragga.raggabackend.simulation.building;

import java.math.BigDecimal;

abstract public class Building{
    BigDecimal price;
    BigDecimal rent;
    BigDecimal desirability;
    int[] size = new int[2];
}
