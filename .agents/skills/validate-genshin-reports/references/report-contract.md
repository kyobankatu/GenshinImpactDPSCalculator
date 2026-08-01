# Report contract

Verify affected paths end to end:

- combat event -> `CombatLogSink` / `VisualLogger`;
- logged fields -> `SimulationRecord`;
- simulator and snapshots -> `ReportDataBuilder`;
- typed runtime state -> `ReportViewAdapter`;
- immutable report bundle -> `ReportData`;
- markup and Chart.js datasets -> `ReportHtmlRenderer`;
- destination and encoding -> `ReportFileWriter` / `HtmlReportGenerator`.

Check damage contribution, cumulative damage, reaction damage, action damage, rolling DPS, aura, energy, buff uptime, stat snapshots, formula detail, filters, character assets, empty series, and escaped labels as applicable. Require UTF-8 and a self-contained report unless the existing contract says otherwise.
