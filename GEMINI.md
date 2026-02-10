# Istruzioni per Gemini CLI

## Gestione dei Branch e Pull Request
Per garantire che le Pull Request siano focalizzate e pulite (senza file inutili come workflow, README modificati o conflitti di altri branch), segui sempre questa procedura prima di aprire una PR o quando richiesto di "ripulire":

1.  **Fetch dell'upstream:** Assicurati di avere l'ultimo stato del repository originale.
    ```bash
    git fetch origin master
    ```

2.  **Reset del branch:** Porta il branch della issue allo stato esatto dell'upstream master.
    ```bash
    git checkout <branch-issue>
    git reset --hard origin/master
    ```

3.  **Applicazione selettiva (Cherry-pick dei file):** Recupera **solo** i file necessari per il fix dal branch di sviluppo o dal commit specifico.
    ```bash
    git checkout <branch-con-modifiche> -- <percorso/del/file1> <percorso/del/file2>
    ```

4.  **Commit e Push forzato:** Crea un commit pulito e aggiorna il fork.
    ```bash
    git commit -m "messaggio chiaro riferito alla issue"
    git push fork <branch-issue> --force
    ```

## Regole d'Oro
- **NON** creare MAI una Pull Request (PR) di tua iniziativa. Attendi sempre l'istruzione esplicita: "Fai la PR". L'utente deve testare le modifiche (tramite APK o altro) prima dell'invio ufficiale.
- **MAI** includere file di GitHub Actions (`.github/workflows/`), `.gitignore`, `trigger_build.txt` o file di log/temporanei nelle Pull Request.
- **MAI** includere screenshot o immagini nella PR a meno che non siano icone o risorse grafiche integrate nel codice dell'app.
- **MAI** includere modifiche al `README.md` o `translations_en_it.md` in PR di correzione bug o nuove feature di codice, a meno che non sia esplicitamente richiesto.
- **SOLO CODICE E RISORSE**: Le Pull Request devono contenere esclusivamente modifiche al codice sorgente (`.kt`, `.sq`) e risorse UI (`xml`, `strings.xml`) strettamente necessarie al task.
- **TRADUZIONI OBBLIGATORIE**: Ogni volta che viene aggiunta una nuova stringa, è **MANDATORIO** fornire sempre la traduzione in italiano nel file `values-it/strings.xml`.
- Se una PR contiene file estranei, ripeti la procedura di pulizia sopra descritta per "snellirla".
- **Utilizzo di Context7**: Usa sempre lo strumento `context7` per recuperare documentazione aggiornata ed esempi di codice idiomatici per librerie e framework (es. Jetpack Compose), evitando di basarti solo sulla conoscenza latente.
- **Operazioni Git**: Esegui sempre le operazioni di `add`, `commit` e `push` insieme in un'unica sequenza (es. `git add . && git commit -m "..." && git push fork <branch> --force`). Usa sempre il nome del remoto `fork` per il repository personale.
