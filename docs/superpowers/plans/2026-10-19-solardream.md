# SolarDream Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Un protector de pantalla (DreamService) para la TCL Google TV que muestra en tiempo real producción solar, consumo de la casa y red, leyendo el DTU Hoymiles por la red local.

**Architecture:** Una app Android en Kotlin sin dependencias externas. `DtuClient` habla el protocolo local de Hoymiles (TCP 10081, tramas `HM` + protobuf escrito a mano), `EnergyModel` convierte la lectura cruda en un `EnergySnapshot`, `ConnectionTracker` + `FlowState` deciden qué se muestra, y `FlowView` (Canvas) dibuja el flujo Paneles → Casa → Red. `EnergyPoller` une todo con un hilo en segundo plano y lo usan tanto `SolarDreamService` como la actividad de respaldo.

**Tech Stack:** Kotlin 2.0.21, Android Gradle Plugin 8.7.3, Gradle 8.9, JDK 21, compileSdk 35, minSdk 31, JUnit 4. Python 3 con `hoymiles-wifi` solo para herramientas de captura (no forma parte de la app).

**Spec:** `docs/superpowers/specs/2026-10-19-solardream-design.md`

## Global Constraints

- Lenguaje Kotlin; la app es un `DreamService` (protector de pantalla), sin WebView ni librerías nativas.
- TV destino: TCL Google TV, Android 12 (SDK 31), 2 GB de RAM, ABI armeabi-v7a (32 bits). `minSdk = 31`.
- DTU: Hoymiles DTU-Pro en `192.168.88.16`, TCP puerto `10081`. La IP es un ajuste (`settings put global solardream_dtu_host <ip>`), no una constante en el código fuente de la lógica.
- La app es de solo lectura: el único comando que envía al DTU es la petición de datos en tiempo real (`0xA3 0x11`). Nada de límites de potencia, reinicios ni firmware.
- Si el DTU no responde tres veces seguidas: nodos atenuados y el texto `Sin conexión · última lectura a las HH:MM`. Nunca se muestran cifras viejas como actuales.
- Si responde sin datos de medidor: se muestra solo la producción; Casa y Red aparecen como `n/d`.
- Diseño B (flujo de energía): tres nodos Paneles (amarillo) → Casa (azul) → Red (verde al exportar, naranja al importar), con flechas animadas solo cuando hay flujo; desplazamiento de unos píxeles cada minuto contra quemado de pantalla; fondo oscuro.
- Textos de la interfaz en español; cifras en kW con coma decimal (`2,41 kW`).
- Commits: terminan con la línea `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.
- Todos los comandos `adb` usan `-s 192.168.88.3:5555` (hay más dispositivos conectados al PC).

## Desviaciones respecto al spec (a confirmar con el usuario al entregar)

1. `DtuClient.readRealData()` es una función bloqueante que corre en un hilo de fondo, no `suspend`: evita añadir la dependencia de corrutinas.
2. El pie de pantalla muestra solo la energía **producida** hoy. La energía **consumida** hoy necesita una línea base persistente de los contadores del medidor y se deja para una segunda iteración.

## Mapa de archivos

```
settings.gradle.kts, build.gradle.kts, gradle.properties, .gitignore, local.properties (no versionado)
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/java/com/jairo/solardream/
  dtu/Crc16.kt            CRC-16/MODBUS
  dtu/DtuProtocolException.kt
  dtu/ProtoReader.kt      lector mínimo de protobuf
  dtu/ProtoWriter.kt      escritor mínimo de protobuf
  dtu/RawReading.kt       lectura cruda combinada
  dtu/DtuProtocol.kt      construir petición, parsear respuesta, combinar páginas
  dtu/DtuClient.kt        E/S por socket
  model/MeterConvention.kt
  model/EnergySnapshot.kt
  model/EnergyModel.kt    RawReading -> EnergySnapshot
  model/ConnectionTracker.kt
  ui/FlowState.kt         qué se muestra (texto, flechas, colores lógicos)
  ui/FlowView.kt          dibujo en Canvas
  EnergyPoller.kt         hilo de sondeo
  DtuSettings.kt          IP del DTU
  SolarDreamService.kt    el protector de pantalla
  MainActivity.kt         respaldo (Tarea 9, solo si el protector no se activa)
app/src/test/java/com/jairo/solardream/...  pruebas JUnit
app/src/test/resources/fixtures/*.bin       tramas de referencia
tools/make_golden.py      genera tramas de referencia con la librería oficial
tools/capture_fixture.py  captura tramas reales del DTU
docs/calibration.md       resultado de la calibración
```

---

### Task 1: Proyecto Gradle y CRC-16

**Files:**
- Create: `.gitignore`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `local.properties`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/jairo/solardream/dtu/Crc16.kt`
- Test: `app/src/test/java/com/jairo/solardream/dtu/Crc16Test.kt`

**Interfaces:**
- Produces: `object Crc16 { fun compute(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Int }` (devuelve un entero 0..0xFFFF).

- [ ] **Step 1: Crear los archivos de configuración de Gradle**

`.gitignore`:
```
.gradle/
build/
local.properties
.idea/
*.iml
*.apk
tools/.venv/
.superpowers/
captures/
```

`settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "SolarDream"
include(":app")
```

`build.gradle.kts`:
```kotlin
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}
```

`gradle.properties`:
```
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
android.nonTransitiveRClass=true
kotlin.code.style=official
```

`local.properties`:
```
sdk.dir=C\:\\Users\\jairo\\AppData\\Local\\Android\\Sdk
```

`app/build.gradle.kts`:
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.jairo.solardream"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jairo.solardream"
        minSdk = 31
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
```

`app/src/main/AndroidManifest.xml` (los componentes se añaden en tareas posteriores):
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-feature android:name="android.software.leanback" android:required="false" />
    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />

    <application
        android:allowBackup="false"
        android:label="Solar Dream" />
</manifest>
```

- [ ] **Step 2: Generar el wrapper de Gradle**

Run (PowerShell, desde `C:\Users\jairo\SolarDream`):
```powershell
New-Item -ItemType Directory -Force C:\Users\jairo\tools | Out-Null
Invoke-WebRequest https://services.gradle.org/distributions/gradle-8.9-bin.zip -OutFile C:\Users\jairo\tools\gradle-8.9-bin.zip
Expand-Archive C:\Users\jairo\tools\gradle-8.9-bin.zip C:\Users\jairo\tools -Force
C:\Users\jairo\tools\gradle-8.9\bin\gradle.bat wrapper --gradle-version 8.9
```
Expected: `BUILD SUCCESSFUL` y existen `gradlew.bat` y `gradle/wrapper/gradle-wrapper.jar`.

- [ ] **Step 3: Escribir la prueba que falla**

`app/src/test/java/com/jairo/solardream/dtu/Crc16Test.kt`:
```kotlin
package com.jairo.solardream.dtu

import org.junit.Assert.assertEquals
import org.junit.Test

class Crc16Test {
    @Test
    fun `vector estandar CRC-16 MODBUS`() {
        assertEquals(0x4B37, Crc16.compute("123456789".toByteArray(Charsets.US_ASCII)))
    }

    @Test
    fun `respeta offset y longitud`() {
        val data = "xx123456789yy".toByteArray(Charsets.US_ASCII)
        assertEquals(0x4B37, Crc16.compute(data, offset = 2, length = 9))
    }

    @Test
    fun `entrada vacia devuelve el valor inicial`() {
        assertEquals(0xFFFF, Crc16.compute(ByteArray(0)))
    }
}
```

- [ ] **Step 4: Ejecutar la prueba y comprobar que falla**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.jairo.solardream.dtu.Crc16Test"`
Expected: FAIL con `Unresolved reference: Crc16` (error de compilación).

- [ ] **Step 5: Implementar**

`app/src/main/java/com/jairo/solardream/dtu/Crc16.kt`:
```kotlin
package com.jairo.solardream.dtu

/** CRC-16/MODBUS: polinomio 0xA001 (reflejado), valor inicial 0xFFFF. */
object Crc16 {
    fun compute(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Int {
        var crc = 0xFFFF
        for (i in offset until offset + length) {
            crc = crc xor (data[i].toInt() and 0xFF)
            repeat(8) {
                crc = if ((crc and 1) != 0) (crc ushr 1) xor 0xA001 else crc ushr 1
            }
        }
        return crc
    }
}
```

- [ ] **Step 6: Ejecutar la prueba y comprobar que pasa**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.jairo.solardream.dtu.Crc16Test"`
Expected: PASS (3 pruebas).

- [ ] **Step 7: Commit**

```powershell
git add .
git commit -m "feat: scaffold Gradle project and CRC-16" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Tramas de referencia y protocolo del DTU

**Files:**
- Create: `tools/make_golden.py`
- Create: `app/src/test/resources/fixtures/*.bin` (generados por el script)
- Create: `app/src/main/java/com/jairo/solardream/dtu/DtuProtocolException.kt`, `ProtoReader.kt`, `ProtoWriter.kt`, `RawReading.kt`, `DtuProtocol.kt`
- Create: `app/src/test/java/com/jairo/solardream/Fixtures.kt`
- Test: `app/src/test/java/com/jairo/solardream/dtu/DtuProtocolTest.kt`

**Interfaces:**
- Consumes: `Crc16.compute(data, offset, length): Int`
- Produces:
  - `class DtuProtocolException(message: String) : java.io.IOException(message)`
  - `data class RawReading(val deviceSerial: String, val timestamp: Long, val meterTotalPower: Int?, val dtuPower: Long, val dtuDailyEnergy: Long)`
  - `object DtuProtocol` con:
    - `data class Page(val deviceSerial: String, val timestamp: Long, val totalPages: Int, val page: Int, val meterTotalPower: Int?, val dtuPower: Long, val dtuDailyEnergy: Long)`
    - `fun buildRealDataRequest(seq: Int, timeText: String, epochSec: Long, page: Int): ByteArray`
    - `fun parseRealDataResponse(frame: ByteArray): Page`
    - `fun merge(pages: List<Page>): RawReading`
  - `object Fixtures { fun load(name: String): ByteArray }` (código de pruebas)

- [ ] **Step 1: Crear el generador de tramas de referencia**

`tools/make_golden.py`:
```python
"""Genera tramas de referencia con la librería oficial hoymiles-wifi.

Uso (desde la raíz del repo):
    python -m venv tools/.venv
    tools/.venv/Scripts/python -m pip install hoymiles-wifi
    tools/.venv/Scripts/python tools/make_golden.py
"""
import struct
from pathlib import Path

from crcmod import mkCrcFun
from hoymiles_wifi.protobuf import RealDataNew_pb2 as pb

OUT = Path(__file__).resolve().parent.parent / "app/src/test/resources/fixtures"
crc16 = mkCrcFun(0x18005, rev=True, initCrc=0xFFFF, xorOut=0x0000)


def frame(payload: bytes, seq: int = 1) -> bytes:
    return b"HM\xa3\x11" + struct.pack(">HHH", seq, crc16(payload), len(payload) + 10) + payload


def response(ap, cp, meter=False, dtu=False):
    r = pb.RealDataNewReqDTO()
    r.device_serial_number = "400000000001"
    r.timestamp = 1792463207
    r.ap = ap
    r.cp = cp
    if meter:
        m = r.meter_data.add()
        m.device_type = 1
        m.serial_number = 12000000000001
        m.phase_total_power = -143
        m.phase_A_power = -65
        m.phase_B_power = -77
        m.voltage_phase_A = 1243
        m.voltage_phase_B = 1216
        m.current_phase_A = 586
        m.current_phase_B = 721
    if dtu:
        pv = r.pv_data.add()
        pv.serial_number = 11000000000001
        pv.port_number = 1
        pv.power = 6000
        pv.energy_total = 1082806
        r.dtu_power = 24100
        r.dtu_daily_energy = 8200
    return frame(r.SerializeToString())


def main():
    OUT.mkdir(parents=True, exist_ok=True)

    req = pb.RealDataNewResDTO()
    req.time_ymd_hms = b"2026-10-19 21:30:00"
    req.offset = 28800
    req.time = 1792463400
    req.cp = 2
    (OUT / "golden_request_cp2.bin").write_bytes(frame(req.SerializeToString()))

    (OUT / "golden_response_p1of4.bin").write_bytes(response(ap=4, cp=1, meter=True, dtu=True))
    (OUT / "golden_single.bin").write_bytes(response(ap=1, cp=0, meter=True, dtu=True))
    (OUT / "golden_page0_of2.bin").write_bytes(response(ap=2, cp=0, dtu=True))
    (OUT / "golden_page1_of2.bin").write_bytes(response(ap=2, cp=1, meter=True))
    print("escrito en", OUT)


main()
```

- [ ] **Step 2: Ejecutar el generador**

Run:
```powershell
python -m venv tools/.venv
tools/.venv/Scripts/python -m pip install hoymiles-wifi
tools/.venv/Scripts/python tools/make_golden.py
```
Expected: imprime `escrito en ...\app\src\test\resources\fixtures` y existen 5 archivos `.bin`.

- [ ] **Step 3: Escribir el helper de fixtures y las pruebas que fallan**

`app/src/test/java/com/jairo/solardream/Fixtures.kt`:
```kotlin
package com.jairo.solardream

object Fixtures {
    fun load(name: String): ByteArray {
        val stream = checkNotNull(Fixtures::class.java.classLoader.getResourceAsStream("fixtures/$name")) {
            "falta el fixture $name"
        }
        return stream.use { it.readBytes() }
    }
}
```

`app/src/test/java/com/jairo/solardream/dtu/DtuProtocolTest.kt`:
```kotlin
package com.jairo.solardream.dtu

import com.jairo.solardream.Fixtures
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class DtuProtocolTest {

    @Test
    fun `la peticion es identica a la que genera la libreria oficial`() {
        val built = DtuProtocol.buildRealDataRequest(
            seq = 1,
            timeText = "2026-10-19 21:30:00",
            epochSec = 1792463400L,
            page = 2,
        )
        assertArrayEquals(Fixtures.load("golden_request_cp2.bin"), built)
    }

    @Test
    fun `parsea todos los campos de la respuesta`() {
        val page = DtuProtocol.parseRealDataResponse(Fixtures.load("golden_response_p1of4.bin"))
        assertEquals("400000000001", page.deviceSerial)
        assertEquals(1792463207L, page.timestamp)
        assertEquals(4, page.totalPages)
        assertEquals(1, page.page)
        assertEquals(-143, page.meterTotalPower)
        assertEquals(24100L, page.dtuPower)
        assertEquals(8200L, page.dtuDailyEnergy)
    }

    @Test
    fun `una pagina sin medidor devuelve potencia de medidor nula`() {
        val page = DtuProtocol.parseRealDataResponse(Fixtures.load("golden_page0_of2.bin"))
        assertNull(page.meterTotalPower)
        assertEquals(24100L, page.dtuPower)
    }

    @Test
    fun `un CRC alterado se rechaza`() {
        val frame = Fixtures.load("golden_single.bin")
        frame[frame.size - 1] = (frame[frame.size - 1].toInt() xor 0x01).toByte()
        assertThrows(DtuProtocolException::class.java) { DtuProtocol.parseRealDataResponse(frame) }
    }

    @Test
    fun `una trama truncada se rechaza`() {
        val frame = Fixtures.load("golden_single.bin").copyOf(40)
        assertThrows(DtuProtocolException::class.java) { DtuProtocol.parseRealDataResponse(frame) }
    }

    @Test
    fun `una trama sin cabecera HM se rechaza`() {
        val frame = Fixtures.load("golden_single.bin")
        frame[0] = 'X'.code.toByte()
        assertThrows(DtuProtocolException::class.java) { DtuProtocol.parseRealDataResponse(frame) }
    }

    @Test
    fun `combinar paginas toma el medidor de la pagina que lo trae`() {
        val p0 = DtuProtocol.parseRealDataResponse(Fixtures.load("golden_page0_of2.bin"))
        val p1 = DtuProtocol.parseRealDataResponse(Fixtures.load("golden_page1_of2.bin"))
        val merged = DtuProtocol.merge(listOf(p0, p1))
        assertEquals(-143, merged.meterTotalPower)
        assertEquals(24100L, merged.dtuPower)
        assertEquals(8200L, merged.dtuDailyEnergy)
        assertEquals("400000000001", merged.deviceSerial)
    }

    @Test
    fun `combinar sin ninguna pagina con medidor deja el medidor nulo`() {
        val p0 = DtuProtocol.parseRealDataResponse(Fixtures.load("golden_page0_of2.bin"))
        assertNull(DtuProtocol.merge(listOf(p0)).meterTotalPower)
    }
}
```

- [ ] **Step 4: Ejecutar y comprobar que falla**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.jairo.solardream.dtu.DtuProtocolTest"`
Expected: FAIL con `Unresolved reference: DtuProtocol`.

- [ ] **Step 5: Implementar la excepción, el lector y el escritor de protobuf**

`app/src/main/java/com/jairo/solardream/dtu/DtuProtocolException.kt`:
```kotlin
package com.jairo.solardream.dtu

import java.io.IOException

class DtuProtocolException(message: String) : IOException(message)
```

`app/src/main/java/com/jairo/solardream/dtu/ProtoReader.kt`:
```kotlin
package com.jairo.solardream.dtu

/** Lector mínimo de protobuf: solo lo necesario para las respuestas del DTU. */
internal class ProtoReader(
    private val buf: ByteArray,
    private var pos: Int = 0,
    private val end: Int = buf.size,
) {
    fun hasMore(): Boolean = pos < end

    fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            if (pos >= end) throw DtuProtocolException("varint truncado")
            if (shift >= 64) throw DtuProtocolException("varint demasiado largo")
            val b = buf[pos++].toInt()
            result = result or ((b and 0x7F).toLong() shl shift)
            if ((b and 0x80) == 0) return result
            shift += 7
        }
    }

    fun readBytes(): ByteArray {
        val len = readLength()
        val out = buf.copyOfRange(pos, pos + len)
        pos += len
        return out
    }

    fun readMessage(): ProtoReader {
        val len = readLength()
        val sub = ProtoReader(buf, pos, pos + len)
        pos += len
        return sub
    }

    fun skip(wireType: Int) {
        when (wireType) {
            0 -> readVarint()
            1 -> advance(8)
            2 -> advance(readLength())
            5 -> advance(4)
            else -> throw DtuProtocolException("tipo de cable no soportado: $wireType")
        }
    }

    private fun readLength(): Int {
        val len = readVarint()
        if (len < 0 || len > end - pos) throw DtuProtocolException("longitud fuera de rango: $len")
        return len.toInt()
    }

    private fun advance(n: Int) {
        if (n > end - pos) throw DtuProtocolException("campo fuera de rango")
        pos += n
    }
}
```

`app/src/main/java/com/jairo/solardream/dtu/ProtoWriter.kt`:
```kotlin
package com.jairo.solardream.dtu

import java.io.ByteArrayOutputStream

/** Escritor mínimo de protobuf. Omite los valores cero, como hace proto3. */
internal class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun varint(field: Int, value: Long) {
        if (value == 0L) return
        tag(field, 0)
        rawVarint(value)
    }

    fun bytes(field: Int, value: ByteArray) {
        if (value.isEmpty()) return
        tag(field, 2)
        rawVarint(value.size.toLong())
        out.write(value)
    }

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun tag(field: Int, wireType: Int) = rawVarint(((field shl 3) or wireType).toLong())

    private fun rawVarint(value: Long) {
        var x = value
        while (true) {
            if ((x and 0x7FL.inv()) == 0L) {
                out.write(x.toInt())
                return
            }
            out.write(((x and 0x7F) or 0x80).toInt())
            x = x ushr 7
        }
    }
}
```

- [ ] **Step 6: Implementar `RawReading` y `DtuProtocol`**

`app/src/main/java/com/jairo/solardream/dtu/RawReading.kt`:
```kotlin
package com.jairo.solardream.dtu

/**
 * Lectura combinada de todas las páginas, en las unidades crudas del DTU.
 * [meterTotalPower] es nulo si ninguna página trae datos de medidor.
 */
data class RawReading(
    val deviceSerial: String,
    val timestamp: Long,
    val meterTotalPower: Int?,
    val dtuPower: Long,
    val dtuDailyEnergy: Long,
)
```

`app/src/main/java/com/jairo/solardream/dtu/DtuProtocol.kt`:
```kotlin
package com.jairo.solardream.dtu

/**
 * Protocolo local de Hoymiles (puerto 10081).
 * Trama: "HM" + comando(2) + secuencia(2) + CRC16(2) + longitud total(2) + protobuf.
 */
object DtuProtocol {
    private const val HEADER_SIZE = 10
    private const val UTC_OFFSET_SECONDS = 28800L
    private const val CMD_HI = 0xA3
    private const val CMD_LO = 0x11

    data class Page(
        val deviceSerial: String,
        val timestamp: Long,
        val totalPages: Int,
        val page: Int,
        val meterTotalPower: Int?,
        val dtuPower: Long,
        val dtuDailyEnergy: Long,
    )

    fun buildRealDataRequest(seq: Int, timeText: String, epochSec: Long, page: Int): ByteArray {
        val body = ProtoWriter()
        body.bytes(1, timeText.toByteArray(Charsets.US_ASCII))
        body.varint(2, page.toLong())
        body.varint(4, UTC_OFFSET_SECONDS)
        body.varint(5, epochSec)
        val payload = body.toByteArray()

        val out = ByteArray(HEADER_SIZE + payload.size)
        out[0] = 'H'.code.toByte()
        out[1] = 'M'.code.toByte()
        out[2] = CMD_HI.toByte()
        out[3] = CMD_LO.toByte()
        putU16(out, 4, seq and 0xFFFF)
        putU16(out, 6, Crc16.compute(payload))
        putU16(out, 8, payload.size + HEADER_SIZE)
        payload.copyInto(out, HEADER_SIZE)
        return out
    }

    fun parseRealDataResponse(frame: ByteArray): Page {
        if (frame.size < HEADER_SIZE) throw DtuProtocolException("trama demasiado corta: ${frame.size} bytes")
        if (frame[0] != 'H'.code.toByte() || frame[1] != 'M'.code.toByte()) {
            throw DtuProtocolException("cabecera inválida")
        }
        val declared = getU16(frame, 8)
        if (declared != frame.size) {
            throw DtuProtocolException("longitud declarada $declared distinta de la recibida ${frame.size}")
        }
        val expected = getU16(frame, 6)
        val actual = Crc16.compute(frame, HEADER_SIZE, frame.size - HEADER_SIZE)
        if (expected != actual) throw DtuProtocolException("CRC incorrecto")
        return decode(ProtoReader(frame, HEADER_SIZE))
    }

    fun merge(pages: List<Page>): RawReading {
        require(pages.isNotEmpty()) { "se necesita al menos una página" }
        return RawReading(
            deviceSerial = pages.first().deviceSerial,
            timestamp = pages.maxOf { it.timestamp },
            meterTotalPower = pages.firstNotNullOfOrNull { it.meterTotalPower },
            dtuPower = pages.maxOf { it.dtuPower },
            dtuDailyEnergy = pages.maxOf { it.dtuDailyEnergy },
        )
    }

    private fun decode(r: ProtoReader): Page {
        var serial = ""
        var timestamp = 0L
        var totalPages = 0
        var page = 0
        var meter: Int? = null
        var dtuPower = 0L
        var dailyEnergy = 0L
        while (r.hasMore()) {
            val tag = r.readVarint().toInt()
            val field = tag ushr 3
            val wire = tag and 7
            when {
                field == 1 && wire == 2 -> serial = String(r.readBytes(), Charsets.US_ASCII)
                field == 2 && wire == 0 -> timestamp = r.readVarint()
                field == 3 && wire == 0 -> totalPages = r.readVarint().toInt()
                field == 4 && wire == 0 -> page = r.readVarint().toInt()
                field == 6 && wire == 2 -> {
                    val power = decodeMeterPower(r.readMessage())
                    if (meter == null) meter = power
                }
                field == 12 && wire == 0 -> dtuPower = r.readVarint()
                field == 13 && wire == 0 -> dailyEnergy = r.readVarint()
                else -> r.skip(wire)
            }
        }
        return Page(serial, timestamp, totalPages, page, meter, dtuPower, dailyEnergy)
    }

    /** Campo 3 de MeterMO (phase_total_power, int32 con signo). Ausente significa 0. */
    private fun decodeMeterPower(r: ProtoReader): Int {
        var power = 0
        while (r.hasMore()) {
            val tag = r.readVarint().toInt()
            val field = tag ushr 3
            val wire = tag and 7
            if (field == 3 && wire == 0) power = r.readVarint().toInt() else r.skip(wire)
        }
        return power
    }

    private fun putU16(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value ushr 8).toByte()
        buf[offset + 1] = value.toByte()
    }

    private fun getU16(buf: ByteArray, offset: Int): Int =
        ((buf[offset].toInt() and 0xFF) shl 8) or (buf[offset + 1].toInt() and 0xFF)
}
```

- [ ] **Step 7: Ejecutar y comprobar que pasa**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.jairo.solardream.dtu.DtuProtocolTest"`
Expected: PASS (8 pruebas).

- [ ] **Step 8: Commit**

```powershell
git add .
git commit -m "feat: DTU protocol framing and protobuf decoding" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 3: DtuClient (E/S por socket)

**Files:**
- Create: `app/src/main/java/com/jairo/solardream/dtu/DtuClient.kt`
- Test: `app/src/test/java/com/jairo/solardream/dtu/DtuClientTest.kt`

**Interfaces:**
- Consumes: `DtuProtocol.buildRealDataRequest`, `DtuProtocol.parseRealDataResponse`, `DtuProtocol.merge`, `DtuProtocolException`, `RawReading`
- Produces: `class DtuClient(host: String, port: Int = 10081, timeoutMs: Int = 5000, pageGapMs: Long = 2000, now: () -> Long = System::currentTimeMillis)` con `fun readRealData(): RawReading` (bloqueante; lanza `IOException` si falla).

- [ ] **Step 1: Escribir las pruebas que fallan (con un DTU falso por socket)**

`app/src/test/java/com/jairo/solardream/dtu/DtuClientTest.kt`:
```kotlin
package com.jairo.solardream.dtu

import com.jairo.solardream.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList

/** DTU falso: atiende una conexión por respuesta, o acepta y calla si [silent]. */
private class FakeDtu(
    private val responses: List<ByteArray>,
    private val silent: Boolean = false,
) : AutoCloseable {
    private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    val port: Int get() = server.localPort
    val requestsSeen = CopyOnWriteArrayList<ByteArray>()

    init {
        Thread {
            try {
                if (silent) {
                    server.accept().use { Thread.sleep(3000) }
                } else {
                    for (response in responses) {
                        server.accept().use { socket ->
                            requestsSeen += readRequest(socket.getInputStream())
                            socket.getOutputStream().apply {
                                write(response)
                                flush()
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun readRequest(input: InputStream): ByteArray {
        val data = DataInputStream(input)
        val head = ByteArray(10)
        data.readFully(head)
        val total = ((head[8].toInt() and 0xFF) shl 8) or (head[9].toInt() and 0xFF)
        val rest = ByteArray(total - 10)
        data.readFully(rest)
        return head + rest
    }

    override fun close() = server.close()
}

class DtuClientTest {

    @Test
    fun `lee una sola pagina`() {
        FakeDtu(listOf(Fixtures.load("golden_single.bin"))).use { dtu ->
            val client = DtuClient("127.0.0.1", dtu.port, timeoutMs = 2000, pageGapMs = 0)
            val reading = client.readRealData()
            assertEquals(-143, reading.meterTotalPower)
            assertEquals(24100L, reading.dtuPower)
            assertEquals(1, dtu.requestsSeen.size)
            assertEquals(0xA3, dtu.requestsSeen[0][2].toInt() and 0xFF)
            assertEquals(0x11, dtu.requestsSeen[0][3].toInt() and 0xFF)
        }
    }

    @Test
    fun `pide todas las paginas y las combina`() {
        val pages = listOf(Fixtures.load("golden_page0_of2.bin"), Fixtures.load("golden_page1_of2.bin"))
        FakeDtu(pages).use { dtu ->
            val client = DtuClient("127.0.0.1", dtu.port, timeoutMs = 2000, pageGapMs = 0)
            val reading = client.readRealData()
            assertEquals(2, dtu.requestsSeen.size)
            assertEquals(-143, reading.meterTotalPower)
            assertEquals(24100L, reading.dtuPower)
        }
    }

    @Test
    fun `si el DTU no responde lanza IOException dentro del timeout`() {
        FakeDtu(emptyList(), silent = true).use { dtu ->
            val client = DtuClient("127.0.0.1", dtu.port, timeoutMs = 300, pageGapMs = 0)
            assertThrows(IOException::class.java) { client.readRealData() }
        }
    }

    @Test
    fun `una respuesta corrupta lanza DtuProtocolException`() {
        val bad = Fixtures.load("golden_single.bin")
        bad[bad.size - 1] = (bad[bad.size - 1].toInt() xor 0x01).toByte()
        FakeDtu(listOf(bad)).use { dtu ->
            val client = DtuClient("127.0.0.1", dtu.port, timeoutMs = 2000, pageGapMs = 0)
            assertThrows(DtuProtocolException::class.java) { client.readRealData() }
        }
    }
}
```

- [ ] **Step 2: Ejecutar y comprobar que falla**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.jairo.solardream.dtu.DtuClientTest"`
Expected: FAIL con `Unresolved reference: DtuClient`.

- [ ] **Step 3: Implementar**

`app/src/main/java/com/jairo/solardream/dtu/DtuClient.kt`:
```kotlin
package com.jairo.solardream.dtu

import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Cliente bloqueante del DTU. Una conexión por petición, como hace la app oficial.
 * El DTU necesita unos 2 s entre peticiones, por eso [pageGapMs].
 */
class DtuClient(
    private val host: String,
    private val port: Int = 10081,
    private val timeoutMs: Int = 5000,
    private val pageGapMs: Long = 2000,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var seq = 0
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    @Throws(IOException::class)
    fun readRealData(): RawReading {
        val first = exchange(0)
        val pages = mutableListOf(first)
        for (page in 1 until maxOf(1, first.totalPages)) {
            if (pageGapMs > 0) Thread.sleep(pageGapMs)
            pages += exchange(page)
        }
        return DtuProtocol.merge(pages)
    }

    private fun exchange(page: Int): DtuProtocol.Page {
        seq = (seq + 1) and 0xFFFF
        val nowMs = now()
        val request = DtuProtocol.buildRealDataRequest(
            seq = seq,
            timeText = timeFormat.format(Date(nowMs)),
            epochSec = nowMs / 1000,
            page = page,
        )
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            socket.soTimeout = timeoutMs
            socket.getOutputStream().apply {
                write(request)
                flush()
            }
            return DtuProtocol.parseRealDataResponse(readFrame(socket.getInputStream()))
        }
    }

    private fun readFrame(input: InputStream): ByteArray {
        val head = readFully(input, HEADER_SIZE)
        val total = ((head[8].toInt() and 0xFF) shl 8) or (head[9].toInt() and 0xFF)
        if (total < HEADER_SIZE || total > MAX_FRAME) {
            throw DtuProtocolException("longitud de trama inválida: $total")
        }
        return head + readFully(input, total - HEADER_SIZE)
    }

    private fun readFully(input: InputStream, n: Int): ByteArray {
        val out = ByteArray(n)
        var off = 0
        while (off < n) {
            val read = input.read(out, off, n - off)
            if (read < 0) throw DtuProtocolException("conexión cerrada tras $off de $n bytes")
            off += read
        }
        return out
    }

    private companion object {
        const val HEADER_SIZE = 10
        const val MAX_FRAME = 4096
    }
}
```

- [ ] **Step 4: Ejecutar y comprobar que pasa**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.jairo.solardream.dtu.DtuClientTest"`
Expected: PASS (4 pruebas).

- [ ] **Step 5: Commit**

```powershell
git add .
git commit -m "feat: blocking DTU client over TCP" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Modelo de energía

**Files:**
- Create: `app/src/main/java/com/jairo/solardream/model/MeterConvention.kt`, `EnergySnapshot.kt`, `EnergyModel.kt`
- Test: `app/src/test/java/com/jairo/solardream/model/EnergyModelTest.kt`

**Interfaces:**
- Consumes: `RawReading`
- Produces:
  - `data class MeterConvention(val importIsPositive: Boolean = false, val dtuPowerWattsPerUnit: Double = 0.1, val dailyEnergyWhPerUnit: Double = 1.0)` con `companion object { val CALIBRATED = MeterConvention() }`
  - `data class EnergySnapshot(val productionW: Int, val gridW: Int, val houseW: Int?, val producedTodayWh: Long, val timestampSec: Long, val hasMeter: Boolean)`. `gridW` positivo = exportando, negativo = importando. `houseW` es nulo sin medidor.
  - `class EnergyModel(convention: MeterConvention = MeterConvention.CALIBRATED) { fun toSnapshot(raw: RawReading): EnergySnapshot }`

Las pruebas pasan la convención de forma explícita para no depender de los valores calibrados (Tarea 8 puede cambiarlos).

- [ ] **Step 1: Escribir las pruebas que fallan**

`app/src/test/java/com/jairo/solardream/model/EnergyModelTest.kt`:
```kotlin
package com.jairo.solardream.model

import com.jairo.solardream.dtu.RawReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnergyModelTest {
    private val negativeIsImport = MeterConvention(
        importIsPositive = false,
        dtuPowerWattsPerUnit = 0.1,
        dailyEnergyWhPerUnit = 1.0,
    )

    private fun raw(meter: Int?, dtuPower: Long, daily: Long = 8200L) =
        RawReading("400000000001", 1792463207L, meter, dtuPower, daily)

    @Test
    fun `exportando a la red`() {
        val s = EnergyModel(negativeIsImport).toSnapshot(raw(meter = 1290, dtuPower = 24100))
        assertEquals(2410, s.productionW)
        assertEquals(1290, s.gridW)
        assertEquals(1120, s.houseW)
        assertEquals(8200L, s.producedTodayWh)
        assertEquals(1792463207L, s.timestampSec)
        assertTrue(s.hasMeter)
    }

    @Test
    fun `de noche importando de la red`() {
        val s = EnergyModel(negativeIsImport).toSnapshot(raw(meter = -143, dtuPower = 0))
        assertEquals(0, s.productionW)
        assertEquals(-143, s.gridW)
        assertEquals(143, s.houseW)
    }

    @Test
    fun `convencion invertida cambia el signo de la red`() {
        val conv = negativeIsImport.copy(importIsPositive = true)
        val s = EnergyModel(conv).toSnapshot(raw(meter = 143, dtuPower = 0))
        assertEquals(-143, s.gridW)
        assertEquals(143, s.houseW)
    }

    @Test
    fun `sin medidor no hay casa ni red`() {
        val s = EnergyModel(negativeIsImport).toSnapshot(raw(meter = null, dtuPower = 24100))
        assertFalse(s.hasMeter)
        assertNull(s.houseW)
        assertEquals(0, s.gridW)
        assertEquals(2410, s.productionW)
    }

    @Test
    fun `la casa nunca es negativa`() {
        val s = EnergyModel(negativeIsImport).toSnapshot(raw(meter = 500, dtuPower = 1000))
        assertEquals(100, s.productionW)
        assertEquals(0, s.houseW)
    }

    @Test
    fun `aplica las escalas de la convencion`() {
        val conv = MeterConvention(false, dtuPowerWattsPerUnit = 1.0, dailyEnergyWhPerUnit = 10.0)
        val s = EnergyModel(conv).toSnapshot(raw(meter = 0, dtuPower = 2410, daily = 820))
        assertEquals(2410, s.productionW)
        assertEquals(8200L, s.producedTodayWh)
    }
}
```

- [ ] **Step 2: Ejecutar y comprobar que falla**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.jairo.solardream.model.EnergyModelTest"`
Expected: FAIL con `Unresolved reference: EnergyModel`.

- [ ] **Step 3: Implementar**

`app/src/main/java/com/jairo/solardream/model/MeterConvention.kt`:
```kotlin
package com.jairo.solardream.model

/**
 * Cómo interpretar los números crudos del DTU. Los valores por defecto son hipótesis
 * a partir de una lectura nocturna; la Tarea 8 los calibra con lecturas de día.
 *
 * - [importIsPositive]: si la potencia del medidor es positiva al importar de la red.
 * - [dtuPowerWattsPerUnit]: vatios por unidad del campo dtu_power.
 * - [dailyEnergyWhPerUnit]: vatios-hora por unidad del campo dtu_daily_energy.
 */
data class MeterConvention(
    val importIsPositive: Boolean = false,
    val dtuPowerWattsPerUnit: Double = 0.1,
    val dailyEnergyWhPerUnit: Double = 1.0,
) {
    companion object {
        val CALIBRATED = MeterConvention()
    }
}
```

`app/src/main/java/com/jairo/solardream/model/EnergySnapshot.kt`:
```kotlin
package com.jairo.solardream.model

/**
 * Estado de energía ya normalizado.
 * [gridW] > 0 exporta a la red, < 0 importa. [houseW] es nulo si no hay medidor.
 */
data class EnergySnapshot(
    val productionW: Int,
    val gridW: Int,
    val houseW: Int?,
    val producedTodayWh: Long,
    val timestampSec: Long,
    val hasMeter: Boolean,
)
```

`app/src/main/java/com/jairo/solardream/model/EnergyModel.kt`:
```kotlin
package com.jairo.solardream.model

import com.jairo.solardream.dtu.RawReading
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class EnergyModel(private val convention: MeterConvention = MeterConvention.CALIBRATED) {

    fun toSnapshot(raw: RawReading): EnergySnapshot {
        val productionW = (raw.dtuPower * convention.dtuPowerWattsPerUnit).roundToInt()
        val meter = raw.meterTotalPower
        val hasMeter = meter != null
        val gridW = when {
            meter == null -> 0
            convention.importIsPositive -> -meter
            else -> meter
        }
        val houseW = if (hasMeter) (productionW - gridW).coerceAtLeast(0) else null
        return EnergySnapshot(
            productionW = productionW,
            gridW = gridW,
            houseW = houseW,
            producedTodayWh = (raw.dtuDailyEnergy * convention.dailyEnergyWhPerUnit).roundToLong(),
            timestampSec = raw.timestamp,
            hasMeter = hasMeter,
        )
    }
}
```

- [ ] **Step 4: Ejecutar y comprobar que pasa**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.jairo.solardream.model.EnergyModelTest"`
Expected: PASS (6 pruebas).

- [ ] **Step 5: Commit**

```powershell
git add .
git commit -m "feat: energy model with meter convention" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Seguimiento de conexión y estado de pantalla

**Files:**
- Create: `app/src/main/java/com/jairo/solardream/model/ConnectionTracker.kt`
- Create: `app/src/main/java/com/jairo/solardream/ui/FlowState.kt`
- Test: `app/src/test/java/com/jairo/solardream/model/ConnectionTrackerTest.kt`
- Test: `app/src/test/java/com/jairo/solardream/ui/FlowStateTest.kt`

**Interfaces:**
- Consumes: `EnergySnapshot`
- Produces:
  - `class ConnectionTracker(maxFailures: Int = 3)` con `fun onSuccess(snapshot: EnergySnapshot, nowMillis: Long)`, `fun onFailure()`, `fun state(): ConnectionTracker.State`; `data class State(val connected: Boolean, val last: EnergySnapshot?, val lastOkMillis: Long?)`.
  - `enum class Arrow { OFF, FORWARD, BACKWARD }`, `enum class GridMode { EXPORT, IMPORT, IDLE, UNKNOWN }`
  - `data class FlowState(val panelsText: String, val houseText: String, val gridText: String, val gridLabel: String, val panelsToHouse: Arrow, val houseGrid: Arrow, val gridMode: GridMode, val dimmed: Boolean, val footer: String, val corner: String)` con `companion object { const val ACTIVE_W = 5; val CONNECTING: FlowState; fun from(state: ConnectionTracker.State, formatTime: (Long) -> String): FlowState }`. `houseGrid` FORWARD = Casa→Red (exporta), BACKWARD = Red→Casa (importa).

- [ ] **Step 1: Escribir las pruebas que fallan**

`app/src/test/java/com/jairo/solardream/model/ConnectionTrackerTest.kt`:
```kotlin
package com.jairo.solardream.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionTrackerTest {
    private val snap = EnergySnapshot(2410, 1290, 1120, 8200L, 1792463207L, true)

    @Test
    fun `al inicio no esta conectado y no hay lectura`() {
        val s = ConnectionTracker().state()
        assertFalse(s.connected)
        assertNull(s.last)
        assertNull(s.lastOkMillis)
    }

    @Test
    fun `una lectura correcta conecta`() {
        val t = ConnectionTracker()
        t.onSuccess(snap, 1000L)
        val s = t.state()
        assertTrue(s.connected)
        assertEquals(snap, s.last)
        assertEquals(1000L, s.lastOkMillis)
    }

    @Test
    fun `dos fallos siguen conectado, el tercero desconecta y conserva la ultima lectura`() {
        val t = ConnectionTracker(maxFailures = 3)
        t.onSuccess(snap, 1000L)
        t.onFailure()
        t.onFailure()
        assertTrue(t.state().connected)
        t.onFailure()
        val s = t.state()
        assertFalse(s.connected)
        assertEquals(snap, s.last)
        assertEquals(1000L, s.lastOkMillis)
    }

    @Test
    fun `un exito reinicia los fallos`() {
        val t = ConnectionTracker(maxFailures = 3)
        t.onSuccess(snap, 1000L)
        t.onFailure()
        t.onFailure()
        t.onSuccess(snap, 2000L)
        t.onFailure()
        t.onFailure()
        assertTrue(t.state().connected)
        assertEquals(2000L, t.state().lastOkMillis)
    }
}
```

`app/src/test/java/com/jairo/solardream/ui/FlowStateTest.kt`:
```kotlin
package com.jairo.solardream.ui

import com.jairo.solardream.model.ConnectionTracker
import com.jairo.solardream.model.EnergySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowStateTest {
    private fun connected(snapshot: EnergySnapshot) = ConnectionTracker.State(true, snapshot, 1000L)
    private val time: (Long) -> String = { "21:30" }

    @Test
    fun `exportando`() {
        val f = FlowState.from(connected(EnergySnapshot(2410, 1290, 1120, 8200L, 0L, true)), time)
        assertEquals("2,41 kW", f.panelsText)
        assertEquals("1,12 kW", f.houseText)
        assertEquals("1,29 kW", f.gridText)
        assertEquals("Red (exporta)", f.gridLabel)
        assertEquals(Arrow.FORWARD, f.panelsToHouse)
        assertEquals(Arrow.FORWARD, f.houseGrid)
        assertEquals(GridMode.EXPORT, f.gridMode)
        assertFalse(f.dimmed)
        assertEquals("Hoy: 8,2 kWh producidos", f.footer)
        assertEquals("21:30", f.corner)
    }

    @Test
    fun `de noche importando`() {
        val f = FlowState.from(connected(EnergySnapshot(0, -143, 143, 0L, 0L, true)), time)
        assertEquals(Arrow.OFF, f.panelsToHouse)
        assertEquals(Arrow.BACKWARD, f.houseGrid)
        assertEquals(GridMode.IMPORT, f.gridMode)
        assertEquals("Red (importa)", f.gridLabel)
        assertEquals("0,14 kW", f.gridText)
        assertEquals("0,14 kW", f.houseText)
    }

    @Test
    fun `flujo minimo se considera en reposo`() {
        val f = FlowState.from(connected(EnergySnapshot(2, 3, 0, 0L, 0L, true)), time)
        assertEquals(Arrow.OFF, f.panelsToHouse)
        assertEquals(Arrow.OFF, f.houseGrid)
        assertEquals(GridMode.IDLE, f.gridMode)
    }

    @Test
    fun `sin medidor casa y red son n-d`() {
        val f = FlowState.from(connected(EnergySnapshot(2410, 0, null, 8200L, 0L, false)), time)
        assertEquals("n/d", f.houseText)
        assertEquals("n/d", f.gridText)
        assertEquals(GridMode.UNKNOWN, f.gridMode)
        assertEquals(Arrow.OFF, f.houseGrid)
        assertEquals(Arrow.FORWARD, f.panelsToHouse)
    }

    @Test
    fun `desconectado atenua y muestra la hora de la ultima lectura`() {
        val state = ConnectionTracker.State(false, EnergySnapshot(2410, 1290, 1120, 8200L, 0L, true), 1000L)
        val f = FlowState.from(state, time)
        assertTrue(f.dimmed)
        assertEquals(Arrow.OFF, f.panelsToHouse)
        assertEquals(Arrow.OFF, f.houseGrid)
        assertEquals("Sin conexión · última lectura a las 21:30", f.footer)
    }

    @Test
    fun `antes de la primera lectura dice conectando`() {
        val f = FlowState.from(ConnectionTracker.State(false, null, null), time)
        assertTrue(f.dimmed)
        assertEquals("Conectando…", f.footer)
        assertEquals("—", f.panelsText)
    }
}
```

- [ ] **Step 2: Ejecutar y comprobar que fallan**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.jairo.solardream.model.ConnectionTrackerTest" --tests "com.jairo.solardream.ui.FlowStateTest"`
Expected: FAIL con `Unresolved reference: ConnectionTracker`.

- [ ] **Step 3: Implementar `ConnectionTracker`**

`app/src/main/java/com/jairo/solardream/model/ConnectionTracker.kt`:
```kotlin
package com.jairo.solardream.model

/** Decide si seguimos "conectados": se pierde la conexión tras [maxFailures] fallos seguidos. */
class ConnectionTracker(private val maxFailures: Int = 3) {

    data class State(
        val connected: Boolean,
        val last: EnergySnapshot?,
        val lastOkMillis: Long?,
    )

    private var failures = 0
    private var last: EnergySnapshot? = null
    private var lastOkMillis: Long? = null

    @Synchronized
    fun onSuccess(snapshot: EnergySnapshot, nowMillis: Long) {
        failures = 0
        last = snapshot
        lastOkMillis = nowMillis
    }

    @Synchronized
    fun onFailure() {
        failures++
    }

    @Synchronized
    fun state(): State = State(
        connected = last != null && failures < maxFailures,
        last = last,
        lastOkMillis = lastOkMillis,
    )
}
```

- [ ] **Step 4: Implementar `FlowState`**

`app/src/main/java/com/jairo/solardream/ui/FlowState.kt`:
```kotlin
package com.jairo.solardream.ui

import com.jairo.solardream.model.ConnectionTracker
import java.util.Locale
import kotlin.math.abs

enum class Arrow { OFF, FORWARD, BACKWARD }

enum class GridMode { EXPORT, IMPORT, IDLE, UNKNOWN }

/** Todo lo que [FlowView] necesita dibujar, ya decidido y probado sin Android. */
data class FlowState(
    val panelsText: String,
    val houseText: String,
    val gridText: String,
    val gridLabel: String,
    val panelsToHouse: Arrow,
    val houseGrid: Arrow,
    val gridMode: GridMode,
    val dimmed: Boolean,
    val footer: String,
    val corner: String,
) {
    companion object {
        const val ACTIVE_W = 5
        private val ES: Locale = Locale.forLanguageTag("es")

        val CONNECTING: FlowState = from(ConnectionTracker.State(false, null, null)) { "" }

        fun from(state: ConnectionTracker.State, formatTime: (Long) -> String): FlowState {
            val s = state.last
                ?: return FlowState(
                    panelsText = "—",
                    houseText = "—",
                    gridText = "—",
                    gridLabel = "Red",
                    panelsToHouse = Arrow.OFF,
                    houseGrid = Arrow.OFF,
                    gridMode = GridMode.UNKNOWN,
                    dimmed = true,
                    footer = "Conectando…",
                    corner = "",
                )

            val live = state.connected
            val time = state.lastOkMillis?.let(formatTime) ?: ""

            val mode = when {
                !s.hasMeter -> GridMode.UNKNOWN
                s.gridW >= ACTIVE_W -> GridMode.EXPORT
                s.gridW <= -ACTIVE_W -> GridMode.IMPORT
                else -> GridMode.IDLE
            }
            val label = when (mode) {
                GridMode.EXPORT -> "Red (exporta)"
                GridMode.IMPORT -> "Red (importa)"
                else -> "Red"
            }
            val houseGrid = when {
                !live -> Arrow.OFF
                mode == GridMode.EXPORT -> Arrow.FORWARD
                mode == GridMode.IMPORT -> Arrow.BACKWARD
                else -> Arrow.OFF
            }
            val panelsToHouse =
                if (live && s.productionW >= ACTIVE_W) Arrow.FORWARD else Arrow.OFF

            return FlowState(
                panelsText = kw(s.productionW),
                houseText = s.houseW?.let { kw(it) } ?: "n/d",
                gridText = if (s.hasMeter) kw(abs(s.gridW)) else "n/d",
                gridLabel = label,
                panelsToHouse = panelsToHouse,
                houseGrid = houseGrid,
                gridMode = mode,
                dimmed = !live,
                footer = if (live) {
                    "Hoy: ${String.format(ES, "%.1f", s.producedTodayWh / 1000.0)} kWh producidos"
                } else {
                    "Sin conexión · última lectura a las $time"
                },
                corner = time,
            )
        }

        private fun kw(watts: Int): String = String.format(ES, "%.2f kW", watts / 1000.0)
    }
}
```

- [ ] **Step 5: Ejecutar y comprobar que pasan**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.jairo.solardream.model.ConnectionTrackerTest" --tests "com.jairo.solardream.ui.FlowStateTest"`
Expected: PASS (4 + 6 pruebas).

- [ ] **Step 6: Commit**

```powershell
git add .
git commit -m "feat: connection tracking and flow screen state" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Vista, sondeo y protector de pantalla

Esta tarea es la capa de Android. La lógica ya está probada en las tareas anteriores; aquí se verifica que compila y se empaqueta, y se prueba visualmente en la Tarea 9.

**Files:**
- Create: `app/src/main/java/com/jairo/solardream/ui/FlowView.kt`
- Create: `app/src/main/java/com/jairo/solardream/DtuSettings.kt`
- Create: `app/src/main/java/com/jairo/solardream/EnergyPoller.kt`
- Create: `app/src/main/java/com/jairo/solardream/SolarDreamService.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `FlowState`, `Arrow`, `GridMode`, `DtuClient`, `EnergyModel`, `MeterConvention.CALIBRATED`, `ConnectionTracker`
- Produces:
  - `class FlowView(context: Context) : View` con `fun render(state: FlowState)`
  - `object DtuSettings { const val DEFAULT_HOST = "192.168.88.16"; fun host(context: Context): String }`
  - `class EnergyPoller(host: String, view: FlowView)` con `fun start()` y `fun stop()`
  - `class SolarDreamService : DreamService`

- [ ] **Step 1: Escribir `DtuSettings`**

`app/src/main/java/com/jairo/solardream/DtuSettings.kt`:
```kotlin
package com.jairo.solardream

import android.content.Context
import android.provider.Settings

/** La IP del DTU se cambia con: adb shell settings put global solardream_dtu_host <ip> */
object DtuSettings {
    const val DEFAULT_HOST = "192.168.88.16"
    private const val KEY = "solardream_dtu_host"

    fun host(context: Context): String =
        Settings.Global.getString(context.contentResolver, KEY)
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_HOST
}
```

- [ ] **Step 2: Escribir `FlowView`**

`app/src/main/java/com/jairo/solardream/ui/FlowView.kt`:
```kotlin
package com.jairo.solardream.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Dibuja Paneles → Casa → Red con flechas animadas. Toda la lógica está en [FlowState]. */
class FlowView(context: Context) : View(context) {

    private var state: FlowState = FlowState.CONNECTING
    private var dashPhase = 0f
    private var shiftIndex = 0
    private val handler = Handler(Looper.getMainLooper())
    private val density = resources.displayMetrics.density

    private val dashOn = 24f * density
    private val dashOff = 18f * density

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private val animator = ValueAnimator.ofFloat(0f, dashOn + dashOff).apply {
        duration = 1200
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            dashPhase = it.animatedValue as Float
            invalidate()
        }
    }

    private val shifter = object : Runnable {
        override fun run() {
            shiftIndex = (shiftIndex + 1) % SHIFTS.size
            invalidate()
            handler.postDelayed(this, 60_000L)
        }
    }

    fun render(newState: FlowState) {
        state = newState
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
        handler.postDelayed(shifter, 60_000L)
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        handler.removeCallbacks(shifter)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(BACKGROUND)
        val (dx, dy) = SHIFTS[shiftIndex]
        canvas.save()
        canvas.translate(dx * density, dy * density)

        val w = width.toFloat()
        val h = height.toFloat()
        val r = min(w, h) * 0.12f
        val cy = h * 0.44f
        val xs = floatArrayOf(w * 0.2f, w * 0.5f, w * 0.8f)
        val alpha = if (state.dimmed) 90 else 255
        val gap = r * 0.25f

        val gridColor = when (state.gridMode) {
            GridMode.EXPORT -> GREEN
            GridMode.IMPORT -> ORANGE
            else -> MUTED
        }

        drawArrow(canvas, xs[0] + r + gap, xs[1] - r - gap, cy, state.panelsToHouse, YELLOW, alpha)
        drawArrow(canvas, xs[1] + r + gap, xs[2] - r - gap, cy, state.houseGrid, gridColor, alpha)

        drawNode(canvas, xs[0], cy, r, YELLOW, alpha, state.panelsText, "Paneles", this::drawSun)
        drawNode(canvas, xs[1], cy, r, BLUE, alpha, state.houseText, "Casa", this::drawHouse)
        drawNode(canvas, xs[2], cy, r, gridColor, alpha, state.gridText, state.gridLabel, this::drawBolt)

        text.color = MUTED
        text.alpha = alpha
        text.textSize = r * 0.30f
        text.isFakeBoldText = false
        canvas.drawText(state.footer, w / 2f, h * 0.86f, text)

        text.textAlign = Paint.Align.RIGHT
        text.textSize = r * 0.26f
        canvas.drawText(state.corner, w - 24f * density, 36f * density, text)
        text.textAlign = Paint.Align.CENTER

        canvas.restore()
    }

    private fun drawNode(
        canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, alpha: Int,
        value: String, label: String,
        icon: (Canvas, Float, Float, Float) -> Unit,
    ) {
        stroke.color = color
        stroke.alpha = alpha
        stroke.pathEffect = null
        canvas.drawCircle(cx, cy, r, stroke)
        icon(canvas, cx, cy, r)

        text.color = TEXT
        text.alpha = alpha
        text.textSize = r * 0.5f
        text.isFakeBoldText = true
        canvas.drawText(value, cx, cy + r + r * 0.6f, text)

        text.color = MUTED
        text.alpha = alpha
        text.textSize = r * 0.26f
        text.isFakeBoldText = false
        canvas.drawText(label, cx, cy + r + r * 1.0f, text)
    }

    private fun drawArrow(
        canvas: Canvas, x1: Float, x2: Float, y: Float, dir: Arrow, color: Int, alpha: Int,
    ) {
        if (dir == Arrow.OFF) {
            stroke.color = MUTED
            stroke.alpha = if (alpha < 255) 30 else 60
            stroke.pathEffect = null
            canvas.drawLine(x1, y, x2, y, stroke)
            return
        }
        val forward = dir == Arrow.FORWARD
        val start = if (forward) x1 else x2
        val end = if (forward) x2 else x1
        stroke.color = color
        stroke.alpha = alpha
        stroke.pathEffect = DashPathEffect(floatArrayOf(dashOn, dashOff), -dashPhase)
        canvas.drawLine(start, y, end, y, stroke)
        stroke.pathEffect = null

        val head = 14f * density
        val sign = if (forward) 1f else -1f
        val path = Path().apply {
            moveTo(end, y)
            lineTo(end - sign * head, y - head * 0.6f)
            lineTo(end - sign * head, y + head * 0.6f)
            close()
        }
        fill.color = color
        fill.alpha = alpha
        canvas.drawPath(path, fill)
    }

    private fun drawSun(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        canvas.drawCircle(cx, cy, r * 0.28f, stroke)
        for (i in 0 until 8) {
            val a = i * PI.toFloat() / 4f
            canvas.drawLine(
                cx + cos(a) * r * 0.42f, cy + sin(a) * r * 0.42f,
                cx + cos(a) * r * 0.62f, cy + sin(a) * r * 0.62f, stroke,
            )
        }
    }

    private fun drawHouse(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val roof = Path().apply {
            moveTo(cx - r * 0.45f, cy)
            lineTo(cx, cy - r * 0.45f)
            lineTo(cx + r * 0.45f, cy)
        }
        canvas.drawPath(roof, stroke)
        canvas.drawRect(cx - r * 0.32f, cy, cx + r * 0.32f, cy + r * 0.4f, stroke)
    }

    private fun drawBolt(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val bolt = Path().apply {
            moveTo(cx + r * 0.10f, cy - r * 0.50f)
            lineTo(cx - r * 0.25f, cy + r * 0.05f)
            lineTo(cx, cy + r * 0.05f)
            lineTo(cx - r * 0.10f, cy + r * 0.50f)
            lineTo(cx + r * 0.25f, cy - r * 0.05f)
            lineTo(cx, cy - r * 0.05f)
            close()
        }
        canvas.drawPath(bolt, stroke)
    }

    private companion object {
        const val BACKGROUND = 0xFF0B0D10.toInt()
        const val TEXT = 0xFFE8EAED.toInt()
        const val MUTED = 0xFF9AA0A6.toInt()
        const val YELLOW = 0xFFFDD663.toInt()
        const val BLUE = 0xFF8AB4F8.toInt()
        const val GREEN = 0xFF81C995.toInt()
        const val ORANGE = 0xFFFFA726.toInt()
        val SHIFTS = listOf(0 to 0, 12 to 8, -10 to 14, 14 to -10, -12 to -8)
    }
}
```

- [ ] **Step 3: Escribir `EnergyPoller`**

`app/src/main/java/com/jairo/solardream/EnergyPoller.kt`:
```kotlin
package com.jairo.solardream

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.jairo.solardream.dtu.DtuClient
import com.jairo.solardream.model.ConnectionTracker
import com.jairo.solardream.model.EnergyModel
import com.jairo.solardream.model.MeterConvention
import com.jairo.solardream.ui.FlowState
import com.jairo.solardream.ui.FlowView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** Sondea el DTU en un hilo de fondo y entrega el estado a la vista en el hilo principal. */
class EnergyPoller(host: String, private val view: FlowView) {
    private val client = DtuClient(host)
    private val model = EnergyModel(MeterConvention.CALIBRATED)
    private val tracker = ConnectionTracker()
    private val main = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
    private var executor: ScheduledExecutorService? = null

    @Synchronized
    fun start() {
        if (executor != null) return
        executor = Executors.newSingleThreadScheduledExecutor().also {
            it.scheduleWithFixedDelay({ pollOnce() }, 0, POLL_DELAY_SECONDS, TimeUnit.SECONDS)
        }
    }

    @Synchronized
    fun stop() {
        executor?.shutdownNow()
        executor = null
    }

    private fun pollOnce() {
        try {
            val snapshot = model.toSnapshot(client.readRealData())
            tracker.onSuccess(snapshot, System.currentTimeMillis())
        } catch (e: Exception) {
            Log.w(TAG, "lectura fallida: ${e.message}")
            tracker.onFailure()
        }
        val flow = FlowState.from(tracker.state()) { timeFormat.format(Date(it)) }
        main.post { view.render(flow) }
    }

    private companion object {
        const val TAG = "SolarDream"
        const val POLL_DELAY_SECONDS = 4L
    }
}
```

- [ ] **Step 4: Escribir `SolarDreamService`**

`app/src/main/java/com/jairo/solardream/SolarDreamService.kt`:
```kotlin
package com.jairo.solardream

import android.service.dreams.DreamService
import com.jairo.solardream.ui.FlowView

class SolarDreamService : DreamService() {
    private var poller: EnergyPoller? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        val view = FlowView(this)
        setContentView(view)
        poller = EnergyPoller(DtuSettings.host(this), view)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        poller?.start()
    }

    override fun onDreamingStopped() {
        poller?.stop()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        poller?.stop()
        poller = null
        super.onDetachedFromWindow()
    }
}
```

- [ ] **Step 5: Registrar el servicio en el manifiesto**

Reemplazar `app/src/main/AndroidManifest.xml` por:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-feature android:name="android.software.leanback" android:required="false" />
    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />

    <application
        android:allowBackup="false"
        android:label="Solar Dream">

        <service
            android:name=".SolarDreamService"
            android:exported="true"
            android:label="Solar Dream"
            android:permission="android.permission.BIND_DREAM_SERVICE">
            <intent-filter>
                <action android:name="android.service.dreams.DreamService" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </service>
    </application>
</manifest>
```

- [ ] **Step 6: Compilar, ejecutar todas las pruebas y empaquetar**

Run: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`, todas las pruebas en verde y existe `app\build\outputs\apk\debug\app-debug.apk`.

- [ ] **Step 7: Commit**

```powershell
git add .
git commit -m "feat: flow view, poller and dream service" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Instalar en la TV y activar el protector de pantalla

Puede hacerse de noche: sirve para comprobar que el protector arranca y que muestra "Conectando…" y luego datos nocturnos (producción 0, importando).

**Files:** ninguno (solo comandos `adb`). Si el protector no se activa, ver Step 6.

- [ ] **Step 1: Confirmar la conexión con la TV**

Run (PowerShell):
```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb connect 192.168.88.3:5555
& $adb -s 192.168.88.3:5555 shell getprop ro.product.model
```
Expected: `connected to 192.168.88.3:5555` y `Smart TV`. Si dice `unauthorized`, aceptar el aviso en la pantalla de la TV.

- [ ] **Step 2: Instalar el APK**

Run: `& $adb -s 192.168.88.3:5555 install -r app\build\outputs\apk\debug\app-debug.apk`
Expected: `Success`.

- [ ] **Step 3: Registrar el protector y fijar la IP del DTU**

Run:
```powershell
& $adb -s 192.168.88.3:5555 shell settings put global solardream_dtu_host 192.168.88.16
& $adb -s 192.168.88.3:5555 shell settings put secure screensaver_components com.jairo.solardream/com.jairo.solardream.SolarDreamService
& $adb -s 192.168.88.3:5555 shell settings put secure screensaver_enabled 1
& $adb -s 192.168.88.3:5555 shell settings get secure screensaver_components
```
Expected: la última línea imprime `com.jairo.solardream/com.jairo.solardream.SolarDreamService`.

- [ ] **Step 4: Lanzar el protector de pantalla**

Run:
```powershell
& $adb -s 192.168.88.3:5555 shell am start -n com.android.systemui/.Somnambulator
Start-Sleep 5
& $adb -s 192.168.88.3:5555 shell "dumpsys dreams | grep -i -E 'solardream|current'"
```
Expected: `dumpsys` menciona `com.jairo.solardream`. En la TV debería verse el panel oscuro con los tres nodos.

- [ ] **Step 5: Capturar la pantalla y comprobar memoria**

Run:
```powershell
New-Item -ItemType Directory -Force captures | Out-Null
cmd /c "`"$adb`" -s 192.168.88.3:5555 exec-out screencap -p > captures\tv-dream.png"
& $adb -s 192.168.88.3:5555 shell "dumpsys meminfo com.jairo.solardream | grep -E 'TOTAL|Java Heap|Native Heap'"
```
(se usa `cmd /c` porque la redirección `>` de PowerShell corrompe los PNG binarios). Abrir `captures\tv-dream.png` y comprobar a ojo: tres nodos, cifras legibles, pie de página. Expected de memoria: `TOTAL` por debajo de unos 80 MB (RAM de la TV: ~800 MB libres).

- [ ] **Step 6: Si el protector NO se activa (plan B)**

Si `dumpsys dreams` no menciona la app, o la TV vuelve al inicio al ejecutar `Somnambulator`, el protector de Google TV ignora protectores de terceros. Crear la actividad de respaldo, que muestra la misma vista desde el lanzador.

`app/src/main/java/com/jairo/solardream/MainActivity.kt`:
```kotlin
package com.jairo.solardream

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import com.jairo.solardream.ui.FlowView

class MainActivity : Activity() {
    private var poller: EnergyPoller? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val view = FlowView(this)
        setContentView(view)
        poller = EnergyPoller(DtuSettings.host(this), view)
    }

    override fun onStart() {
        super.onStart()
        poller?.start()
    }

    override fun onStop() {
        poller?.stop()
        super.onStop()
    }
}
```

Dentro de `<application>` en `AndroidManifest.xml`, antes del `<service>`:
```xml
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@android:style/Theme.NoTitleBar.Fullscreen">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
        </activity>
```

Run:
```powershell
.\gradlew.bat :app:assembleDebug
& $adb -s 192.168.88.3:5555 install -r app\build\outputs\apk\debug\app-debug.apk
& $adb -s 192.168.88.3:5555 shell am start -n com.jairo.solardream/.MainActivity
```
Expected: la vista aparece a pantalla completa. Anotar en `docs/calibration.md` (Tarea 8) que el protector no funciona en esta TV y que se usa la actividad.

- [ ] **Step 7: Commit (solo si se creó la actividad de respaldo)**

```powershell
git add .
git commit -m "feat: fallback launcher activity" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Calibración con lecturas de día

**Requiere sol y que la casa tenga algún consumo.** Fija unidades y signos reales. Si algo no coincide con las hipótesis, **parar y avisar al usuario**; no adivinar.

**Files:**
- Create: `tools/capture_fixture.py`
- Create: `docs/calibration.md`
- Modify: `app/src/main/java/com/jairo/solardream/model/MeterConvention.kt` (solo si la calibración lo exige)

- [ ] **Step 1: Escribir la herramienta de captura**

`tools/capture_fixture.py`:
```python
"""Captura las páginas crudas de datos en tiempo real del DTU y resume qué trae cada una.

Uso:
    tools/.venv/Scripts/python tools/capture_fixture.py 192.168.88.16 captures day
Escribe captures/day_page0.bin, day_page1.bin, ...
"""
import socket
import struct
import sys
import time
from datetime import datetime
from pathlib import Path

from crcmod import mkCrcFun
from hoymiles_wifi.protobuf import RealDataNew_pb2 as pb

crc16 = mkCrcFun(0x18005, rev=True, initCrc=0xFFFF, xorOut=0x0000)


def request_frame(seq: int, page: int) -> bytes:
    req = pb.RealDataNewResDTO()
    req.time_ymd_hms = datetime.now().strftime("%Y-%m-%d %H:%M:%S").encode()
    req.offset = 28800
    req.time = int(time.time())
    req.cp = page
    payload = req.SerializeToString()
    return b"HM\xa3\x11" + struct.pack(">HHH", seq, crc16(payload), len(payload) + 10) + payload


def read_exact(sock: socket.socket, n: int) -> bytes:
    data = b""
    while len(data) < n:
        chunk = sock.recv(n - len(data))
        if not chunk:
            raise IOError("conexión cerrada")
        data += chunk
    return data


def exchange(host: str, seq: int, page: int) -> bytes:
    with socket.create_connection((host, 10081), timeout=10) as sock:
        sock.sendall(request_frame(seq, page))
        head = read_exact(sock, 10)
        total = struct.unpack(">H", head[8:10])[0]
        return head + read_exact(sock, total - 10)


def summarize(page: int, frame: bytes) -> None:
    msg = pb.RealDataNewReqDTO()
    msg.ParseFromString(frame[10:])
    print(f"--- página {page}: ap={msg.ap} cp={msg.cp} timestamp={msg.timestamp}")
    print(f"    dtu_power={msg.dtu_power} dtu_daily_energy={msg.dtu_daily_energy}")
    print(f"    suma sgs.active_power={sum(s.active_power for s in msg.sgs_data)} "
          f"suma pv.power={sum(p.power for p in msg.pv_data)} "
          f"(sgs={len(msg.sgs_data)} pv={len(msg.pv_data)})")
    for m in msg.meter_data:
        print(f"    medidor: total={m.phase_total_power} A={m.phase_A_power} B={m.phase_B_power} "
              f"Vab={m.voltage_phase_A}/{m.voltage_phase_B} Iab={m.current_phase_A}/{m.current_phase_B} "
              f"E_total={m.energy_total_power} E_consumida={m.energy_total_consumed}")


def main() -> None:
    host, outdir, label = sys.argv[1], Path(sys.argv[2]), sys.argv[3]
    outdir.mkdir(parents=True, exist_ok=True)
    seq = 0
    page = 0
    total = 1
    while page < total:
        if page:
            time.sleep(2.1)
        seq += 1
        frame = exchange(host, seq, page)
        (outdir / f"{label}_page{page}.bin").write_bytes(frame)
        summarize(page, frame)
        if page == 0:
            first = pb.RealDataNewReqDTO()
            first.ParseFromString(frame[10:])
            total = max(1, first.ap)
        page += 1


main()
```

- [ ] **Step 2: Capturar una lectura de día**

Run (con sol y producción visible en la app Hoymiles):
```powershell
tools/.venv/Scripts/python tools/capture_fixture.py 192.168.88.16 captures day
```
Anotar a la vez, de la app oficial de Hoymiles (S-Miles) o de otra referencia: **potencia actual (W)** y **energía de hoy (kWh)**.
Expected: imprime un resumen por página; `dtu_power` mayor que 0.

- [ ] **Step 3: Verificar unidades de producción**

Comparar con la referencia anotada:
- `dtuPowerWattsPerUnit` = potencia de referencia (W) / `dtu_power`. Hipótesis: `0.1`.
- `dailyEnergyWhPerUnit` = energía de hoy de referencia (Wh) / `dtu_daily_energy`. Hipótesis: `1.0`.

Si el cociente se aleja más de ~10 % de la hipótesis, usar el cociente redondeado a una potencia de diez razonable.

- [ ] **Step 4: Verificar el signo del medidor**

Con producción alta (por ejemplo más de 1500 W) y consumo doméstico normal (menos de unos 500 W de base):
- Si el medidor marca un valor **positivo grande** → la casa exporta y la hipótesis `importIsPositive = false` es correcta (el −143 W de la noche ya implica importar).
- Si marca un valor **negativo grande** con producción alta → el signo está invertido: poner `importIsPositive = true`. Contrastarlo con el −143 W nocturno: si de noche importar daba negativo y de día exportar también da negativo, las dos lecturas no son coherentes con un medidor en el punto de conexión a la red. **Parar y avisar al usuario** (puede que el medidor mida la carga, no la red).
- Comprobar que `Casa = producción − red` da un consumo plausible (positivo y razonable).

- [ ] **Step 5: Registrar qué página trae qué**

En el resumen del Step 2, anotar en qué páginas aparecen `dtu_power`, `dtu_daily_energy` y `medidor`. Si todo viene en la página 0, informar al usuario: se podría leer solo esa página y bajar el ciclo de actualización de ~12 s a ~5 s.

- [ ] **Step 6: Escribir `docs/calibration.md`**

Crear el archivo con estas secciones, rellenadas con los valores medidos (fecha, hora, lecturas de referencia, cocientes calculados, signo observado, páginas que traen cada dato, y si el protector de pantalla funcionó o hubo que usar la actividad):
```markdown
# Calibración

Fecha y hora de la captura:
## Referencia (app Hoymiles)
## Lectura del DTU
## Unidades: dtuPowerWattsPerUnit, dailyEnergyWhPerUnit
## Signo del medidor: importIsPositive
## Páginas que traen cada dato
## Protector de pantalla en esta TV
```

- [ ] **Step 7: Aplicar la calibración**

Si algún valor difiere de los actuales, editar `MeterConvention.CALIBRATED` en `MeterConvention.kt`, por ejemplo:
```kotlin
        val CALIBRATED = MeterConvention(
            importIsPositive = false,
            dtuPowerWattsPerUnit = 0.1,
            dailyEnergyWhPerUnit = 1.0,
        )
```
(con los valores medidos). Si no cambia nada, dejarlo como está.

Run: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug`
Expected: `BUILD SUCCESSFUL` (las pruebas pasan la convención de forma explícita, no dependen de `CALIBRATED`).

- [ ] **Step 8: Commit**

```powershell
git add tools docs app
git commit -m "docs: calibrate meter convention with daylight readings" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Verificación final en la TV con datos de día

**Files:** ninguno.

- [ ] **Step 1: Reinstalar con la calibración**

Run:
```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb -s 192.168.88.3:5555 install -r app\build\outputs\apk\debug\app-debug.apk
```
Expected: `Success`.

- [ ] **Step 2: Lanzar y capturar**

Run:
```powershell
& $adb -s 192.168.88.3:5555 shell am start -n com.android.systemui/.Somnambulator
Start-Sleep 20
cmd /c "`"$adb`" -s 192.168.88.3:5555 exec-out screencap -p > captures\tv-dream-day.png"
```
(si se usa la actividad de respaldo: `am start -n com.jairo.solardream/.MainActivity`). Abrir la captura y comprobar:
- Producción, Casa y Red muestran cifras coherentes con la app de Hoymiles y con `Casa = Paneles − Red`.
- La flecha Paneles → Casa y la flecha Casa → Red (o Red → Casa) tienen el sentido y color correctos (verde exportando, naranja importando).
- El pie dice `Hoy: X,X kWh producidos` y coincide con la referencia.

- [ ] **Step 3: Probar la pérdida de conexión**

Run:
```powershell
& $adb -s 192.168.88.3:5555 shell settings put global solardream_dtu_host 192.168.88.250
```
Reiniciar el protector (salir con el mando y volver a lanzar). Tras ~40 s esperar `Sin conexión · última lectura…` o `Conectando…` con los nodos atenuados. Después restaurar:
```powershell
& $adb -s 192.168.88.3:5555 shell settings put global solardream_dtu_host 192.168.88.16
```
Expected: con la IP errónea nunca aparecen cifras como actuales; al restaurar, vuelven los datos.

- [ ] **Step 4: Memoria y estabilidad**

Run:
```powershell
& $adb -s 192.168.88.3:5555 shell "dumpsys meminfo com.jairo.solardream | grep -E 'TOTAL'"
& $adb -s 192.168.88.3:5555 shell "logcat -d -s SolarDream:W | tail -20"
```
Expected: memoria total por debajo de unos 80 MB tras varios minutos; el log no muestra avisos repetidos de lectura fallida con el DTU accesible.

- [ ] **Step 5: Entregar**

Resumir al usuario: qué se verificó, las desviaciones del spec (cliente bloqueante, solo energía producida hoy), si el protector funcionó o se usa la actividad, y qué página trae cada dato por si quiere acortar el ciclo de actualización.
