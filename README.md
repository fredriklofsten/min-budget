# Min budget

Enkel budgetapp för Android. Ladda ner: [min-budget.apk](https://github.com/fredriklofsten/min-budget/raw/main/min-budget.apk)

1. Öppna filen på mobilen.
2. Tillåt installation från okända källor när Android frågar.
3. Tryck Installera. Kräver Android 8 eller nyare.

Allt sparas bara på din telefon.

## Nytt i 1.3: lönen uppdateras på lönedagen

Bocka i "Beloppet varierar" på en inkomst under Planera (Lön är ibockad från början). På lönedagen får du en notis kl. 8 och appen frågar hur mycket lönen blev. Beloppet du skriver in blir lönen och allt räknas om till nästa lön.

**Uppdaterar du från version 1.2?** Den gamla appen måste avinstalleras först:

1. Öppna den gamla appen, gå till Profil → Säkerhetskopia → **Spara kopia som fil**.
2. Avinstallera den gamla appen och installera den nya.
3. Gå till Profil → Säkerhetskopia → **Läs in kopia från fil** och välj filen.

## Bygga själv

Android-projektet ligger i `android/`. Kopiera `index.html` till `android/app/src/main/assets/` och kör `gradle assembleRelease`.
