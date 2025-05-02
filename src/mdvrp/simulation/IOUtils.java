package mdvrp.simulation;

import mdvrp.model.Bloqueo;
import mdvrp.model.Pedido;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class IOUtils {

    public static List<Pedido> cargarPedidos(String a) throws Exception {
        System.out.println("Cargando pedidos desde: " + a);
        List<Pedido> p=new ArrayList<>();
        List<String> l= Files.readAllLines(Paths.get(a));
        for(String s:l){
            try{
                String[] p1=s.split(":");
                if(p1.length!=2)continue;
                int m= TiempoUtils.parsearMarcaDeTiempo(p1[0].trim());
                String[] d=p1[1].split(",");
                if(d.length!=5)continue;
                int x=Integer.parseInt(d[0].trim());
                int y=Integer.parseInt(d[1].trim());
                double v=Double.parseDouble(d[3].trim().replace("m3",""));
                int h=Integer.parseInt(d[4].trim().replace("h",""));
                p.add(new Pedido(x,y,v,h,m));
            } catch(Exception e)
            {
                System.err.println("Error P: "+s+" - "+e.getMessage());
            }
        }
        p.sort(Comparator.comparingInt(pd->pd.momentoPedido));
        System.out.println("Pedidos cargados: "+p.size());
        return p;
    }

    public static List<Bloqueo> cargarBloqueos(String a) throws Exception {
        System.out.println("Cargando bloqueos desde: " + a);
        List<Bloqueo> b=new ArrayList<>();
        List<String> l=Files.readAllLines(Paths.get(a));
        for(String s:l){
            try{
                String[] p1=s.split(":");
                if(p1.length!=2)continue;
                String[] t=p1[0].split("-");
                if(t.length!=2)continue;
                int i= TiempoUtils.parsearMarcaDeTiempo(t[0].trim());
                int f= TiempoUtils.parsearMarcaDeTiempo(t[1].trim());
                String[] c=p1[1].split(",");
                List<int[]> pts=new ArrayList<>();
                for(int k=0; k<c.length; k+=2){
                    pts.add(new int[]{Integer.parseInt(c[k].trim()),Integer.parseInt(c[k+1].trim())});
                }
                if(!pts.isEmpty()) b.add(new Bloqueo(i,f,pts));
            } catch(Exception e)
            {
                System.err.println("Error B: "+s+" - "+e.getMessage());
            }
        }
        System.out.println("Bloqueos cargados: "+b.size());
        return b;
    }

}
