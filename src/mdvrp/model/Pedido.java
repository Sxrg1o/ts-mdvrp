package mdvrp.model;

public class Pedido {
    public int x,y;
    public double volumen;
    public int horaLimite,momentoPedido;
    public Pedido(int x, int y, double v, int h, int m){
        this.x=x;
        this.y=y;
        this.volumen=v;
        this.horaLimite=h;
        this.momentoPedido=m;
    }
}