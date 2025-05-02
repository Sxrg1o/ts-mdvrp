package mdvrp.model;

public enum TruckType {
    TA(2.5, 25.0, 12.5),
    TB(2.0, 15.0, 7.5),
    TC(1.5, 10.0, 5.0),
    TD(1.0, 5.0, 2.5);
    public final double taraTon; public final double capacidadM3; public final double pesoCargaMaxTon;
    TruckType(double t, double c, double p){taraTon=t; capacidadM3=c; pesoCargaMaxTon=p;}
    public double calcularPesoCargaActualTon(double cargaM3){
        if(capacidadM3==0) return 0;
        return Math.min(1.0, cargaM3 / capacidadM3) * pesoCargaMaxTon;
    }
}