package be.magycks.ragga.generation;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.util.concurrent.atomic.AtomicLong;

@Entity
public class Node {
    // variables
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long Id;
    private double x, y;
    private int degree;
    private double splitNumber;

    // constructors
    public Node() {}
    public Node(double x, double y, double splitNumber) {
        this.x = x;
        this.y = y;
        this.splitNumber = splitNumber;
    }

    // getters
    public long getId() {
        return Id;
    }
    public double getX() {
        return x;
    }
    public double getY() {
        return y;
    }
    public int getDegree() {
        return degree;
    }
    public double getSplitNumber() {
        return splitNumber;
    }

    // setters
    public void setDegree(int degree) {
        this.degree = degree;
    }
    public void setSplitNumber(double splitNumber) {this.splitNumber = splitNumber;}
    public void setId(long id) {
        Id = id;
    }
    public void setX(double x) {
        this.x = x;
    }
    public void setY(double y) {
        this.y = y;
    }

    @Override
    public String toString() {
        return "Node{" +
                "x=" + x +
                ", y=" + y +
                ", Id=" + Id +
                '}';
    }
}
