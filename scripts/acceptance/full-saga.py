#!/usr/bin/env python3
"""Bounded public-edge proof for the Order/flow customer saga."""

import argparse
import json
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from html.parser import HTMLParser


PERSONAS = ("customer", "warehouse", "delivery", "admin")
PERSONA_LABELS = {"customer": "customer", "warehouse": "warehouse", "delivery": "delivery", "administrator": "admin", "admin": "admin"}
PERSONA_USERNAMES = {"customer": "johndoe", "warehouse": "warehouse_worker", "delivery": "delivery_driver", "admin": "admin"}
POLL_INTERVAL_SECONDS = 0.5
HTTP_TIMEOUT_SECONDS = 10
EXPECTED_HISTORY = ("PENDING", "CONFIRMED", "PACKAGED", "SHIPPED", "DELIVERED")


def sanitized_url(url):
    parts = urllib.parse.urlsplit(url)
    host = parts.hostname or ""
    if parts.port:
        host += ":" + str(parts.port)
    return urllib.parse.urlunsplit((parts.scheme, host, parts.path, "", ""))


def request_error(method, url, expected, actual, order_id="unknown", last_state="unknown"):
    return RuntimeError(
        "HTTP request failed: method={method} url={url} expected_status={expected_status} actual_status={actual_status} "
        "order_id={order_id} last_observed_order_state={last_state}".format(
            method=method, url=sanitized_url(url), expected_status=expected, actual_status=actual,
            order_id=order_id, last_state=last_state,
        )
    )


def order_id_from_url(url):
    parts = urllib.parse.urlsplit(url).path.split("/")
    try:
        return parts[parts.index("orders") + 1]
    except (ValueError, IndexError):
        return "unknown"


def request_json(method, url, *, token=None, body=None, headers=None, expected=(200,)) -> dict:
    """Make one bounded JSON request without exposing credentials in failures."""
    request_headers = {"Accept": "application/json"}
    if headers:
        request_headers.update(headers)
    if token:
        request_headers["Authorization"] = "Bearer " + token
    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        request_headers["Content-Type"] = "application/json"
    request = urllib.request.Request(url, data=data, headers=request_headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=HTTP_TIMEOUT_SECONDS) as response:
            status, payload = response.getcode(), response.read()
    except urllib.error.HTTPError as error:
        status, payload = error.code, error.read()
        error.close()
    except urllib.error.URLError:
        raise request_error(method, url, expected, "NETWORK_ERROR", order_id_from_url(url)) from None
    if status not in expected:
        raise request_error(method, url, expected, status, order_id_from_url(url))
    if not payload:
        return {}
    try:
        decoded = json.loads(payload.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        raise request_error(method, url, expected, status, order_id_from_url(url)) from None
    if not isinstance(decoded, (dict, list)):
        raise request_error(method, url, expected, status, order_id_from_url(url))
    return decoded


def request_text(method, url, *, expected=(200,)):
    request = urllib.request.Request(url, headers={"Accept": "text/html"}, method=method)
    try:
        with urllib.request.urlopen(request, timeout=HTTP_TIMEOUT_SECONDS) as response:
            status, payload = response.getcode(), response.read()
    except urllib.error.HTTPError as error:
        raise request_error(method, url, expected, error.code) from None
    except urllib.error.URLError:
        raise request_error(method, url, expected, "NETWORK_ERROR") from None
    if status not in expected:
        raise request_error(method, url, expected, status)
    try:
        return payload.decode("utf-8")
    except UnicodeDecodeError:
        raise request_error(method, url, expected, status) from None


class DemoPersonaParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.records = {persona: [] for persona in PERSONAS}
        self.current_persona = None
        self.current_record = None
        self.capture_kind = None
        self.capture_text = []

    def handle_starttag(self, tag, attrs):
        attributes = dict(attrs)
        if "data-demo-persona" in attributes:
            persona = PERSONA_LABELS.get(attributes.get("data-demo-persona", "").strip().lower())
            username, password = attributes.get("data-demo-username"), attributes.get("data-demo-password")
            if not persona or not username or not password:
                raise RuntimeError("Demo credential markup contains an ambiguous persona control")
            self.records[persona].append((username, password))
        if tag == "strong":
            self.capture_kind, self.capture_text = "label", []
        elif tag == "code":
            self.capture_kind, self.capture_text = "code", []

    def handle_data(self, data):
        if self.capture_kind:
            self.capture_text.append(data)

    def handle_endtag(self, tag):
        if tag == "strong" and self.capture_kind == "label":
            label = "".join(self.capture_text).strip().rstrip(":").lower()
            self.current_persona = PERSONA_LABELS.get(label)
            self.current_record = None
            self.capture_kind, self.capture_text = None, []
        elif tag == "code" and self.capture_kind == "code":
            value = "".join(self.capture_text).strip()
            if self.current_persona and value:
                if self.current_record is None:
                    self.current_record = []
                    self.records[self.current_persona].append(self.current_record)
                self.current_record.append(value)
            self.capture_kind, self.capture_text = None, []


def parse_demo_personas(markup):
    parser = DemoPersonaParser()
    parser.feed(markup)
    result = {}
    for persona in PERSONAS:
        records = parser.records[persona]
        normalized = []
        for record in records:
            if isinstance(record, list):
                if len(record) != 2:
                    raise RuntimeError("Demo credential markup contains an ambiguous persona=" + persona)
                normalized.append((record[0], record[1]))
            else:
                normalized.append(record)
        if not normalized:
            raise RuntimeError("Demo credential markup is missing persona=" + persona)
        if len(normalized) != 1:
            raise RuntimeError("Demo credential markup contains duplicate persona=" + persona)
        result[persona] = normalized[0]
    return result


def discover_demo_personas(base_url):
    return parse_demo_personas(request_text("GET", base_url.rstrip("/") + "/login"))


def login(base_url: str, username: str, password: str) -> str:
    response = request_json("POST", base_url.rstrip("/") + "/api/auth/login",
                            body={"username": username, "password": password})
    token = response.get("accessToken")
    if not isinstance(token, str) or not token:
        raise RuntimeError("Login response did not include an access token for username=" + username)
    return token


def login_until_ready(base_url, username, password, deadline):
    last_error = None
    while time.monotonic() < deadline:
        try:
            return login(base_url, username, password)
        except RuntimeError as error:
            last_error = error
            if "actual_status=503" not in str(error):
                raise
            time.sleep(min(POLL_INTERVAL_SECONDS, max(0, deadline - time.monotonic())))
    if last_error:
        raise last_error
    raise RuntimeError("Gateway login readiness deadline elapsed without an attempt")


def poll_order(base_url: str, token: str, order_id: str, expected: str, deadline: float) -> dict:
    url, last_state = base_url.rstrip("/") + "/api/orders/" + order_id, "unknown"
    while time.monotonic() < deadline:
        response = request_json("GET", url, token=token)
        last_state = response.get("status", "missing")
        if last_state == expected:
            return response
        time.sleep(min(POLL_INTERVAL_SECONDS, max(0, deadline - time.monotonic())))
    raise request_error("GET", url, (200,), 200, order_id, last_state)


def poll_inventory(base_url, token, product_id, expected_available, deadline):
    url, last_available = base_url.rstrip("/") + "/api/store/admin/inventory/" + product_id, "unknown"
    while time.monotonic() < deadline:
        response = request_json("GET", url, token=token)
        last_available = response.get("availableQuantity", "missing")
        if last_available == expected_available:
            return response
        time.sleep(min(POLL_INTERVAL_SECONDS, max(0, deadline - time.monotonic())))
    raise RuntimeError("Inventory did not settle: method=GET url={url} expected_status=200 actual_status=200 "
                       "order_id=unknown last_observed_order_state=availableQuantity:{actual}".format(
                           url=sanitized_url(url), actual=last_available))


def require_status(response, expected, action, order_id):
    actual = response.get("status", "missing")
    if actual != expected:
        raise RuntimeError("Saga command returned the wrong state: method=POST url={action} expected_status=200 "
                           "actual_status=200 order_id={order_id} last_observed_order_state={actual}".format(
                               action=action, order_id=order_id, actual=actual))


def assert_history(history, order_id):
    if not isinstance(history, list):
        raise RuntimeError("Order history was not a list for order_id=" + order_id)
    states, cursor = [entry.get("toStatus") for entry in history], 0
    for state in states:
        if cursor < len(EXPECTED_HISTORY) and state == EXPECTED_HISTORY[cursor]:
            cursor += 1
    if cursor != len(EXPECTED_HISTORY):
        raise RuntimeError("Order history missed the Saga sequence: method=GET url=/api/orders/{order_id}/history "
                           "expected_status=200 actual_status=200 order_id={order_id} last_observed_order_state={state}".format(
                               order_id=order_id, state=states[-1] if states else "missing"))
    for entry in history:
        if not entry.get("eventId") or not entry.get("correlationId"):
            raise RuntimeError("Order history omitted technical evidence: method=GET url=/api/orders/{order_id}/history "
                               "expected_status=200 actual_status=200 order_id={order_id} last_observed_order_state={state}".format(
                                   order_id=order_id, state=entry.get("toStatus", "missing")))
    return states


def run_saga(base_url, timeout_seconds, personas=None):
    base_url, deadline = base_url.rstrip("/"), time.monotonic() + timeout_seconds
    personas = personas or {persona: (PERSONA_USERNAMES[persona], None) for persona in PERSONAS}
    customer_token = login_until_ready(base_url, *personas["customer"], deadline)
    warehouse_token = login_until_ready(base_url, *personas["warehouse"], deadline)
    delivery_token = login_until_ready(base_url, *personas["delivery"], deadline)
    admin_token = login_until_ready(base_url, *personas["admin"], deadline)
    catalog = request_json("GET", base_url + "/api/store/products?inStock=true&size=100", token=customer_token)
    product = next((item for item in catalog.get("content", [])
                    if item.get("active") and item.get("availableQuantity", 0) > 0), None)
    if not product or not product.get("id"):
        raise RuntimeError("No active in-stock catalog product was available for the Saga acceptance test")
    product_id = product["id"]
    starting = request_json("GET", base_url + "/api/store/admin/inventory/" + product_id, token=admin_token)
    starting_available = starting.get("availableQuantity")
    if not isinstance(starting_available, int) or starting_available < 1:
        raise RuntimeError("Selected product has no usable starting availability for product_id=" + product_id)
    correlation_id, idempotency_key = str(uuid.uuid4()), str(uuid.uuid4())
    body = {"items": [{"productId": product_id, "quantity": 1}]}
    headers = {"Idempotency-Key": idempotency_key, "X-Correlation-Id": correlation_id}
    created = request_json("POST", base_url + "/api/orders", token=customer_token, body=body, headers=headers, expected=(201,))
    order_id = created.get("id")
    if not order_id:
        raise RuntimeError("Order creation did not return an order ID")
    replayed = request_json("POST", base_url + "/api/orders", token=customer_token, body=body, headers=headers, expected=(201,))
    if replayed.get("id") != order_id:
        raise RuntimeError("Idempotent order creation returned a different order ID")
    poll_order(base_url, customer_token, order_id, "CONFIRMED", deadline)
    request_json("POST", base_url + "/api/orders/" + order_id + "/pack", token=customer_token, expected=(403,))
    packed = request_json("POST", base_url + "/api/orders/" + order_id + "/pack", token=warehouse_token,
                          headers={"X-Correlation-Id": correlation_id})
    require_status(packed, "PACKAGED", "/pack", order_id)
    require_status(request_json("POST", base_url + "/api/orders/" + order_id + "/pack", token=warehouse_token,
                                headers={"X-Correlation-Id": correlation_id}), "PACKAGED", "/pack", order_id)
    tracking = "saga-" + str(uuid.uuid4())
    shipped = request_json("POST", base_url + "/api/orders/" + order_id + "/ship", token=delivery_token,
                           body={"trackingReference": tracking}, headers={"X-Correlation-Id": correlation_id})
    require_status(shipped, "SHIPPED", "/ship", order_id)
    require_status(request_json("POST", base_url + "/api/orders/" + order_id + "/ship", token=delivery_token,
                                body={"trackingReference": tracking}, headers={"X-Correlation-Id": correlation_id}),
                   "SHIPPED", "/ship", order_id)
    delivered = request_json("POST", base_url + "/api/orders/" + order_id + "/deliver", token=delivery_token,
                             headers={"X-Correlation-Id": correlation_id})
    require_status(delivered, "DELIVERED", "/deliver", order_id)
    require_status(request_json("POST", base_url + "/api/orders/" + order_id + "/deliver", token=delivery_token,
                                headers={"X-Correlation-Id": correlation_id}), "DELIVERED", "/deliver", order_id)
    final_inventory = poll_inventory(base_url, admin_token, product_id, starting_available - 1, deadline)
    if final_inventory.get("availableQuantity") != starting_available - 1:
        raise RuntimeError("Final availability was not exactly one lower for product_id=" + product_id)
    history_states = assert_history(request_json("GET", base_url + "/api/orders/" + order_id + "/history",
                                                 token=customer_token), order_id)
    return {"order_id": order_id, "product_id": product_id, "history_states": history_states}


def main():
    parser = argparse.ArgumentParser(description="Run the bounded public Order/flow Saga acceptance test")
    parser.add_argument("--base-url", required=True, help="Gateway base URL, for example http://localhost:8080")
    parser.add_argument("--timeout-seconds", type=int, default=90, help="Total bounded runtime (1-300 seconds)")
    args = parser.parse_args()
    if args.timeout_seconds < 1 or args.timeout_seconds > 300:
        parser.error("--timeout-seconds must be between 1 and 300")
    try:
        result = run_saga(args.base_url, args.timeout_seconds, discover_demo_personas(args.base_url))
    except RuntimeError as error:
        print("Saga acceptance failed: " + str(error))
        raise SystemExit(1)
    print("Saga acceptance passed: order_id={order_id} product_id={product_id} history={history}".format(
        order_id=result["order_id"], product_id=result["product_id"], history=",".join(result["history_states"])))


if __name__ == "__main__":
    main()
