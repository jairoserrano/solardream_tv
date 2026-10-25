# SolarDream TV

Protector de pantalla nativo para Android TV y Google TV que muestra el flujo de energía entre los paneles solares, el hogar y la red eléctrica. Lee los datos de un DTU Hoymiles por la red local, sin depender de una API en la nube.

La interfaz está desarrollada en Kotlin con Android Canvas y se ejecuta como un `DreamService` a pantalla completa. Versión actual: **0.1.0**.

## Experiencia visual

- Casa central con volumen e iluminación suave, acompañada de paneles solares y una torre eléctrica dibujados en Canvas.
- Fondo oscuro, cifras grandes en kW y resumen de la energía producida hoy en kWh.
- Partículas que indican el sentido del flujo: paneles hacia el hogar, hogar hacia la red o red hacia el hogar.
- Producción en dorado, exportación en verde menta e importación en ámbar; el hogar utiliza azul como referencia visual.
- Paneles en reposo cuando no hay producción suficiente para activar el flujo.
- Animación detenida cuando no hay flujos activos, se pierde la conexión o la ventana está oculta.
- Desplazamiento leve de la composición cada minuto para reducir la permanencia de elementos en una misma posición.

La composición utiliza una referencia de 1600 × 900 y se escala conservando sus proporciones.

## Datos y comportamiento

| Indicador | Interpretación |
| --- | --- |
| Producción solar | Potencia instantánea del DTU, mostrada en kW. |
| Consumo del hogar | Producción menos intercambio con la red, con un mínimo de cero. Requiere medidor. |
| Red eléctrica | Magnitud de la potencia intercambiada, acompañada del estado de importación o exportación. |
| Producción de hoy | Energía diaria acumulada, mostrada en kWh. |
| Última lectura | Hora local de la última consulta completada correctamente. |

Internamente, `gridW > 0` significa exportación y `gridW < 0` significa importación. Los flujos se activan desde **5 W**. Sin medidor, el consumo del hogar y el intercambio con la red aparecen como `n/d`.

El sondeo se ejecuta en segundo plano, con una espera de **4 segundos después de terminar cada consulta**. Una consulta puede incluir varias páginas, separadas por 2 segundos, por lo que la actualización completa puede tardar más de 4 segundos. El cliente usa TCP en el puerto **10081** y tiempos de espera de conexión y lectura de 5 segundos.

Antes de recibir datos se muestra el estado de conexión. Tras **3 fallos consecutivos**, se conservan las últimas cifras, se atenúa la escena y se informa la hora de la última lectura. Una consulta correcta restablece el estado conectado.

## Requisitos de desarrollo

- JDK 17.
- Android SDK con plataforma API 35 y herramientas de compilación correspondientes.
- Android SDK Platform-Tools (`adb`) para instalar y configurar la TV.
- Acceso a los repositorios de dependencias durante la primera compilación.

El proyecto incluye Gradle Wrapper **8.9**, Android Gradle Plugin **8.7.3** y Kotlin **2.0.21**. La configuración Android es `minSdk = 31` (Android 12), `targetSdk = 36` (Android 16) y `compileSdk = 36`.

La TV debe permitir protectores de pantalla mediante `DreamService` y alcanzar el DTU por la red local. La compatibilidad del protocolo con otros modelos o versiones de firmware requiere verificación.

## Compilación y pruebas

Abre la raíz del proyecto en Android Studio y configura el SDK local. Para compilar desde PowerShell, establece `JAVA_HOME` en un JDK 17 y configura `sdk.dir` en `local.properties` o la ubicación del SDK en tu entorno.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

En Linux o macOS:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

El APK de depuración se genera en `app/build/outputs/apk/debug/app-debug.apk`.

Para generar el paquete firmado de producción (Android App Bundle) para Google Play:

```powershell
.\gradlew.bat :app:bundleRelease
```

El bundle se genera en `app/build/outputs/bundle/release/app-release.aab`. Para la guía completa de publicación, consulta [CHECKLIST_PUBLICACION.md](CHECKLIST_PUBLICACION.md).

Las pruebas unitarias cubren CRC, codificación y lectura del protocolo, combinación de páginas, cliente TCP, normalización de energía, seguimiento de conexión y estados de presentación. Las pruebas de `DtuClient` utilizan sockets locales; el entorno de ejecución debe permitirlos. La apariencia y el comportamiento del protector requieren además comprobación en una TV.

## Instalación y configuración

Activa la depuración en la TV y autoriza la conexión ADB. Los siguientes ejemplos suponen que `adb` está en el `PATH` y que la TV admite ADB por TCP. Sustituye la dirección y el puerto por los de tu dispositivo; si utiliza emparejamiento inalámbrico, complétalo primero.

```powershell
$tv = "192.168.1.50:5555"
adb connect $tv
adb -s $tv install -r app/build/outputs/apk/debug/app-debug.apk
```

Configura la dirección del DTU y selecciona SolarDream como protector de pantalla:

```powershell
adb -s $tv shell settings put global solardream_dtu_host 192.168.1.60
adb -s $tv shell settings put secure screensaver_components dev.jairoserrano.solardream/dev.jairoserrano.solardream.SolarDreamService
adb -s $tv shell settings put secure screensaver_enabled 1
```

Si no se configura `solardream_dtu_host`, se utiliza la dirección predeterminada `192.168.88.16`. También puedes abrir la app directamente desde el lanzador de tu TV (Google TV / Android TV) y presionar el botón **OK** o **Menú** de tu control remoto para cambiar la IP del DTU en pantalla.

La app se puede iniciar como protector de pantalla o directamente como aplicación desde el lanzador del sistema:

```powershell
adb -s $tv shell am start -n dev.jairoserrano.solardream/.MainActivity
```

También puedes activar o previsualizar Solar Dream desde los ajustes de protector de pantalla de la TV. Para consultar el servicio y los avisos de comunicación:

```powershell
adb -s $tv shell dumpsys dreams
adb -s $tv logcat -d -s SolarDream:W
```

## Arquitectura

| Componente | Responsabilidad |
| --- | --- |
| `SolarDreamService` | Aloja la vista y arranca o detiene el sondeo con el ciclo de vida del protector. |
| `DtuSettings` | Obtiene la dirección del DTU desde `Settings.Global`. |
| `EnergyPoller` | Consulta en un hilo de fondo y entrega el estado visual al hilo principal. |
| `dtu/` | Cliente TCP, tramas, CRC16 y lectura/escritura de campos Protobuf. |
| `model/` | Convierte unidades, calcula el balance energético y conserva el estado de conexión. |
| `ui/FlowState` | Decide textos, direcciones, estados de red y atenuación sin depender de Android. |
| `ui/FlowView` | Dibuja la escena y controla las partículas y el desplazamiento periódico. |

La aplicación no incorpora dependencias externas de ejecución. JUnit se usa para las pruebas; Python solo interviene en la herramienta opcional de generación de fixtures.

## Calibración y estado de validación

La escala de potencia del medidor se contrastó con una lectura nocturna y un consumo aproximado de 1,5 kW. Los valores usados actualmente en `MeterConvention` son:

| Parámetro | Valor actual | Estado |
| --- | --- | --- |
| `meterPowerWattsPerUnit` | `10.0` W por unidad | Contrastado con lectura nocturna. |
| `importIsPositive` | `false` | Importación negativa observada de noche; pendiente contraste diurno. |
| `dtuPowerWattsPerUnit` | `0.1` W por unidad | Pendiente contraste con producción diurna. |
| `dailyEnergyWhPerUnit` | `1.0` Wh por unidad | Pendiente contraste con una referencia de energía diaria. |

El registro de integración documenta la activación del protector en la TV. La revisión visual del nuevo diseño en el dispositivo y la calibración diurna siguen pendientes. Consulta [el registro de calibración](docs/calibration.md) para las mediciones disponibles.

## Fixtures de protocolo

Las tramas de referencia están incluidas en `app/src/test/resources/fixtures/`; no es necesario regenerarlas para ejecutar las pruebas. Para recrearlas con la biblioteca Python `hoymiles-wifi`, desde la raíz del proyecto en Windows:

```powershell
python -m venv tools/.venv
tools/.venv/Scripts/python -m pip install hoymiles-wifi
tools/.venv/Scripts/python tools/make_golden.py
```

La herramienta sobrescribe los archivos de referencia. Revisa sus diferencias antes de incorporarlas al repositorio.

## Documentación adicional

- [Diseño inicial](docs/superpowers/specs/2026-10-19-solardream-design.md).
- [Plan de implementación inicial](docs/superpowers/plans/2026-10-19-solardream.md).
- [Calibración e integración con la TV](docs/calibration.md).

Los documentos de diseño y planificación describen la propuesta inicial y pueden contener pasos o alternativas que no se implementaron. El código y este README describen el comportamiento actual.
