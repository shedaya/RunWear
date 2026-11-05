#!/usr/bin/env node
/**
 * Cross-timezone verification for the PWA "location-frame now" fix
 * (fix/gen4-pwa-location-now-2026-11-04).
 *
 * "Now" must be the VIEWED LOCATION's live local time and date, not the
 * device clock (Sol, 2026-11-04). Proven bug: device in Kansas City
 * (America/Chicago) viewing Aspen (America/Denver) — the pill read the
 * phone's 6:43 PM instead of Aspen's 5:43 PM.
 *
 * This harness extracts the REAL functions from ../index.php
 * (locationNowFromOffset, locationNow, isSameDay, formatDate, formatTime)
 * — no copies — and exercises them in child processes whose TZ is the
 * DEVICE timezone, with the location's Open-Meteo utc_offset_seconds
 * stubbed into `state`, and Date.now() frozen at a fixed instant.
 *
 * Run:  node runwearpwa22/tests/location-now.test.js
 * Exit code 0 = all cases pass.
 */
'use strict';
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const INDEX = path.join(__dirname, '..', 'index.php');

// ---- Fixed instant for cases 1–3: 2026-11-05T00:43:00Z ----------------
// = Nov 4, 6:43 PM in Chicago (CST, UTC-6) — the recording's device time
// = Nov 4, 5:43 PM in Denver  (MST, UTC-7) — Aspen local time
// = Nov 4, 7:43 PM in New York (EST, UTC-5)
// = Nov 4, 4:43 PM in Los Angeles (PST, UTC-8)
const INSTANT_A = Date.UTC(2026, 10, 5, 0, 43);
// Case 4 instant: 2026-11-04T11:30:00Z
// = Nov 4, 6:30 AM in New York (device), but Nov 5, 12:30 AM in Auckland
// (NZDT, UTC+13) — the location is already on the NEXT calendar day.
const INSTANT_B = Date.UTC(2026, 10, 4, 11, 30);

const CASES = [
    {
        id: 'aspen (the recorded bug)',
        deviceTz: 'America/Chicago', offsetSeconds: -7 * 3600, nowMs: INSTANT_A,
        expect: { deviceHour: 18, locHour: 17, locMinute: 43, locDay: 4, locTime: '5:43 PM', locDateLabel: 'Today', yesterdayLabelNotToday: true },
    },
    {
        id: 'mirror: NY device, LA location',
        deviceTz: 'America/New_York', offsetSeconds: -8 * 3600, nowMs: INSTANT_A,
        expect: { deviceHour: 19, locHour: 16, locMinute: 43, locDay: 4, locTime: '4:43 PM', locDateLabel: 'Today' },
    },
    {
        id: 'control: same timezone (Chicago device, Chicago location)',
        deviceTz: 'America/Chicago', offsetSeconds: -6 * 3600, nowMs: INSTANT_A,
        expect: { deviceHour: 18, locHour: 18, locMinute: 43, locDay: 4, locTime: '6:43 PM', locDateLabel: 'Today' },
    },
    {
        id: 'date rollover: NY device, Auckland location already on next day',
        deviceTz: 'America/New_York', offsetSeconds: 13 * 3600, nowMs: INSTANT_B,
        expect: { deviceHour: 6, deviceDay: 4, locHour: 0, locMinute: 30, locDay: 5, locTime: '12:30 AM', locDateLabel: 'Today', yesterdayLabelNotToday: true },
    },
];

// ---------- extraction of the real functions from index.php ----------
function extractFunction(src, name) {
    let start = src.indexOf(`function ${name}(`);
    if (start === -1) throw new Error(`function ${name} not found in index.php`);
    if (src.slice(start - 6, start) === 'async ') start -= 6; // keep `async` (fetchWeather)
    const braceStart = src.indexOf('{', start);
    let depth = 0;
    for (let i = braceStart; i < src.length; i++) {
        if (src[i] === '{') depth++;
        else if (src[i] === '}') { depth--; if (depth === 0) return src.slice(start, i + 1); }
    }
    throw new Error(`unbalanced braces in ${name}`);
}

// ---------- child mode: run one case, print JSON ----------
if (process.argv[2] === '--child') {
    const c = CASES[Number(process.argv[3])];
    const src = fs.readFileSync(INDEX, 'utf8');
    const code = ['locationNowFromOffset', 'locationNow', 'isSameDay', 'formatDate', 'formatTime']
        .map(n => extractFunction(src, n)).join('\n');
    // Freeze "now" at the case instant, stub the location frame in state.
    Date.now = () => c.nowMs;
    const state = { locationUtcOffsetSeconds: c.offsetSeconds };
    const factory = new Function('state', `${code}
        const locNow = locationNow();
        const deviceNow = new Date(${c.nowMs});
        // Location-frame "yesterday at this time" (for the Today-roll check):
        const locYesterday = locationNowFromOffset(${c.offsetSeconds}, ${c.nowMs} - 24*3600*1000);
        return {
            deviceHour: deviceNow.getHours(), deviceDay: deviceNow.getDate(),
            locHour: locNow.getHours(), locMinute: locNow.getMinutes(), locDay: locNow.getDate(),
            locTime: formatTime(locNow),
            locDateLabel: formatDate(locNow),
            yesterdayLabel: formatDate(locYesterday),
            viewingNow: isSameDay(locNow, locationNow()) && locNow.getHours() === locationNow().getHours(),
        };`);
    console.log(JSON.stringify(factory(state)));
    process.exit(0);
}

// ---------- child mode: integration through the REAL fetchWeather ----------
// Device TZ=America/Chicago (set by parent). A canned Open-Meteo response
// for Aspen (America/Denver, utc_offset_seconds=-25200, current time
// "2026-11-04T17:43") is stubbed in as fetch. Proves: the response's frame
// is stored, the Now state re-anchors state.selectedDate to 5:43 PM
// location time, the current-conditions branch is chosen, and an explicit
// 11 AM pick stays on the hourly branch at hour 11.
if (process.argv[2] === '--integration') {
    (async () => {
        const src = fs.readFileSync(INDEX, 'utf8');
        const coarseStart = src.indexOf('const coarseCoordinate');
        const coarse = src.slice(coarseStart, src.indexOf(';', coarseStart) + 1);
        const code = [coarse,
            ...['locationNowFromOffset', 'locationNow', 'isSameDay', 'fetchWeather']
                .map(n => extractFunction(src, n))].join('\n');
        Date.now = () => INSTANT_A;
        const canned = {
            utc_offset_seconds: -25200, timezone: 'America/Denver',
            current: {
                time: '2026-11-04T17:43', temperature_2m: 55, relative_humidity_2m: 30,
                dew_point_2m: 20, apparent_temperature: 54, precipitation: 0,
                precipitation_probability: 0, rain: 0, snowfall: 0, weather_code: 0,
                cloud_cover: 5, wind_speed_10m: 6, wind_gusts_10m: 9, uv_index: 1,
            },
            hourly: {
                time: ['2026-11-04T11:00', '2026-11-04T17:00'],
                temperature_2m: [40, 54], relative_humidity_2m: [35, 30], dew_point_2m: [22, 20],
                apparent_temperature: [39, 54], precipitation_probability: [0, 0],
                precipitation: [0, 0], rain: [0, 0], snowfall: [0, 0], weather_code: [0, 0],
                cloud_cover: [10, 5], wind_speed_10m: [5, 6], wind_gusts_10m: [8, 9],
                uv_index: [2, 1], visibility: [24140, 24140],
            },
        };
        const state = {
            useCelsius: false, locationUtcOffsetSeconds: null, locationTimezone: null,
            isNowSelected: true, selectedDate: new Date(INSTANT_A), // device-frame 6:43 PM
        };
        const run = new Function('state', 'fetch', `${code}
            return (async () => {
                const wNow = await fetchWeather(39.19, -106.82, state.selectedDate);
                const nowAnchor = { hour: state.selectedDate.getHours(), minute: state.selectedDate.getMinutes(),
                    offset: state.locationUtcOffsetSeconds, tz: state.locationTimezone,
                    weatherHour: wNow.hour, weatherTemp: wNow.temp };
                state.isNowSelected = false;
                state.selectedDate = new Date(2026, 10, 4, 11, 0, 0, 0); // explicit 11 AM pick (location frame)
                const wPick = await fetchWeather(39.19, -106.82, state.selectedDate);
                return { nowAnchor, pick: { weatherHour: wPick.hour, weatherTemp: wPick.temp, selHour: state.selectedDate.getHours() } };
            })();`);
        const result = await run(state, async () => ({ ok: true, json: async () => canned }));
        console.log(JSON.stringify(result));
    })().catch(e => { console.error(e); process.exit(1); });
    return; // in child mode do not fall through to the parent runner
}

// ---------- parent mode: spawn children under each device TZ ----------
let failures = 0;
CASES.forEach((c, i) => {
    const out = execFileSync(process.execPath, [__filename, '--child', String(i)], {
        env: { ...process.env, TZ: c.deviceTz }, encoding: 'utf8',
    });
    const r = JSON.parse(out);
    const checks = [];
    const eq = (label, actual, expected) => checks.push([label, actual, expected, actual === expected]);
    eq('device hour (sanity: harness TZ applied)', r.deviceHour, c.expect.deviceHour);
    if (c.expect.deviceDay !== undefined) eq('device day', r.deviceDay, c.expect.deviceDay);
    eq('location hour', r.locHour, c.expect.locHour);
    eq('location minute', r.locMinute, c.expect.locMinute);
    eq('location day-of-month', r.locDay, c.expect.locDay);
    eq('pill time (formatTime)', r.locTime, c.expect.locTime);
    eq('pill date (formatDate of now)', r.locDateLabel, c.expect.locDateLabel);
    eq('isViewingNow-style check', r.viewingNow, true);
    if (c.expect.yesterdayLabelNotToday) {
        checks.push(['location yesterday is NOT labelled "Today"', r.yesterdayLabel !== 'Today', true, r.yesterdayLabel !== 'Today']);
    }
    const bad = checks.filter(x => !x[3]);
    failures += bad.length;
    console.log(`\nCASE ${i + 1}: ${c.id}  [device TZ=${c.deviceTz}, location utc_offset_seconds=${c.offsetSeconds}]`);
    for (const [label, actual, expected, ok] of checks) {
        console.log(`  ${ok ? 'PASS' : 'FAIL'}  ${label}: got ${JSON.stringify(actual)}${ok ? '' : `, expected ${JSON.stringify(expected)}`}`);
    }
    if (c.expect.deviceHour !== c.expect.locHour) {
        console.log(`  NOTE  naive device-clock now would read hour ${r.deviceHour} — the old, buggy pill value.`);
    }
});
{
    const out = execFileSync(process.execPath, [__filename, '--integration'], {
        env: { ...process.env, TZ: 'America/Chicago' }, encoding: 'utf8',
    });
    const r = JSON.parse(out);
    const checks = [];
    const eq = (label, actual, expected) => checks.push([label, actual, expected, actual === expected]);
    eq('offset stored from response', r.nowAnchor.offset, -25200);
    eq('timezone stored from response', r.nowAnchor.tz, 'America/Denver');
    eq('Now re-anchored: selected hour', r.nowAnchor.hour, 17);
    eq('Now re-anchored: selected minute', r.nowAnchor.minute, 43);
    eq('Now weather hour', r.nowAnchor.weatherHour, 17);
    eq('Now weather from current block (temp 55)', r.nowAnchor.weatherTemp, 55);
    eq('explicit pick: weather hour stays 11', r.pick.weatherHour, 11);
    eq('explicit pick: hourly branch (temp 40)', r.pick.weatherTemp, 40);
    eq('explicit pick: selection unmoved', r.pick.selHour, 11);
    const bad = checks.filter(x => !x[3]);
    failures += bad.length;
    console.log(`\nCASE 5: integration via real fetchWeather [device TZ=America/Chicago, canned Aspen response]`);
    for (const [label, actual, expected, ok] of checks) {
        console.log(`  ${ok ? 'PASS' : 'FAIL'}  ${label}: got ${JSON.stringify(actual)}${ok ? '' : `, expected ${JSON.stringify(expected)}`}`);
    }
}
console.log(failures === 0 ? '\nALL CASES PASS' : `\n${failures} CHECK(S) FAILED`);
process.exit(failures === 0 ? 0 : 1);
