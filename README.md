# Valorant Picker

Aplicación Android para VALORANT con tres herramientas: una ruleta de agentes, recomendaciones de pickeo para tu equipo según el mapa y un sorteo de armas. Está hecha con Kotlin y Jetpack Compose, y es de uso personal.

## Características

La navegación se hace con una barra inferior de cuatro iconos. El estilo es oscuro, con tarjetas, tipografía monoespaciada y un único acento rojo, inspirado en un panel de control tipo "dashboard".

| Icono | Pestaña | Qué hace |
|-------|---------|----------|
| Dado | **Agentes** | Ruleta animada para elegir un agente al azar |
| Bombilla | **Equipo** | Mejores agentes de cada rol en el mapa que elijas, con su porcentaje de victorias |
| Mira | **Armas** | Ruleta de armas limitada por tus créditos |
| Perfil | **Perfil** | Tu rango, estadísticas y últimas partidas |

### Agentes
- Ruleta animada con frenado progresivo y puntero.
- Filtros por rol con los iconos oficiales, centrados. Se bloquean mientras la ruleta gira.
- Resultado con la cara del agente, su nombre y su rol en español.
- Todo cabe en una pantalla, sin scroll.

### Equipo
- Lista desplegable con todos los mapas de partida. Los que están en rotación llevan un punto verde.
- Para cada rol (Controlador, Iniciador, Duelista y Centinela) muestra los 3 mejores agentes del mapa con su porcentaje de victorias. El primero va destacado.
- El orden tiene en cuenta el número de partidas, para no fiarse de muestras pequeñas.
- Un punto de color indica si los datos están en vivo (verde) o son de respaldo (ámbar), y un botón los actualiza.

### Armas
- Escribes tus créditos (máximo 9000) y la ruleta solo incluye las armas que puedas comprar.
- Resultado con imagen, nombre, categoría y costo.
- No incluye el cuchillo. Todo cabe en una pantalla, sin scroll.

### Perfil
- Se conecta a la API de HenrikDev con tu Riot ID (por defecto, el configurado en el código) y tu API key.
- Muestra tu carta de jugador, nivel, región, rango actual con RR y cambio de la última partida, y rango pico.
- Calcula K/D, porcentaje de victorias, porcentaje de headshots y ADR con tus últimas 10 partidas, y las lista con mapa, modo, agente, marcador y K/D/A.

### Perfil: cómo conectar
1. Entra al Discord de HenrikDev (enlace en [docs.henrikdev.xyz](https://docs.henrikdev.xyz)) y verifícate.
2. En el canal `#get-a-key` elige "VALORANT (Basic Key)". El bot te da una clave al instante.
3. En la app, abre la pestaña Perfil, escribe tu Riot ID y pega la clave. También puedes cambiarlos desde Ajustes (engranaje).

La clave se guarda solo en tu teléfono. No la subas a GitHub.

### General
- Pantalla de carga solo la primera vez: descarga todas las imágenes y quedan guardadas en el dispositivo. Después la app abre directo y solo baja lo que sea nuevo.
- Barra superior roja con letras negras y barra inferior con una animación sutil en la pestaña activa.
- Ajustes de duración de las ruletas, intensidad y color del puntero.
- Todo lo que eliges se guarda automáticamente en el dispositivo.

## De dónde salen los datos

| Dato | Fuente |
|------|--------|
| Agentes, roles, armas, mapas, iconos de rango e imágenes | [valorant-api.com](https://valorant-api.com), se actualiza solo al abrir la app |
| Porcentaje de victorias por agente y mapa | [MetaBot.gg](https://metabot.gg), todos los rangos, leído en vivo al elegir un mapa |
| Perfil, rango y partidas | [HenrikDev API](https://docs.henrikdev.xyz), con tu propia API key |

Los porcentajes de victoria no se guardan en el dispositivo: se piden cada vez. Solo si MetaBot no responde, la app usa una copia de respaldo del 6 de noviembre de 2026 para los mapas Bind, Haven, Lotus y Sunset, y lo indica con el punto ámbar.

Si Riot añade un agente o un arma, aparece sin recompilar. La única excepción sería un **rol nuevo**: habría que añadirlo a `Role` en `MainActivity.kt`. La lista `POOL` solo sirve para marcar los mapas en rotación y conviene actualizarla cuando cambie el pool.

## Tecnologías

- Kotlin 2.0.20
- Jetpack Compose (Material 3 y Material Icons Extended)
- Coil 2.7.0 para cargar y cachear imágenes
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
        ├── res/                   # Icono de la app
        └── java/com/example/valpicker/
            └── MainActivity.kt    # Toda la lógica y la interfaz
```


## Hecho con Inteligencia Artificial e Inteligencia Artesanal

Este proyecto es el resultado de combinar dos formas de inteligencia:

- **Inteligencia Artificial:** [Claude](https://www.anthropic.com/claude), de Anthropic, ayudó a escribir el código, a organizar el proyecto y a resolver dudas durante el desarrollo.
- **Inteligencia Artesanal:** la idea, las decisiones, las pruebas, los ajustes y el cuidado de cada detalle son humanos. Una persona dirigió el proceso, probó la app y la fue moldeando a su gusto.

La IA aceleró el trabajo, pero la intención y el criterio fueron artesanales. Una herramienta ayuda, y quien la usa decide qué construir.

## Créditos y aviso legal

- Los datos e imágenes de agentes, roles, armas, mapas y rangos provienen de [valorant-api.com](https://valorant-api.com), un servicio de la comunidad.
- Los datos de perfil provienen de la [API de HenrikDev](https://docs.henrikdev.xyz), un servicio no oficial de la comunidad.
- Los porcentajes de victoria por mapa provienen de [MetaBot.gg](https://metabot.gg). Son estadísticas de partidas de la comunidad, cambian con cada parche y no garantizan el resultado de una partida.
- Idea inspirada en [valorantpicker.com](https://www.valorantpicker.com/) y [valorantagentwheel.com](https://valorantagentwheel.com/).
- Este proyecto no está afiliado, respaldado ni patrocinado por Riot Games ni por MetaBot. VALORANT y todos los elementos relacionados son propiedad de Riot Games, Inc.
