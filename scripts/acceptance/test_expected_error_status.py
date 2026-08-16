import importlib.util
import io
import pathlib
import unittest
import urllib.error
from unittest.mock import patch


SCRIPT = pathlib.Path(__file__).with_name("full-saga.py")


def load_runner():
    spec = importlib.util.spec_from_file_location("full_saga", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class ExpectedErrorStatusTest(unittest.TestCase):
    def test_expected_forbidden_http_error_is_a_valid_negative_authorization_result(self):
        """A deliberate forbidden assertion must not be mistaken for a transport failure."""
        runner = load_runner()
        response = urllib.error.HTTPError(
            "http://gateway/api/orders/order-id/pack", 403, "Forbidden", {}, io.BytesIO(b'{"status":403}')
        )
        with patch.object(runner.urllib.request, "urlopen", side_effect=response):
            self.assertEqual({"status": 403}, runner.request_json(
                "POST", "http://gateway/api/orders/order-id/pack", expected=(403,)
            ))


if __name__ == "__main__":
    unittest.main()
