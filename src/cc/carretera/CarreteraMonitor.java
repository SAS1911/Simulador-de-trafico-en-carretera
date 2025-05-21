package cc.carretera;

import es.upm.babel.cclib.Monitor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Implementación del recurso compartido Carretera con Monitores
 */
public class CarreteraMonitor implements Carretera {
    private final int SEGMENTOS;
    private final int CARRILES;

    // Mapa que guarda la información de cada coche: posición y ticks restantes
    private final Map<String, Pair<Pos, Integer>> coches = new HashMap<>();

    // Monitor y condiciones para sincronización
    private final Monitor monitor = new Monitor();
    private final Monitor.Cond puedeEntrar = monitor.newCond();
    private final Monitor.Cond puedeAvanzar = monitor.newCond();
    private final Monitor.Cond puedeCircular = monitor.newCond();

    public CarreteraMonitor(int segmentos, int carriles) {
        this.SEGMENTOS = segmentos;
        this.CARRILES = carriles;
    }

    public Pos entrar(String id, int tks) {
        monitor.enter();
        try {
            // Esperar hasta que haya carriles libres en el primer segmento
            while (carrilesLibres(1).isEmpty()) {
                puedeEntrar.await();
            }

            // Seleccionar un carril libre
            Set<Integer> libres = carrilesLibres(1);
            int carrilElegido = libres.iterator().next();

            Pos pos = new Pos(1, carrilElegido);
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
            if (info == null) {
                throw new IllegalArgumentException("Coche no encontrado: " + id);
            }

            int siguienteSegmento = info.getFirst().getSegmento() + 1;

            // Esperar hasta que haya carriles libres en el siguiente segmento
            while (carrilesLibres(siguienteSegmento).isEmpty()) {
                puedeAvanzar.await();
            }

            // Seleccionar un carril libre
            Set<Integer> libres = carrilesLibres(siguienteSegmento);
            int carrilElegido = libres.iterator().next();

            Pos nuevaPos = new Pos(siguienteSegmento, carrilElegido);
            coches.put(id, new Pair<>(nuevaPos, tks));

            // Notificar a otros coches que podrían entrar o avanzar
            puedeEntrar.signalAll();
            puedeAvanzar.signalAll();

            return nuevaPos;
        } finally {
            monitor.leave();
        }
    }

    public void circulando(String id) {
        monitor.enter();
        try {
            Pair<Pos, Integer> info = coches.get(id);
            if (info == null) {
                throw new IllegalArgumentException("Coche no encontrado: " + id);
            }

            // Esperar hasta que los ticks lleguen a 0
            while (info.getSecond() > 0) {
                puedeCircular.await();
                info = coches.get(id); // Actualizar info después del tick
            }
        } finally {
            monitor.leave();
        }
    }

    public void salir(String id) {
        monitor.enter();
        try {
            coches.remove(id);
            // Notificar a otros coches que podrían entrar o avanzar
            puedeEntrar.signalAll();
            puedeAvanzar.signalAll();
        } finally {
            monitor.leave();
        }
    }

    public void tick() {
        monitor.enter();
        try {
            // Reducir en 1 los ticks de todos los coches
            for (Map.Entry<String, Pair<Pos, Integer>> entry : coches.entrySet()) {
                String id = entry.getKey();
                Pair<Pos, Integer> info = entry.getValue();
                int nuevosTicks = Math.max(0, info.getSecond() - 1);
                coches.put(id, new Pair<>(info.getFirst(), nuevosTicks));
            }

            // Notificar a TODOS los coches que están circulando
            puedeCircular.signalAll();
        } finally {
            monitor.leave();
        }
    }

    // Método auxiliar para obtener los carriles libres en un segmento
    private Set<Integer> carrilesLibres(int segmento) {
        Set<Integer> ocupados = new HashSet<>();

        for (Pair<Pos, Integer> info : coches.values()) {
            if (info.getFirst().getSegmento() == segmento) {
                ocupados.add(info.getFirst().getCarril());
            }
        }

        Set<Integer> libres = new HashSet<>();
        for (int c = 1; c <= CARRILES; c++) {
            if (!ocupados.contains(c)) {
                libres.add(c);
            }
        }

        return libres;
    }

    // Clase auxiliar Pair para tuplas
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