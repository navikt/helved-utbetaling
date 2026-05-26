# Tema 5: Endringshåndtering

Status: Utkast

- [Powerpoint](https://navno.sharepoint.com/:p:/r/sites/Helhetligkvalitetssystem/Delte%20dokumenter/Minimum%20kontrollrammeverk%20%C3%B8konomisystem%20(MKR-%C3%98S)/Presentasjoner%20fra%20oppskytningsrampe/Tema%205%20oppstartsm%C3%B8te.pptx?d=w61f4ed122ca54013b15559c9486b2af3&csf=1&web=1&e=khxD82) med kravene for _Endringshåndtering_.
- [Excel](https://navno.sharepoint.com/:x:/r/sites/Helhetligkvalitetssystem/_layouts/15/Doc.aspx?sourcedoc=%7B1416B70A-089A-49D8-99C1-AA5CA1D5DB1C%7D&file=Minimum%20kontrollrammeverk%20%25u00f8konomisystem%20(v1.1).xlsx&action=default&mobileredirect=true) med hele kontrollrammeverket

## 5.1 Regelsett for endringshåndtering​

Vi (Team hel ved) følger som regel dette mønsteret:

- Endringer gjøres via par- eller mobprogrammering uten bruk av pull requests. Dette fungerer som løpende godkjenning av endringer. Når kode sjekkes inn i git legger vi på `Co-authored-by` for å dokumentere hvem som har vært involvert i utviklingen av endringen.
- For sporbarhet refererer vi til oppgave-id i hver commit. 
- Alle kodeendringer skal være dekket av automatiserte tester.
- Automatiske bygg- og testjobber må kjøre grønt for at koden skal deployes.
- Vi bruker GitHub som sentral plattform for kildekode, endringshistorikk og utrulling. Bygg, testing og deploy gjennomføres ved hjelp av GitHub Actions.
- Vi deplyer til dev og prod samtidig (fordi forskjeller mellom miljøene gjør endringshåndtering vanskeligere). 

Unntak:

- At testene kjører grønt kan være godkjenning nok til å også deploye en endring uten at andre har sett på det først. Det særlig gjeldene i ferier og ved produksjonsfeil som oppstår utenfor normal arbeidstid
- Endringer som gjøres solo kan også inkluderes og godkjennes via pull request, eller ved at man før push til main tar en gjennomgang av endringen sammen med andre i teamet
- Ved større endringer, eller endringer som er av en slik art at vi ikke får testet de fullt ut lokalt, skrur vi av deploy til prod for å verifisere i dev først. Det kan for eksempel være relevant ved endringer av topologi og infrastruktur.




## 5.2 Klassifisering av endringer​

Vi bruker ikke klassifisering av endringer. Alle endringer går derfor som normale endringer.


## 5.3 Testing og godkjenning av endringer​

For å kunne deploye kode må man ha tilgang til teamets område (`helved`) på Nais-plattformen. Det forekommer derfor ikke uautoriserte endringer annet en hva som kommer fra Dependabot.

For Dependabot er det påskrudd en cooldown-periode på én uke, som betyr at endringer må modnes én uke før Dependabot tar dem inn. Merge av pull requests fra Dependabot trigger ikke automatisk deploy. Deploy kan kun trigges av medlemmer av Team hel ved.


## 5.4 Produksjonssetting - Manuell​

Ikke relevant. Vi gjør ikke manuell prodsetting.

## 5.5 Produksjonssetting - Automatisk​

All produksjonssetting skjer automatisert gjennom GitHub Actions, med tester og andre kontroller som sikrer kvalitet og sporbarhet.