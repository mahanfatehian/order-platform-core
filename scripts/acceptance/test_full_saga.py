import importlib.util
import pathlib
import unittest
from unittest.mock import patch


SCRIPT = pathlib.Path(__file__).with_name("full-saga.py")
ORDER_ID = "11111111-1111-1111-1111-111111111111"
PRODUCT_ID = "22222222-2222-2222-2222-222222222222"
PERSONAS = {
    "customer": ("johndoe", "test-only"),
    "warehouse": ("warehouse_worker", "test-only"),
    "delivery": ("delivery_driver", "test-only"),
    "admin": ("admin", "test-only"),
}


def load_runner():
    spec = importlib.util.spec_from_file_location("full_saga", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def fake_gateway(calls):
    """A gateway that answers every request the saga makes, recording each one in calls."""
    history = [
        {"toStatus": "PENDING", "eventId": "a", "correlationId": "c"},
        {"toStatus": "CONFIRMED", "eventId": "b", "correlationId": "c"},
        {"toStatus": "PACKAGED", "eventId": "d", "correlationId": "c"},
        {"toStatus": "SHIPPED", "eventId": "e", "correlationId": "c"},
        {"toStatus": "DELIVERED", "eventId": "f", "correlationId": "c"},
    ]
    inventory_reads = iter((8, 7))

    def fake_request(method, url, *, token=None, body=None, headers=None, expected=(200,)):
        calls.append((method, url, token, body, headers, expected))
        if url.endswith("/api/auth/login"):
            return {"accessToken": "token-" + body["username"]}
        if "/api/store/products?" in url:
            return {"content": [{"id": PRODUCT_ID, "active": True, "availableQuantity": 8}]}
        if "/api/store/admin/inventory/" in url:
            return {"availableQuantity": next(inventory_reads)}
        if url.endswith("/api/orders") and method == "POST":
            return {"id": ORDER_ID, "status": "PENDING"}
        if url.endswith("/api/orders/" + ORDER_ID) and method == "GET":
            return {"id": ORDER_ID, "status": "CONFIRMED"}
        if url.endswith("/pack"):
            return {"id": ORDER_ID, "status": "PACKAGED"}
        if url.endswith("/ship"):
            return {"id": ORDER_ID, "status": "SHIPPED"}
        if url.endswith("/deliver"):
            return {"id": ORDER_ID, "status": "DELIVERED"}
        if url.endswith("/history"):
            return history
        raise AssertionError("Unexpected request: " + method + " " + url)

    return fake_request


class FullSagaContractTest(unittest.TestCase):
    def test_runner_drives_the_public_saga_without_duplicate_transitions(self):
        """A removed idempotency retry or lifecycle command must make this fail."""
        runner = load_runner()
        calls = []

        with patch.object(runner, "request_json", side_effect=fake_gateway(calls)), \
                patch.object(runner, "poll_order", side_effect=lambda *args: {"id": ORDER_ID, "status": args[3]}), \
                patch.object(runner, "poll_inventory", side_effect=lambda *args: {"availableQuantity": 7}):
            result = runner.run_saga("http://gateway.example", 30, PERSONAS)

        self.assertEqual(ORDER_ID, result["order_id"])
        self.assertEqual(PRODUCT_ID, result["product_id"])
        create_calls = [call for call in calls if call[0] == "POST" and call[1].endswith("/api/orders")]
        self.assertEqual(2, len(create_calls))
        self.assertEqual(create_calls[0][4]["Idempotency-Key"], create_calls[1][4]["Idempotency-Key"])
        self.assertEqual({"items": [{"productId": PRODUCT_ID, "quantity": 1}]}, create_calls[0][3])
        self.assertIn(("POST", "http://gateway.example/api/orders/" + ORDER_ID + "/pack",
                       "token-johndoe", None, None, (403,)), calls)
        lifecycle_paths = [call[1] for call in calls if call[0] == "POST" and "/api/orders/" in call[1]]
        self.assertEqual(3, lifecycle_paths.count("http://gateway.example/api/orders/" + ORDER_ID + "/pack"))
        self.assertEqual(2, lifecycle_paths.count("http://gateway.example/api/orders/" + ORDER_ID + "/ship"))
        self.assertEqual(2, lifecycle_paths.count("http://gateway.example/api/orders/" + ORDER_ID + "/deliver"))

    def test_runner_waits_for_backend_routes_the_gateway_has_not_discovered_yet(self):
        """Compose reports the gateway healthy before its Eureka cache lists every backend."""
        runner = load_runner()
        calls = []
        gateway = fake_gateway(calls)
        cold_routes = {"/api/store/", "/api/orders"}

        def cold_start(method, url, **kwargs):
            route = next((prefix for prefix in cold_routes if prefix in url), None)
            if route:
                cold_routes.discard(route)
                raise runner.request_error(method, url, kwargs.get("expected", (200,)), 503)
            return gateway(method, url, **kwargs)

        with patch.object(runner, "request_json", side_effect=cold_start), \
                patch.object(runner, "poll_order", side_effect=lambda *args: {"id": ORDER_ID, "status": args[3]}), \
                patch.object(runner, "poll_inventory", side_effect=lambda *args: {"availableQuantity": 7}):
            result = runner.run_saga("http://gateway.example", 30, PERSONAS)

        self.assertEqual(ORDER_ID, result["order_id"])
        self.assertEqual(set(), cold_routes, "both cold routes must have been met and retried")


if __name__ == "__main__":
    unittest.main()
