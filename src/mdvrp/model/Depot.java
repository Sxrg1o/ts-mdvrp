package mdvrp.model;

import java.util.Objects;

public class Depot extends Location {

    public String id;
    public double capacidadActualM3;
    public final double capacidadMaximaM3;
    public Depot(String id, int x, int y, double capMax) {
        super(x, y); this.id = id;
        this.capacidadMaximaM3 = capMax;
        this.capacidadActualM3 = capMax;
    }
    @Override public String toString() { return "Depot[" + id + "]"; }
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Depot depot = (Depot) o;
        return Objects.equals(id, depot.id);
    }
    @Override public int hashCode() { return Objects.hash(id); }
    public boolean isMainPlant() { return capacidadMaximaM3 == Double.POSITIVE_INFINITY;}

}
