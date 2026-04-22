# Tema 2: Ikke personlige brukere og Autentisering

Lenker til utfyllende informasjon om kravene:

- [Powerpoint](https://navno.sharepoint.com/:p:/r/sites/Helhetligkvalitetssystem/Delte%20dokumenter/Minimum%20kontrollrammeverk%20%C3%B8konomisystem%20(MKR-%C3%98S)/Presentasjoner%20fra%20oppskytningsrampe/Tema%202%20oppstartsm%C3%B8te.pptx?d=wed148c72e0634b4ea2e7433ddf05526c&csf=1&web=1&e=VIRIQa) med kravene for _Ikke personlige brukere og Autentisering_
- [Excel](https://navno.sharepoint.com/:x:/r/sites/Helhetligkvalitetssystem/_layouts/15/Doc.aspx?sourcedoc=%7B1416B70A-089A-49D8-99C1-AA5CA1D5DB1C%7D&file=Minimum%20kontrollrammeverk%20%25u00f8konomisystem%20(v1.1).xlsx&action=default&mobileredirect=true) med hele kontrollrammeverket

---

Lenker til relevant, eksisterende dokumentasjon for Team hel ved og tjenesten Utsjekk:

- [K267.1: Applikasjoner skal ha et forsvarlig sikkerhetsnivå](https://etterlevelse.ansatt.nav.no/dokumentasjon/85240a1e-2b08-4298-b7a3-bbb520472dac/INFOSIKKERHET/krav/267/1). Se suksesskriterium 7: Vi har tilgangskontroll på alle endepunkter.
- [K205.2: NAV må sikre at utenforstående ikke får uberettiget innsyn i enkeltvedtak, forhåndsvarsel og meldinger](https://etterlevelse.ansatt.nav.no/dokumentasjon/85240a1e-2b08-4298-b7a3-bbb520472dac/EL_KOM/krav/205/2). Se suksesskriterium 2: Nav skal sikre tilgangsstyring for egne medarbeidere.
- [K125.2: Sikkerhet i økonomisystemet](https://etterlevelse.ansatt.nav.no/dokumentasjon/85240a1e-2b08-4298-b7a3-bbb520472dac/OKONOMI/krav/125/2). Se suksesskriterium 1: Tilgangskontroll og roller.

## Ikke-personlige brukere

Utsjekk er en plattformtjeneste uten GUI. Det gjøres ingen saksbehandling i løsningen. Løsnigen tar i mot vedtak fra fagsystemer, transformerer dem og videresender til OS.

Vi har ingen innlogging og følgelig heller ingen ikke-personlige brukere i løsningen. 

Vi har et verktøy kalt Peisen som vi bruker til overvåking og debugging av utbetalinger. I produksjon er det kun Team hel ved som har tilgang til Peisen. Tilgang administreres via Mine tilganger. Det er kun personlige brukere.




## Autentisering

Alle endepunkter i Utsjekk har tilgangskontroll. 

Tilgang til Peisen (internt verktøy) gis via en egen Entra ID-gruppe og administreres via Mine tilganger. Vi bruker ellers standard NAIS-funksjonalitet.

Se punktet over for mer utfyllende dokumentasjon og lenker.