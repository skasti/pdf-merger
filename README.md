# PDF-sammenslåing

Et terminalverktøy i Kotlin som slår sammen 1–15 PDF-filer til én ny PDF. Alle sidene tas med i rekkefølgen du velger. Filene behandles lokalt med Apache PDFBox, og terminalgrensesnittet bruker Mordant 3.1.0 til paneler, fokusmarkering og fullskjermvisning, med JLine til tastatur og native terminalutskrift. På Windows skrives Unicode-tegn direkte til konsollen, uavhengig av aktiv kodepage.

Du trenger Java 21 eller nyere.

I IntelliJ på Windows: velg **PDF-merger** i Run-listen øverst og trykk **Run**. Den delte konfigurasjonen i `.run/PDF-merger.run.xml` bygger programmet med `installDist` og åpner det i Windows Terminal, på samme måte som i retro-zip. Windows Terminal og IntelliJs Shell Script-støtte må være installert. Bruk denne konfigurasjonen til TUI-et; kjøring av `MainKt.main()` via Gradle bruker en Run-konsoll uten nødvendig terminaltilgang.

Du kan også bygge og starte fra PowerShell i prosjektmappen:

```powershell
.\gradlew.bat installDist
.\build\install\pdf-merger\bin\pdf-merger.bat
```

Du kan også velge startmappe:

```powershell
.\build\install\pdf-merger\bin\pdf-merger.bat "C:\Users\stig-\Documents"
```

Bruk en vanlig terminal, for eksempel Windows Terminal eller terminalfanen i IntelliJ. IntelliJs vanlige Run-konsoll støtter ikke nødvendigvis det interaktive grensesnittet. Anbefalt størrelse er minst 100 kolonner; minimum er 64 kolonner og 16 rader.

Hvis Windows Terminal gir feilen `TERM=dumb`, kjør `$env:TERM = 'xterm-256color'` i PowerShell og start programmet igjen i samme terminal.

På Linux/macOS: `./gradlew installDist`, deretter `./build/install/pdf-merger/bin/pdf-merger`.

| Tast | Handling |
| --- | --- |
| ↑ / ↓ | Flytt markøren i aktivt panel |
| Enter | Åpne mappe eller velg/fjern markert PDF |
| Mellomrom | Velg/fjern en PDF; maksimalt 15 |
| Tab | Bytt mellom filvelger og valgt rekkefølge |
| + / - | Flytt filen opp/ned i rekkefølgepanelet |
| Delete | Fjern en fil fra rekkefølgepanelet |
| Backspace | Gå til overordnet mappe, eller fjern fra rekkefølgepanelet |
| G | Skriv inn en mappe, også på en annen disk |
| O | Skriv inn navn eller full sti til utfilen |
| M | Slå sammen valgte filer |
| Q / Esc / Ctrl+C | Avslutt |

Du kan velge filer fra flere mapper. I tekstfeltene bekrefter Enter, Esc avbryter, Backspace sletter siste tegn og Ctrl+U tømmer feltet. Relative stier tolkes fra mappen som vises i filvelgeren. `.pdf` legges til automatisk hvis det mangler i navnet på utfilen.

Standard utfil er `samlet.pdf` i startmappen. Eksisterende filer overskrives aldri, og originalene beholdes. Mappen du lagrer til må finnes. Etter sammenslåing vises antall filer, antall sider og lagringssted. Krypterte/passordbeskyttede PDF-er må lagres som ukrypterte kopier før de kan brukes.

Kjør testene med `./gradlew test` (`.\gradlew.bat test` på Windows).
