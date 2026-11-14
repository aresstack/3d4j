# 3d4j

<img src="src/main/resources/images/cover.jpg" alt="Nur zur ZIHR – Cover" width="320">

Grundgerüst für eine 3D-Engine in reinem Java 21. Alles ist in Java geschrieben, auch die
eingebundenen Bibliotheken; nativen Code bringt das Projekt nicht mit. Fenster, Eingabe und
Grafik werden über die Foreign Function & Memory API (FFM) direkt aus den Systembibliotheken
angesprochen:

| Plattform | Fenster und Eingabe | Grafik |
|---|---|---|
| Windows x64 | `user32.dll` | Direct3D 11 (`d3d11.dll`, `d3dcompiler_47.dll`), ersatzweise OpenGL 3.3 über WGL |
| Linux x64 | Xlib (`libX11.so.6`) | OpenGL 3.3 über GLX (`libGL.so.1`) |

Gerendert wird mit Shadern, Vertex-Buffern und voller Hardwarebeschleunigung. Unter Windows
ist Direct3D 11 der Standard, weil es auch dort läuft, wo kein OpenGL-Treiber vorhanden ist
(Firmenrechner, virtuelle Maschinen, Remote-Sitzungen): Fehlt ein Hardware-Treiber, nutzt
die Engine WARP, den Software-Renderer von Windows.

## Voraussetzungen

- **Genau JDK 21.** Die FFM-API ist dort eine Preview-API, und Preview-Klassen laufen nur
  auf der Java-Version, für die sie kompiliert wurden.
- Windows x64 oder Linux x64 mit X11 (unter Wayland über XWayland).
- Windows: nichts weiter, Direct3D 11 gehört zum System. Linux: ein Treiber mit OpenGL 3.3
  oder neuer.

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

Gestartet wird „Nur zur ZIHR“: erst das Panoramabild als Ladebildschirm (Leertaste, Enter oder
Mausklick überspringt ihn), dann ein begehbarer Testplatz mit 140 Gebäuden, Bäumen, Laternen
und Besuchern.

| Taste | Wirkung |
|---|---|
| W A S D | Gehen |
| E / Q | Hoch / runter |
| Umschalt | Schneller |
| Maustaste halten und ziehen, oder Pfeiltasten | Umsehen |
| 1 bis 5 | Interne Auflösung: 100, 75, 50, 33, 25 Prozent |
| F | Hochskalieren weich oder pixelig |
| R | Framezeit-Statistik zurücksetzen |
| P | `screenshot.png` speichern |
| Esc | Pausenmenü (Weiterspielen, Einstellungen, Spiel beenden) |

Umgesehen wird mit gehaltener Maustaste statt mit eingefangenem Mauszeiger, weil das auch in
Remote-Sitzungen und virtuellen Maschinen zuverlässig funktioniert.

### Messen

Die Titelzeile zeigt jede Sekunde die interne Auflösung, die Bildrate sowie mittlere Framezeit
und 99. Perzentil. Beim Beenden gibt die Engine die Statistik des ganzen Laufs aus, mit 95. und
99. Perzentil. Für vergleichbare Zahlen gibt es eine feste Kamerafahrt:

```
java -jar 3d4j.jar --benchmark --no-vsync --frames=3000
java -jar 3d4j.jar --benchmark --no-vsync --frames=3000 --scale=0.5
```

Argumente: `--benchmark` (kein Ladebildschirm, feste Kamerafahrt), `--scale=0.5` (interne
Auflösung), `--frames=N` (nach N Frames beenden), `--screenshot=DATEI` (den letzten dieser
Frames speichern), `--time=SEKUNDEN` (Szene an diesem Zeitpunkt einfrieren, für vergleichbare
Bilder), `--view=x,y,z,gier,nick` (mit `--benchmark`: fester Standpunkt statt Rundflug, Winkel
in Grad; z. B. `--view=0,2.2,-100,0,8` vor dem WEST.SIDE-Hof), `--no-title`, `--no-vsync`.

Der drehende Würfel ist als Minimalbeispiel weiter enthalten:
`java -Dthreed4j.main=com.aresstack.threed4j.demo.CubeDemo -jar 3d4j.jar`.

Beim Start gibt die Engine aus, womit sie rendert, zum Beispiel
`Direct3D 11 (feature level 11_0) | <Name der Grafikkarte> | hardware`. Steht dort
`software rendering on the CPU (WARP)`, ist kein Grafiktreiber nutzbar und die CPU rechnet.
Die Grafik-API lässt sich mit `-Dthreed4j.backend=d3d11` oder `-Dthreed4j.backend=opengl`
erzwingen (Standard `auto`: unter Windows Direct3D 11 und nur bei einem Fehler OpenGL).

## Grafiken aus Dateien und vom Chatmodell

Bäume, Laternen und Besucher des Testplatzes sind eingebaute Platzhalter. Liegt im
Asset-Ordner eine Datei mit passendem Namen, ersetzt sie den Platzhalter:

| Datei | Motiv |
|---|---|
| `blossom-tree`, `green-tree` | Bäume |
| `lamp` | Laterne |
| `visitor-1`, `visitor-2`, `visitor-3` | Besucher |

Jeweils als `.svg` oder `.png`, hochkant im Verhältnis 1:2 mit transparentem Hintergrund. Der
Ordner ist `.3d4j/assets` im Benutzerverzeichnis (unter Windows `%USERPROFILE%\.3d4j\assets`),
änderbar mit `-Dthreed4j.assets=ORDNER`. Eine SVG-Datei rechnet das Spiel beim Start mit Apache
Batik in ein PNG um. Es genügt also, ein SVG in den Ordner zu legen, auch eines, das man aus
einem Chat kopiert hat. Beim Laden der Szene meldet die Konsole, wie viele Sprites aus dem
Ordner kommen.

### Das Menü „Die Welt erschaffen“

Solange noch keine erzeugten Motive im Asset-Ordner liegen, öffnet sich nach dem Ladebildschirm
ein Menü im Stil des Spiels. Es führt in drei Schritten zur erzeugten Welt, nach dem Vorbild der
Einrichtungsassistenten von Space Harvester und Enterprise AI Client:

1. **Zugang:** oben wählt man **KeePass** oder **Manuell**.
   - *KeePass* (vorgewählt): „Koppeln“ verbindet das Spiel mit KeePass. KeePass zeigt ein
     Einmal-Passwort, das man im Menü eintippt; das ist nur beim ersten Mal nötig, danach
     verbindet sich das Menü beim Öffnen selbst. Dann wählt man in der Auswahlliste den Eintrag
     des KI-Dienstes. Die **URL des Eintrags ist die Adresse der API** (`https://…/v1`), sein
     **Passwort der API-Key**. Tippen sucht in der Liste, die Pfeiltasten blättern. Der zuletzt
     gewählte Eintrag ist beim nächsten Mal vorgewählt, ebenso der aus `chat.apiKeyRef` des
     Enterprise AI Client.
   - *Manuell:* Adresse und API-Key eintippen. Der Key wird nicht gespeichert.
2. **Modell:** Das Menü lädt die Modelle des Dienstes und wählt `openai/gpt-oss-120b` vor,
   falls angeboten.
3. **Welt:** „Zeichnen lassen“ fragt alle Motive gleichzeitig an und zeigt je Motiv den Stand.
   „Spielen“ startet das Spiel mit dem, was gelungen ist.

Die Schritte oben sind anklickbar, sobald sie möglich sind (Modell erst mit Zugang, Welt erst mit
Modell); ein Klick auf den nächsten Schritt wirkt wie „Weiter“. Im Schritt „Welt“ zeichnet „neu“
neben einem Motiv nur dieses eine noch einmal.

Aus dem Pausenmenü geöffnet, springt das Menü direkt zum Schritt „Welt“: Zugang und Modell der
letzten Runde gelten weiter (im selben Programmlauf aus dem Speicher, sonst holt das Menü den
gemerkten Eintrag ohne Nachfrage aus KeePass). Ändern lässt sich beides über die Schritte oben.

„Ohne KI spielen“ verlässt das Menü jederzeit. `--setup` öffnet es auch dann, wenn schon Motive
vorhanden sind, `--no-setup` unterdrückt es. Bedient wird es mit der Maus; Tab wechselt das
Feld, Enter löst den rechten Knopf aus, Strg+V fügt ein.

### Häuser: Stadtteile und ihre Bauteil-Sammlung

Die Häuser haben Fenster, Türen und Läden. Sie stammen aus einer Sammlung je **Stadtteil**
(`District`): Bilder mit durchsichtigem Rand unter `assets/library/<stadtteil>/` als
`window-001.svg|.png|.json`, `door-…`, `shop-…`. Die JSON-Datei nennt Modell, Zeitpunkt und
Auftrag. Bisher gibt es einen Stadtteil, das Blütenviertel um den Startplatz; `District.at(x, z)`
ist die Stelle, an der weitere ihre Grenzen bekommen.

- **Gleicher Stadtteil, gleicher Stil:** Jede Anfrage enthält den Stil des Stadtteils (ein
  Satz, den alle Teile teilen) und eine Variante aus einer festen Liste (Rundbogen, Fensterläden,
  Blumenkasten …), damit die Teile zusammenpassen und sich trotzdem unterscheiden.
- **Im Hintergrund:** Beim Spielen zeichnet `BackgroundArtist` auf einem eigenen Thread ein
  Teil nach dem anderen, bis die Sammlung 8 Fenster, 4 Türen und 4 Läden hat. Den Zugang nimmt
  er aus dem Weltmenü dieses Programmlaufs, sonst holt er den gemerkten ohne Nachfrage
  (KeePass-Eintrag oder Umgebungsvariable). Ohne Zugang wartet er. Abschalten: Pausenmenü,
  Einstellungen, Reiter „Welt“; dort steht auch der Stand.
- **Zuweisung:** Solange ein Stadtteil kein erzeugtes Teil einer Art hat, steht ein eingebautes
  dafür, alle Häuser sehen dann gleich aus. Jedes Haus bewertet jedes Teil mit einer Zahl, die
  nur von Haus und Teil abhängt, und nimmt das höchstbewertete. Kommt ein Teil dazu, übernimmt
  es seinen Anteil der Häuser, die übrigen bleiben, wie sie waren; dieselbe Sammlung ergibt
  immer dieselbe Stadt. Fensterabstand, Geschosshöhe, Läden ja oder nein und die Seite der Tür
  sind ebenfalls Eigenschaften des Hauses.
- **Im Renderer:** Die Wände sind ein eigenes Netz (fünfter Draw Call). Eine Wand ist ein
  einziges Rechteck; der Shader wiederholt das Teil entlang der Wand und zeigt die Wandfarbe,
  wo das Bild durchsichtig ist. Ein Haus kostet so ein Dutzend Rechtecke, egal wie viele
  Fenster es hat, und kein Pixel wird doppelt gezeichnet. In der Ferne blendet die Wand in die
  Mischfarbe aus Wand und Teil über.

Ein Bild, das man unter passendem Namen in den Ordner legt, zählt genauso; das Spiel bemerkt
neue Teile innerhalb einer Sekunde.

### Easter Egg: WEST.SIDE

Vom Start geradeaus die Straße hinunter, hinter den letzten Häusern, steht das Büroensemble
WEST.SIDE aus Bonn: Gebäude B (Generalzolldirektion) links, Gebäude A (Ministerium) rechts,
dahinter das Parkhaus, davor rechts der Altbau; die Straße führt in den Hof. Links eine dunkle
Halle, rechts Wohnblöcke mit rotbraunen Dächern, wie im Luftbild. Es taucht erst beim
Hinlaufen aus dem Dunst auf.

Maße und Aufbau stammen aus dem Skizzenmodell (`generate_assets.py`: 62 m lang, 18 m tief,
vier Geschosse, Stützenraster 6,9 m, Parkhaus 20 × 64 m). Dessen GLB-Dateien lädt das Spiel
nicht: Sie haben rund 23.000 texturierte Dreiecke, das Dreifache der ganzen Stadt, und die
Engine kann noch keine Modelldateien laden. `WestSide` baut dasselbe Gebäude aus etwa 3.000
einfarbigen Dreiecken nach, im Stil der übrigen Stadt: das rote Raster aus Stützen und Riegeln
als Quader, jedes Fenster als dunkler Rahmen mit Scheibe davor.

### Das Pausenmenü

Esc hält das Spiel an, legt einen Schleier darüber und zeigt eine Karte im Stil des Menüs: **Weiterspielen**,
**Einstellungen**, **Spiel beenden**. Bedient wird sie mit der Maus oder mit Pfeiltasten und
Enter; Esc führt eine Ebene zurück bzw. ins Spiel.

Die Einstellungen haben vier Reiter; alles, was sich im Spiel einstellen lässt, gehört dorthin:

- **Bild:** Auflösung (100 bis 25 %, wie die Tasten 1 bis 5) und Hochskalieren (weich oder
  pixelig, wie F).
- **Steuerung:** Geschwindigkeit der Maus beim Umsehen (langsam, normal, schnell).
- **KI:** „Nachdenken vor dem Zeichnen“ (Standard, wenig, mittel, viel) wird als
  `reasoning_effort` gesendet, bei „Standard“ gar nicht, wie im Enterprise AI Client.
  „Antwortlänge in Tokens“ ist `max_tokens`; Nachdenken zählt mit.
- **Welt:** „Häuser im Hintergrund weiterzeichnen“ an oder aus, mit dem Stand der Sammlung.
  Darunter „Welt neu zeichnen lassen …“: öffnet das Menü „Die Welt erschaffen“ aus dem
  laufenden Spiel.

Bild und Steuerung merkt sich das Spiel in `.3d4j/game.properties` (`--scale=` hat Vorrang,
`--benchmark` ignoriert die Datei), die KI-Werte in `.3d4j/generator.properties`
(`chat.reasoningEffort`, `chat.maxTokens`, `background.enabled`), wo sie auch für `generate` gelten. Das Modell wählt
man im Menü „Die Welt erschaffen“.

Dafür mischt der Renderer im Overlay (nach dem Hochskalieren der Szene) durchsichtige Pixel
mit dem, was darunter liegt, in OpenGL wie in Direct3D 11. Die Szene selbst bleibt deckend
gezeichnet; im laufenden Spiel kostet das nichts.

### Der Generator auf der Kommandozeile

Dasselbe ohne Menü:

```
java -jar 3d4j.jar generate
```

Er fragt jedes Motiv als SVG an (mehrere Anfragen gleichzeitig), prüft, ob sich die Antwort
zeichnen lässt und ein freigestelltes Motiv zeigt, fragt bei Fehlern bis zu dreimal mit einem
Hinweis nach und legt SVG und PNG im Asset-Ordner ab. Das Spiel selbst braucht kein Netz.

- **Dienst und Modell:** eine OpenAI-kompatible API (`POST <Basis-URL>/chat/completions`).
  Der Generator verwendet, was im Menü gewählt wurde (`.3d4j/generator.properties`), sonst die
  Konfiguration des Enterprise AI Client (`chat.baseUrl`, `chat.model`, `chat.apiKeyRef`).
  Oder einmal angeben, die Werte werden gemerkt: `--entry=TITEL --model=openai/gpt-oss-120b`.
- **Adresse und API-Key:** aus dem KeePass-Eintrag über das KeePassRPC-Plugin:
  Passwort = API-Key, URL = Adresse. `--base-url=https://.../v1` hat
  Vorrang vor der URL des Eintrags. Beim ersten Lauf zeigt KeePass einen Kopplungscode, den
  man in die Konsole tippt; die Kopplung liegt danach in `.3d4j/keepass.properties` und gilt
  auch für das Menü. Ist die Umgebungsvariable `THREED4J_API_KEY` gesetzt, wird sie statt
  KeePass verwendet; dann muss die Adresse angegeben sein.
- **Antwortlänge:** Jede Anfrage sendet `max_tokens` (Standard 8192, sonst `chat.maxTokens` aus
  `.3d4j/generator.properties` oder dem Enterprise AI Client). Meldet der Dienst
  `finish_reason=length`, gilt die Antwort als abgeschnitten und wird nicht verwendet.
- **Weitere Argumente:** `--parallel=N` (Standard 4), `--only=lamp,visitor-1`, `--force`
  (vorhandene Motive neu erzeugen).
- **Proxy:** Den Weg zum Dienst bestimmt `win-proxy-java`, wie im Enterprise AI Client und in
  Space Harvester. Gelesen wird `network.proxy.*` aus der Konfiguration des Enterprise AI
  Client (Modus, PAC-URL, Host, Port, `nonProxyHosts`, BASIC-Anmeldung aus KeePass); dieselben
  Schlüssel in `.3d4j/generator.properties` haben Vorrang, davor
  `-Dthreed4j.proxy.mode=… -Dthreed4j.proxy.pacUrl=… -Dthreed4j.proxy.host=… -Dthreed4j.proxy.port=…`.
  Ohne Einstellung gilt unter Windows `PAC_URL_POWERSHELL` (PAC-Skript der Windows-Einstellungen),
  sonst `DISABLED`. `java -jar 3d4j.jar generate --route=https://dienst/` zeigt Schritt für
  Schritt, welcher Weg für eine Adresse herauskommt. Menü und Generator verwenden denselben Weg.
- **Zertifikate:** Unter Windows gelten Javas eigene Liste und zusätzlich die Stammzertifikate
  des Windows-Speichers, damit firmeneigene Zertifikate (Dienst oder prüfender Proxy) akzeptiert
  werden.

Abgelehnte Antworten des Modells landen zur Ansicht als `<name>.rejected.txt` im Asset-Ordner.

**Motive aus Bauplan und Bauteilen.** Der Pfingstrosenbusch (`peony-bush`) wird nicht als
eine Zeichnung angefragt. Das Modell liefert, was es kann: einen Bauplan als JSON (Blütenblätter
je Ring, Größe, Versatz, Farben) und fünf kleine Bauteile als SVG-Fragment (drei Blütenblätter,
Blütenmitte, Blatt), alle Bauteile gleichzeitig. Alles Räumliche macht Java (`FlowerComposer`):

- Zahlen des Plans außerhalb ihres Bereichs werden hineingezogen, unbrauchbare Farben ersetzt.
- Jedes Bauteil wird geprüft und neu aufgebaut: nur eine flache Gruppe aus Pfaden (Kreise nur in
  der Blütenmitte), nur Geometrie und Füllfarben werden übernommen. Die Silhouette bekommt die
  Farbe aus dem Plan, Schattierungen werden an der Silhouette abgeschnitten, die Kontur im Stil
  des Spiels kommt zuletzt obendrauf. Ein Bauteil, das sein Feld verlässt oder viel zu klein
  ist, wird mit Begründung neu angefragt (bis zu dreimal).
- Montage: Blütenblätter nach Plan zur Blüte, fünf Blüten und ein Blätterfächer zum Busch;
  jedes Bauteil steht einmal unter `<defs>` und wird mit `<use>` platziert.

Der Plan steht danach in `peony-bush.json`. Im Spiel stehen die Büsche um den Brunnen und am
Rand des Platzes.

**Bodentexturen.** Rasen (`lawn-tile`) und Straße (`street-tile`) sind quadratische Kacheln,
von oben gesehen, die sich nahtlos wiederholen. Der Generator verlangt dafür: ein Rechteck in
der Grundfarbe über das ganze Quadrat, darauf kleine Formen, die mindestens 12 Einheiten Abstand
zu allen Rändern halten. Geprüft wird, dass die Kachel das Quadrat vollständig deckt und ihr
äußerster Rand einfarbig ist; sonst wird mit Begründung neu angefragt. Ohne Dateien zeichnet das
Spiel eingebaute Kacheln. Der Platz hat vorerst nur die eingebaute.

Im Spiel ist der Boden ein eigenes Netz mit eigenem Shader (vierter Draw Call). Die
Texturkoordinate ergibt sich aus der Position in der Welt (eine Kachel je 4 m), alle Kacheln
liegen in einem Atlas mit wiederholtem Rand. Weil Texturen noch keine Mipmaps haben, blendet der
Shader das Muster zwischen 30 und 75 m Entfernung in die Durchschnittsfarbe der Kachel über;
sonst würde der Boden in der Ferne flimmern.

**Alte Motive bleiben erhalten.** Zu jedem erzeugten Motiv schreibt der Generator `<name>.json`
(Modell, Zeitpunkt, Beschreibung, Größe). Wird ein Motiv neu gezeichnet, wandern SVG, PNG und
JSON des bisherigen vorher nach `history/<name>/<JJJJMMTT-HHMMSS>.svg|.png|.json` im
Asset-Ordner; der Zeitstempel ist der Zeitpunkt, zu dem das alte Motiv entstand. Das gilt auch
für von Hand abgelegte Dateien. Das Spiel lädt nur die Dateien direkt im Asset-Ordner.

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
| `platform` | `Window`, `GLWindow`, `Input`, `Key`, `MouseButton` |
| `platform.win32` | `Win32Api` (Bindings) und `Win32Window` (Fenster, Nachrichten, optional WGL) |
| `platform.x11` | `X11Api` (Bindings) und `X11Window` (Fenster, Events, GLX) |
| `gfx` | Die Grafik-Schnittstelle der Engine: `GraphicsDevice`, `Shader`, `ShaderLayout`, `Mesh`, `Texture`, dazu `MeshBuilder`, `Meshes`, `Textures`, `Camera`, `Screenshot` |
| `gfx.gl` | OpenGL-Implementierung: `GLDevice`, `GLShader`, `GLMesh`, `GLTexture` |
| `gfx.d3d11` | Direct3D-11-Implementierung: `D3D11Api` (Bindings), `ComObject` (COM-Aufrufe), `D3D11Device`, `D3D11Shader`, `D3D11Mesh`, `D3D11Texture` |
| `core` | `Engine` (Game-Loop), `Display` (Auswahl von Fenster und Grafik-API), `Game`, `EngineContext`, `EngineConfig`, `FrameStats` |
| `math` | `Vec3`, `Mat4` |
| `assets` | `AssetStore` (Asset-Ordner), `PartLibrary` (Bauteil-Sammlung je Stadtteil), `AssetCatalog`, `SvgRasterizer` (SVG zu Bild mit Batik) |
| `generator` | `AiSetup` (Schnittstelle für das Menü), `AssetGenerator`, `FlowerComposer` (Motiv aus Bauplan und Bauteilen), `PartArtist` und `BackgroundArtist` (Bauteile der Häuser), `ChatClient`, `KeePassApiKey`, `KeePassEntry`, `GeneratorConfig`, `ProxyRoutes` |
| `com.aresstack.enterpriseai…keepassrpc` | KeePassRPC-Anbindung (Kopplung, Einträge), unveränderte Kopie aus dem Enterprise AI Client, wie in Space Harvester; siehe `UPSTREAM.md` dort. Eigene Datei nur `KeePassRpcLink` |
| `demo` | `PlazaDemo` (Ladebildschirm, Menü und Testplatz), `SetupScreen` (das Menü), `PauseMenu`, `GameSettings`, `PlazaScene`, `District`, `Facades`, `FacadeAtlas`, `WestSide` (Easter Egg), `GroundAtlas`, `SpriteAtlas`, `CubeDemo` |

Ein Spiel benutzt nur `core`, `gfx`, `math` und `platform`; von OpenGL oder Direct3D sieht es
nichts.

### Ein Frame in zwei Phasen

Die Szene wird in ein Bild in interner Auflösung gerendert (Fenstergröße mal `renderScale`),
mit Tiefentest. `Game.render` zeichnet in dieses Bild. Danach streckt die Engine es auf die
Fenstergröße, und `Game.renderOverlay` zeichnet in voller Auflösung darüber: Ladebildschirm,
Menüs, Anzeigen. So bleibt Schrift scharf, während die Szene mit weniger Pixeln auskommt. Das
ist der wichtigste Hebel, wenn ein Software-Renderer rechnet, denn dort kostet jeder Pixel
CPU-Zeit.

Der Testplatz zeigt die übrigen Sparmaßnahmen: alle Gebäude in einem einzigen Mesh, alle
Bäume, Laternen und Besucher als Sprites in einem zweiten (das Ausrichten zur Kamera und die
Bewegung rechnet der Vertex-Shader), der Himmel als drittes. Drei Zeichenaufrufe pro Frame,
Beleuchtung in drei festen Stufen, Dunst statt Fernsicht, Sprites ausgestanzt statt
transparent.

### Shader

Jeder Shader liegt unter `src/main/resources/shaders` in beiden Sprachen vor: `name.vert` und
`name.frag` (GLSL) für OpenGL, `name.hlsl` mit den Einstiegspunkten `vertexMain` und
`pixelMain` für Direct3D. Das `ShaderLayout` in Java beschreibt Vertex-Attribute und Uniforms.
Für die HLSL-Datei ist seine Reihenfolge bindend: Attribut `i` hat die Semantik `ATTRIBUTEi`,
und die Uniforms bilden in derselben Reihenfolge einen `cbuffer` an Register `b0`.

Beide Backends verhalten sich gleich: Matrizen folgen den OpenGL-Konventionen von `Mat4`,
Dreiecke gegen den Uhrzeigersinn zeigen zum Betrachter, Rückseiten werden verworfen. Den
anderen Tiefenbereich von Direct3D gleicht eine Zeile im HLSL-Vertex-Shader aus.

### Ein eigenes Spiel

```java
public final class MyGame implements Game {
    public static void main(String[] args) {
        Engine.run(EngineConfig.of("Mein Spiel", 1280, 720), new MyGame());
    }

    @Override public void init(EngineContext context) { /* context.graphics(): Shader und Meshes anlegen */ }
    @Override public void update(EngineContext context, double deltaSeconds) { /* Logik, Eingabe */ }
    @Override public void render(EngineContext context) { /* graphics.clear(...), graphics.draw(mesh, shader) */ }
    @Override public void renderOverlay(EngineContext context) { /* optional: 2D in voller Auflösung */ }
    @Override public void dispose() { /* GPU-Ressourcen freigeben */ }
}
```

### Eine OpenGL-Funktion ergänzen

In `GL` ein Handle mit der C-Signatur und eine statische Wrapper-Methode anlegen, nach dem
Muster der vorhandenen Funktionen. Für Direct3D entsprechend in `D3D11Api`: Dort steht zu
jeder COM-Methode ihre Slot-Nummer in der virtuellen Methodentabelle. `GL` darf erst benutzt werden, wenn ein `Window`
existiert: Die Funktionsadressen liefert unter Windows erst der Grafiktreiber, sobald ein
Kontext aktiv ist.

## Build

`.github/workflows/build.yml` baut das JAR, prüft Manifest und Class-File-Versionen (Launcher
ohne, FFM-Schicht mit Preview-Markierung), startet es auf einem virtuellen Display mit einem
schlichten `java -jar` (einmal den Ladebildschirm, einmal den Testplatz) und veröffentlicht es anschließend unter dem festen Release-Tag `latest`.

## Teststand

| Umgebung | Stand |
|---|---|
| Linux x64, OpenGL (Mesa llvmpipe unter Xvfb) | getestet: Start über den Launcher, Rendering, Tastatur, Größenänderung, Schließen |
| Windows-Codepfad mit Windows-JDK 21 unter Wine, Direct3D 11 | getestet: dieselben Punkte; das Bild ist pixelgleich mit dem OpenGL-Bild |
| Windows-Codepfad unter Wine, OpenGL | getestet: dieselben Punkte |
| Echtes Windows, Direct3D 11 auf WARP („Microsoft Basic Render Driver“) | Würfel-Demo läuft; der Testplatz ist dort noch nicht gelaufen |
| Echtes Windows, Direct3D 11 mit Grafikkartentreiber | noch nicht getestet |
| Echtes Windows ohne OpenGL-Treiber, OpenGL | scheitert erwartungsgemäß mit einer Diagnose („GDI Generic“) |

## Grenzen und nächste Schritte

- Text, Audio, Kollision und das Laden von Modelldateien fehlen noch.
- Menü und Generator sind nur gegen einen nachgebauten Dienst getestet, nicht gegen ein echtes
  KeePass und nicht gegen den echten KI-Dienst. Kopplung und Eintragsliste laufen gegen den
  nachgebauten KeePassRPC-Server des Enterprise AI Client (`FakeKeePassRpcServer`). Der Weg
  über einen Proxy ist mit festem Proxy und mit einer PAC-Datei geprüft, nicht mit den
  Windows-Modi (PowerShell) und nicht mit Anmeldung am Proxy.
- Das Menü ist als ein Bild mit Java 2D gemalt; Textfelder kennen nur Anhängen, Löschen am
  Ende und Einfügen.
- Ohne Dateien im Asset-Ordner sind die Sprites mit Java 2D gemalte Platzhalter (`SpriteAtlas`).
- Texturen haben keine Mipmaps, weit entfernte Sprites flimmern deshalb leicht.
- macOS wird nicht unterstützt (dort wäre Cocoa über `objc_msgSend` nötig).
- Der Game-Loop hat einen variablen Zeitschritt und ohne VSync keine Bildratenbegrenzung.
- Umstieg auf Java 22 oder neuer: Die FFM-API ist dort final. Alle Methoden, die sich
  zwischen Preview und finaler API geändert haben, werden nur in `ffm/Native.java` benutzt.
  Danach können `--enable-preview` und der Neustart im Launcher entfallen.
