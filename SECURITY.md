# Security policy

## Reporting a vulnerability

Please do not open a public issue for a suspected vulnerability. Use GitHub's private vulnerability reporting feature for this repository, including reproduction steps and affected paths. Do not include real credentials or customer data.

## Supported configuration

The committed `.env.example`, seeded users, and `dev` profile are for a local showcase only. They are not production credentials. A deployed environment must provide unique secrets, disable demo data, require HTTPS cookies, restrict infrastructure ports, and protect administrative documentation endpoints.

## Scope

Useful reports include authentication or authorization bypass, token leakage, CSRF, injection, unsafe event replay, cross-user order access, inventory integrity violations, and exposed infrastructure.
