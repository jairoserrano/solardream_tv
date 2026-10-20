# SolarDream: protector de pantalla de energía para Android TV

Fecha: 2026-10-19
Estado: diseño aprobado, pendiente de revisión del spec escrito

## Objetivo

Mostrar en la TCL Google TV ("TV del estudio", 192.168.88.3) un protector de pantalla que muestre en tiempo real la producción solar, el consumo de la casa y el intercambio con la red, leyendo directamente el DTU Hoymiles por la red local, sin depender de la nube de Hoymiles.

## Contexto verificado

- TV: TCL Google TV, Android 12 (SDK 31), 2 GB de RAM (~800 MB disponibles), ABI armeabi-v7a (32 bits). Acceso por ADB en 192.168.88.3:5555.
- DTU: Hoymiles DTU-Pro en 192.168.88.16, firmware V00.07.02. Puerto 10081 abierto (protocolo local de Hoymiles, mensajes protobuf). El puerto 502 (Modbus TCP) está cerrado.
- Inversores: dos microinversores (4 entradas y 2 entradas).
- Medidor: DDSU666, accesible por el mismo puerto. `get-real-data-new` devuelve `meterData` con potencia total y por fase, voltaje, corriente, factor de potencia y energías acumuladas.
- La consulta respondió sin credenciales y sin cifrado, usando la librería Python `hoymiles-wifi` 0.6.0 (solo comandos de lectura).

## Alcance

Dentro:
- Una app Android (Kotlin) con un `DreamService` (protector de pantalla), sin actividad propia salvo la mínima que Android exija para registrarse.
- Cliente del protocolo local del DTU, modelo de datos y pantalla con el diseño "flujo de energía".
- Instalación por ADB y activación con `settings put secure screensaver_components`.

Fuera (YAGNI):
- Control del DTU (límites de potencia, reinicio, firmware). La app es solo lectura.
- Histórico, gráficas, notificaciones, acceso remoto o nube.
- Soporte para otros modelos de DTU o más de un DTU.

## Arquitectura

Una sola app con tres unidades de responsabilidad única:

1. **Cliente del DTU** (`DtuClient`): abre TCP a `<ip>:10081`, envía la petición de datos en tiempo real, decodifica la respuesta protobuf y devuelve una lectura cruda. No sabe nada de pantalla.
   - Interfaz: `suspend fun readRealData(): RawReading`.
2. **Modelo de energía** (`EnergyModel`): convierte `RawReading` en `EnergySnapshot` (producción W, red W con signo, consumo W, energía del día, marca de tiempo, estado). Contiene toda la lógica de signos y unidades.
   - Interfaz: `fun toSnapshot(raw: RawReading): EnergySnapshot`.
3. **Pantalla** (`SolarDreamService` y vista): sondea cada 5 a 10 s con `DtuClient`, pasa el resultado por `EnergyModel` y dibuja el flujo Paneles → Casa → Red.

La IP del DTU es un ajuste de la app (valor inicial 192.168.88.16), no una constante en el código.

## Pantalla (diseño elegido: B, flujo de energía)

- Tres nodos circulares: Paneles (amarillo), Casa (azul), Red (verde al exportar, naranja al importar), cada uno con su cifra en kW debajo.
- Flechas entre nodos, animadas solo cuando hay flujo. Cuando se importa de la red, la flecha final cambia de sentido (Red → Casa) y se pone naranja.
- De noche, sin producción: el nodo de paneles se apaga y la flecha va de Red a Casa.
- Línea pequeña inferior con la energía del día (producida y consumida) y, en una esquina, la hora de la última lectura.
- Fondo oscuro. Desplazamiento de unos píxeles cada minuto para evitar quemado de pantalla.

## Origen de cada valor

- **Paneles**: potencia de los inversores en `get-real-data-new`.
- **Red**: potencia total del medidor (`phaseTotalPower`).
- **Casa**: calculada como producción + importación − exportación; no se lee directamente.
- Los signos, unidades y la energía del día se calibran con lecturas de día y se fijan en `EnergyModel` antes de implementar la pantalla.

Hipótesis a confirmar con lecturas de día (no verificadas):
- Voltaje en décimas de voltio y corriente en miliamperios.
- Significado del signo de `phaseTotalPower` (importación o exportación).
- Significado de `energyTotalPower` frente a `energyTotalConsumed`.
- Campo de potencia de los inversores (ausente de noche).

## Errores

- Si el DTU no responde tres veces seguidas: nodos atenuados y mensaje "Sin conexión · última lectura a las HH:MM". Nunca se muestran cifras viejas como actuales.
- Si responde sin datos de medidor: se muestra solo la producción, y Casa y Red aparecen como "n/d".
- Timeouts de red acotados para no bloquear el hilo de la pantalla; la lectura corre fuera del hilo principal.

## Pruebas

- **DtuClient**: pruebas unitarias con respuestas reales grabadas del DTU (noche y día) para que el parseo sea determinista.
- **EnergyModel**: pruebas con casos de exportación, importación y noche, y con medidor ausente.
- **Pantalla**: pruebas de la lógica que decide dirección de flechas y colores.
- **En la TV**: instalar por ADB, activar el protector y dispararlo para verlo; verificar consumo de RAM.

## Riesgos

- **Protector de pantalla en Google TV**: el menú puede ofrecer solo protectores de Google. Mitigación: forzar con `settings put secure screensaver_components`. Si no funciona en esta TCL, el plan B es abrir la misma vista como actividad desde el lanzador.
- **Protocolo**: no hay documentación oficial; se implementa a partir de la librería `hoymiles-wifi` y de capturas reales. Cambios de firmware del DTU podrían romperlo.
- **Memoria**: la TV tiene ~800 MB libres; la app debe mantenerse ligera (sin WebView, sin librerías pesadas).
- **ABI de 32 bits** (armeabi-v7a): evitar dependencias nativas que no lo soporten.

## Pasos previos a la implementación

1. Grabar lecturas reales del DTU de día y de noche (`get-real-data-new` en JSON y, si hace falta, bytes crudos) para usarlas como fixtures.
2. Calibrar signos y unidades con esas lecturas.
3. Confirmar que el protector de pantalla se puede activar en la TV con ADB.
