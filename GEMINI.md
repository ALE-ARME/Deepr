# Regole di Sviluppo Deepr

## Lint e Qualità del Codice
- **Mandatorio**: Prima di ogni operazione di `git commit` o `git push`, è obbligatorio eseguire il controllo di formattazione locale utilizzando l'eseguibile Java di `ktlint`.
- **Comando**: `java -jar ktlint <file1> <file2> ...`
- **Scopo**: Evitare fallimenti della CI (GitHub Actions) dovuti a problemi di formattazione (indentazione, virgole, import inutilizzati, ecc.) che Yogesh non accetta nelle PR.

## Gestione Branch
- Seguire lo schema degli **Stacked PRs** (1-N, es. 1-fix-..., 2-feat-...): è una serie numerata di PR dipendenti. Per creare un branch successivo, fai il checkout dell'ultimo (es. `git checkout 1-fix`) e da lì stacca il nuovo (es. `git checkout -b 2-feat`). In questo modo ogni branch contiene le modifiche dei precedenti.
- Il branch di base per il tuo fork verso l'upstream deve essere allineato all'upstream master (es. un branch chiamato `yogesh` o `upstream/master`). Assicurati che la base della catena parta sempre da questo punto stabile.
