# ADR-0002: Escalate to a captcha on failed sign-ins and meter the anonymous endpoints

- **Status:** Accepted
- **Date:** 2026-08-19
- **Decision owners:** Order platform maintainers

## Context

The sign-in and registration forms in `web-ui-service` were reachable by anonymous callers with nothing to make a wrong guess cost more than a right one. Credentials could be guessed as fast as the backend would answer, and accounts could be created in a loop.

Raw volume was not the missing piece. `gateway-service` already meters `POST /login` and `POST /register` per address and the rest of the UI per session, and it is the only published application port, so every browser request already passes a ceiling. What no layer did was react to *evidence*. An edge limiter sees requests, not outcomes: it cannot tell a failed credential attempt from a successful one, so it can only make guessing expensive by taxing everyone who signs in correctly at the same rate.

Two different problems hide behind the phrase "too many sign-in attempts", and conflating them produces a control that is wrong in both directions:

- **Credential guessing.** Someone is testing passwords. The evidence is a *failed* authentication, and the appropriate answer is to make each further guess expensive for a human-operated client and impractical for a script.
- **Request volume.** Someone is hammering an endpoint. The evidence is the *request itself*, whatever the outcome, and the appropriate answer is to refuse service for a while.

A control that reacts to volume alone punishes a legitimate user who mistypes a password twice. A control that reacts to failures alone leaves the endpoint wide open whenever the backend is degraded and never returns a failure to count.

## Decision

### 1. A captcha is demanded only after repeated credential failures

`LoginAttemptService` counts failed sign-ins against two keys: the calling address and the targeted account. Crossing the threshold on either one demands a captcha on the next submission. Both keys are needed:

| Attack shape | Caught by |
| --- | --- |
| One host guessing against many accounts | address counter |
| Many hosts guessing against one account | account counter |

A successful sign-in clears both counters. Only a genuine `401` counts; a `503` from a degraded `auth-service` never does, because an outage is not evidence about the caller and must not push an innocent user towards a challenge.

Registration counts every submission rather than only failures. A script creating accounts succeeds every time, so failure-only accounting would never notice it.

### 2. The challenge is rendered in process

The challenge is drawn with Java2D and held in the Redis-backed session; the expected answer is never sent to the browser. There is no third-party captcha vendor, no API key, and no outbound network call, so a checkout of this repository behaves identically offline and in CI.

A challenge is consumed on the first verification attempt whether or not the answer was correct. A solved captcha therefore cannot be replayed, and a wrong guess always costs the caller a fresh image.

### 3. Request volume is metered separately

`AuthRateLimitInterceptor` caps how often one address may submit the sign-in and registration forms, and how many challenge images it may request. This counts every request regardless of outcome, which is exactly what the captcha thresholds deliberately do not do. The two controls are complementary: the rate limiter bounds volume, the captcha reacts to evidence of guessing.

This duplicates the edge ceiling rather than replacing it, and that is the point. The gateway limit protects the platform; this one lives beside the counters that decide when a challenge appears, so the two stay tuned together and the service is still bounded if it is ever reached by anything other than the gateway.

Rendering a page is never metered. Charging a `GET` would lock people out of the very page explaining why they were blocked.

### 4. Counters degrade rather than switch off

Counters live in Redis so every instance observes the same tally. When Redis cannot be reached, `AttemptCounterStore` falls back to a bounded in-process map instead of reporting zero.

This is a deliberate trade-off. A client spread across N instances gets N times the allowance while Redis is down, which is a far smaller hole than counting nothing at all. Reads take the larger of the shared and local tallies, so attempts recorded during an outage still hold once Redis returns rather than resetting an attacker to zero. The fallback map is capacity-bounded and refuses unfamiliar keys once full, so a flood of distinct usernames cannot turn the mitigation into a memory exhaustion vector.

## Consequences

- A legitimate user who mistypes a password a few times sees a captcha. This is the intended cost, and thresholds are configurable per environment.
- The controls are visible in a local demo without any external account or key.
- The image challenge is not accessible to users relying on a screen reader, and no audio alternative is offered. This is a known and accepted limitation of the current implementation, recorded here so it is not mistaken for an oversight.
- Losing Redis weakens, but does not remove, both controls.
