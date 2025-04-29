import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

class BloqueoEtiquetado {
    int x, y;
    String tramo;

    BloqueoEtiquetado(int x, int y, String tramo) {
        this.x = x;
        this.y = y;
        this.tramo = tramo;
    }
    // Necesario para usar en HashSet en TabuList
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BloqueoEtiquetado that = (BloqueoEtiquetado) o;
        return x == that.x && y == that.y && Objects.equals(tramo, that.tramo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, tramo);
    }
}

public class TabuSearchPlanner_MDVRP {
    static final int GRID_WIDTH = 70;
    static final int GRID_HEIGHT = 50;
    static final double VELOCIDAD_KMH = 50.0;
    static final double MINUTOS_POR_KM = 60.0 / VELOCIDAD_KMH;

    // --- Clases del Modelo del Dominio ---

    // Nodo Base (Coordenadas)
    static class Location {
        int x, y;
        Location(int x, int y) { this.x = x; this.y = y; }
        Point toPoint() { return new Point(x, y); }
        @Override public String toString() { return "(" + x + "," + y + ")"; }

        // Distancia Manhattan (simple, usar BFS para la real)
        int manhattanDist(Location other) {
            return Math.abs(this.x - other.x) + Math.abs(this.y - other.y);
        }
    }

    // Depósitos (Planta y Tanques Intermedios)
    static class Depot extends Location {
        String id;
        double capacidadActualM3; // Relevante para intermedios al inicio del día
        final double capacidadMaximaM3;

        Depot(String id, int x, int y, double capMax) {
            super(x, y);
            this.id = id;
            this.capacidadMaximaM3 = capMax;
            this.capacidadActualM3 = capMax; // Se llena a las 00:00
        }
        @Override public String toString() { return "Depot[" + id + "@" + super.toString() + "]"; }
    }

    // Tipos de Camión (Datos de la tabla)
    enum TruckType {
        TA(2.5, 25.0, 12.5), // Tara (Ton), Capacidad (m³), Peso Carga Max GLP (Ton)
        TB(2.0, 15.0, 7.5),
        TC(1.5, 10.0, 5.0),
        TD(1.0, 5.0, 2.5);

        final double taraTon;
        final double capacidadM3;
        final double pesoCargaMaxTon;

        TruckType(double tara, double cap, double pesoCarga) {
            this.taraTon = tara;
            this.capacidadM3 = cap;
            this.pesoCargaMaxTon = pesoCarga;
        }

        // Calcula el peso de una carga específica en Toneladas
        double calcularPesoCargaActualTon(double cargaActualM3) {
            if (capacidadM3 == 0) return 0;
            // Proporción: (cargaActualM3 / capacidadM3) * pesoCargaMaxTon
            return (cargaActualM3 / capacidadM3) * pesoCargaMaxTon;
        }
    }

    // Unidad de Camión Específica
    static class Truck {
        String id; // e.g., "TA01"
        TruckType type;
        Depot homeDepot; // Depósito base asignado
        boolean disponible; // Para futuro mantenimiento/averías
        // Estado durante planificación/simulación (podría ir en una clase separada de estado)
        Location currentLocation;
        double cargaActualM3;

        Truck(String id, TruckType type, Depot homeDepot) {
            this.id = id;
            this.type = type;
            this.homeDepot = homeDepot;
            this.disponible = true; // Inicialmente disponible
            this.currentLocation = homeDepot; // Empieza en su depósito
            this.cargaActualM3 = 0; // Empieza vacío
        }
        @Override public String toString() { return "Truck[" + id + "]"; }
    }

    // Nodos Cliente (Pedidos)
    static class CustomerNode extends Location {
        int id; // ID único del pedido/nodo
        double demandaM3;
        int tiempoPedidoMinutos; // Momento en que se hizo el pedido (minutos desde inicio sim)
        int tiempoLimiteEntregaMinutos; // Momento límite absoluto para la entrega (minutos desde inicio sim)
        boolean atendido = false;

        static int nextId = 0;

        CustomerNode(int x, int y, double demanda, int tPedido, int hLimite) {
            super(x, y);
            this.id = nextId++;
            this.demandaM3 = demanda;
            this.tiempoPedidoMinutos = tPedido;
            // Tiempo límite absoluto = tiempo pedido + horas límite convertidas a minutos
            this.tiempoLimiteEntregaMinutos = tPedido + hLimite * 60;
        }
        @Override public String toString() { return "Cust[" + id + "@" + super.toString() + ", Dem:" + demandaM3 + ", Lim:" + tiempoLimiteEntregaMinutos + "]"; }
    }

    // Ruta: Secuencia de nodos para un camión específico
    static class Route {
        Truck truck;
        Depot startDepot;
        List<CustomerNode> sequence; // Lista ordenada de clientes a visitar
        Depot endDepot;
        double cost = Double.POSITIVE_INFINITY; // Costo (combustible) de esta ruta
        boolean feasible = false; // Indica si cumple capacidad y ventanas de tiempo

        Route(Truck truck, Depot depot) {
            this.truck = truck;
            this.startDepot = depot;
            this.endDepot = depot; // Asume regreso al mismo depot
            this.sequence = new ArrayList<>();
        }
        @Override public String toString() {
            String path = sequence.stream().map(n -> String.valueOf(n.id)).collect(Collectors.joining("->"));
            return "Route[" + truck.id + ": " + startDepot.id + " -> " + path + " -> " + endDepot.id + "]";
        }
    }

    // Solución: Conjunto de rutas que cubren los pedidos
    static class Solution {
        List<Route> routes;
        Set<CustomerNode> unassignedCustomers; // Clientes aún no asignados a ninguna ruta
        double totalCost = Double.POSITIVE_INFINITY;
        boolean fullyFeasible = false;

        Solution() {
            this.routes = new ArrayList<>();
            this.unassignedCustomers = new HashSet<>();
        }
        // Constructor para clonar soluciones
        Solution(Solution other) {
            this.routes = new ArrayList<>();
            for(Route r : other.routes) {
                Route newR = new Route(r.truck, r.startDepot);
                newR.sequence = new ArrayList<>(r.sequence);
                newR.endDepot = r.endDepot;
                newR.cost = r.cost;
                newR.feasible = r.feasible;
                this.routes.add(newR);
            }
            this.unassignedCustomers = new HashSet<>(other.unassignedCustomers);
            this.totalCost = other.totalCost;
            this.fullyFeasible = other.fullyFeasible;
        }
    }


    // --- Estado de la Simulación ---
    static boolean[][] bloqueado = new boolean[GRID_WIDTH][GRID_HEIGHT];
    static List<Depot> depots = new ArrayList<>();
    static List<Truck> fleet = new ArrayList<>();
    static List<CustomerNode> activeCustomers = new ArrayList<>(); // Clientes activos (pedidos recibidos, no necesariamente asignados)
    static List<Pedido> pendingPedidos; // Pedidos del archivo aún no activados por tiempo
    static List<Bloqueo> definedBloqueos;
    static int currentSimTime = 0; // Tiempo actual en minutos

    // --- Parámetros TS (Ajustar) ---
    static final int MAX_ITERATIONS_TS = 500; // Reducido para prueba inicial
    static final int TABU_TENURE_2OPT = 7;
    // static final int TABU_TENURE_RELOCATE = 10; // Si se implementa

    // --- Estructura Lista Tabú (Adaptable a diferentes movimientos) ---
    interface Move { } // Interfaz marcadora

    // Movimiento 2-opt DENTRO de una ruta
    static class Move_2Opt implements Move {
        String truckId;
        int custIndex1, custIndex2; // Índices en la *secuencia de clientes* de la ruta

        Move_2Opt(String tid, int i1, int i2) {
            this.truckId = tid;
            this.custIndex1 = Math.min(i1, i2);
            this.custIndex2 = Math.max(i1, i2);
        }
        @Override public String toString() { return "2Opt(T:" + truckId + ", C:" + custIndex1 + "," + custIndex2 + ")"; }
    }

    // (Futuro) Movimiento de Reubicación (cliente de ruta A a ruta B)
    // static class Move_Relocate implements Move { ... }

    static Queue<Move> tabuQueue = new LinkedList<>();
    static Set<Move> tabuSet = new HashSet<>();


    // --- Método Principal (Simulación) ---
    public static void main(String[] args) throws Exception {
        initializeSimulation();

        int tiempoFinal = 7 * 24 * 60; // Simulación de 1 semana para probar

        while (currentSimTime <= tiempoFinal) {

            boolean newEvent = updateSimulationState(currentSimTime);

            // --- REPLANIFICACIÓN ---
            // ¿Cuándo replanificar?
            // 1. Al llegar nuevos pedidos significativos?
            // 2. Periódicamente (ej. cada hora)?
            // 3. Cuando ocurre un evento disruptivo (avería - no implementado)?
            // Por simplicidad, planificaremos al final o cuando haya nuevos pedidos.
            if (newEvent && !activeCustomers.isEmpty()) {
                System.out.println("\n=== REPLANIFICANDO RUTAS en t=" + currentSimTime + " ===");
                Solution replannedSolution = planRoutesWithTabuSearch();
                // Aquí podríamos actualizar el estado de los camiones según las nuevas rutas
                // y avanzar la simulación de forma más detallada.
                if (replannedSolution != null) {
                    System.out.println("  (Replanificación completada, costo: " + formatCost(replannedSolution.totalCost) + ")");
                } else {
                    System.err.println("  (Replanificación falló o no fue necesaria)");
                }
            }

            if (currentSimTime % 60 == 0) { // Info cada hora
                System.out.println("--- Tiempo: " + (currentSimTime / (24*60)) + "d " +
                        ((currentSimTime % (24*60)) / 60) + "h " +
                        (currentSimTime % 60) + "m ---");
            }

            // Pausa opcional
            // Thread.sleep(10);

            currentSimTime++;
        }

        System.out.println("\n✅ Simulación finalizada en minuto " + currentSimTime);
        // Planificación final si no se hizo antes
        Solution finalSolution = null;
        if (!activeCustomers.isEmpty()) {
            System.out.println("\n🚀 Planificación Final con Tabu Search...");
            finalSolution = planRoutesWithTabuSearch();
        } else {
            System.out.println("🤷 No hay clientes activos para planificar.");
        }

        if (finalSolution != null && !finalSolution.routes.isEmpty()) { // Asegurarse de que hay algo que mostrar
            System.out.println("\n📈 Mostrando visualización de la solución final...");
            visualizarSolucion(finalSolution); // <<-- ÚNICA LLAMADA A VISUALIZACIÓN
        } else if (finalSolution != null && finalSolution.routes.isEmpty() && !finalSolution.unassignedCustomers.isEmpty()){
            System.out.println(" T_T No se pudieron asignar rutas a los clientes activos. No se muestra visualización de rutas.");
            // visualizarSolucionSimple(finalSolution); // Una versión que solo muestre nodos
        }
        else {
            System.out.println(" T_T No hay solución final generada para visualizar.");
        }
    }

    // --- Inicialización ---
    static void initializeSimulation() throws Exception {
        System.out.println("Inicializando simulación...");
        // 1. Cargar Datos
        pendingPedidos = cargarPedidos("pedidos.txt"); // Usar formato ventas2025mm
        definedBloqueos = cargarBloqueos("bloqueos.txt"); // Usar formato aaaamm.bloqueadas

        // 2. Crear Depósitos
        depots.add(new Depot("Planta", 12, 8, Double.POSITIVE_INFINITY)); // Capacidad infinita
        depots.add(new Depot("Norte", 42, 42, 160.0));
        depots.add(new Depot("Este", 63, 3, 160.0));
        System.out.println("Depósitos creados: " + depots.size());

        // 3. Crear Flota de Camiones
        // Asignar un depósito base (ej. Planta principal para todos inicialmente)
        Depot mainDepot = depots.get(0);
        int count = 0;
        for (int i = 1; i <= 2; i++) fleet.add(new Truck(String.format("TA%02d", i), TruckType.TA, mainDepot)); count+=2;
        for (int i = 1; i <= 4; i++) fleet.add(new Truck(String.format("TB%02d", i), TruckType.TB, mainDepot)); count+=4;
        for (int i = 1; i <= 4; i++) fleet.add(new Truck(String.format("TC%02d", i), TruckType.TC, mainDepot)); count+=4;
        for (int i = 1; i <= 10; i++) fleet.add(new Truck(String.format("TD%02d", i), TruckType.TD, mainDepot)); count+=10;
        System.out.println("Flota creada: " + count + " camiones.");

        // 4. Inicializar estado
        bloqueado = new boolean[GRID_WIDTH][GRID_HEIGHT];
        activeCustomers.clear();
        currentSimTime = 0;

        System.out.println("Inicialización completa.");
    }

    // --- Actualización del Estado en cada Minuto ---
    static boolean updateSimulationState(int minutoActual) {
        boolean nuevoPedido = activateNewPedidos(minutoActual);
        boolean cambioBloqueo = updateBloqueos(minutoActual);
        // Aquí iría la lógica de reabastecimiento de depósitos a las 00:00,
        // chequeo de mantenimientos/averías (futuro), etc.
        if (minutoActual % (24 * 60) == 0) { // A las 00:00
            for (Depot d : depots) {
                if (d.capacidadMaximaM3 != Double.POSITIVE_INFINITY) {
                    d.capacidadActualM3 = d.capacidadMaximaM3; // Rellenar intermedios
                    // System.out.println("Depot " + d.id + " reabastecido.");
                }
            }
        }

        return nuevoPedido || cambioBloqueo; // Indica si ocurrió un evento relevante
    }

    // --- Métodos de Activación/Actualización (Adaptados) ---
    static boolean activateNewPedidos(int minutoActual) {
        boolean added = false;
        Iterator<Pedido> it = pendingPedidos.iterator();
        while (it.hasNext()) {
            Pedido p = it.next();
            if (p.momentoPedido == minutoActual) {
                CustomerNode customer = new CustomerNode(p.x, p.y, p.volumen, p.momentoPedido, p.horaLimite);
                activeCustomers.add(customer);
                System.out.println("⏰ t=" + minutoActual + " -> Nuevo Pedido ID:" + customer.id + " en " + customer + " recibido.");
                it.remove();
                added = true;
            } else if (p.momentoPedido > minutoActual) {
                // Los pedidos están ordenados por tiempo, podemos parar de buscar
                // break; // Descomentar si se garantiza orden en el archivo
            }
        }
        return added;
    }

    static boolean updateBloqueos(int minutoActual) {
        boolean changed = false;
        for (Bloqueo b : definedBloqueos) {
            if (b.inicioMinutos == minutoActual) {
                changed = true;
                // System.out.print("🚧 Bloqueo Activado t="+minutoActual+": ");
                activateOrDeactivateBloqueo(b, true);
            }
            if (b.finMinutos == minutoActual) {
                changed = true;
                // System.out.print("✅ Bloqueo Desactivado t="+minutoActual+": ");
                activateOrDeactivateBloqueo(b, false);
            }
        }
        // if (changed) System.out.println();
        return changed;
    }

    static void activateOrDeactivateBloqueo(Bloqueo b, boolean activate) {
        // Los bloqueos son NODOS (esquinas)
        for (int[] punto : b.puntosBloqueados) {
            int x = punto[0];
            int y = punto[1];
            if (x >= 0 && x < GRID_WIDTH && y >= 0 && y < GRID_HEIGHT) {
                bloqueado[x][y] = activate;
                // System.out.print("("+x+","+y+") ");
            }
        }
    }

    // --- Planificación de Rutas con Búsqueda Tabú (MDVRP Simplificado) ---
    static Solution planRoutesWithTabuSearch() {
        if (activeCustomers.isEmpty()) {
            System.out.println("No hay clientes activos para planificar.");
            return null;
        }

        long startTime = System.currentTimeMillis();

        // 1. Crear Solución Inicial (Greedy - muy simple)
        Solution currentSolution = createInitialSolutionGreedy();
        if (currentSolution == null || currentSolution.routes.isEmpty()) {
            System.err.println("No se pudo generar una solución inicial factible.");
            return null;
        }

        evaluateSolution(currentSolution); // Calcula costo y factibilidad inicial
        Solution bestSolution = new Solution(currentSolution); // Clonar

        System.out.println("  Solución Inicial | Costo: " + formatCost(bestSolution.totalCost) + " | Rutas: " + bestSolution.routes.size() + " | Factible: " + bestSolution.fullyFeasible);

        // Limpiar lista Tabú
        tabuQueue.clear();
        tabuSet.clear();

        // 2. Bucle TS
        for (int iter = 0; iter < MAX_ITERATIONS_TS; iter++) {
            Solution bestNeighborSolution = null;
            Move bestNeighborMove = null;
            double bestNeighborCost = Double.POSITIVE_INFINITY;
            boolean bestNeighborIsTabu = false;

            // 3. Generar Vecindario (Solo 2-Opt intra-ruta por ahora)
            for (Route currentRoute : currentSolution.routes) {
                if (currentRoute.sequence.size() < 2) continue; // Necesita al menos 2 clientes para 2-opt

                for (int i = 0; i < currentRoute.sequence.size() -1; i++) {
                    for (int j = i + 1; j < currentRoute.sequence.size(); j++) {
                        // Crear movimiento
                        Move_2Opt move = new Move_2Opt(currentRoute.truck.id, i, j);

                        // Crear copia de la solución actual para modificarla
                        Solution neighborSolution = new Solution(currentSolution);
                        // Encontrar la ruta correspondiente en la copia
                        Route routeToModify = findRouteInSolution(neighborSolution, currentRoute.truck.id);

                        if (routeToModify != null) {
                            // Aplicar 2-opt en la copia
                            apply2OptIntraRoute(routeToModify, i, j);

                            // Evaluar la solución vecina completa
                            evaluateSolution(neighborSolution); // Recalcula costo y factibilidad

                            boolean isTabu = tabuSet.contains(move);

                            // Actualizar mejor vecino de *esta iteración*
                            if (neighborSolution.totalCost < bestNeighborCost) {
                                bestNeighborCost = neighborSolution.totalCost;
                                bestNeighborSolution = neighborSolution; // Guardar copia
                                bestNeighborMove = move;
                                bestNeighborIsTabu = isTabu;
                            }
                        }
                    }
                }
            }
            // --- (Aquí se añadiría la generación de vecinos con otros movimientos como Relocate) ---


            // 4. Seleccionar Siguiente Movimiento y Actualizar
            if (bestNeighborSolution == null) {
                // System.out.println("Iter " + iter + ": No se encontraron vecinos (¿rutas muy cortas?).");
                break; // Salir si no hay vecinos
            }

            boolean moveChosen = false;
            // Criterio de Aspiración: Si es tabú PERO mejora la MEJOR global, se acepta
            if (bestNeighborIsTabu) {
                if (bestNeighborCost < bestSolution.totalCost) {
                    currentSolution = bestNeighborSolution; // Aceptar el vecino tabú
                    // System.out.println("  Iter " + iter + ": Aspiración! Mov. Tabú " + bestNeighborMove + " aceptado. Costo: " + formatCost(currentSolution.totalCost));
                    moveChosen = true;
                }
                // Si es tabú y no mejora global, NO se elige (necesitaríamos buscar el mejor NO tabú)
                else {
                    // Lógica para encontrar el mejor NO tabú (omitida por simplicidad ahora)
                    // Si no hay no-tabú, podríamos parar o quedarnos.
                    // System.out.println("  Iter " + iter + ": Mejor vecino es Tabú y no aspira. Buscando alternativas...");
                    // Por ahora, si el mejor es tabú y no aspira, no nos movemos.
                    moveChosen = false;

                }

            } else {
                // Si el mejor vecino no es tabú, lo aceptamos
                currentSolution = bestNeighborSolution;
                moveChosen = true;
            }


            // 5. Actualizar Lista Tabú y Mejor Solución Global
            if (moveChosen) {
                // Añadir el *movimiento* a la lista tabú
                tabuQueue.offer(bestNeighborMove);
                tabuSet.add(bestNeighborMove);
                while (tabuQueue.size() > TABU_TENURE_2OPT) { // Adaptar si hay más tipos de moves
                    tabuSet.remove(tabuQueue.poll());
                }

                // Actualizar mejor solución global si es necesario
                if (currentSolution.totalCost < bestSolution.totalCost) {
                    bestSolution = new Solution(currentSolution); // Clonar la nueva mejor
                    System.out.println("  Iter " + iter + ": ✨ Nueva Mejor Solución Global! Costo: " + formatCost(bestSolution.totalCost) + " Factible: " + bestSolution.fullyFeasible);
                }
            } else if (iter > 0) {
                // No se eligió movimiento (ej. mejor era tabú y no aspiró)
                // Podría implementarse diversificación aquí.
                //System.out.println("  Iter " + iter + ": No se seleccionó movimiento.");
            }


            if (iter % 50 == 0 && iter > 0) {
                System.out.println("  Iter " + iter + " | Costo Actual: " + formatCost(currentSolution.totalCost) + " | Mejor Costo: " + formatCost(bestSolution.totalCost));
            }

        } // Fin bucle TS

        long endTime = System.currentTimeMillis();
        System.out.println("\n🏁 Búsqueda Tabú (MDVRP) completada en " + (endTime - startTime) + " ms.");
        System.out.println("🏆 Mejor solución encontrada:");
        System.out.println("  Costo Total (Combustible): " + formatCost(bestSolution.totalCost));
        System.out.println("  Totalmente Factible: " + bestSolution.fullyFeasible);
        System.out.println("  Rutas (" + bestSolution.routes.size() + "):");
        for(Route r : bestSolution.routes) {
            System.out.println("    " + r + " | Costo: " + formatCost(r.cost) + " | Factible: " + r.feasible);
        }
        if(!bestSolution.unassignedCustomers.isEmpty()) {
            System.err.println("  ⚠️ Clientes NO ASIGNADOS: " + bestSolution.unassignedCustomers.size());
        }

        return bestSolution;
    }


    static String formatCost(double cost) {
        if (cost == Double.POSITIVE_INFINITY) return "INF";
        return String.format("%.2f Gal", cost);
    }

    // Encuentra una ruta específica en una solución por ID de camión
    static Route findRouteInSolution(Solution solution, String truckId) {
        for (Route route : solution.routes) {
            if (route.truck.id.equals(truckId)) {
                return route;
            }
        }
        return null; // No encontrado
    }

    // Aplica 2-opt a la secuencia de clientes de una ruta
    static void apply2OptIntraRoute(Route route, int index1, int index2) {
        List<CustomerNode> seq = route.sequence;
        // Invertir el segmento entre index1 (inclusive) y index2 (inclusive)
        int start = Math.min(index1, index2);
        int end = Math.max(index1, index2);
        while (start < end) {
            CustomerNode temp = seq.get(start);
            seq.set(start, seq.get(end));
            seq.set(end, temp);
            start++;
            end--;
        }
    }


    // --- Creación de Solución Inicial (Ejemplo Greedy Simple) ---
    // ¡Esta parte es crucial y compleja en VRP! Este es un placeholder muy básico.
    static Solution createInitialSolutionGreedy() {
        Solution initialSol = new Solution();
        initialSol.unassignedCustomers.addAll(activeCustomers); // Empezar con todos sin asignar

        // Crear rutas vacías para cada camión disponible
        for (Truck truck : fleet) {
            if (truck.disponible) {
                // Asignar depósito base (usar el homeDepot definido en el camión)
                initialSol.routes.add(new Route(truck, truck.homeDepot));
            }
        }

        if (initialSol.routes.isEmpty()) return null; // No hay camiones

        // Intentar asignar clientes uno por uno (podría ser aleatorio o por cercanía)
        List<CustomerNode> customersToAssign = new ArrayList<>(initialSol.unassignedCustomers);
        Collections.shuffle(customersToAssign); // Asignar en orden aleatorio

        for (CustomerNode customer : customersToAssign) {
            boolean assigned = false;
            // Intentar insertar en la *mejor* ruta posible (la que incremente menos el costo y sea factible)
            Route bestRouteForCustomer = null;
            int bestInsertionPos = -1;
            double minCostIncrease = Double.POSITIVE_INFINITY;

            for (Route route : initialSol.routes) {
                // Intentar insertar en cada posición posible de esta ruta
                for (int pos = 0; pos <= route.sequence.size(); pos++) {
                    // Simular inserción
                    route.sequence.add(pos, customer);
                    double currentLoad = calculateRouteLoad(route);

                    // Chequeo rápido de capacidad
                    if (currentLoad <= route.truck.type.capacidadM3) {
                        // Evaluar costo y factibilidad de esta inserción (simplificado aquí)
                        // ¡La evaluación completa es costosa! Se haría un chequeo rápido.
                        double potentialCost = calculateRouteCost(route, currentSimTime); // Evalúa la ruta modificada
                        if (potentialCost < Double.POSITIVE_INFINITY) { // Si es factible y calculable
                            // Calcular el incremento de costo (aproximado o real)
                            double costIncrease = potentialCost - (route.cost == Double.POSITIVE_INFINITY ? 0 : route.cost) ; // Costo incremental

                            if (costIncrease < minCostIncrease) {
                                minCostIncrease = costIncrease;
                                bestRouteForCustomer = route;
                                bestInsertionPos = pos;
                            }
                        }
                    }
                    // Deshacer simulación
                    route.sequence.remove(pos);
                }
            }

            // Si se encontró una inserción válida
            if (bestRouteForCustomer != null) {
                bestRouteForCustomer.sequence.add(bestInsertionPos, customer);
                evaluateRoute(bestRouteForCustomer, currentSimTime); // Recalcular costo real de la ruta modificada
                initialSol.unassignedCustomers.remove(customer);
                assigned = true;
                // System.out.println("  Asignado C" + customer.id + " a " + bestRouteForCustomer.truck.id + " en pos " + bestInsertionPos);
            }

            if (!assigned) {
                // System.err.println("  No se pudo asignar C" + customer.id + " a ninguna ruta inicial.");
                // El cliente queda en unassignedCustomers
            }
        }

        // Eliminar rutas que quedaron vacías
        initialSol.routes.removeIf(route -> route.sequence.isEmpty());

        return initialSol;
    }

    static double calculateRouteLoad(Route route) {
        return route.sequence.stream().mapToDouble(c -> c.demandaM3).sum();
    }

    // --- Evaluación de Solución y Rutas ---
    static void evaluateSolution(Solution solution) {
        solution.totalCost = 0;
        solution.fullyFeasible = true;
        for (Route route : solution.routes) {
            evaluateRoute(route, currentSimTime); // Evalúa cada ruta individualmente
            if (!route.feasible) {
                solution.fullyFeasible = false;
                solution.totalCost += route.cost; // Añadir costo (posiblemente con penalización)
                // O simplemente marcar la solución completa como infactible si una ruta lo es?
                // solution.totalCost = Double.POSITIVE_INFINITY; // Opción más estricta
                // break;
            } else {
                solution.totalCost += route.cost;
            }
        }
        // Si hay clientes sin asignar, la solución no es factible en el contexto de VRP
        if (!solution.unassignedCustomers.isEmpty()) {
            solution.fullyFeasible = false;
            // Añadir penalización alta por clientes no asignados
            solution.totalCost += solution.unassignedCustomers.size() * 1000000.0; // Penalización ejemplo
        }


        // Si ninguna ruta es factible o hay no asignados, el costo total debería reflejarlo
        if (!solution.fullyFeasible && solution.totalCost != Double.POSITIVE_INFINITY) {
            // Asegurarse de que una solución no factible tenga un costo muy alto si no es ya infinito
            // solution.totalCost += 1e9; // Otra forma de penalizar
        }


    }

    // Evalúa UNA ruta: calcula costo de combustible y verifica factibilidad
    static void evaluateRoute(Route route, int startTime) {
        route.cost = calculateRouteCost(route, startTime);
        route.feasible = (route.cost != Double.POSITIVE_INFINITY);
    }

    // Calcula costo (combustible) y verifica factibilidad (capacidad, tiempo) de UNA ruta
    static double calculateRouteCost(Route route, int startTime) {
        double totalFuelCost = 0;
        double currentLoadM3 = 0;
        int currentTime = startTime; // Tiempo de salida del depósito
        Location currentLocation = route.startDepot;

        // 1. Calcular carga inicial necesaria y verificar capacidad
        currentLoadM3 = route.sequence.stream().mapToDouble(c -> c.demandaM3).sum();
        if (currentLoadM3 > route.truck.type.capacidadM3) {
            // System.err.println("Ruta " + route.truck.id + ": CAPACIDAD EXCEDIDA (" + currentLoadM3 + "/" + route.truck.type.capacidadM3 + ")");
            return Double.POSITIVE_INFINITY; // Infeasible por capacidad
        }

        // 2. Simular recorrido tramo por tramo
        // Tramo: Depósito -> Primer Cliente
        if (!route.sequence.isEmpty()) {
            CustomerNode firstCustomer = route.sequence.get(0);
            int distDepotToFirst = distanciaReal(currentLocation, firstCustomer);
            if (distDepotToFirst == Integer.MAX_VALUE) return Double.POSITIVE_INFINITY; // Ruta bloqueada

            double fuelSegment = calculateFuelConsumed(distDepotToFirst, currentLoadM3, route.truck);
            totalFuelCost += fuelSegment;

            int travelTimeMinutes = (int) Math.round(distDepotToFirst * MINUTOS_POR_KM);
            currentTime += travelTimeMinutes;

            // Verificar Ventana de Tiempo del primer cliente
            if (currentTime > firstCustomer.tiempoLimiteEntregaMinutos) {
                // System.err.println("Ruta " + route.truck.id + ": TIEMPO LÍMITE EXCEDIDO para C" + firstCustomer.id + " (Llegada: " + currentTime + ", Límite: " + firstCustomer.tiempoLimiteEntregaMinutos + ")");
                return Double.POSITIVE_INFINITY; // Infeasible por tiempo
            }

            // Asumir llegada = inicio servicio (no hay tiempo de espera/servicio modelado aún)
            currentLocation = firstCustomer;
            currentLoadM3 -= firstCustomer.demandaM3; // Descargar
        } else {
            // Ruta vacía, costo 0 (¿o costo de ir y volver?)
            return 0.0;
        }


        // Tramos: Cliente -> Siguiente Cliente
        for (int i = 0; i < route.sequence.size() - 1; i++) {
            CustomerNode originCust = route.sequence.get(i);
            CustomerNode destCust = route.sequence.get(i + 1);

            int distSegment = distanciaReal(currentLocation, destCust);
            if (distSegment == Integer.MAX_VALUE) return Double.POSITIVE_INFINITY; // Ruta bloqueada

            double fuelSegment = calculateFuelConsumed(distSegment, currentLoadM3, route.truck);
            totalFuelCost += fuelSegment;

            int travelTimeMinutes = (int) Math.round(distSegment * MINUTOS_POR_KM);
            currentTime += travelTimeMinutes;

            // Verificar Ventana de Tiempo del siguiente cliente
            if (currentTime > destCust.tiempoLimiteEntregaMinutos) {
                // System.err.println("Ruta " + route.truck.id + ": TIEMPO LÍMITE EXCEDIDO para C" + destCust.id + " (Llegada: " + currentTime + ", Límite: " + destCust.tiempoLimiteEntregaMinutos + ")");
                return Double.POSITIVE_INFINITY; // Infeasible por tiempo
            }

            currentLocation = destCust;
            currentLoadM3 -= destCust.demandaM3; // Descargar
        }

        // Tramo: Último Cliente -> Depósito Final
        int distLastToDepot = distanciaReal(currentLocation, route.endDepot);
        if (distLastToDepot == Integer.MAX_VALUE) return Double.POSITIVE_INFINITY; // Ruta bloqueada

        // Carga es 0 (o casi 0) en el regreso
        double fuelReturn = calculateFuelConsumed(distLastToDepot, 0.0, route.truck);
        totalFuelCost += fuelReturn;

        // Si llegó hasta aquí, la ruta es factible en términos de capacidad y tiempo
        return totalFuelCost;
    }

    // Calcula el combustible para un segmento
    static double calculateFuelConsumed(int distanceKm, double currentLoadM3, Truck truck) {
        if (distanceKm == 0) return 0;
        // 1. Calcular peso de la carga actual
        double pesoCargaActualTon = truck.type.calcularPesoCargaActualTon(currentLoadM3);
        // 2. Calcular peso combinado
        double pesoCombinadoTon = truck.type.taraTon + pesoCargaActualTon;
        // 3. Aplicar fórmula
        double fuelGal = (double) distanceKm * pesoCombinadoTon / 180.0;
        return fuelGal;
    }


    // --- Cálculo de Distancia Real (BFS) ---
    // (Igual que en la versión anterior, pero usa Location en lugar de Node/Punto)
    private static int distanciaReal(Location from, Location to) {
        if (from == null || to == null) return Integer.MAX_VALUE;
        if (from.x < 0 || from.x >= GRID_WIDTH || from.y < 0 || from.y >= GRID_HEIGHT) return Integer.MAX_VALUE;
        if (to.x < 0 || to.x >= GRID_WIDTH || to.y < 0 || to.y >= GRID_HEIGHT) return Integer.MAX_VALUE;
        // Un nodo bloqueado no puede ser origen ni destino de un TRAMO (no se puede pasar por él)
        // ¿Pero puede ser el punto de entrega final? Asumamos que sí por ahora.
        // if (bloqueado[from.x][from.y]) return Integer.MAX_VALUE; // Origen bloqueado, no se puede salir
        // if (bloqueado[to.x][to.y]) return Integer.MAX_VALUE; // Destino bloqueado, no se puede llegar

        Queue<int[]> queue = new LinkedList<>();
        Map<Point, Integer> distancias = new HashMap<>();
        Set<Point> visited = new HashSet<>();

        Point startPoint = from.toPoint();
        Point endPoint = to.toPoint();

        // Si inicio y fin son el mismo
        if (startPoint.equals(endPoint)) return 0;

        queue.add(new int[]{from.x, from.y});
        distancias.put(startPoint, 0);
        visited.add(startPoint);

        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        while (!queue.isEmpty()) {
            int[] actual = queue.poll();
            Point actualPoint = new Point(actual[0], actual[1]);
            int d = distancias.get(actualPoint);

            // Explorar vecinos
            for (int[] dir : dirs) {
                int nx = actual[0] + dir[0];
                int ny = actual[1] + dir[1];
                Point nextPoint = new Point(nx, ny);

                // Si es el destino, retornamos distancia
                if (nextPoint.equals(endPoint)) {
                    return d + 1;
                }

                // Verificar límites, no visitado y NO BLOQUEADO
                if (nx >= 0 && ny >= 0 && nx < GRID_WIDTH && ny < GRID_HEIGHT && !visited.contains(nextPoint)) {
                    // Un nodo bloqueado NO se puede atravesar
                    if (!bloqueado[nx][ny]) {
                        visited.add(nextPoint);
                        distancias.put(nextPoint, d + 1);
                        queue.add(new int[]{nx, ny});
                    }
                }
            }
        }
        // System.err.println("No se encontró ruta BFS desde " + from + " hasta " + to);
        return Integer.MAX_VALUE; // No se encontró ruta
    }

    // --- Carga de Datos (Adaptar nombres de archivo y parsing si es necesario) ---
    static List<Pedido> cargarPedidos(String archivo) throws Exception {
        // Reusar la lógica de carga anterior, asegurando que parsea bien
        // el formato "ventas2025mm" y las horas límite.
        // ... (Misma lógica de `cargarPedidos` de la respuesta anterior) ...
        System.out.println("Cargando pedidos desde: " + archivo);
        List<Pedido> pedidos = new ArrayList<>();
        List<String> lineas = Files.readAllLines(Paths.get(archivo));
        for (String linea : lineas) {
            try { // Formato: 11d13h31m:45,43,c-167,9m3,36h
                String[] partesTiempoDatos = linea.split(":");
                if (partesTiempoDatos.length != 2) continue;
                int momento = TiempoUtils.parsearMarcaDeTiempo(partesTiempoDatos[0].trim());
                String[] datos = partesTiempoDatos[1].split(",");
                if (datos.length != 5) continue;
                int x = Integer.parseInt(datos[0].trim());
                int y = Integer.parseInt(datos[1].trim());
                // String idCliente = datos[2].trim(); // No usado directamente
                double volumen = Double.parseDouble(datos[3].trim().replace("m3", ""));
                // Horas límite es duración desde el pedido
                int horaLimite = Integer.parseInt(datos[4].trim().replace("h", ""));

                pedidos.add(new Pedido(x, y, volumen, horaLimite, momento));
            } catch (Exception e) {
                System.err.println("Error al parsear línea de pedido: '" + linea + "' - " + e.getMessage());
            }
        }
        // Ordenar por tiempo de pedido puede ser útil
        pedidos.sort(Comparator.comparingInt(p -> p.momentoPedido));
        System.out.println("Pedidos cargados y ordenados: " + pedidos.size());
        return pedidos;
    }

    static List<Bloqueo> cargarBloqueos(String archivo) throws Exception {
        // Reusar la lógica de carga anterior
        // ... (Misma lógica de `cargarBloqueos` de la respuesta anterior) ...
        System.out.println("Cargando bloqueos desde: " + archivo);
        List<Bloqueo> bloqueos = new ArrayList<>();
        List<String> lineas = Files.readAllLines(Paths.get(archivo));
        for (String linea : lineas) {
            try { // Formato: 01d06h00m-01d15h00m:31,21,34,21
                String[] partesTiempoCoords = linea.split(":");
                if (partesTiempoCoords.length != 2) continue;
                String[] tiempos = partesTiempoCoords[0].split("-");
                if (tiempos.length != 2) continue;
                int inicio = TiempoUtils.parsearMarcaDeTiempo(tiempos[0].trim());
                int fin = TiempoUtils.parsearMarcaDeTiempo(tiempos[1].trim());
                String[] coords = partesTiempoCoords[1].split(",");
                List<int[]> puntos = new ArrayList<>();
                // Los puntos definen los NODOS bloqueados
                for (int i = 0; i < coords.length; i += 2) {
                    int x = Integer.parseInt(coords[i].trim());
                    int y = Integer.parseInt(coords[i + 1].trim());
                    puntos.add(new int[]{x, y});
                }
                if (!puntos.isEmpty()) {
                    bloqueos.add(new Bloqueo(inicio, fin, puntos));
                }
            } catch (Exception e) {
                System.err.println("Error al parsear línea de bloqueo: '" + linea + "' - " + e.getMessage());
            }
        }
        System.out.println("Bloqueos cargados: " + bloqueos.size());
        return bloqueos;
    }

    // --- Visualización (Adaptar para múltiples rutas) ---
    static void visualizarSolucion(Solution solution) {
        System.out.println("Preparando visualización...");
        // Convertir depots y clientes a Puntos
        List<GridVisualizer_MDVRP.Punto> puntosClientesVis = activeCustomers.stream()
                .filter(c -> !solution.unassignedCustomers.contains(c)) // Solo clientes asignados
                .map(c -> new GridVisualizer_MDVRP.Punto(c.x, c.y, GridVisualizer_MDVRP.PuntoTipo.CLIENTE, String.valueOf(c.id)))
                .collect(Collectors.toList());

        List<GridVisualizer_MDVRP.Punto> puntosDepotsVis = depots.stream()
                .map(d -> new GridVisualizer_MDVRP.Punto(d.x, d.y, GridVisualizer_MDVRP.PuntoTipo.DEPOSITO, d.id))
                .collect(Collectors.toList());

        List<GridVisualizer_MDVRP.RutaVisual> rutasVis = new ArrayList<>();
        Color[] routeColors = {Color.BLUE, Color.RED, Color.ORANGE, Color.MAGENTA, Color.PINK, Color.YELLOW.darker(), Color.CYAN, Color.GRAY};
        int colorIndex = 0;

        for(Route route : solution.routes) {
            if (!route.sequence.isEmpty()) {
                List<GridVisualizer_MDVRP.Punto> secuenciaPuntos = new ArrayList<>();
                // Añadir depot inicial
                secuenciaPuntos.add(new GridVisualizer_MDVRP.Punto(route.startDepot.x, route.startDepot.y, GridVisualizer_MDVRP.PuntoTipo.DEPOSITO, route.startDepot.id));
                // Añadir clientes
                route.sequence.forEach(c -> secuenciaPuntos.add(new GridVisualizer_MDVRP.Punto(c.x, c.y, GridVisualizer_MDVRP.PuntoTipo.CLIENTE, String.valueOf(c.id))));
                // Añadir depot final
                secuenciaPuntos.add(new GridVisualizer_MDVRP.Punto(route.endDepot.x, route.endDepot.y, GridVisualizer_MDVRP.PuntoTipo.DEPOSITO, route.endDepot.id));

                // Asignar color
                Color color = routeColors[colorIndex % routeColors.length];
                colorIndex++;

                rutasVis.add(new GridVisualizer_MDVRP.RutaVisual(route.truck.id, secuenciaPuntos, color));
            }
        }


        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Visualización GLP - Rutas MDVRP (TS)");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            GridVisualizer_MDVRP panel = new GridVisualizer_MDVRP(puntosDepotsVis, puntosClientesVis, rutasVis, bloqueado);
            panel.setPreferredSize(new Dimension(GRID_WIDTH * 12, GRID_HEIGHT * 12)); // Ajustar tamaño celda
            JScrollPane scrollPane = new JScrollPane(panel); // Añadir scroll si es muy grande
            frame.add(scrollPane);
            frame.pack();
            frame.setSize(900, 700); // Tamaño fijo inicial
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    // Clases Pedido y Bloqueo (usadas para carga inicial)
    static class Pedido {
        int x, y; double volumen; int horaLimite; int momentoPedido;
        Pedido(int x,int y,double v,int hl, int mp){this.x=x;this.y=y;this.volumen=v;this.horaLimite=hl;this.momentoPedido=mp;}
    }
    static class Bloqueo {
        int inicioMinutos, finMinutos; List<int[]> puntosBloqueados;
        Bloqueo(int i, int f, List<int[]> p){this.inicioMinutos=i;this.finMinutos=f;this.puntosBloqueados=p;}
    }

}