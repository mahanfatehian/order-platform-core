import importlib.util
import pathlib
import unittest


SCRIPT = pathlib.Path(__file__).with_name("full-saga.py")


def load_runner():
    spec = importlib.util.spec_from_file_location("full_saga", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class RequestDiagnosticsTest(unittest.TestCase):
    def test_http_error_reports_required_context_without_query_secrets(self):
        """A failed request must keep useful diagnostics while redacting its URL query."""
        error = load_runner().request_error(
            "POST", "http://gateway.example/api/auth/login?password=never-print", (200,), 503
        )
        message = str(error)
        self.assertIn("method=POST", message)
        self.assertIn("url=http://gateway.example/api/auth/login", message)
        self.assertIn("expected_status=(200,)", message)
        self.assertIn("actual_status=503", message)
        self.assertIn("order_id=unknown", message)
        self.assertIn("last_observed_order_state=unknown", message)
        self.assertNotIn("never-print", message)


if __name__ == "__main__":
    unittest.main()
