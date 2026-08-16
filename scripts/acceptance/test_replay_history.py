import importlib.util
import inspect
import pathlib
import unittest
from unittest.mock import patch


SCRIPT = pathlib.Path(__file__).with_name("full-saga.py")


def load_runner():
    spec = importlib.util.spec_from_file_location("full_saga", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class ReplayHistoryContractTest(unittest.TestCase):
    def test_runner_rejects_a_replay_that_adds_a_second_history_identity(self):
        """A replay that returns 200 but writes another PACKAGED event must fail the proof."""
        runner = load_runner()
        order_id = "11111111-1111-1111-1111-111111111111"
        product_id = "22222222-2222-2222-2222-222222222222"
        history = [
            {"toStatus": "PENDING", "eventId": "pending", "correlationId": "c"},
            {"toStatus": "CONFIRMED", "eventId": "confirmed", "correlationId": "c"},
        ]
        pack_count = ship_count = delivery_count = replay_history_reads = 0
        replay_started = False

        def fake_request(method, url, *, token=None, body=None, headers=None, expected=(200,)):
            nonlocal pack_count, ship_count, delivery_count, replay_history_reads, replay_started
            if url.endswith("/api/auth/login"):
                return {"accessToken": "token-" + body["username"]}
            if "/api/store/products?" in url:
                return {"content": [{"id": product_id, "active": True, "availableQuantity": 8}]}
            if "/api/store/admin/inventory/" in url:
                return {"availableQuantity": 8}
            if url.endswith("/api/orders") and method == "POST":
                return {"id": order_id, "status": "PENDING"}
            if url.endswith("/api/orders/" + order_id) and method == "GET":
                return {"id": order_id, "status": "CONFIRMED"}
            if url.endswith("/history"):
                if replay_started:
                    replay_history_reads += 1
                    if replay_history_reads == 3:
                        history.append({"toStatus": "PACKAGED", "eventId": "pack-2", "correlationId": "c"})
                return list(history)
            if url.endswith("/pack"):
                if token == "token-customer-user":
                    return {"status": 403}
                pack_count += 1
                if pack_count == 1:
                    history.append({"toStatus": "PACKAGED", "eventId": "pack-1", "correlationId": "c"})
                else:
                    replay_started = True
                return {"id": order_id, "status": "PACKAGED"}
            if url.endswith("/ship"):
                ship_count += 1
                if ship_count == 1:
                    history.append({"toStatus": "SHIPPED", "eventId": "ship-1", "correlationId": "c"})
                return {"id": order_id, "status": "SHIPPED"}
            if url.endswith("/deliver"):
                delivery_count += 1
                if delivery_count == 1:
                    history.append({"toStatus": "DELIVERED", "eventId": "deliver-1", "correlationId": "c"})
                return {"id": order_id, "status": "DELIVERED"}
            raise AssertionError("Unexpected request: " + method + " " + url)

        personas = {
            "customer": ("customer-user", "runtime-only"),
            "warehouse": ("warehouse-user", "runtime-only"),
            "delivery": ("delivery-user", "runtime-only"),
            "admin": ("admin-user", "runtime-only"),
        }
        with patch.object(runner, "request_json", side_effect=fake_request), \
                patch.object(runner, "poll_order", return_value={"id": order_id, "status": "CONFIRMED"}), \
                patch.object(runner, "poll_inventory", return_value={"availableQuantity": 7}):
            with self.assertRaisesRegex(RuntimeError, "Replay added history"):
                runner.run_saga("http://gateway.example", 30, personas)

    def test_run_saga_requires_runtime_discovered_personas(self):
        """Removing the runtime persona mapping must be an API error, never a fallback login."""
        runner = load_runner()
        self.assertIs(inspect.Parameter.empty, inspect.signature(runner.run_saga).parameters["personas"].default)

    def test_run_saga_rejects_any_persona_set_other_than_the_four_demo_roles(self):
        runner = load_runner()
        with self.assertRaisesRegex(RuntimeError, "exactly the four expected roles"):
            runner.run_saga("http://gateway.example", 30, {"customer": ("u", "p")})

    def test_login_diagnostic_does_not_echo_discovered_persona_values(self):
        runner = load_runner()
        with patch.object(runner, "request_json", return_value={"accessToken": ""}):
            with self.assertRaises(RuntimeError) as raised:
                runner.login("http://gateway.example", "captured-user", "captured-password")
        self.assertNotIn("captured-user", str(raised.exception))
        self.assertNotIn("captured-password", str(raised.exception))


if __name__ == "__main__":
    unittest.main()
