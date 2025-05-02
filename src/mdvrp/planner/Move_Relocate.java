package mdvrp.planner;

import java.util.Objects;

public class Move_Relocate implements Move {
    public int customerPartId; public String sourceTruckId; public String destTruckId;
    public Move_Relocate(int cpId, String src, String dest){
        customerPartId=cpId;
        sourceTruckId=src;
        destTruckId=dest;
    }
    @Override public String toString(){return "Reloc(CP:"+customerPartId+",From:"+sourceTruckId+",To:"+destTruckId+")";}
    @Override public boolean equals(Object o){
        if(this==o) return true;
        if(o==null||getClass()!=o.getClass()) return false;
        Move_Relocate m=(Move_Relocate)o;
        return customerPartId==m.customerPartId && Objects.equals(destTruckId,m.destTruckId);}
    @Override public int hashCode(){ return Objects.hash(customerPartId, destTruckId); }
}