import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class GridVisualizer_MDVRP extends JPanel {

    // Constantes
    static final int GRID_WIDTH = TabuSearchPlanner_MDVRP.GRID_WIDTH;
    static final int GRID_HEIGHT = TabuSearchPlanner_MDVRP.GRID_HEIGHT;
    static final int CELL_SIZE = 12;
    static final int NODE_SIZE = CELL_SIZE - 4;
    static final int NODE_OFFSET = 2;

    enum PuntoTipo {
        DEPOSITO,
        CLIENTE
    }

    public static class BloqueoEtiquetado {
        int x, y;
        String etiqueta;

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

    public static class Punto {
        int x, y;
        PuntoTipo tipo;
        String label;

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
            // return x == punto.x && y == punto.y;
            return x == punto.x && y == punto.y && tipo == punto.tipo && Objects.equals(label, punto.label);
        }

        @Override
        public int hashCode() {
            // return Objects.hash(x, y); // Basado solo en ubicación
            // O basado en todo
            return Objects.hash(x, y, tipo, label);
        }
        @Override
        public String toString() { return label + "@(" + x + "," + y + ")"; }
    }

    public static class RutaVisual {
        String truckId;
        List<Punto> secuenciaCompleta;
        Color color;

        public RutaVisual(String truckId, List<Punto> secuencia, Color color) {
            this.truckId = truckId;
            this.secuenciaCompleta = secuencia;
            this.color = color;
        }
    }

    private final List<Punto> depots;
    private final List<Punto> customers;
    private final List<RutaVisual> routesToDraw;
    private final boolean[][] matrizBloqueado;
    private final List<Punto> puntosBloqueadosGrid;
    private final List<BloqueoEtiquetado> bloqueosEnRutas;

    public GridVisualizer_MDVRP(List<Punto> depots, List<Punto> customers, List<RutaVisual> routes, boolean[][] bloqueadoActual) {
        this.depots = (depots != null) ? depots : new ArrayList<>();
        this.customers = (customers != null) ? customers : new ArrayList<>();
        this.routesToDraw = (routes != null) ? routes : new ArrayList<>();
        this.matrizBloqueado = bloqueadoActual;

        this.puntosBloqueadosGrid = new ArrayList<>();
        this.bloqueosEnRutas = new ArrayList<>();

        if (this.matrizBloqueado != null) {
            for (int i = 0; i < GRID_WIDTH; i++) {
                for (int j = 0; j < GRID_HEIGHT; j++) {
                    if (this.matrizBloqueado[i][j]) {
                        puntosBloqueadosGrid.add(new Punto(i, j, null, "Bloqueado"));
                    }
                }
            }
        }
        setBackground(Color.WHITE);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        bloqueosEnRutas.clear();

        drawGrid(g2d);
        drawBlockedCells(g2d);
        drawRoutes(g2d);
        drawNodes(g2d);
        drawBlockedRouteLabels(g2d);
    }

    private void drawGrid(Graphics2D g) {
        g.setColor(new Color(230, 230, 230));
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
        Set<Point> labeledBlockedCellsThisPaint = new HashSet<>();

        for (RutaVisual ruta : routesToDraw) {
            g.setColor(ruta.color);
            g.setStroke(new BasicStroke(1.5f));
            List<Punto> secuencia = ruta.secuenciaCompleta;
            if (secuencia == null || secuencia.size() < 2) continue;

            for (int i = 0; i < secuencia.size() - 1; i++) {
                Punto origen = secuencia.get(i);
                Punto destino = secuencia.get(i + 1);
                if (origen == null || destino == null) continue;

                List<Point> caminoReal = encontrarCaminoBFS(origen, destino);

                if (caminoReal != null && !caminoReal.isEmpty()) {
                    Point pPrev = new Point(origen.x, origen.y);
                    for (Point pActual : caminoReal) {
                        g.drawLine(pPrev.x * CELL_SIZE + CELL_SIZE / 2, pPrev.y * CELL_SIZE + CELL_SIZE / 2,
                                pActual.x * CELL_SIZE + CELL_SIZE / 2, pActual.y * CELL_SIZE + CELL_SIZE / 2);
                        if (esBloqueado(pActual.x, pActual.y)) {
                            if (!labeledBlockedCellsThisPaint.contains(pActual)) {
                                bloqueosEnRutas.add(new BloqueoEtiquetado(pActual.x, pActual.y, ruta.truckId));
                                labeledBlockedCellsThisPaint.add(pActual);
                            }
                        }
                        pPrev = pActual;
                    }
                } else {
                    Stroke dashed = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{3}, 0);
                    g.setStroke(dashed); g.setColor(Color.MAGENTA);
                    g.drawLine(origen.x * CELL_SIZE + CELL_SIZE / 2, origen.y * CELL_SIZE + CELL_SIZE / 2,
                            destino.x * CELL_SIZE + CELL_SIZE / 2, destino.y * CELL_SIZE + CELL_SIZE / 2);
                    g.setStroke(new BasicStroke(1.5f)); g.setColor(ruta.color);
                }
            }
        }
    }

    private void drawNodes(Graphics2D g) {
        Font nodeFont = new Font("Arial", Font.BOLD, 9);
        g.setFont(nodeFont);
        FontMetrics fm = g.getFontMetrics();
        Set<Point> drawnLocations = new HashSet<>();

        g.setColor(new Color(0, 100, 0));
        for (Punto depot : depots) {
            Point loc = new Point(depot.x, depot.y);
            if (drawnLocations.contains(loc)) continue;

            g.fillRect(depot.x * CELL_SIZE + NODE_OFFSET, depot.y * CELL_SIZE + NODE_OFFSET,
                    NODE_SIZE, NODE_SIZE);
            g.setColor(Color.WHITE);
            int labelWidth = fm.stringWidth(depot.label);
            g.drawString(depot.label, depot.x * CELL_SIZE + (CELL_SIZE - labelWidth) / 2,
                    depot.y * CELL_SIZE + CELL_SIZE / 2 + fm.getAscent() / 2 - 1);
            g.setColor(new Color(0, 100, 0));
            drawnLocations.add(loc);
        }

        g.setColor(Color.RED);
        for (Punto customer : customers) {
            Point loc = new Point(customer.x, customer.y);
            // if (drawnLocations.contains(loc)) continue; // Comentar si queremos ver clientes encima de depots

            g.fillOval(customer.x * CELL_SIZE + NODE_OFFSET, customer.y * CELL_SIZE + NODE_OFFSET,
                    NODE_SIZE, NODE_SIZE);
            g.setColor(Color.BLACK);
            String lbl = customer.label;
            // Acortar etiqueta si es muy larga
            // if (lbl.length() > 3) lbl = "C" + lbl;
            g.drawString(lbl, customer.x * CELL_SIZE + NODE_OFFSET + NODE_SIZE + 2, customer.y * CELL_SIZE + NODE_OFFSET + NODE_SIZE/2);
            g.setColor(Color.RED);
            drawnLocations.add(loc);
        }
    }

    private void drawBlockedRouteLabels(Graphics2D g) {
        g.setColor(Color.ORANGE);
        g.setFont(new Font("Arial", Font.BOLD, 8));
        FontMetrics fm = g.getFontMetrics();
        Map<Point, List<String>> labelsByLocation = new HashMap<>();
        for (BloqueoEtiquetado be : bloqueosEnRutas) {
            Point p = new Point(be.x, be.y);
            labelsByLocation.computeIfAbsent(p, k -> new ArrayList<>()).add(be.etiqueta);
        }

        for (Map.Entry<Point, List<String>> entry : labelsByLocation.entrySet()) {
            Point location = entry.getKey();
            String label = String.join(",", entry.getValue());
            int labelWidth = fm.stringWidth(label);
            g.drawString(label, location.x * CELL_SIZE + (CELL_SIZE - labelWidth) / 2,
                    location.y * CELL_SIZE + fm.getAscent());
        }
    }

    private boolean esBloqueado(int x, int y) {
        if (matrizBloqueado == null || x < 0 || x >= GRID_WIDTH || y < 0 || y >= GRID_HEIGHT) {
            return false;
        }
        return matrizBloqueado[x][y];
    }

    private List<Point> encontrarCaminoBFS(Punto from, Punto to) {
        if (from == null || to == null) return null;
        Point startPoint = new Point(from.x, from.y);
        Point endPoint = new Point(to.x, to.y);
        if (startPoint.equals(endPoint)) return new ArrayList<>();

        Queue<Point> queue = new LinkedList<>();
        Map<Point, Point> predecesores = new HashMap<>();
        Set<Point> visited = new HashSet<>();

        queue.add(startPoint);
        visited.add(startPoint);
        predecesores.put(startPoint, null);

        int[][] dirs = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
        boolean found = false;
        while (!queue.isEmpty()) {
            Point currentPoint = queue.poll();
            if (currentPoint.equals(endPoint)) { found = true; break; }
            for (int[] dir : dirs) {
                int nx = currentPoint.x + dir[0];
                int ny = currentPoint.y + dir[1];
                Point nextPoint = new Point(nx, ny);
                if (nx >= 0 && nx < GRID_WIDTH && ny >= 0 && ny < GRID_HEIGHT && !visited.contains(nextPoint)) {
                    if (!esBloqueado(nx, ny)) {
                        visited.add(nextPoint);
                        predecesores.put(nextPoint, currentPoint);
                        queue.add(nextPoint);
                    }
                }
            }
        }

        if (!found) return null;
        LinkedList<Point> path = new LinkedList<>();
        Point step = endPoint;
        while (step != null && !step.equals(startPoint)) {
            path.addFirst(step);
            step = predecesores.get(step);
        }
        return path;
    }

}