# Guía Completa de Publicación en Google Play Store — SolarDream TV

Esta guía contiene todo lo necesario para publicar **SolarDream TV** en la Google Play Store, especialmente configurada y optimizada para Android TV y Google TV.

---

## 1. Datos Críticos de la Llave de Firma (Keystore)

Tu aplicación ha sido configurada con una llave de firma de producción (`Release Keystore`).

> [!CAUTION]
> **RESPALDA ESTOS ARCHIVOS FUERA DE ESTE EQUIPO (por ejemplo, en un gestor de contraseñas o almacenamiento seguro en la nube).**
> Si pierdes el archivo `.jks` o la contraseña, Google no te permitirá publicar actualizaciones de la aplicación.

- **Archivo Keystore:** `solardream-release.jks` (ubicado en la raíz del proyecto).
- **Archivo de credenciales:** `key.properties` (ignorado en `.gitignore` para tu seguridad).
- **Alias de la llave:** `solardream`
- **Algoritmo y validez:** RSA 2048 bits, válido por 10.000 días (~27 años).
- **Contraseña del almacén (`storePassword`):** `8DE43F4B657E175B4600B24D49A02044`
- **Contraseña de la clave (`keyPassword`):** `8DE43F4B657E175B4600B24D49A02044`

---

## 2. Archivo para Subir a Google Play Console

Google Play exige el formato **Android App Bundle (.aab)**:

- **Ruta del bundle firmado:**
  `play_store_assets/SolarDream-v1.2.0.aab` (o `app/build/outputs/bundle/release/app-release.aab`)
- **Tamaño:** ~271 KB (ultra-optimizado, nativo sin dependencias pesadas).
- **Versión configurada:** `versionCode = 5`, `versionName = "1.2.0"` (Target SDK: API 36, R8 optimizado con mapping.txt).
- **Línea de tiempo del día:** Gráfico continuo de 24 horas (intervalos de 15 min) que muestra la curva en tiempo real de producción solar (dorado), consumo doméstico (azul) e importación de la red (ámbar) con cálculo de energía acumulada en kWh y marcador horario en vivo.
- **Compatibilidad multimarca local:** Hoymiles DTU (puerto 10081), SolaX Modbus TCP (puerto 502), Deye/SunSynk Solarman V5 (puerto 8899) y Modbus TCP (puerto 502), y estándar universal SunSpec (puerto 502).
- **Escaneo de red local:** Función de escaneo automático de subred (/24) desde el diálogo de ajustes con el mando a distancia.
- **Despliegue automatizado:** Flujo de GitHub Actions listo en `.github/workflows/deploy-play-store.yml`.

*(Si deseas generar un nuevo bundle en el futuro tras hacer cambios locales, solo ejecuta: `.\gradlew.bat :app:bundleRelease`)*

---

## 3. Material Gráfico Listo para la Ficha de Play Store

Todos los recursos visuales cumplen al 100% las especificaciones oficiales de Google Play y Android TV y están en la carpeta `play_store_assets/`:

| Recurso | Archivo | Dimensiones / Requisito | Dónde se sube en Play Console |
|---|---|---|---|
| **Ícono de la aplicación** | `play_store_assets/app_icon_512.png` | 512 × 512 px PNG (32 bits) | Ficha de Play Store principal > Ícono |
| **Gráfico de funciones** | `play_store_assets/feature_graphic_1024x500.png` | 1024 × 500 px PNG | Ficha de Play Store principal > Gráfico de funciones |
| **Banner de Android TV (Play Store)** | `play_store_assets/tv_banner_1280x720.png` | 1280 × 720 px PNG (sin transparencia) | Ficha de Play Store principal > Banner para TV |
| **Banner de Android TV (App Launcher)** | `play_store_assets/tv_banner_320x180.png` | 320 × 180 px PNG | Recurso interno de la app (`res/drawable`) |
| **Captura de TV 1** | `play_store_assets/screenshots/screenshot_tv_1.png` | 1920 × 1080 px PNG | Ficha de Play Store principal > Capturas de pantalla de TV |
| **Captura de TV 2** | `play_store_assets/screenshots/screenshot_tv_2.png` | 1920 × 1080 px PNG | Ficha de Play Store principal > Capturas de pantalla de TV |
| **Captura de TV 3** | `play_store_assets/screenshots/screenshot_tv_3.png` | 1920 × 1080 px PNG | Ficha de Play Store principal > Capturas de pantalla de TV |

---

## 4. Textos de la Ficha (Metadatos)

En `play_store_assets/STORE_LISTING.md` tienes los textos exactos redactados y listos para copiar y pegar:
- **Título:** `SolarDream TV`
- **Descripción breve:** `Monitor de energía solar y red en tiempo real para Android TV y Google TV.`
- **Descripción completa:** Texto detallado con viñetas de características, compatibilidad y privacidad.

---

## 5. Política de Privacidad

Google Play exige obligatoriamente una URL pública de Política de Privacidad.
- Se ha generado el archivo `PRIVACY_POLICY.md` en la raíz del proyecto.
- **Opciones rápidas para publicarlo en internet y obtener la URL:**
  1. **GitHub:** Si el repositorio es público en GitHub, puedes copiar el enlace directo al archivo `PRIVACY_POLICY.md` o activarlo en GitHub Pages.
  2. **GitHub Gist:** Copia el texto y crea un Gist público en [gist.github.com](https://gist.github.com).
  3. **Notion / Telegra.ph:** Copia el contenido en una página pública gratuita de Notion o [telegra.ph](https://telegra.ph).

---

## 6. Paso a Paso en Google Play Console

### Paso A: Acceso y Creación de la Aplicación
1. Ingresa en [Google Play Console](https://play.google.com/console).
2. Si es tu primera vez, abona la tarifa única de registro de desarrollador de Google ($25 USD) y verifica tu identidad.
3. Haz clic en **Crear aplicación**:
   - **Nombre:** `SolarDream TV`
   - **Idioma predeterminado:** Español (o Español de Latinoamérica)
   - **Tipo de aplicación:** Aplicación
   - **¿Es gratis o de pago?:** Gratis
   - Acepta las declaraciones de políticas del programa y leyes de exportación de EE. UU.

---

### Paso B: Ficha de Play Store Principal (Presencia en la tienda)
En el menú lateral izquierdo, ve a **Ficha de Play Store principal**:
1. Pega el **Nombre**, la **Descripción breve** y la **Descripción completa** desde `play_store_assets/STORE_LISTING.md`.
2. Sube los archivos gráficos de `play_store_assets/`:
   - Ícono de la aplicación (`app_icon_512.png`)
   - Gráfico de funciones (`feature_graphic_1024x500.png`)
   - Ve a la sección **Android TV**:
     - Sube el Banner para TV (`tv_banner_320x180.png`)
     - Sube las 3 capturas de TV (`screenshot_tv_1.png`, etc.)
3. Haz clic en **Guardar**.

---

### Paso C: Declaraciones de Contenido de la Aplicación (Obligatorias)
En el menú lateral izquierdo, ve a **Políticas y programas** > **Contenido de la aplicación** y completa cada una de las tareas:

1. **Política de privacidad:**
   - Pega la URL pública donde alojaste el contenido de `PRIVACY_POLICY.md`.
2. **Acceso a aplicaciones:**
   - Selecciona *"Todas las funciones están disponibles sin restricciones de acceso"* (la app no requiere login de usuario).
3. **Anuncios:**
   - Selecciona *"No, mi aplicación no contiene anuncios"*.
4. **Clasificación del contenido:**
   - Haz clic en iniciar cuestionario.
   - Introduce tu correo electrónico.
   - Categoría: Selecciona **Utilidades, productividad, comunicación u otras**.
   - Responde **NO** a todas las preguntas sobre violencia, contenido sexual, apuestas, lenguaje ofensivo o intercambio de ubicación física.
   - Al finalizar, obtendrás una clasificación **PEGI 3 / Para todos (Everyone)**. Guarda y confirma.
5. **Público objetivo y contenido:**
   - Grupos de edad: Marca **Mayores de 18 años** (o 13 a 17 si lo deseas, pero para TV suele recomendarse mayores de 18 para evitar requisitos adicionales de la política infantil de Google).
   - ¿Puede atraer involuntariamente a menores?: Marca "No".
6. **Aplicaciones de noticias:**
   - Selecciona *"No"*.
7. **Seguridad de los datos (Data Safety):**
   - ¿Tu aplicación recopila o comparte datos del usuario?: Selecciona **"No"** (SolarDream no recopila ni envía datos a internet).
8. **Declaración para Android TV:**
   - Confirma que la aplicación está diseñada para Android TV y selecciona que incluye la interfaz para televisión.

---

### Paso D: Configuración de Configuración de la Tienda
1. **Categoría:** `Casa y hogar` (o `Herramientas`).
2. **Etiquetas:** Selecciona etiquetas relacionadas (ej. *Energía*, *Herramientas*).
3. **Datos de contacto del desarrollador:**
   - Introduce un correo electrónico de soporte válido (será visible públicamente en la ficha de la tienda).

---

### Paso E: Crear el Lanzamiento (Release) y Subir el AAB
1. En el menú lateral, ve a **Producción** (o si prefieres probar primero con tu propia cuenta, a **Pruebas internas** / *Internal testing*).
2. Haz clic en **Crear nueva versión**.
3. **Firma de aplicaciones de Play (Play App Signing):**
   - Google te preguntará sobre la clave de firma. Haz clic en continuar/usar la clave generada por Google o subir tu bundle (Google extraerá la clave pública de `solardream-release.jks` automáticamente para la clave de carga).
4. **Subir paquete de aplicaciones:**
   - Arrastra el archivo `app-release.aab` ubicado en:
     `C:\Users\jairo\SolarDream\app\build\outputs\bundle\release\app-release.aab`
5. **Nombre de la versión:** `1.0.0`
6. **Notas de la versión:**
   ```text
   Versión inicial de SolarDream TV:
   - Visualización del flujo de energía solar en tiempo real.
   - Lectura local desde DTU Hoymiles sin intermediarios en la nube.
   - Compatible como protector de pantalla (Daydream) y aplicación independiente.
   - Configuración sencilla de IP desde el mando de la TV.
   ```
7. Haz clic en **Siguiente** / **Revisar versión**.
8. Si no hay advertencias bloqueantes, haz clic en **Guardar** e **Iniciar lanzamiento a producción** (o a pruebas).

---

## 7. Tiempo de Revisión de Google
- Para cuentas de desarrollador nuevas, Google suele tardar entre **2 a 5 días laborables** en revisar y aprobar la primera versión.
- Una vez aprobada, aparecerá automáticamente disponible en la Google Play Store de dispositivos Android TV y Google TV (TCL, Philips, Sony, Xiaomi TV, Chromecast con Google TV, etc.).

---

## 8. Despliegue Automatizado con GitHub Actions (CI/CD)

El proyecto incluye el flujo automatizado [`.github/workflows/deploy-play-store.yml`](.github/workflows/deploy-play-store.yml) que compila, prueba, firma y despliega automáticamente la versión a Google Play Store (y genera el Release de GitHub con el AAB y APK adjuntos).

### A. Configuración de Secretos en GitHub
En tu repositorio de GitHub, ve a **Settings** > **Secrets and variables** > **Actions** y añade los siguientes 5 secretos del repositorio:

| Nombre del Secreto | Descripción | Valor / Cómo obtenerlo |
|---|---|---|
| `RELEASE_KEYSTORE_BASE64` | Archivo Keystore en Base64 | Ejecuta en PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("solardream-release.jks")) \| Set-Clipboard` y pega el contenido. |
| `KEYSTORE_PASSWORD` | Contraseña del almacén de claves | `8DE43F4B657E175B4600B24D49A02044` |
| `KEY_ALIAS` | Alias de la llave | `solardream` |
| `KEY_PASSWORD` | Contraseña de la llave | `8DE43F4B657E175B4600B24D49A02044` |
| `PLAY_STORE_JSON_KEY` | Llave de cuenta de servicio de Google Play (JSON) | Archivo JSON de la cuenta de servicio de Google Cloud vinculada con permisos de lanzamiento en Play Console. |

### B. Cómo obtener `PLAY_STORE_JSON_KEY` de Google Play:
1. En **Google Play Console**, ve a **Configuración** > **Acceso a la API** (*API access*).
2. Vincula un proyecto de Google Cloud (o crea uno nuevo).
3. Haz clic en **Crear cuenta de servicio** y sigue el enlace a la consola de Google Cloud.
4. En Google Cloud, crea una cuenta de servicio (ej. `solardream-publisher`), ve a la pestaña **Claves** > **Agregar clave** > **Crear clave nueva (JSON)** y descarga el archivo `.json`.
5. Vuelve a Google Play Console > **Acceso a la API**, busca la cuenta creada, haz clic en **Administrar permisos**, y en **Permisos de la app** dale acceso a `dev.jairoserrano.solardream` con permisos para publicar versiones de producción y pruebas.
6. Copia todo el texto del archivo JSON y pégalo como el secreto `PLAY_STORE_JSON_KEY` en GitHub.

### C. Cómo disparar un despliegue automático:
- **Opción 1: Publicar un tag de versión (Recomendado)**
  ```bash
  git tag v1.2.0
  git push origin v1.2.0
  ```
  El workflow se activará automáticamente, compilará, pasará los tests unitarios, desplegará a producción en Play Store y creará la release de GitHub con los binarios.
- **Opción 2: Manual desde la interfaz de GitHub**
  En la pestaña **Actions** de GitHub, selecciona el flujo **Deploy to Google Play Store**, haz clic en **Run workflow**, elige la rama (`main`) y la pista de despliegue (`production`, `internal`, `alpha`, o `beta`).

