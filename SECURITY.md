# Security policy

## Reporting a vulnerability

Please do not open a public issue for a suspected vulnerability. Use GitHub's private vulnerability reporting feature for this repository, including reproduction steps and affected paths. Do not include real credentials or customer data.

## Potential credential exposure

Treat a potentially exposed credential as live: rotate or revoke it first, then investigate its source and scope. Do not copy credential values into terminals, logs, issues, pull requests, chat, screenshots, or scanner reports. Use redacted scanner output and report only the metadata needed to remediate the exposure.

The `.gitleaksignore` file is a reviewed baseline of exact historical finding fingerprints. It neither removes Git history nor makes an old credential safe; any credential associated with a baseline finding still requires rotation or revocation and an owner review.

## Supported configuration

The committed `.env.example`, seeded users, and `dev` profile are for a local showcase only. They are not production credentials. A deployed environment must provide unique secrets, disable demo data, require HTTPS cookies, restrict infrastructure ports, and protect administrative documentation endpoints.

## Scope

Useful reports include authentication or authorization bypass, token leakage, CSRF, injection, unsafe event replay, cross-user order access, inventory integrity violations, and exposed infrastructure.
