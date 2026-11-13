# 3d4j

Grundgerüst für eine 3D-Engine in reinem Java 21 – ohne eine einzige zusätzliche Bibliothek.
Fenster, Eingabe und OpenGL werden über die Foreign Function & Memory API (FFM) direkt
aus den Systembibliotheken angesprochen:

| Plattform | Fenster und Eingabe | OpenGL-Kontext |
|---|---|---|
| Windows x64 | `user32.dll`, `gdi32.dll` | WGL (`opengl32.dll`) |
| Linux x64 | Xlib (`libX11.so.6`) | GLX (`libGL.so.1`) |

Gerendert wird mit einem OpenGL-3.3-Core-Kontext, also mit Shadern, Vertex-Buffern und
voller Hardwarebeschleunigung.

## Voraussetzungen

- **Genau JDK 21.** Die FFM-API ist dort eine Preview-API, und Preview-Klassen laufen nur
  auf der Java-Version, für die sie kompiliert wurden.
- Windows x64 oder Linux x64 mit X11 (unter Wayland über XWayland).
- Ein Grafiktreiber mit OpenGL 3.3 oder neuer.

## Starten

Das fertige JAR baut eine GitHub Action bei jedem Push auf `main` und legt es als festes
Release-Asset ab:

**[3d4j.jar herunterladen](https://github.com/aresstack/3d4j/releases/download/latest/3d4j.jar)**

Starten per Doppelklick oder mit

```
java -jar 3d4j.jar
```

Mehr ist nicht nötig: Das Manifest nennt den `Launcher` als Hauptklasse, und der startet die
JVM selbst noch einmal mit `--enable-preview` (siehe [Der Launcher](#der-launcher)).
Bei einem Doppelklick läuft das Programm auf `javaw` ohne Konsole; Fehler wie eine falsche
Java-Version erscheinen dann in einem Dialog.

Die Demo zeigt einen beleuchteten, rotierenden Würfel:

| Taste | Wirkung |
|---|---|
| Pfeiltasten | Kamera um den Würfel drehen |
| W / S | Heran- und herauszoomen |
| Leertaste | Rotation anhalten |
| P | `screenshot.png` speichern |
| Esc | Beenden |

Argumente der Demo: `--frames=N` beendet nach N Frames, `--screenshot=DATEI` speichert den
letzten dieser Frames, `--no-vsync` schaltet die Bildsynchronisation ab.

## Der Launcher

`java -jar` startet die JVM ohne `--enable-preview`, die Engine braucht das Flag aber.
`Launcher` löst das so:

1. Er ist **ohne** Preview kompiliert (eigenes Source-Set `src/launcher/java`) und kennt die
   Engine nur dem Namen nach.
2. Er versucht, die Engine-Klasse `ffm.Native` zu laden. Scheitert das mit
   `UnsupportedClassVersionError`, ist Preview nicht aktiv.
3. Dann startet er dieselbe JVM erneut mit `--enable-preview --enable-native-access=ALL-UNNAMED`,
   reicht alle JVM-Optionen und Argumente durch und gibt den Exit-Code des Kindprozesses zurück.

Zwei Details dazu:

- javac markiert nur die Klassen als Preview, die selbst eine Preview-API benutzen. Die
  Hauptklasse eines Spiels tut das meist nicht, deshalb prüft der Launcher eine feste
  Engine-Klasse statt der Hauptklasse.
- Ein Debug-Agent (`-agentlib:jdwp`) wird nicht an den zweiten Prozess weitergereicht, weil
  dieser sonst denselben Port belegen wollte.

Welche Klasse gestartet wird, bestimmt die System-Property `threed4j.main`
(Standard: `com.aresstack.threed4j.demo.CubeDemo`):

```
java -Dthreed4j.main=mein.spiel.Main -jar build/libs/3d4j.jar
```

## Entwickeln und Debuggen

Lokal bauen: `./gradlew jar` (unter Windows `gradlew.bat jar`), das JAR liegt danach unter
`build/libs/3d4j.jar`.

`./gradlew run` startet die Demo direkt mit den Preview-Flags, ohne zweiten Prozess.
In der IDE entsprechend: Hauptklasse `CubeDemo`, VM-Optionen

```
--enable-preview --enable-native-access=ALL-UNNAMED
```

`./gradlew runJar` startet dagegen über den Launcher, so wie `java -jar`.

## Aufbau

Alles liegt unter `com.aresstack.threed4j`:

| Paket | Inhalt |
|---|---|
| `launcher` | Selbststart mit `--enable-preview` (eigenes Source-Set, ohne Preview kompiliert) |
| `ffm` | `Native`: Hilfsschicht über der FFM-API (Bibliotheken laden, Strings, Speicher) |
| `gl` | `GL`: handgeschriebene OpenGL-Bindings; `GLLoader`: Auflösung der Funktionsadressen |
| `platform` | `Window`, `Input`, `Key` und die Auswahl der Plattform |
| `platform.win32` | `Win32Api` (Bindings) und `Win32Window` (Fenster, Nachrichten, WGL) |
| `platform.x11` | `X11Api` (Bindings) und `X11Window` (Fenster, Events, GLX) |
| `core` | `Engine` (Game-Loop), `Game`, `EngineContext`, `EngineConfig` |
| `gfx` | `ShaderProgram`, `Mesh`, `Meshes`, `Camera`, `Screenshot` |
| `math` | `Vec3`, `Mat4` |
| `demo` | `CubeDemo` |

Die Shader liegen als Ressourcen unter `src/main/resources/shaders`.

### Ein eigenes Spiel

```java
public final class MyGame implements Game {
    public static void main(String[] args) {
        Engine.run(EngineConfig.of("Mein Spiel", 1280, 720), new MyGame());
    }

    @Override public void init(EngineContext context) { /* Shader und Meshes laden */ }
    @Override public void update(EngineContext context, double deltaSeconds) { /* Logik, Eingabe */ }
    @Override public void render(EngineContext context) { /* zeichnen */ }
    @Override public void dispose() { /* GPU-Ressourcen freigeben */ }
}
```

### Eine OpenGL-Funktion ergänzen

In `GL` ein Handle mit der C-Signatur und eine statische Wrapper-Methode anlegen, nach dem
Muster der vorhandenen Funktionen. `GL` darf erst benutzt werden, wenn ein `Window`
existiert: Die Funktionsadressen liefert unter Windows erst der Grafiktreiber, sobald ein
Kontext aktiv ist.

## Build

`.github/workflows/build.yml` baut das JAR, prüft Manifest und Class-File-Versionen (Launcher
ohne, FFM-Schicht mit Preview-Markierung), startet es auf einem virtuellen Display mit einem
schlichten `java -jar` und veröffentlicht es anschließend unter dem festen Release-Tag `latest`.

## Teststand

| Umgebung | Stand |
|---|---|
| Linux x64, Mesa (llvmpipe) unter Xvfb | getestet: Start über den Launcher, Rendering, Tastatur, Größenänderung, Schließen |
| Windows-Codepfad mit Windows-JDK 21 unter Wine | getestet: dieselben Punkte |
| Echtes Windows mit echtem Grafiktreiber | noch nicht getestet |

## Grenzen und nächste Schritte

- Maus, Texturen, Text und Audio fehlen noch.
- macOS wird nicht unterstützt (dort wäre Cocoa über `objc_msgSend` nötig).
- Der Game-Loop hat einen variablen Zeitschritt und ohne VSync keine Bildratenbegrenzung.
- Umstieg auf Java 22 oder neuer: Die FFM-API ist dort final. Alle Methoden, die sich
  zwischen Preview und finaler API geändert haben, werden nur in `ffm/Native.java` benutzt.
  Danach können `--enable-preview` und der Neustart im Launcher entfallen.
