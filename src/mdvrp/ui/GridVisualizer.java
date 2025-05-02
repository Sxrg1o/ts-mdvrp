package mdvrp.ui; // Añadido

import mdvrp.state.GlobalState; // Corregido el import

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class GridVisualizer extends JPanel {

    // Constantes (Acceden a GlobalState correctamente)
    static final int GRID_WIDTH = GlobalState.GRID_WIDTH;
    static final int GRID_HEIGHT = GlobalState.GRID_HEIGHT;
    static final int CELL_SIZE = 12;
    static final int NODE_SIZE = CELL_SIZE - 4;
    static final int NODE_OFFSET = 2;

    // Enum y Clases Anidadas (públicas para ser usadas por SimulationVisualizer si es necesario)
    public enum PuntoTipo {
        DEPOSITO,
        CLIENTE
    }

    // Representa una celda bloqueada que fue intersectada por una ruta específica
    public static class BloqueoEtiquetado {
        int x, y;
        String etiqueta; // truckId que pasó por aquí

        BloqueoEtiquetado(int x, int y, String etiqueta) {
            this.x = x;
            this.y = y;
            this.etiqueta = etiqueta;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BloqueoEtiquetado that = (BloqueoEtiquetado) o;
            return x == that.x && y == that.y && Objects.equals(etiqueta, that.etiqueta);
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y, etiqueta);
        }
    }

    // Representa un punto notable en el grid (Depot o Cliente)
    public static class Punto {
        public final int x, y; // Hacer final si no cambian después de creación
        public final PuntoTipo tipo;
        public final String label;

        public Punto(int x, int y, PuntoTipo tipo, String label) {
            this.x = x;
            this.y = y;
            this.tipo = tipo;
            this.label = label;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Punto punto = (Punto) o;
            // Considerar si la igualdad debe ser solo por x,y o incluir tipo/label
            return x == punto.x && y == punto.y && tipo == punto.tipo && Objects.equals(label, punto.label);
        }

        @Override
        public int hashCode() {
            // Coherente con equals
            return Objects.hash(x, y, tipo, label);
        }
        @Override
        public String toString() { return label + "@(" + x + "," + y + ")"; }
    }

    // Representa una ruta completa a dibujar para un camión
    public static class RutaVisual {
        public final String truckId; // Hacer final
        // Lista de Puntos (Depots/Clientes) que definen los hitos de la ruta
        public final List<Punto> secuenciaCompleta; // Hacer final, lista inmutable?
        public final Color color; // Hacer final

        public RutaVisual(String truckId, List<Punto> secuencia, Color color) {
            this.truckId = truckId;
            // Crear copia defensiva para asegurar inmutabilidad si se desea
            this.secuenciaCompleta = new ArrayList<>(secuencia);
            this.color = color;
        }
    }

    // Campos de instancia para los datos a visualizar
    private final List<Punto> depots;
    private final List<Punto> customers;
    private final List<RutaVisual> routesToDraw;
    private final boolean[][] matrizBloqueado; // Referencia al estado actual de bloqueos
    private final List<Punto> puntosBloqueadosGrid; // Lista de celdas bloqueadas (para pintarlas rápido)
    private final List<BloqueoEtiquetado> bloqueosEnRutas; // Calculado durante paintComponent

    // Constructor (Corregido el nombre)
    public GridVisualizer(List<Punto> depots, List<Punto> customers, List<RutaVisual> routes, boolean[][] bloqueadoActual) {
        this.depots = (depots != null) ? new ArrayList<>(depots) : new ArrayList<>(); // Copias defensivas
        this.customers = (customers != null) ? new ArrayList<>(customers) : new ArrayList<>(); // Copias defensivas
        this.routesToDraw = (routes != null) ? new ArrayList<>(routes) : new ArrayList<>(); // Copias defensivas
        this.matrizBloqueado = bloqueadoActual; // Referencia directa está bien aquí si se actualiza externamente

        this.puntosBloqueadosGrid = new ArrayList<>();
        this.bloqueosEnRutas = new ArrayList<>(); // Se limpia en cada paint

        // Precalcular las celdas bloqueadas para dibujarlas eficientemente
        if (this.matrizBloqueado != null) {
            for (int i = 0; i < GRID_WIDTH; i++) {
                for (int j = 0; j < GRID_HEIGHT; j++) {
                    if (this.matrizBloqueado[i][j]) {
                        // Usamos nuestro propio Punto, tipo null está bien
                        puntosBloqueadosGrid.add(new Punto(i, j, null, "Bloqueado"));
                    }
                }
            }
        }
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(GRID_WIDTH * CELL_SIZE + 1, GRID_HEIGHT * CELL_SIZE + 1)); // Tamaño preferido
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        bloqueosEnRutas.clear(); // Limpiar etiquetas de bloqueos de rutas anteriores

        drawGrid(g2d);
        drawBlockedCells(g2d); // Dibuja el fondo gris de las celdas bloqueadas
        drawRoutes(g2d);       // Dibuja las líneas de las rutas y detecta colisiones con bloqueos
        drawNodes(g2d);        // Dibuja los Depots y Clientes encima
        drawBlockedRouteLabels(g2d); // Dibuja las etiquetas de los camiones en celdas bloqueadas
    }

    // --- Métodos de Dibujo y Ayudantes ---

    private void drawGrid(Graphics2D g) {
        g.setColor(new Color(230, 230, 230)); // Gris claro
        int panelWidth = getWidth(); // Usar tamaño real del panel
        int panelHeight = getHeight();
        int width = GRID_WIDTH * CELL_SIZE;
        int height = GRID_HEIGHT * CELL_SIZE;
        for (int i = 0; i <= GRID_WIDTH; i++) g.drawLine(i * CELL_SIZE, 0, i * CELL_SIZE, height);
        for (int j = 0; j <= GRID_HEIGHT; j++) g.drawLine(0, j * CELL_SIZE, width, j * CELL_SIZE);
    }

    private void drawBlockedCells(Graphics2D g) {
        g.setColor(Color.DARK_GRAY);
        for (Punto b : puntosBloqueadosGrid) {
            g.fillRect(b.x * CELL_SIZE, b.y * CELL_SIZE, CELL_SIZE, CELL_SIZE);
        }
    }

    private void drawRoutes(Graphics2D g) {
        Set<Point> labeledBlockedCellsThisPaint = new HashSet<>(); // Para no etiquetar la misma celda varias veces por pasada

        for (RutaVisual ruta : routesToDraw) {
            g.setColor(ruta.color);
            g.setStroke(new BasicStroke(1.5f)); // Grosor de línea
            List<Punto> secuencia = ruta.secuenciaCompleta;
            if (secuencia == null || secuencia.size() < 2) continue;

            for (int i = 0; i < secuencia.size() - 1; i++) {
                Punto origen = secuencia.get(i);
                Punto destino = secuencia.get(i + 1);
                if (origen == null || destino == null) continue;

                // Encuentra el camino celda por celda usando BFS
                List<Point> caminoReal = encontrarCaminoBFS(origen, destino);

                if (caminoReal != null && !caminoReal.isEmpty()) {
                    // Dibuja el camino encontrado segmento a segmento
                    Point pPrev = new Point(origen.x, origen.y);
                    for (Point pActual : caminoReal) {
                        g.drawLine(pPrev.x * CELL_SIZE + CELL_SIZE / 2, pPrev.y * CELL_SIZE + CELL_SIZE / 2,
                                pActual.x * CELL_SIZE + CELL_SIZE / 2, pActual.y * CELL_SIZE + CELL_SIZE / 2);

                        // Si este punto del camino está bloqueado, registrar para etiqueta
                        if (esBloqueado(pActual.x, pActual.y)) {
                            if (!labeledBlockedCellsThisPaint.contains(pActual)) {
                                bloqueosEnRutas.add(new BloqueoEtiquetado(pActual.x, pActual.y, ruta.truckId));
                                labeledBlockedCellsThisPaint.add(pActual);
                            }
                        }
                        pPrev = pActual;
                    }
                    // Dibujar el último segmento desde el penúltimo punto del camino hasta el destino final
                    Point ultimoPuntoCamino = caminoReal.get(caminoReal.size()-1);
                    g.drawLine(ultimoPuntoCamino.x * CELL_SIZE + CELL_SIZE / 2, ultimoPuntoCamino.y * CELL_SIZE + CELL_SIZE / 2,
                            destino.x * CELL_SIZE + CELL_SIZE / 2, destino.y * CELL_SIZE + CELL_SIZE / 2);


                } else {
                    // Si no se encontró camino (ruta totalmente bloqueada), dibujar línea punteada magenta
                    Stroke dashed = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{3}, 0);
                    Stroke originalStroke = g.getStroke();
                    Color originalColor = g.getColor();
                    g.setStroke(dashed); g.setColor(Color.MAGENTA);
                    g.drawLine(origen.x * CELL_SIZE + CELL_SIZE / 2, origen.y * CELL_SIZE + CELL_SIZE / 2,
                            destino.x * CELL_SIZE + CELL_SIZE / 2, destino.y * CELL_SIZE + CELL_SIZE / 2);
                    g.setStroke(originalStroke); g.setColor(originalColor); // Restaurar
                }
            }
        }
    }

    private void drawNodes(Graphics2D g) {
        Font nodeFont = new Font("Arial", Font.BOLD, 9);
        g.setFont(nodeFont);
        FontMetrics fm = g.getFontMetrics();
        // No usar Set si queremos dibujar clientes encima de depots
        // Set<Point> drawnLocations = new HashSet<>();

        // Dibujar Depots primero
        g.setColor(new Color(0, 100, 0)); // Verde oscuro
        for (Punto depot : depots) {
            Point loc = new Point(depot.x, depot.y);
            //if (drawnLocations.contains(loc)) continue;

            g.fillRect(depot.x * CELL_SIZE + NODE_OFFSET, depot.y * CELL_SIZE + NODE_OFFSET,
                    NODE_SIZE, NODE_SIZE); // Cuadrado
            g.setColor(Color.WHITE); // Texto blanco
            int labelWidth = fm.stringWidth(depot.label);
            // Centrar texto
            g.drawString(depot.label, depot.x * CELL_SIZE + (CELL_SIZE - labelWidth) / 2,
                    depot.y * CELL_SIZE + CELL_SIZE / 2 + fm.getAscent() / 2 - 1);
            g.setColor(new Color(0, 100, 0)); // Volver a verde
            // drawnLocations.add(loc);
        }

        // Dibujar Clientes (después, para que queden encima si coinciden)
        g.setColor(Color.BLUE); // Cambiado a Azul para clientes
        for (Punto customer : customers) {
            g.fillOval(customer.x * CELL_SIZE + NODE_OFFSET, customer.y * CELL_SIZE + NODE_OFFSET,
                    NODE_SIZE, NODE_SIZE); // Círculo
            g.setColor(Color.BLACK); // Texto negro cerca del nodo
            String lbl = customer.label; // Usar etiqueta (ID de parte)
            // Dibujar etiqueta al lado
            g.drawString(lbl, customer.x * CELL_SIZE + CELL_SIZE + 2 , customer.y * CELL_SIZE + CELL_SIZE/2 + fm.getAscent()/2);
            g.setColor(Color.BLUE); // Volver a azul
        }
    }

    private void drawBlockedRouteLabels(Graphics2D g) {
        g.setColor(Color.ORANGE);
        g.setFont(new Font("Arial", Font.BOLD, 8));
        FontMetrics fm = g.getFontMetrics();
        Map<Point, List<String>> labelsByLocation = new HashMap<>();

        // Agrupar etiquetas por celda bloqueada
        for (BloqueoEtiquetado be : bloqueosEnRutas) {
            Point p = new Point(be.x, be.y);
            labelsByLocation.computeIfAbsent(p, k -> new ArrayList<>()).add(be.etiqueta);
        }

        // Dibujar etiquetas agrupadas
        for (Map.Entry<Point, List<String>> entry : labelsByLocation.entrySet()) {
            Point location = entry.getKey();
            // Unir IDs con comas si varios camiones pasan por la misma celda bloqueada
            String label = String.join(",", new HashSet<>(entry.getValue())); // Usar Set para evitar duplicados del mismo camión
            int labelWidth = fm.stringWidth(label);
            // Dibujar etiqueta encima de la celda bloqueada
            g.drawString(label, location.x * CELL_SIZE + (CELL_SIZE - labelWidth) / 2,
                    location.y * CELL_SIZE + fm.getAscent()); // Posición Y ajustada
        }
    }

    // Verifica si una celda está bloqueada (usando la matriz actual)
    private boolean esBloqueado(int x, int y) {
        // Chequeo de límites por seguridad
        if (matrizBloqueado == null || x < 0 || x >= GRID_WIDTH || y < 0 || y >= GRID_HEIGHT) {
            return true; // Considerar fuera de límites como bloqueado para BFS
        }
        return matrizBloqueado[x][y];
    }

    // Encuentra el camino celda por celda usando BFS
    private List<Point> encontrarCaminoBFS(Punto from, Punto to) {
        if (from == null || to == null) return null;
        Point startPoint = new Point(from.x, from.y);
        Point endPoint = new Point(to.x, to.y);

        if (startPoint.equals(endPoint)) return new ArrayList<>(); // Ya está en el destino

        Queue<Point> queue = new LinkedList<>();
        Map<Point, Point> predecesores = new HashMap<>(); // Para reconstruir el camino
        Set<Point> visited = new HashSet<>();

        queue.add(startPoint);
        visited.add(startPoint);
        predecesores.put(startPoint, null); // Inicio no tiene predecesor

        int[][] dirs = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}}; // Movimientos posibles
        boolean found = false;

        while (!queue.isEmpty()) {
            Point currentPoint = queue.poll();

            // ¿Llegamos al destino?
            if (currentPoint.equals(endPoint)) {
                found = true;
                break; // Salir del bucle BFS
            }

            // Explorar vecinos
            for (int[] dir : dirs) {
                int nx = currentPoint.x + dir[0];
                int ny = currentPoint.y + dir[1];
                Point nextPoint = new Point(nx, ny);

                // Validar límites y si ya fue visitado
                if (nx >= 0 && nx < GRID_WIDTH && ny >= 0 && ny < GRID_HEIGHT && !visited.contains(nextPoint)) {
                    // Validar si la celda NO está bloqueada
                    if (!esBloqueado(nx, ny)) {
                        visited.add(nextPoint);
                        predecesores.put(nextPoint, currentPoint); // Guardar de dónde vino
                        queue.add(nextPoint); // Añadir a la cola para explorar
                    }
                    // Opcional: Podrías marcar la celda bloqueada como visitada si quisieras
                    // else { visited.add(nextPoint); }
                }
            }
        }

        // Reconstruir el camino si se encontró el destino
        if (!found) {
            System.err.println("WARN: No se encontró camino BFS desde " + startPoint + " hasta " + endPoint);
            return null; // No hay camino
        }

        LinkedList<Point> path = new LinkedList<>();
        Point step = endPoint;
        // Retroceder desde el destino hasta el inicio usando los predecesores
        while (step != null) {
            // No añadir el punto de inicio al camino devuelto (la línea se dibuja desde 'from')
            if (!step.equals(startPoint)) {
                path.addFirst(step);
            }
            step = predecesores.get(step);
            // Control anti-bucle infinito (por si acaso)
            if (path.size() > GRID_WIDTH * GRID_HEIGHT) {
                System.err.println("ERROR: Posible bucle infinito reconstruyendo camino BFS.");
                return null;
            }
        }

        return path; // Devuelve la lista de puntos (celdas) del camino (sin incluir el inicio)
    }

} // Fin de la clase GridVisualizer_MDVRP