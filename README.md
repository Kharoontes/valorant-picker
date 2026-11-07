# Valorant Picker

Aplicación Android para VALORANT con tres herramientas: una ruleta de agentes, recomendaciones de pickeo para tu equipo según el mapa y un sorteo de armas. Está hecha con Kotlin y Jetpack Compose, y es de uso personal.

## Características

La navegación se hace con una barra inferior de tres iconos:

| Icono | Pestaña | Qué hace |
|-------|---------|----------|
| Persona | **Agentes** | Ruleta animada para elegir un agente al azar |
| Bombilla | **Recomendaciones** | Eliges mapa y equipo, y la app te dice qué agente conviene más según el porcentaje de victorias |
| Mira | **Armas** | Sorteo aleatorio de armas con su imagen, nombre y precio |

### Agentes
- Ruleta animada con frenado progresivo y puntero.
- Filtros por rol con los iconos oficiales, todos en una sola fila.
- Los roles se bloquean mientras la ruleta gira, para que no se descuadre.
- Resultado con la cara del agente, su nombre y su rol en español.

### Recomendaciones
- Eliges el mapa del pool competitivo actual y hasta 5 agentes de tu equipo.
- La app consulta en vivo el **porcentaje de victorias de cada agente en ese mapa** y calcula la **mejor opción** para completar tu equipo.
- El cálculo tiene en cuenta el porcentaje de victorias, el número de partidas (para no fiarse de muestras pequeñas) y el equilibrio de roles.
- Con el equipo completo te muestra el porcentaje de cada integrante en ese mapa, el promedio del equipo y los cambios que te convienen.
- Si tocas un agente recomendado, se añade a tu primer hueco libre.

### Armas
- Sorteo con animación: las armas del sorteo se iluminan una a una hasta detenerse en la elegida.
- Resultado con imagen, nombre, categoría y costo en créditos.
- No incluye el cuchillo.

### General
- Pantalla de carga al iniciar: actualiza los datos y precarga todas las imágenes, así nada tarda en aparecer después.
- Ajustes de duración, intensidad y color del puntero.
- Todo lo que eliges se guarda automáticamente en el dispositivo.

## De dónde salen los datos

| Dato | Fuente |
|------|--------|
| Agentes, roles, habilidades, armas, mapas e imágenes | [valorant-api.com](https://valorant-api.com), se actualiza solo al abrir la app |
| Porcentaje de victorias por agente y mapa | [MetaBot.gg](https://metabot.gg), todos los rangos, leído en vivo al elegir un mapa |

Los porcentajes de victoria no se guardan en el dispositivo: se piden cada vez. Solo si MetaBot no responde, la app usa una copia de respaldo del 6 de noviembre de 2026 para los mapas Bind, Haven, Lotus y Sunset, y lo avisa en pantalla.

Si Riot añade un agente o un arma, aparece sin recompilar. Para un agente nuevo se muestra además un aviso con su nombre. La única excepción sería un **rol nuevo**: habría que añadirlo a `Role` en `MainActivity.kt`. Del mismo modo, cuando cambie el pool de mapas competitivos hay que actualizar la lista `POOL`.

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

- Los datos e imágenes de agentes, roles, armas y mapas provienen de [valorant-api.com](https://valorant-api.com), un servicio de la comunidad.
- Los porcentajes de victoria por mapa provienen de [MetaBot.gg](https://metabot.gg). Son estadísticas de partidas de la comunidad, cambian con cada parche y no garantizan el resultado de una partida.
- Idea inspirada en [valorantpicker.com](https://www.valorantpicker.com/) y [valorantagentwheel.com](https://valorantagentwheel.com/).
- Este proyecto no está afiliado, respaldado ni patrocinado por Riot Games ni por MetaBot. VALORANT y todos los elementos relacionados son propiedad de Riot Games, Inc.
