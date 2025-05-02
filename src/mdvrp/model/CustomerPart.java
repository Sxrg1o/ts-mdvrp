package mdvrp.model;

import java.util.Objects;

public class CustomerPart extends Location {
    public int partId;          // ID único de esta parte
    public int originalOrderId; // ID para agrupar partes del mismo pedido original
    public double demandM3;     // Demanda de esta parte específica
    public int arrivalTimeMinutes;
    public int deadlineMinutes;
    public boolean served = false;

    public static int nextPartId = 0;

    public CustomerPart(int originalOrderId, int x, int y, double demand, int tArrival, int deadline) {
        super(x, y);
        this.partId = nextPartId++;
        this.originalOrderId = originalOrderId;
        this.demandM3 = demand;
        this.arrivalTimeMinutes = tArrival;
        this.deadlineMinutes = deadline;
    }
    @Override public String toString() {
        return "CustPart[" + partId + "(Orig:" + originalOrderId + ")@" + super.toString() + ", Dem:" + demandM3 + ", Lim:" + deadlineMinutes + "]";
    }
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomerPart that = (CustomerPart) o;
        return partId == that.partId;
    }
    @Override public int hashCode() {
        return Objects.hash(partId);
    }

}