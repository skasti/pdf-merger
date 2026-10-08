# PDF Merger

A local Kotlin terminal application that merges 1–15 PDF files into a new PDF. Every page is included in the order you choose, and files can be selected from multiple directories.

PDF processing uses Apache PDFBox. The fullscreen interface uses Mordant panels and focus highlighting, with JLine providing keyboard input and native console output. On Windows, Unicode text is written directly to the console.

The interface currently uses Norwegian labels and messages.

## Requirements

- Java 21 or later.
- An interactive terminal, such as Windows Terminal or the terminal tab in IntelliJ IDEA.
- A terminal window of at least 64 columns × 16 rows; 100 columns or more is recommended.

The Gradle wrapper is included, so you do not need to install Gradle separately.

## Build and run

### Windows

Run these commands in PowerShell from the project directory:

```powershell
.\gradlew.bat installDist
.\build\install\pdf-merger\bin\pdf-merger.bat
```

You can also choose a starting directory:

```powershell
.\build\install\pdf-merger\bin\pdf-merger.bat "$env:USERPROFILE\Documents"
```

If Windows Terminal reports `TERM=dumb`, set the terminal type and start the application again in the same window:

```powershell
$env:TERM = 'xterm-256color'
```

### Linux and macOS

From the project directory:

```sh
bash ./gradlew installDist
./build/install/pdf-merger/bin/pdf-merger
```

Pass an optional starting directory, or use `--help` to show the command-line usage:

```sh
./build/install/pdf-merger/bin/pdf-merger "$HOME/Documents"
./build/install/pdf-merger/bin/pdf-merger --help
```

### IntelliJ IDEA on Windows

Select **PDF-merger** from the Run configuration list and click **Run**. The shared configuration in `.run/PDF-merger.run.xml` builds the application with `installDist` and opens it in Windows Terminal.

Windows Terminal and IntelliJ's Shell Script support must be installed. Use this configuration for the interactive interface: running `MainKt.main()` through Gradle uses a Run console that may not provide the required terminal access.

## Keyboard controls

| Key | Action |
| --- | --- |
| ↑ / ↓ | Move the cursor in the active panel |
| Enter | Open a directory or select/remove the highlighted PDF |
| Space | Select/remove a PDF; up to 15 files |
| Tab | Switch between the file browser and selection order |
| + / - | Move a file up/down in the selection order |
| Delete | Remove a file from the selection order |
| Backspace | Open the parent directory, or remove a file in the selection panel |
| G | Enter a directory path, including a path on another drive |
| O | Enter a filename or full path for the output PDF |
| M | Merge the selected files |
| Q / Esc / Ctrl+C | Quit |

In path prompts, **Enter** confirms, **Esc** cancels, **Backspace** deletes the last character, and **Ctrl+U** clears the field. Relative paths are resolved from the directory shown in the file browser. The `.pdf` extension is added automatically if it is missing from the output filename.

## Output and supported files

The default output is `samlet.pdf` in the starting directory. Existing files are never overwritten, and source files are left intact. The output directory must already exist.

After merging, the interface shows the number of files, the total page count, and the output path. Encrypted or password-protected PDFs must be saved as unencrypted copies before they can be merged.

## Tests

On Windows:

```powershell
.\gradlew.bat test
```

On Linux or macOS:

```sh
bash ./gradlew test
```

## License

This project is licensed under the [MIT License](LICENSE). Third-party dependencies retain their own licenses.
