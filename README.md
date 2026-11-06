# Valorant Picker

Aplicación Android para elegir agentes de VALORANT al azar con una ruleta animada. Está hecha con Kotlin y Jetpack Compose, y es de uso personal.

## Características

- **Ruleta animada** con frenado progresivo, puntero y colores por rol.
- **Giro individual** para obtener un agente.
- **Giro de equipo** para generar una composición de 1 a 5 agentes sin repetir, con un rol opcional por hueco y opción de mezclar los roles.
- **Filtros por rol** (Controller, Sentinel, Initiator y Duelist).
- **Agent Pool** para activar o desactivar agentes concretos.
- **Presets** guardados para el pool de agentes y para el equipo.
- **Retratos de los agentes**, descargados desde [valorant-api.com](https://valorant-api.com).
- **Ajustes de giro y sonido:** duración, intensidad, color del puntero, sonido de ticks y volumen de victoria.
- **Compartir el resultado** como texto.
- Todo se guarda automáticamente en el dispositivo.

## Tecnologías

- Kotlin 2.0.20
- Jetpack Compose (Material 3)
- Coil 2.7.0 para cargar imágenes
- Gradle 8.9 y Android Gradle Plugin 8.5.2
- GitHub Actions para compilar el APK

Requiere Android 7.0 (API 24) o superior.

## Estructura del proyecto

```
.
├── .github/workflows/main.yml     # Compilación automática del APK
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        └── java/com/example/valpicker/
            └── MainActivity.kt    # Toda la lógica y la interfaz
```


## Cómo funciona

- **Retratos:** al abrir la app se consulta `https://valorant-api.com/v1/agents?isPlayableCharacter=true`. Las rutas de las imágenes se guardan en el dispositivo, así que solo hace falta conexión la primera vez.
- **Ruleta:** el ganador se decide antes de girar y la animación se calcula para que el puntero se detenga justo sobre él.
- **Sonidos:** se generan con tonos básicos del sistema, sin archivos de audio.

## Actualizar la lista de agentes

Los agentes están definidos en la lista `AGENTS` al principio de `MainActivity.kt`. Cuando salga uno nuevo, añade su nombre en la lista del rol que le corresponda. El nombre debe coincidir con el de la API para que aparezca su imagen.

## Hecho con Inteligencia Artificial e Inteligencia Artesanal

Este proyecto es el resultado de combinar dos formas de inteligencia:

- **Inteligencia Artificial:** [Claude](https://www.anthropic.com/claude), de Anthropic, ayudó a escribir el código, a organizar el proyecto y a resolver dudas durante el desarrollo.
- **Inteligencia Artesanal:** la idea, las decisiones, las pruebas, los ajustes y el cuidado de cada detalle son humanos. Una persona dirigió el proceso, probó la app y la fue moldeando a su gusto.

La IA aceleró el trabajo, pero la intención y el criterio fueron artesanales. Una herramienta ayuda, y quien la usa decide qué construir.

## Créditos y aviso legal

- Los datos e imágenes de los agentes provienen de [valorant-api.com](https://valorant-api.com), un servicio de la comunidad.
- Idea inspirada en [valorantpicker.com](https://www.valorantpicker.com/) y [valorantagentwheel.com](https://valorantagentwheel.com/).
- Este proyecto no está afiliado, respaldado ni patrocinado por Riot Games. VALORANT y todos los elementos relacionados son propiedad de Riot Games, Inc.
