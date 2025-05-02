package mdvrp.model;

import java.util.List;

public class Bloqueo {
    public int inicioMinutos,finMinutos;
    public List<int[]> puntosBloqueados;
    public Bloqueo(int i,int f,List<int[]> p){
        inicioMinutos=i;finMinutos=f;puntosBloqueados=p;
    }
}
