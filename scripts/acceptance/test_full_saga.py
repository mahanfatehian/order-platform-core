import importlib.util
import pathlib
import unittest
from unittest.mock import patch


SCRIPT = pathlib.Path(__file__).with_name("full-saga.py")


def load_runner():
    spec = importlib.util.spec_from_file_location("full_saga", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class FullSagaContractTest(unittest.TestCase):
    def test_runner_drives_the_public_saga_without_duplicate_transitions(self):
        """A removed idempotency retry or lifecycle command must make this fail."""
        runner = load_runner()
        order_id = "11111111-1111-1111-1111-111111111111"
        product_id = "22222222-2222-2222-2222-222222222222"
        calls = []
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
                return {"content": [{"id": product_id, "active": True, "availableQuantity": 8}]}
            if "/api/store/admin/inventory/" in url:
                return {"availableQuantity": next(inventory_reads)}
            if url.endswith("/api/orders") and method == "POST":
                return {"id": order_id, "status": "PENDING"}
            if url.endswith("/api/orders/" + order_id) and method == "GET":
                return {"id": order_id, "status": "CONFIRMED"}
            if url.endswith("/pack"):
                return {"id": order_id, "status": "PACKAGED"}
            if url.endswith("/ship"):
                return {"id": order_id, "status": "SHIPPED"}
            if url.endswith("/deliver"):
                return {"id": order_id, "status": "DELIVERED"}
            if url.endswith("/history"):
                return history
            raise AssertionError("Unexpected request: " + method + " " + url)

        with patch.object(runner, "request_json", side_effect=fake_request), \
                patch.object(runner, "poll_order", side_effect=lambda *args: {"id": order_id, "status": args[3]}), \
                patch.object(runner, "poll_inventory", side_effect=lambda *args: {"availableQuantity": 7}):
            result = runner.run_saga("http://gateway.example", 30)

        self.assertEqual(order_id, result["order_id"])
        self.assertEqual(product_id, result["product_id"])
        create_calls = [call for call in calls if call[0] == "POST" and call[1].endswith("/api/orders")]
        self.assertEqual(2, len(create_calls))
        self.assertEqual(create_calls[0][4]["Idempotency-Key"], create_calls[1][4]["Idempotency-Key"])
        self.assertEqual({"items": [{"productId": product_id, "quantity": 1}]}, create_calls[0][3])
        self.assertIn(("POST", "http://gateway.example/api/orders/" + order_id + "/pack",
                       "token-johndoe", None, None, (403,)), calls)
        lifecycle_paths = [call[1] for call in calls if call[0] == "POST" and "/api/orders/" in call[1]]
        self.assertEqual(3, lifecycle_paths.count("http://gateway.example/api/orders/" + order_id + "/pack"))
        self.assertEqual(2, lifecycle_paths.count("http://gateway.example/api/orders/" + order_id + "/ship"))
        self.assertEqual(2, lifecycle_paths.count("http://gateway.example/api/orders/" + order_id + "/deliver"))


if __name__ == "__main__":
    unittest.main()
