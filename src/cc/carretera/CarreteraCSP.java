package cc.carretera;

import org.jcsp.lang.*;
import java.util.*;

public class CarreteraCSP implements Carretera {
    private final int SEGMENTOS;
    private final int CARRILES;

    // Canales para recibir solicitudes de coches y del reloj
    private final Any2OneChannel entrarChan = Channel.any2one();
    private final Any2OneChannel avanzarChan = Channel.any2one();
    private final Any2OneChannel circulaChan = Channel.any2one();
    private final Any2OneChannel salirChan = Channel.any2one();
    private final Any2OneChannel tickChan = Channel.any2one();

    public CarreteraCSP(int segmentos, int carriles) {
        this.SEGMENTOS = segmentos;
        this.CARRILES = carriles;
        new ProcessManager(new CarreteraProceso()).start(); // Inicia el proceso CSP
    }

    @Override
    public Pos entrar(String id, int tks) {
        One2OneChannel resp = Channel.one2one();
        entrarChan.out().write(new Object[] { id, tks, resp });
        return (Pos) resp.in().read();
    }

    @Override
    public Pos avanzar(String id, int tks) {
        One2OneChannel resp = Channel.one2one();
        avanzarChan.out().write(new Object[] { id, tks, resp });
        return (Pos) resp.in().read();
    }

    @Override
    public void circulando(String id) {
        One2OneChannel resp = Channel.one2one();
        circulaChan.out().write(new Object[] { id, resp });
        resp.in().read();
    }

    @Override
    public void salir(String id) {
        salirChan.out().write(id);
    }

    @Override
    public void tick() {
        tickChan.out().write(null);
    }

    // Proceso principal de la carretera
    private class CarreteraProceso implements CSProcess {
        private final Map<String, Pos> posiciones = new HashMap<>(); // posiciones actuales
        private final Map<String, Integer> ticksRestantes = new HashMap<>(); // ticks que faltan
        private final Map<Integer, Set<Integer>> ocupados = new HashMap<>(); // carriles ocupados por segmento
        private final Queue<Object[]> esperaEntrar = new LinkedList<>();
        private final Queue<Object[]> esperaAvanzar = new LinkedList<>();
        private final Map<String, One2OneChannel> esperandoCirculando = new HashMap<>();

        public void run() {
            for (int i = 1; i <= SEGMENTOS; i++) {
                ocupados.put(i, new HashSet<Integer>());
            }

            AltingChannelInput[] entradas = {
                    tickChan.in(), entrarChan.in(), avanzarChan.in(), circulaChan.in(), salirChan.in()
            };

            Alternative alt = new Alternative(entradas);

            while (true) {
                int index = alt.select();
                switch (index) {
                    case 0:
                        procesarTick();
                        break; // reloj
                    case 1:
                        procesarEntrar();
                        break; // petición de entrada
                    case 2:
                        procesarAvanzar();
                        break; // petición de avance
                    case 3:
                        procesarCirculando();
                        break; // notificación de circulación
                    case 4:
                        procesarSalir();
                        break; // notificación de salida
                }
            }
        }

        private void procesarTick() {
            tickChan.in().read();
            for (String id : new HashSet<>(ticksRestantes.keySet())) {
                int nuevos = Math.max(0, ticksRestantes.get(id) - 1);
                ticksRestantes.put(id, nuevos);
                if (nuevos == 0 && esperandoCirculando.containsKey(id)) {
                    esperandoCirculando.get(id).out().write(null);
                    esperandoCirculando.remove(id);
                }
            }
            intentarAvancesPendientes();
            intentarEntradasPendientes();
        }

        private void procesarEntrar() {
            Object[] msg = (Object[]) entrarChan.in().read();
            String id = (String) msg[0];
            int tks = (Integer) msg[1];
            One2OneChannel resp = (One2OneChannel) msg[2];

            if (intentarColocarEntrada(id, tks, resp))
                return;
            esperaEntrar.add(msg);
        }

        private void procesarAvanzar() {
            Object[] msg = (Object[]) avanzarChan.in().read();
            String id = (String) msg[0];
            int tks = (Integer) msg[1];
            One2OneChannel resp = (One2OneChannel) msg[2];

            if (intentarAvance(id, tks, resp))
                return;
            esperaAvanzar.add(msg);
        }

        private void procesarCirculando() {
            Object[] msg = (Object[]) circulaChan.in().read();
            String id = (String) msg[0];
            One2OneChannel resp = (One2OneChannel) msg[1];

            if (ticksRestantes.get(id) == 0) {
                resp.out().write(null);
            } else {
                esperandoCirculando.put(id, resp);
            }
        }

        private void procesarSalir() {
            String id = (String) salirChan.in().read();
            Pos pos = posiciones.get(id);
            if (pos != null && ticksRestantes.get(id) == 0) {
                ocupados.get(pos.getSegmento()).remove(pos.getCarril());
                posiciones.remove(id);
                ticksRestantes.remove(id);
                intentarEntradasPendientes();
                intentarAvancesPendientes();
            }
        }

        private void intentarEntradasPendientes() {
            Queue<Object[]> nuevaCola = new LinkedList<>();
            while (!esperaEntrar.isEmpty()) {
                Object[] msg = esperaEntrar.poll();
                String id = (String) msg[0];
                int tks = (Integer) msg[1];
                One2OneChannel resp = (One2OneChannel) msg[2];
                if (!intentarColocarEntrada(id, tks, resp)) {
                    nuevaCola.add(msg);
                }
            }
            esperaEntrar.addAll(nuevaCola);
        }

        private boolean intentarColocarEntrada(String id, int tks, One2OneChannel resp) {
            for (int c = 1; c <= CARRILES; c++) {
                if (!ocupados.get(1).contains(c)) {
                    Pos pos = new Pos(1, c);
                    posiciones.put(id, pos);
                    ticksRestantes.put(id, tks);
                    ocupados.get(1).add(c);
                    resp.out().write(pos);
                    return true;
                }
            }
            return false;
        }

        private void intentarAvancesPendientes() {
            Queue<Object[]> nuevaCola = new LinkedList<>();
            boolean huboCambio = false;
            while (!esperaAvanzar.isEmpty()) {
                Object[] msg = esperaAvanzar.poll();
                String id = (String) msg[0];
                int tks = (Integer) msg[1];
                One2OneChannel resp = (One2OneChannel) msg[2];
                if (!intentarAvance(id, tks, resp)) {
                    nuevaCola.add(msg);
                } else {
                    huboCambio = true;
                }
            }
            esperaAvanzar.addAll(nuevaCola);
            if (huboCambio)
                intentarAvancesPendientes();
        }

        private boolean intentarAvance(String id, int tks, One2OneChannel resp) {
            Pos pos = posiciones.get(id);
            if (ticksRestantes.get(id) > 0 || pos.getSegmento() >= SEGMENTOS) {
                return false;
            }
            int sigSeg = pos.getSegmento() + 1;
            for (int c = 1; c <= CARRILES; c++) {
                if (!ocupados.get(sigSeg).contains(c)) {
                    ocupados.get(pos.getSegmento()).remove(pos.getCarril());
                    ocupados.get(sigSeg).add(c);
                    Pos nueva = new Pos(sigSeg, c);
                    posiciones.put(id, nueva);
                    ticksRestantes.put(id, tks);
                    resp.out().write(nueva);
                    intentarEntradasPendientes();
                    intentarAvancesPendientes();
                    return true;
                }
            }
            return false;
        }
    }
}
