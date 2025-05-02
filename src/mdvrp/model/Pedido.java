package mdvrp.model;

public class Pedido {
    public int idCliente;
    public int x,y;
    public double volumen;
    public int horaLimite,momentoPedido;
    public Pedido(int x, int y, double v, int h, int m, int id){
        this.x=x;
        this.y=y;
        this.volumen=v;
        this.horaLimite=h;
        this.momentoPedido=m;
        this.idCliente=id;
    }
}