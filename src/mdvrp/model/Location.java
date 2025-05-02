package mdvrp.model;

import java.awt.*;
import java.util.Objects;

import static mdvrp.simulation.SimulationUtils.distanciaReal;

public class Location {

    public int x, y;
    public Location(int x, int y) { this.x = x; this.y = y; }
    public Point toPoint() { return new Point(x, y); }
    @Override public String toString() { return "(" + x + "," + y + ")"; }
    @Override public boolean equals(Object o) { if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Location loc = (Location) o; return x == loc.x && y == loc.y; }
    @Override public int hashCode() { return Objects.hash(x, y); }
    public int distanceTo(Location other) { return distanciaReal(this, other); }

}
