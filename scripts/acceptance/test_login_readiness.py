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


class LoginReadinessTest(unittest.TestCase):
    def test_retries_only_transient_gateway_login_unavailability_within_deadline(self):
        """A cold discovery route must be retried, while a permanent login error must surface."""
        runner = load_runner()
        unavailable = RuntimeError(
            "HTTP request failed: method=POST url=http://gateway/api/auth/login expected_status=(200,) "
            "actual_status=503 order_id=unknown last_observed_order_state=unknown"
        )
        with patch.object(runner, "login", side_effect=[unavailable, "token"]), \
                patch.object(runner.time, "monotonic", side_effect=[0, 0, 0, 0]), \
                patch.object(runner.time, "sleep") as sleep:
            self.assertEqual("token", runner.login_until_ready("http://gateway", "user", "runtime-only", 10))
        sleep.assert_called_once()
        with patch.object(runner, "login", side_effect=RuntimeError("actual_status=401")), \
                patch.object(runner.time, "monotonic", return_value=0):
            with self.assertRaisesRegex(RuntimeError, "401"):
                runner.login_until_ready("http://gateway", "user", "runtime-only", 10)


if __name__ == "__main__":
    unittest.main()
