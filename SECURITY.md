# Security Policy

CallReel is a fork of ShizuCallRecorder, available at https://github.com/kitsumed/ShizuCallRecorder. Most of its code is shared with the original project.

## Supported versions

Security updates are provided only for the latest release of CallReel.

Releases are built by GitHub Actions and the APK is attested, so that you can trace it back to the workflow run. See the [GitHub CLI documentation](https://cli.github.com/manual/gh_attestation_verify) on how to verify an attestation.

## Reporting a vulnerability

Please report a vulnerability **privately**, through [GitHub private vulnerability reporting](https://github.com/fluxism/CallReel/security/advisories/new), and practice responsible disclosure.

- If the vulnerability is in **video recording** or in something else that only CallReel has, report it here.
- If it is in code **shared with the original project** (audio recording, call detection, Shizuku handling), it most likely affects ShizuCallRecorder too. Please report it to the [original project](https://github.com/kitsumed/ShizuCallRecorder/security/policy) as well, so that both can be fixed.

A useful report includes step-by-step reproduction instructions, the device and Android version, logs, and a proof of concept if you have one.

## Before reporting

Confirm that the issue reproduces on the latest release of CallReel.

## Responsible disclosure

- Do not disclose details publicly right away.
- Wait at least 2 weeks after a release containing the fix before sharing technical details that could lead to exploitation.

## What not to report

- Issues that only affect older versions
- Vulnerabilities that require modifying CallReel itself to be exploitable
- Reports without a reasonable security impact
- Issues in Shizuku, which should be reported to Shizuku
