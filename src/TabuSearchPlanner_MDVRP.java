import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

// BloqueoEtiquetado sin cambios...
class BloqueoEtiquetado {
    int x, y;
    String tramo;

    BloqueoEtiquetado(int x, int y, String tramo) {
        this.x = x;
        this.y = y;
        this.tramo = tramo;
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BloqueoEtiquetado that = (BloqueoEtiquetado) o;
        return x == that.x && y == that.y && Objects.equals(tramo, that.tramo);
    }
    @Override
    public int hashCode() {
        // Usa Objects.hash si usas Java 7+
        return Objects.hash(x, y, tramo);
        // Alternativa para Java < 7:
        // int result = x;
        // result = 31 * result + y;
        // result = 31 * result + (tramo != null ? tramo.hashCode() : 0);
        // return result;
    }
}


public class TabuSearchPlanner_MDVRP {
    // --- Constantes ---
    static final int GRID_WIDTH = 70;
    static final int GRID_HEIGHT = 50;
    static final double VELOCIDAD_KMH = 50.0;
    static final double MINUTOS_POR_KM = 60.0 / VELOCIDAD_KMH;

    // --- Clases del Modelo del Dominio ---

    static class Location {
        int x, y;
        Location(int x, int y) { this.x = x; this.y = y; }
        Point toPoint() { return new Point(x, y); }
        @Override public String toString() { return "(" + x + "," + y + ")"; }
        // NUEVO: equals y hashCode para Location (basado en coordenadas)
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Location location = (Location) o;
            return x == location.x && y == location.y;
        }
        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
        int manhattanDist(Location other) {
            return Math.abs(this.x - other.x) + Math.abs(this.y - other.y);
        }
    }

    static class Depot extends Location {
        String id;
        double capacidadActualM3;
        final double capacidadMaximaM3;
        Depot(String id, int x, int y, double capMax) {
            super(x, y);
            this.id = id;
            this.capacidadMaximaM3 = capMax;
            this.capacidadActualM3 = capMax;
        }
        @Override public String toString() { return "Depot[" + id + "@" + super.toString() + "]"; }
        // NUEVO: equals y hashCode para Depot (basado en id)
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Depot depot = (Depot) o;
            return Objects.equals(id, depot.id);
        }
        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    enum TruckType { /* ... sin cambios ... */
        TA(2.5, 25.0, 12.5), TB(2.0, 15.0, 7.5), TC(1.5, 10.0, 5.0), TD(1.0, 5.0, 2.5);
        final double taraTon; final double capacidadM3; final double pesoCargaMaxTon;
        TruckType(double t, double c, double p){taraTon=t; capacidadM3=c; pesoCargaMaxTon=p;}
        double calcularPesoCargaActualTon(double cargaM3){ if(capacidadM3==0) return 0; return (cargaM3/capacidadM3)*pesoCargaMaxTon;}
    }

    static class Truck {
        String id; TruckType type; Depot homeDepot; boolean disponible;
        Location currentLocation; double cargaActualM3;
        Truck(String id, TruckType type, Depot homeDepot) {
            this.id = id; this.type = type; this.homeDepot = homeDepot;
            this.disponible = true; this.currentLocation = homeDepot; this.cargaActualM3 = 0;
        }
        @Override public String toString() { return "Truck[" + id + "]"; }
        // NUEVO: equals y hashCode para Truck (basado en id)
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

    static class CustomerNode extends Location {
        int id; double demandaM3; int tiempoPedidoMinutos; int tiempoLimiteEntregaMinutos;
        boolean atendido = false; static int nextId = 0;
        CustomerNode(int x, int y, double d, int tP, int hL) {
            super(x, y); this.id = nextId++; this.demandaM3 = d;
            this.tiempoPedidoMinutos = tP; this.tiempoLimiteEntregaMinutos = tP + hL * 60;
        }
        @Override public String toString() { return "Cust[" + id + "@" + super.toString() + ", Dem:" + demandaM3 + ", Lim:" + tiempoLimiteEntregaMinutos + "]"; }
        // NUEVO: equals y hashCode para CustomerNode (basado en id)
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CustomerNode that = (CustomerNode) o;
            return id == that.id;
        }
        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    static class Route { /* ... sin cambios ... */
        Truck truck; Depot startDepot; List<CustomerNode> sequence; Depot endDepot;
        double cost = Double.POSITIVE_INFINITY; boolean feasible = false;
        Route(Truck t, Depot d){truck=t; startDepot=d; endDepot=d; sequence=new ArrayList<>();}
        @Override public String toString() {
            String path = sequence.stream().map(n -> String.valueOf(n.id)).collect(Collectors.joining("->"));
            return "Route[" + truck.id + ": " + startDepot.id + "->" + path + "->" + endDepot.id + "]";
        }
    }

    static class Solution { /* ... sin cambios ... */
        List<Route> routes; Set<CustomerNode> unassignedCustomers;
        double totalCost = Double.POSITIVE_INFINITY; boolean fullyFeasible = false;
        Solution(){routes=new ArrayList<>(); unassignedCustomers=new HashSet<>();}
        Solution(Solution other){ /* ... constructor de copia sin cambios ... */
            this.routes = new ArrayList<>();
            for(Route r : other.routes) {
                Route newR = new Route(r.truck, r.startDepot);
                newR.sequence = new ArrayList<>(r.sequence); // Copia profunda de secuencia
                newR.endDepot = r.endDepot; newR.cost = r.cost; newR.feasible = r.feasible;
                this.routes.add(newR);
            }
            this.unassignedCustomers = new HashSet<>(other.unassignedCustomers); // Copia set
            this.totalCost = other.totalCost; this.fullyFeasible = other.fullyFeasible;
        }
    }

    // --- Estado de la Simulación ---
    static boolean[][] bloqueado = new boolean[GRID_WIDTH][GRID_HEIGHT];
    static List<Depot> depots = new ArrayList<>();
    static List<Truck> fleet = new ArrayList<>();
    static List<CustomerNode> activeCustomers = new ArrayList<>();
    static List<Pedido> pendingPedidos;
    static List<Bloqueo> definedBloqueos;
    static int currentSimTime = 0;

    // --- Parámetros TS ---
    static final int MAX_ITERATIONS_TS = 200; // Aumentar un poco las iteraciones
    static final int TABU_TENURE = 15; // Un tenure unificado para todos los moves

    // --- Lista Tabú ---
    interface Move { } // Interfaz marcadora

    static class Move_2Opt implements Move { /* ... sin cambios ... */
        String truckId; int custIndex1, custIndex2;
        Move_2Opt(String tid, int i1, int i2){truckId=tid; custIndex1=Math.min(i1,i2); custIndex2=Math.max(i1,i2);}
        @Override public String toString() { return "2Opt(T:" + truckId + ",C:" + custIndex1 + "," + custIndex2 + ")"; }
        // NUEVO: equals y hashCode
        @Override public boolean equals(Object o){if(this==o) return true; if(o==null||getClass()!=o.getClass())return false; Move_2Opt m=(Move_2Opt)o; return custIndex1==m.custIndex1 && custIndex2==m.custIndex2 && Objects.equals(truckId,m.truckId);}
        @Override public int hashCode(){ return Objects.hash(truckId, custIndex1, custIndex2); }
    }

    // NUEVO: Movimiento de Reubicación (cliente de ruta A a ruta B)
    static class Move_Relocate implements Move {
        int customerId;
        String sourceTruckId;
        String destTruckId;
        // Podríamos añadir índices para un tabú más específico, pero esto es más simple:
        // Hacer tabú mover este cliente *desde* este camión por un tiempo.
        // O hacer tabú mover este cliente *hacia* el camión destino por un tiempo.
        // Vamos a implementar el tabú basado en (customerId, sourceTruckId) -> previene moverlo de nuevo pronto.
        // O (customerId, destTruckId) -> previene moverlo *a* ese camion de nuevo.
        // Probemos con (customerId, destTruckId) como tabú.

        Move_Relocate(int custId, String srcTid, String destTid) {
            this.customerId = custId;
            this.sourceTruckId = srcTid; // informativo
            this.destTruckId = destTid; // clave para tabú
        }
        @Override public String toString() { return "Reloc(C:" + customerId + ", From:" + sourceTruckId + ", To:" + destTruckId + ")"; }
        // NUEVO: equals y hashCode (basado en cliente y camión destino para tabú)
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Move_Relocate that = (Move_Relocate) o;
            return customerId == that.customerId && Objects.equals(destTruckId, that.destTruckId);
            // Alternativa: Basado en cliente y origen:
            // return customerId == that.customerId && Objects.equals(sourceTruckId, that.sourceTruckId);
        }
        @Override
        public int hashCode() {
            return Objects.hash(customerId, destTruckId);
            // Alternativa: return Objects.hash(customerId, sourceTruckId);
        }
    }

    static Queue<Move> tabuQueue = new LinkedList<>();
    static Set<Move> tabuSet = new HashSet<>();


    // --- Método Principal (Simulación) ---
    public static void main(String[] args) throws Exception { /* ... sin cambios ... */
        initializeSimulation();
        int tiempoFinal = 7 * 24 * 60; // Simulación de 1 semana
        while (currentSimTime <= tiempoFinal) {
            boolean newEvent = updateSimulationState(currentSimTime);
            // --- REPLANIFICACIÓN (OPCIONAL) ---
            if (newEvent && !activeCustomers.isEmpty()) {
                System.out.println("\n=== REPLANIFICANDO RUTAS en t=" + currentSimTime + " ===");
                Solution replannedSolution = planRoutesWithTabuSearch();
                if (replannedSolution != null) {
                    System.out.println("  (Replanificación completada, costo: " + formatCost(replannedSolution.totalCost) + ")");
                } else {
                    System.err.println("  (Replanificación falló o no fue necesaria)");
                }
            }
            //--- Info Horaria ---
            if (currentSimTime > 0 && currentSimTime % (60) == 0) { // Info cada hora
                System.out.println("--- Tiempo: " + (currentSimTime / (24*60)) + "d " +
                        ((currentSimTime % (24*60)) / 60) + "h " +
                        (currentSimTime % 60) + "m --- (" + activeCustomers.size() + " clientes activos)");
            }
            currentSimTime++;
        } // Fin While

        System.out.println("\n✅ Simulación finalizada en minuto " + (currentSimTime -1));
        // --- PLANIFICACIÓN FINAL ---
        Solution finalSolution = null;
        if (!activeCustomers.isEmpty()) {
            System.out.println("\n🚀 Planificación Final con Tabu Search... (" + activeCustomers.size() + " clientes)");
            finalSolution = planRoutesWithTabuSearch();
        } else {
            System.out.println("🤷 No hay clientes activos para planificar al final.");
        }
        // --- VISUALIZACIÓN FINAL ---
        if (finalSolution != null && !finalSolution.routes.isEmpty()) {
            System.out.println("\n📈 Mostrando visualización de la solución final...");
            visualizarSolucion(finalSolution);
        } else if (finalSolution != null && !finalSolution.unassignedCustomers.isEmpty()){
            System.out.println(" T_T No se pudieron asignar todas las rutas. Clientes sin asignar: " + finalSolution.unassignedCustomers.size());
            // Opcional: Mostrar mapa solo con nodos si se desea
            // visualizarSolucionSimple(finalSolution);
        } else {
            System.out.println(" T_T No hay solución final generada o rutas para visualizar.");
        }
        System.out.println("\nPrograma terminado.");
    }

    // --- Inicialización ---
    static void initializeSimulation() throws Exception { /* ... sin cambios ... */
        System.out.println("Inicializando simulación...");
        pendingPedidos = cargarPedidos("pedidos.txt");
        definedBloqueos = cargarBloqueos("bloqueos.txt");
        depots.add(new Depot("Planta", 12, 8, Double.POSITIVE_INFINITY));
        depots.add(new Depot("Norte", 42, 42, 160.0));
        depots.add(new Depot("Este", 63, 3, 160.0));
        System.out.println("Depósitos creados: " + depots.size());
        Depot mainDepot = depots.get(0); int count = 0;
        for (int i=1; i<= 2; i++) fleet.add(new Truck(String.format("TA%02d", i), TruckType.TA, mainDepot)); count+=2;
        for (int i=1; i<= 4; i++) fleet.add(new Truck(String.format("TB%02d", i), TruckType.TB, mainDepot)); count+=4;
        for (int i=1; i<= 4; i++) fleet.add(new Truck(String.format("TC%02d", i), TruckType.TC, mainDepot)); count+=4;
        for (int i=1; i<=10; i++) fleet.add(new Truck(String.format("TD%02d", i), TruckType.TD, mainDepot)); count+=10;
        System.out.println("Flota creada: " + count + " camiones.");
        bloqueado = new boolean[GRID_WIDTH][GRID_HEIGHT];
        activeCustomers.clear(); currentSimTime = 0;
        System.out.println("Inicialización completa.");
    }

    // --- Actualización del Estado ---
    static boolean updateSimulationState(int minutoActual) { /* ... sin cambios ... */
        boolean nuevoPedido = activateNewPedidos(minutoActual);
        boolean cambioBloqueo = updateBloqueos(minutoActual);
        if (minutoActual > 0 && minutoActual % (24 * 60) == 0) {
            for (Depot d : depots) {
                if (d.capacidadMaximaM3 != Double.POSITIVE_INFINITY) {
                    d.capacidadActualM3 = d.capacidadMaximaM3;
                }
            }
        }
        return nuevoPedido || cambioBloqueo;
    }

    // --- Activación/Actualización (Pedidos, Bloqueos) ---
    static boolean activateNewPedidos(int minutoActual) { /* ... sin cambios ... */
        boolean added = false; Iterator<Pedido> it = pendingPedidos.iterator();
        while(it.hasNext()){ Pedido p=it.next(); if(p.momentoPedido==minutoActual){ CustomerNode c=new CustomerNode(p.x,p.y,p.volumen,p.momentoPedido,p.horaLimite); activeCustomers.add(c); System.out.println("⏰ t="+minutoActual+" -> Nuevo Pedido ID:"+c.id+" en "+c+" recibido."); it.remove(); added=true;} else if(p.momentoPedido > minutoActual){ /* break; si ordenado */}} return added;
    }
    static boolean updateBloqueos(int minutoActual) { /* ... sin cambios ... */
        boolean changed = false; for(Bloqueo b:definedBloqueos){ if(b.inicioMinutos==minutoActual){changed=true; activateOrDeactivateBloqueo(b,true);} if(b.finMinutos==minutoActual){changed=true; activateOrDeactivateBloqueo(b,false);}} return changed;
    }
    static void activateOrDeactivateBloqueo(Bloqueo b, boolean activate) { /* ... sin cambios ... */
        for(int[] p:b.puntosBloqueados){int x=p[0],y=p[1]; if(x>=0&&x<GRID_WIDTH&&y>=0&&y<GRID_HEIGHT) bloqueado[x][y]=activate;}
    }

    // --- Planificación de Rutas con Búsqueda Tabú ---
    static Solution planRoutesWithTabuSearch() {
        if (activeCustomers.isEmpty()) {
            System.out.println("No hay clientes activos para planificar.");
            return null;
        }

        long startTime = System.currentTimeMillis();

        // MODIFICADO: Usar la nueva heurística inicial
        System.out.println("  Generando solución inicial con Best Fit...");
        Solution currentSolution = createInitialSolutionBestFit();
        if (currentSolution == null) {
            System.err.println("No se pudo generar una solución inicial.");
            return null;
        }
        if (currentSolution.routes.isEmpty() && !currentSolution.unassignedCustomers.isEmpty()) {
            System.err.println("Solución inicial no pudo asignar ningún cliente.");
            // Podríamos devolver null o intentar TS de todas formas si hay movimientos que asignen
            // Devolveremos null por ahora si no hay rutas iniciales
            return null;
        }

        evaluateSolution(currentSolution);
        Solution bestSolution = new Solution(currentSolution);

        System.out.println("  Solución Inicial | Costo: " + formatCost(bestSolution.totalCost) + " | Rutas: " + bestSolution.routes.size() + " | Sin Asignar: " + bestSolution.unassignedCustomers.size() + " | Factible: " + bestSolution.fullyFeasible);

        tabuQueue.clear();
        tabuSet.clear();

        // --- Bucle TS ---
        for (int iter = 0; iter < MAX_ITERATIONS_TS; iter++) {
            Solution bestNeighborOverall = null; // Mejor vecino encontrado en ESTA iteración (2opt o relocate)
            Move bestMoveOverall = null;         // Movimiento que llevó al mejor vecino
            double bestNeighborCostOverall = Double.POSITIVE_INFINITY;
            boolean bestMoveIsTabuOverall = false;

            // --- Vecindario 1: 2-Opt (Intra-Ruta) ---
            for (Route currentRoute : currentSolution.routes) {
                if (currentRoute.sequence.size() < 2) continue;

                for (int i = 0; i < currentRoute.sequence.size() - 1; i++) {
                    for (int j = i + 1; j < currentRoute.sequence.size(); j++) {
                        Move_2Opt move = new Move_2Opt(currentRoute.truck.id, i, j);
                        Solution neighborSolution = new Solution(currentSolution);
                        Route routeToModify = findRouteInSolution(neighborSolution, currentRoute.truck.id);

                        if (routeToModify != null) {
                            apply2OptIntraRoute(routeToModify, i, j);
                            evaluateSolution(neighborSolution); // Evaluar solución completa
                            boolean isTabu = tabuSet.contains(move);

                            // Comparar con el mejor vecino GLOBAL de esta iteración
                            if (neighborSolution.totalCost < bestNeighborCostOverall) {
                                bestNeighborCostOverall = neighborSolution.totalCost;
                                bestNeighborOverall = neighborSolution;
                                bestMoveOverall = move;
                                bestMoveIsTabuOverall = isTabu;
                            }
                        }
                    } // Fin for j
                } // Fin for i
            } // Fin for rutas (2-Opt)

            // --- Vecindario 2: Relocate (Inter-Ruta) ---
            // NUEVO: Generación de vecinos por Reubicación
            for (int routeIdxA = 0; routeIdxA < currentSolution.routes.size(); routeIdxA++) {
                Route routeA = currentSolution.routes.get(routeIdxA);
                if (routeA.sequence.isEmpty()) continue;

                // Iterar hacia atrás para evitar problemas con índices al quitar
                for (int custIdxA = routeA.sequence.size() - 1; custIdxA >= 0; custIdxA--) {
                    CustomerNode customerToMove = routeA.sequence.get(custIdxA);

                    for (int routeIdxB = 0; routeIdxB < currentSolution.routes.size(); routeIdxB++) {
                        if (routeIdxA == routeIdxB) continue; // No mover a la misma ruta
                        Route routeB = currentSolution.routes.get(routeIdxB);

                        // Intentar insertar en cada posición de routeB
                        for (int posB = 0; posB <= routeB.sequence.size(); posB++) {
                            // Simular movimiento en una copia
                            Solution neighborSolution = new Solution(currentSolution);
                            Route neighborRouteA = findRouteInSolution(neighborSolution, routeA.truck.id);
                            Route neighborRouteB = findRouteInSolution(neighborSolution, routeB.truck.id);

                            if(neighborRouteA == null || neighborRouteB == null) continue; // Seguridad

                            // 1. Verificar si la capacidad de B permite al nuevo cliente
                            double loadB_before = calculateRouteLoad(neighborRouteB);
                            if (loadB_before + customerToMove.demandaM3 > neighborRouteB.truck.type.capacidadM3) {
                                continue; // Salta esta inserción, excede capacidad de B
                            }

                            // 2. Realizar el movimiento en la copia
                            CustomerNode movedCustomer = neighborRouteA.sequence.remove(custIdxA);
                            neighborRouteB.sequence.add(posB, movedCustomer);

                            // 3. Evaluar la solución vecina
                            // ¡Importante! Evaluar *después* de hacer el movimiento
                            evaluateSolution(neighborSolution);

                            // 4. Crear el objeto Move y verificar Tabú
                            // Usamos (customerId, destTruckId) para el tabú
                            Move_Relocate move = new Move_Relocate(movedCustomer.id, routeA.truck.id, routeB.truck.id);
                            boolean isTabu = tabuSet.contains(move);

                            // 5. Comparar con el mejor vecino GLOBAL de la iteración
                            if (neighborSolution.totalCost < bestNeighborCostOverall) {
                                bestNeighborCostOverall = neighborSolution.totalCost;
                                bestNeighborOverall = neighborSolution;
                                bestMoveOverall = move;
                                bestMoveIsTabuOverall = isTabu;
                            }
                            // La copia (neighborSolution) se descarta automáticamente al final de esta iteración interna
                        } // Fin for posB
                    } // Fin for routeIdxB
                } // Fin for custIdxA
            } // Fin for routeIdxA (Relocate)

            // --- Selección del Siguiente Movimiento y Actualización ---
            if (bestNeighborOverall == null) {
                System.out.println("  Iter " + iter + ": No se encontraron vecinos válidos.");
                break; // Salir si no hay mejora posible
            }

            boolean moveChosen = false;
            // Aplicar Criterio de Aspiración
            if (bestMoveIsTabuOverall) {
                if (bestNeighborCostOverall < bestSolution.totalCost) { // Mejora al mejor global histórico?
                    currentSolution = bestNeighborOverall; // Aceptar por aspiración
                    moveChosen = true;
                    // System.out.println("  Iter " + iter + ": Aspiración! Mov. Tabú " + bestMoveOverall + " aceptado. Costo: " + formatCost(currentSolution.totalCost));
                } else {
                    moveChosen = false; // Es tabú y no aspira, no moverse (simplificado)
                    // Podríamos buscar el mejor NO tabú aquí si quisiéramos ser más completos
                }
            } else {
                // No es tabú, aceptar el mejor vecino de la iteración
                currentSolution = bestNeighborOverall;
                moveChosen = true;
            }

            // --- Actualizar Lista Tabú y Mejor Solución Global ---
            if (moveChosen) {
                // Añadir el movimiento ELEGIDO a la lista tabú
                tabuQueue.offer(bestMoveOverall); // Añadir el objeto Move (2Opt o Relocate)
                tabuSet.add(bestMoveOverall);
                // Mantener tamaño de la lista tabú ( Tenure unificado)
                while (tabuQueue.size() > TABU_TENURE) {
                    tabuSet.remove(tabuQueue.poll());
                }

                // Actualizar mejor solución global si la solución actual es mejor
                if (currentSolution.totalCost < bestSolution.totalCost) {
                    bestSolution = new Solution(currentSolution); // Clonar
                    System.out.println("  Iter " + iter + ": ✨ Nueva Mejor Solución Global! Costo: " + formatCost(bestSolution.totalCost) + " Sin Asignar: " + bestSolution.unassignedCustomers.size() + " Factible: " + bestSolution.fullyFeasible);
                }
            } // Fin if(moveChosen)

            // Imprimir progreso ocasionalmente
            if (iter > 0 && iter % 100 == 0) {
                System.out.println("  Iter " + iter + " | Costo Actual: " + formatCost(currentSolution.totalCost) + " | Mejor Costo: " + formatCost(bestSolution.totalCost) + " | Sin Asignar: " + bestSolution.unassignedCustomers.size());
            }

        } // Fin bucle TS

        long endTime = System.currentTimeMillis();
        // --- Imprimir Resumen Final ---
        System.out.println("\n🏁 Búsqueda Tabú (MDVRP con Relocate) completada en " + (endTime - startTime) + " ms.");
        System.out.println("🏆 Mejor solución encontrada:");
        System.out.println("  Costo Total (Combustible): " + formatCost(bestSolution.totalCost));
        System.out.println("  Totalmente Factible: " + bestSolution.fullyFeasible);
        System.out.println("  Clientes Sin Asignar: " + bestSolution.unassignedCustomers.size());
        System.out.println("  Rutas (" + bestSolution.routes.size() + "):");
        for(Route r : bestSolution.routes) {
            System.out.println("    " + r + " | Costo: " + formatCost(r.cost) + " | Factible: " + r.feasible);
        }

        return bestSolution;
    }


    // --- Formato de Costo ---
    static String formatCost(double cost) { /* ... sin cambios ... */
        if (cost == Double.POSITIVE_INFINITY) return "INF";
        return String.format("%.2f Gal", cost);
    }
    // --- Buscar Ruta en Solución ---
    static Route findRouteInSolution(Solution solution, String truckId) { /* ... sin cambios ... */
        for(Route r : solution.routes){ if(r.truck.id.equals(truckId)) return r; } return null;
    }
    // --- Aplicar 2-Opt ---
    static void apply2OptIntraRoute(Route route, int index1, int index2) { /* ... sin cambios ... */
        List<CustomerNode> seq = route.sequence; int start = Math.min(index1, index2); int end = Math.max(index1, index2);
        while(start < end){ CustomerNode temp = seq.get(start); seq.set(start, seq.get(end)); seq.set(end, temp); start++; end--;}
    }


    // --- Creación de Solución Inicial (Mejorada: Best Fit Insertion) ---
    // MODIFICADO: Reemplaza createInitialSolutionGreedy
    static Solution createInitialSolutionBestFit() {
        Solution initialSol = new Solution();
        // Empezar con todos los clientes activos como no asignados
        initialSol.unassignedCustomers.addAll(activeCustomers);

        // Crear rutas vacías iniciales para cada camión disponible
        for (Truck truck : fleet) {
            if (truck.disponible) {
                initialSol.routes.add(new Route(truck, truck.homeDepot));
            }
        }

        if (initialSol.routes.isEmpty()) {
            System.err.println("Error Inicial: No hay camiones disponibles.");
            return null;
        }

        int customersAssignedThisPass;
        do {
            customersAssignedThisPass = 0;
            CustomerNode bestCustomerToAssign = null;
            Route bestRouteForCustomer = null;
            int bestInsertionPos = -1;
            double minGlobalCostIncrease = Double.POSITIVE_INFINITY;

            // Iterar sobre TODOS los clientes sin asignar en cada pasada
            // (Podría ser costoso si hay muchos)
            List<CustomerNode> candidates = new ArrayList<>(initialSol.unassignedCustomers);

            for (CustomerNode customer : candidates) {
                // Buscar la MEJOR inserción posible para ESTE cliente en CUALQUIER ruta
                Route currentBestRoute = null;
                int currentBestPos = -1;
                double currentMinCostInc = Double.POSITIVE_INFINITY;

                for (Route route : initialSol.routes) {
                    for (int pos = 0; pos <= route.sequence.size(); pos++) {
                        // 1. Simular inserción
                        route.sequence.add(pos, customer);
                        // 2. Verificar capacidad RÁPIDAMENTE antes de evaluar costo completo
                        if (calculateRouteLoad(route) <= route.truck.type.capacidadM3) {
                            // 3. Evaluar costo y factibilidad (esta es la parte costosa)
                            double potentialCost = calculateRouteCost(route, currentSimTime); // O tiempo 0 si planificamos antes? Usar currentSimTime.
                            if (potentialCost < Double.POSITIVE_INFINITY) { // Si la ruta es factible con la inserción
                                // 4. Calcular incremento de costo
                                // Costo original: 0 si era infinito o vacía, sino el costo calculado previamente
                                double originalRouteCost = (route.cost == Double.POSITIVE_INFINITY || route.sequence.size() == 1) ? 0 : route.cost;
                                double costIncrease = potentialCost - originalRouteCost;

                                // 5. Actualizar la mejor inserción para ESTE cliente
                                if (costIncrease < currentMinCostInc) {
                                    currentMinCostInc = costIncrease;
                                    currentBestRoute = route;
                                    currentBestPos = pos;
                                }
                            }
                        }
                        // 6. Deshacer simulación
                        route.sequence.remove(pos);
                    } // Fin for posiciones
                } // Fin for rutas

                // Si encontramos la mejor inserción global para ESTE cliente,
                // la comparamos con la mejor inserción global encontrada HASTA AHORA
                if (currentBestRoute != null && currentMinCostInc < minGlobalCostIncrease) {
                    minGlobalCostIncrease = currentMinCostInc;
                    bestCustomerToAssign = customer;
                    bestRouteForCustomer = currentBestRoute;
                    bestInsertionPos = currentBestPos;
                }

            } // Fin for clientes candidatos

            // Si encontramos una mejor inserción global en esta pasada, realizarla
            if (bestCustomerToAssign != null) {
                bestRouteForCustomer.sequence.add(bestInsertionPos, bestCustomerToAssign);
                // Re-evaluar la ruta modificada para actualizar su costo y factibilidad
                evaluateRoute(bestRouteForCustomer, currentSimTime);
                initialSol.unassignedCustomers.remove(bestCustomerToAssign);
                customersAssignedThisPass++;
                System.out.println("  Init Sol: Asignado C" + bestCustomerToAssign.id + " a " + bestRouteForCustomer.truck.id + " (costo ruta: " + formatCost(bestRouteForCustomer.cost) + ")");
            }

        } while (customersAssignedThisPass > 0); // Repetir mientras podamos asignar clientes

        // Eliminar rutas que quedaron vacías al final
        initialSol.routes.removeIf(route -> route.sequence.isEmpty());

        return initialSol;
    }

    // --- Calcular Carga de Ruta ---
    static double calculateRouteLoad(Route route) { /* ... sin cambios ... */
        return route.sequence.stream().mapToDouble(c -> c.demandaM3).sum();
    }
    // --- Evaluación de Solución ---
    static void evaluateSolution(Solution solution) { /* ... sin cambios ... */
        solution.totalCost = 0; solution.fullyFeasible = true;
        if (solution.routes == null) { // Seguridad
            solution.totalCost = Double.POSITIVE_INFINITY;
            solution.fullyFeasible = false;
            return;
        }
        for(Route r : solution.routes){
            evaluateRoute(r, currentSimTime); // Usar tiempo actual para evaluar TW
            if(!r.feasible){ solution.fullyFeasible = false; }
            // Sumar costo incluso si no es factible (ya será infinito o muy alto)
            // Evitar sumar si es infinito para no obtener NaN
            if (r.cost != Double.POSITIVE_INFINITY) {
                if (solution.totalCost == Double.POSITIVE_INFINITY) {
                    solution.totalCost = r.cost; // Primer costo no infinito
                } else {
                    solution.totalCost += r.cost;
                }
            } else {
                solution.totalCost = Double.POSITIVE_INFINITY; // Si alguna ruta es INF, el total es INF
            }
        }
        if(!solution.unassignedCustomers.isEmpty()){
            solution.fullyFeasible = false;
            solution.totalCost = Double.POSITIVE_INFINITY; // Si hay no asignados, la solución NO es factible
            // solution.totalCost += solution.unassignedCustomers.size() * 1000000.0; // Alternativa penalización
        }
    }
    // --- Evaluación de Ruta ---
    static void evaluateRoute(Route route, int startTime) { /* ... sin cambios ... */
        route.cost = calculateRouteCost(route, startTime);
        route.feasible = (route.cost != Double.POSITIVE_INFINITY);
    }
    // --- Calcular Costo de Ruta ---
    static double calculateRouteCost(Route route, int startTime) { /* ... sin cambios ... */
        double totalFuelCost = 0; double currentLoadM3 = 0;
        // Verificar si la ruta o el camión son nulos (seguridad)
        if (route == null || route.truck == null || route.startDepot == null || route.endDepot == null || route.sequence == null) {
            return Double.POSITIVE_INFINITY;
        }
        int timeAtNode = startTime; Location prevLocation = route.startDepot;
        currentLoadM3 = calculateRouteLoad(route);
        if(currentLoadM3 > route.truck.type.capacidadM3) return Double.POSITIVE_INFINITY;

        for(CustomerNode customer : route.sequence) {
            int dist = distanciaReal(prevLocation, customer);
            if(dist == Integer.MAX_VALUE) return Double.POSITIVE_INFINITY; // Bloqueado
            totalFuelCost += calculateFuelConsumed(dist, currentLoadM3, route.truck);
            timeAtNode += (int)Math.round(dist * MINUTOS_POR_KM);
            if(timeAtNode > customer.tiempoLimiteEntregaMinutos) return Double.POSITIVE_INFINITY; // Tarde
            // Podríamos añadir tiempo de servicio aquí si fuera necesario: timeAtNode += T_SERVICIO;
            prevLocation = customer;
            currentLoadM3 -= customer.demandaM3; // Descargar
        }

        int distReturn = distanciaReal(prevLocation, route.endDepot);
        if(distReturn == Integer.MAX_VALUE) return Double.POSITIVE_INFINITY; // Bloqueado regreso
        totalFuelCost += calculateFuelConsumed(distReturn, 0.0, route.truck); // Regreso vacío
        // No se chequea tiempo de regreso al depot (podría añadirse si es necesario)

        return totalFuelCost;
    }
    // --- Calcular Combustible ---
    static double calculateFuelConsumed(int distanceKm, double currentLoadM3, Truck truck) { /* ... sin cambios ... */
        if(distanceKm == 0) return 0; double pesoCarga = truck.type.calcularPesoCargaActualTon(currentLoadM3);
        double pesoTotal = truck.type.taraTon + pesoCarga; return (double)distanceKm * pesoTotal / 180.0;
    }
    // --- Distancia Real BFS ---
    private static int distanciaReal(Location from, Location to) { /* ... sin cambios ... */
        if(from==null || to==null) return Integer.MAX_VALUE;
        if(from.x<0||from.x>=GRID_WIDTH||from.y<0||from.y>=GRID_HEIGHT) return Integer.MAX_VALUE;
        if(to.x<0||to.x>=GRID_WIDTH||to.y<0||to.y>=GRID_HEIGHT) return Integer.MAX_VALUE;
        Queue<int[]> queue=new LinkedList<>(); Map<Point, Integer> dists=new HashMap<>(); Set<Point> visited=new HashSet<>();
        Point start=from.toPoint(), end=to.toPoint(); if(start.equals(end)) return 0;
        queue.add(new int[]{from.x, from.y}); dists.put(start, 0); visited.add(start);
        int[][] DIRS={{0,1},{0,-1},{1,0},{-1,0}};
        while(!queue.isEmpty()){
            int[] curr=queue.poll(); Point pCurr=new Point(curr[0],curr[1]); int d=dists.get(pCurr);
            for(int[] dir : DIRS){
                int nx=curr[0]+dir[0], ny=curr[1]+dir[1]; Point pNext=new Point(nx, ny);
                if(pNext.equals(end)) return d+1;
                if(nx>=0&&nx<GRID_WIDTH&&ny>=0&&ny<GRID_HEIGHT&&!visited.contains(pNext)&&!bloqueado[nx][ny]){
                    visited.add(pNext); dists.put(pNext, d+1); queue.add(new int[]{nx, ny});
                }
            }
        }
        return Integer.MAX_VALUE;
    }
    // --- Carga de Datos ---
    static List<Pedido> cargarPedidos(String archivo) throws Exception { /* ... sin cambios ... */
        System.out.println("Cargando pedidos desde: " + archivo); List<Pedido> p=new ArrayList<>(); List<String> l=Files.readAllLines(Paths.get(archivo));
        for(String s:l){ try{ String[] p1=s.split(":"); if(p1.length!=2)continue; int m=TiempoUtils.parsearMarcaDeTiempo(p1[0].trim()); String[] d=p1[1].split(","); if(d.length!=5)continue; int x=Integer.parseInt(d[0].trim()); int y=Integer.parseInt(d[1].trim()); double v=Double.parseDouble(d[3].trim().replace("m3","")); int h=Integer.parseInt(d[4].trim().replace("h","")); p.add(new Pedido(x,y,v,h,m));} catch(Exception e){System.err.println("Error P: "+s+" - "+e.getMessage());}} p.sort(Comparator.comparingInt(pd->pd.momentoPedido)); System.out.println("Pedidos cargados: "+p.size()); return p;
    }
    static List<Bloqueo> cargarBloqueos(String archivo) throws Exception { /* ... sin cambios ... */
        System.out.println("Cargando bloqueos desde: " + archivo); List<Bloqueo> b=new ArrayList<>(); List<String> l=Files.readAllLines(Paths.get(archivo));
        for(String s:l){ try{ String[] p1=s.split(":"); if(p1.length!=2)continue; String[] t=p1[0].split("-"); if(t.length!=2)continue; int i=TiempoUtils.parsearMarcaDeTiempo(t[0].trim()); int f=TiempoUtils.parsearMarcaDeTiempo(t[1].trim()); String[] c=p1[1].split(","); List<int[]> pts=new ArrayList<>(); for(int k=0; k<c.length; k+=2){pts.add(new int[]{Integer.parseInt(c[k].trim()),Integer.parseInt(c[k+1].trim())});} if(!pts.isEmpty())b.add(new Bloqueo(i,f,pts));} catch(Exception e){System.err.println("Error B: "+s+" - "+e.getMessage());}} System.out.println("Bloqueos cargados: "+b.size()); return b;
    }
    // --- Visualización ---
    static void visualizarSolucion(Solution solution) { /* ... sin cambios ... */
        System.out.println("Preparando visualización...");
        List<GridVisualizer_MDVRP.Punto> cliVis=activeCustomers.stream().filter(c->!solution.unassignedCustomers.contains(c)).map(c->new GridVisualizer_MDVRP.Punto(c.x,c.y,GridVisualizer_MDVRP.PuntoTipo.CLIENTE,String.valueOf(c.id))).collect(Collectors.toList());
        List<GridVisualizer_MDVRP.Punto> depVis=depots.stream().map(d->new GridVisualizer_MDVRP.Punto(d.x,d.y,GridVisualizer_MDVRP.PuntoTipo.DEPOSITO,d.id)).collect(Collectors.toList());
        List<GridVisualizer_MDVRP.RutaVisual> rutVis=new ArrayList<>(); Color[] C={Color.BLUE,Color.RED,Color.ORANGE,Color.MAGENTA,Color.PINK,Color.YELLOW.darker(),Color.CYAN,Color.GRAY, Color.GREEN.darker().darker(), Color.BLUE.darker()}; int ci=0;
        for(Route r:solution.routes){ if(!r.sequence.isEmpty()){ List<GridVisualizer_MDVRP.Punto> seq=new ArrayList<>(); seq.add(new GridVisualizer_MDVRP.Punto(r.startDepot.x,r.startDepot.y,GridVisualizer_MDVRP.PuntoTipo.DEPOSITO,r.startDepot.id)); r.sequence.forEach(c->seq.add(new GridVisualizer_MDVRP.Punto(c.x,c.y,GridVisualizer_MDVRP.PuntoTipo.CLIENTE,String.valueOf(c.id)))); seq.add(new GridVisualizer_MDVRP.Punto(r.endDepot.x,r.endDepot.y,GridVisualizer_MDVRP.PuntoTipo.DEPOSITO,r.endDepot.id)); Color clr=C[ci%C.length]; ci++; rutVis.add(new GridVisualizer_MDVRP.RutaVisual(r.truck.id,seq,clr));}}
        SwingUtilities.invokeLater(()->{JFrame f=new JFrame("Visualización GLP - Rutas MDVRP (TS)"); f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); GridVisualizer_MDVRP p=new GridVisualizer_MDVRP(depVis,cliVis,rutVis,bloqueado); p.setPreferredSize(new Dimension(GRID_WIDTH*12,GRID_HEIGHT*12)); JScrollPane sp=new JScrollPane(p); f.add(sp); f.pack(); f.setSize(900,700); f.setLocationRelativeTo(null); f.setVisible(true);});
    }
    // --- Clases Internas Pedido/Bloqueo ---
    static class Pedido { /* ... sin cambios ... */ int x,y;double volumen;int horaLimite,momentoPedido; Pedido(int x,int y,double v,int h,int m){this.x=x;this.y=y;this.volumen=v;this.horaLimite=h;this.momentoPedido=m;}}
    static class Bloqueo { /* ... sin cambios ... */ int inicioMinutos,finMinutos;List<int[]> puntosBloqueados; Bloqueo(int i,int f,List<int[]> p){inicioMinutos=i;finMinutos=f;puntosBloqueados=p;}}
} // Fin clase TabuSearchPlanner_MDVRP