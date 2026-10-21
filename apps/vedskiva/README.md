# Vedskiva

Scheduler for (grensesnitt)avstemming.
Avstemminger kjøres hver virkedag og avstemmer alle oppdrag og kvitteringer siden forrige virkedag.

## Features
- Lagrer dato for gjennomført avstemming i database
- Kjøres som en kubernetes (cron) job hver dag men gjør ingen ting på helligdager

## Topology
- Oppdrag og kvitteringer fra helved.oppdrag.v1 lagres i egen database
- På virkedag hentes forrige gjennomførte avstemmingsdato og oppdrag fra egen database
- Avstemminger kalkuleres og legges på helved.avstemming.v1

![vedskiva](vedskiva.svg)
