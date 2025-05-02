package mdvrp.model;

import java.util.Objects;

public class Truck {
    public String id;
    public TruckType type;
    public Depot homeDepot;
    public boolean disponible;
    public Location currentLocation;
    public double cargaActualM3;
    public Truck(String id, TruckType type, Depot homeDepot) {
        this.id = id; this.type = type; this.homeDepot = homeDepot;
        this.disponible = true; this.currentLocation = homeDepot; this.cargaActualM3 = 0;
    }
    @Override public String toString() { return "Truck[" + id + "]"; }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Truck truck = (Truck) o;
        return Objects.equals(id, truck.id);
    }
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}