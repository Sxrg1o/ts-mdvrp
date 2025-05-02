package mdvrp.planner;

import java.util.Objects;

public class Move_2Opt implements Move {
    public String truckId;
    public int custIdx1, custIdx2;
    public Move_2Opt(String t, int i1, int i2){
        truckId=t;
        custIdx1=Math.min(i1,i2);
        custIdx2=Math.max(i1,i2);
    }
    @Override public String toString(){return "2Opt(T:"+truckId+",P:"+custIdx1+","+custIdx2+")";}
    @Override public boolean equals(Object o){
        if(this==o) return true;
        if(o==null||getClass()!=o.getClass()) return false;
        Move_2Opt m=(Move_2Opt)o;
        return custIdx1==m.custIdx1 && custIdx2==m.custIdx2 && Objects.equals(truckId, m.truckId);}
    @Override public int hashCode(){ return Objects.hash(truckId, custIdx1, custIdx2); }
}