package cc.carretera;

import es.upm.aedlib.Pair;
import java.util.concurrent.locks.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CarreteraMonitor implements Carretera {
	private final int SEGMENTOS;
	private final int CARRILES;

	private final Map<String, Pair<Pos, Integer>> coches = new HashMap<>();
	private final Lock lock = new ReentrantLock();
	private final Condition puedeEntrar = lock.newCondition();
	private final Condition puedeAvanzar = lock.newCondition();
	private final Condition puedeCircular = lock.newCondition();

	public CarreteraMonitor(int segmentos, int carriles) {
		this.SEGMENTOS = segmentos;
		this.CARRILES = carriles;
	}

	public Pos entrar(String id, int tks) {
		lock.lock();
		try {
			while (carrilesLibres(1).isEmpty()) {
				puedeEntrar.await();
			}

			Set<Integer> libres = carrilesLibres(1);
			int carrilElegido = libres.iterator().next();
			Pos pos = new Pos(1, carrilElegido);
			coches.put(id, new Pair<>(pos, tks));

			return pos;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(e);
		} finally {
			lock.unlock();
		}
	}

	public Pos avanzar(String id, int tks) {
		lock.lock();
		try {
			Pair<Pos, Integer> info = coches.get(id);

			int siguienteSegmento = info.getLeft().getSegmento() + 1;
			while (carrilesLibres(siguienteSegmento).isEmpty()) {
				puedeAvanzar.await();
			}

			Set<Integer> libres = carrilesLibres(siguienteSegmento);
			Pos nuevaPos = new Pos(siguienteSegmento, libres.iterator().next());
			coches.put(id, new Pair<>(nuevaPos, tks));

			// Señalar que ha quedado una posicion libre
			if (info.getLeft().getSegmento() == 1) {
				puedeEntrar.signal();
			} else {
				puedeAvanzar.signal();
			}

			return nuevaPos;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(e);
		} finally {
			lock.unlock();
		}
	}

	public void circulando(String id) {
		lock.lock();
		try {
			Pair<Pos, Integer> infoCoche = coches.get(id);

			while (infoCoche.getRight() > 0) {
				puedeCircular.await();
				infoCoche = coches.get(id); // Actualizar la informacion del coche después del tick
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(e);
		} finally {
			lock.unlock();
		}
	}

	public void salir(String id) {
		lock.lock();
		try {
			coches.remove(id);
			// Señalar que ha quedado un espacio libre

			puedeAvanzar.signal();

		} finally {
			lock.unlock();
		}
	}

	public void tick() {
		lock.lock();
		try {
			// Reducimos ticks de todos los coches
			for (Map.Entry<String, Pair<Pos, Integer>> entry : coches.entrySet()) {
				String id = entry.getKey();
				Pair<Pos, Integer> info = entry.getValue();
				coches.put(id, new Pair<>(info.getLeft(), Math.max(0, info.getRight() - 1)));
			}

			// Dejamos que circulen todos los coches
			puedeCircular.signalAll();

		} finally {
			lock.unlock();
		}
	}

	// Metodo auxiliar para ver los carriles libres de un segmento
	private Set<Integer> carrilesLibres(int segmento) {
		Set<Integer> ocupados = new HashSet<>();
		for (Pair<Pos, Integer> info : coches.values()) {
			if (info.getLeft().getSegmento() == segmento) {
				ocupados.add(info.getLeft().getCarril());
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
}