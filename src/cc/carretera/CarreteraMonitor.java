package cc.carretera;

import es.upm.babel.cclib.Monitor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CarreteraMonitor implements Carretera {
    private final int SEGMENTOS;
    private final int CARRILES;
    private final Map<String, Pair<Pos, Integer>> coches = new HashMap<>();

    private final Monitor monitor = new Monitor();
    private final Monitor.Cond puedeEntrar = monitor.newCond();
    private final Monitor.Cond puedeAvanzar = monitor.newCond();
    private final Monitor.Cond puedeCircular = monitor.newCond();

    // Variable para controlar ticks pendientes
    private boolean tickPending = false;

    public CarreteraMonitor(int segmentos, int carriles) {
        this.SEGMENTOS = segmentos;
        this.CARRILES = carriles;
    }

    public Pos entrar(String id, int tks) {
        monitor.enter();
        try {
            while (carrilesLibres(1).isEmpty()) {
                puedeEntrar.await();
            }
            Set<Integer> libres = carrilesLibres(1);
            Pos pos = new Pos(1, libres.iterator().next());
            coches.put(id, new Pair<>(pos, tks));
            return pos;
        } finally {
            monitor.leave();
        }
    }

    public Pos avanzar(String id, int tks) {
        monitor.enter();
        try {
            Pair<Pos, Integer> info = coches.get(id);
            if (info == null)
                throw new IllegalArgumentException("Coche no encontrado: " + id);

            int siguienteSegmento = info.getFirst().getSegmento() + 1;
            while (carrilesLibres(siguienteSegmento).isEmpty()) {
                puedeAvanzar.await();
            }

            Set<Integer> libres = carrilesLibres(siguienteSegmento);
            Pos nuevaPos = new Pos(siguienteSegmento, libres.iterator().next());
            coches.put(id, new Pair<>(nuevaPos, tks));

            puedeEntrar.signal();
            puedeAvanzar.signal();
            return nuevaPos;
        } finally {
            monitor.leave();
        }
    }

    public void circulando(String id) {
        monitor.enter();
        try {
            Pair<Pos, Integer> info = coches.get(id);
            if (info == null)
                throw new IllegalArgumentException("Coche no encontrado: " + id);

            while (info.getSecond() > 0 || tickPending) {
                if (info.getSecond() <= 0) {
                    tickPending = false;
                    break;
                }
                puedeCircular.await();
                info = coches.get(id);
            }
        } finally {
            monitor.leave();
        }
    }

    public void salir(String id) {
        monitor.enter();
        try {
            coches.remove(id);
            puedeEntrar.signal();
            puedeAvanzar.signal();
        } finally {
            monitor.leave();
        }
    }

    public void tick() {
        monitor.enter();
        try {
            tickPending = true;

            // Reducir ticks de todos los coches
            for (Map.Entry<String, Pair<Pos, Integer>> entry : coches.entrySet()) {
                String id = entry.getKey();
                Pair<Pos, Integer> info = entry.getValue();
                coches.put(id, new Pair<>(info.getFirst(), Math.max(0, info.getSecond() - 1)));
            }

            // Solo una señal permitida
            puedeCircular.signal();
            tickPending = false;
        } finally {
            monitor.leave();
        }
    }

    private Set<Integer> carrilesLibres(int segmento) {
        Set<Integer> ocupados = new HashSet<>();
        for (Pair<Pos, Integer> info : coches.values()) {
            if (info.getFirst().getSegmento() == segmento) {
                ocupados.add(info.getFirst().getCarril());
            }
        }

        Set<Integer> libres = new HashSet<>();
        for (int c = 1; c <= CARRILES; c++) {
            if (!ocupados.contains(c))
                libres.add(c);
        }
        return libres;
    }

    private static class Pair<F, S> {
        private final F first;
        private final S second;

        public Pair(F first, S second) {
            this.first = first;
            this.second = second;
        }

        public F getFirst() {
            return first;
        }

        public S getSecond() {
            return second;
        }
    }
}