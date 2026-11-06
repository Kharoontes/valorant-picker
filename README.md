# Valorant Picker

Aplicación Android para VALORANT con tres herramientas: una ruleta de agentes, recomendaciones de pickeo para tu equipo y un sorteo de armas. Está hecha con Kotlin y Jetpack Compose, y es de uso personal.

## Características

La navegación se hace con una barra inferior de tres iconos:

| Icono | Pestaña | Qué hace |
|-------|---------|----------|
| Persona | **Agentes** | Ruleta animada para elegir un agente al azar |
| Bombilla | **Recomendaciones** | Eliges a tu equipo y la app te sugiere qué agentes te convienen |
| Mira | **Armas** | Sorteo aleatorio de armas con su imagen, nombre y precio |

### Agentes
- Ruleta animada con frenado progresivo y puntero.
- Filtros por rol con los iconos oficiales, todos en una sola fila.
- Resultado con la cara del agente, su nombre y su rol en español.

### Recomendaciones
- Eliges hasta 5 agentes tocando los círculos de tu equipo.
- La app analiza los roles de tu equipo y avisa de lo que falta o sobra (por ejemplo, sin controlador o demasiados duelistas).
- Te propone agentes por rol, con su foto, sus habilidades y la descripción del rol que da la API.
- Si tocas un agente recomendado, se añade a tu primer hueco libre.

### Armas
- Sorteo con animación: las armas del sorteo se iluminan una a una hasta detenerse en la elegida.
- Resultado con imagen, nombre, categoría y costo en créditos.
- Filtros por categoría (pistolas, subfusiles, escopetas, rifles, francotiradores, pesadas y cuerpo a cuerpo).

### General
- Las imágenes se precargan al abrir la app, así que el resultado aparece al instante.
- Ajustes de duración, intensidad, color del puntero y sonido.
- Todo se guarda automáticamente en el dispositivo.

## Agentes y armas nuevos

La app se actualiza sola. Cada vez que la abres con conexión, consulta [valorant-api.com](https://valorant-api.com) y descarga:

- Los agentes con su nombre, rol, foto y habilidades.
- La información de los roles (icono y descripción en español).
- Las armas con su imagen, categoría y precio.

Si Riot añade un agente o un arma, aparece sin tocar el código ni recompilar. Para un agente nuevo se muestra además un aviso con su nombre. Todo se guarda en el dispositivo, así que sin conexión se usa la última versión descargada. La primera vez que se instala hace falta internet para ver imágenes y armas.

La única excepción sería que Riot creara un **rol nuevo**: habría que añadirlo a `Role` en `MainActivity.kt`.

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
        └── java/com/example/valpicker/
            └── MainActivity.kt    # Toda la lógica y la interfaz
```


## Hecho con Inteligencia Artificial e Inteligencia Artesanal

Este proyecto es el resultado de combinar dos formas de inteligencia:

- **Inteligencia Artificial:** [Claude](https://www.anthropic.com/claude), de Anthropic, ayudó a escribir el código, a organizar el proyecto y a resolver dudas durante el desarrollo.
- **Inteligencia Artesanal:** la idea, las decisiones, las pruebas, los ajustes y el cuidado de cada detalle son humanos. Una persona dirigió el proceso, probó la app y la fue moldeando a su gusto.

La IA aceleró el trabajo, pero la intención y el criterio fueron artesanales. Una herramienta ayuda, y quien la usa decide qué construir.

## Créditos y aviso legal

- Los datos e imágenes de agentes, roles y armas provienen de [valorant-api.com](https://valorant-api.com), un servicio de la comunidad.
- Idea inspirada en [valorantpicker.com](https://www.valorantpicker.com/) y [valorantagentwheel.com](https://valorantagentwheel.com/).
- Las recomendaciones de equipo se basan en el equilibrio de roles y en los datos de la API. No usan estadísticas de partidas ni de meta.
- Este proyecto no está afiliado, respaldado ni patrocinado por Riot Games. VALORANT y todos los elementos relacionados son propiedad de Riot Games, Inc.
