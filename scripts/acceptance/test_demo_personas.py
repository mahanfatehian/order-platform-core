import importlib.util
import pathlib
import unittest


SCRIPT = pathlib.Path(__file__).with_name("full-saga.py")


def load_runner():
    spec = importlib.util.spec_from_file_location("full_saga", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class DemoPersonaParserTest(unittest.TestCase):
    def test_parser_accepts_current_and_future_markup_without_leaking_secrets(self):
        """Missing or duplicated demo credentials must fail without exposing them."""
        runner = load_runner()
        current_markup = """
        <span><strong>Customer:</strong> <code>customer-user</code> / <code>secret-customer</code></span>
        <span><strong>Warehouse:</strong> <code>warehouse-user</code> / <code>secret-warehouse</code></span>
        <span><strong>Delivery:</strong> <code>delivery-user</code> / <code>secret-delivery</code></span>
        <span><strong>Administrator:</strong> <code>admin-user</code> / <code>secret-admin</code></span>
        """
        future_markup = """
        <button data-demo-persona=\"customer\" data-demo-username=\"customer-user\" data-demo-password=\"secret-customer\"></button>
        <button data-demo-persona=\"warehouse\" data-demo-username=\"warehouse-user\" data-demo-password=\"secret-warehouse\"></button>
        <button data-demo-persona=\"delivery\" data-demo-username=\"delivery-user\" data-demo-password=\"secret-delivery\"></button>
        <button data-demo-persona=\"admin\" data-demo-username=\"admin-user\" data-demo-password=\"secret-admin\"></button>
        """
        self.assertEqual(("customer-user", "secret-customer"), runner.parse_demo_personas(current_markup)["customer"])
        self.assertEqual(("admin-user", "secret-admin"), runner.parse_demo_personas(future_markup)["admin"])
        with self.assertRaisesRegex(RuntimeError, "missing persona=admin") as missing:
            runner.parse_demo_personas(current_markup.replace("Administrator:", "Operator:", 1))
        self.assertNotIn("secret-admin", str(missing.exception))
        with self.assertRaisesRegex(RuntimeError, "duplicate persona=customer") as duplicate:
            runner.parse_demo_personas(current_markup + current_markup.split("</span>", 1)[0] + "</span>")
        self.assertNotIn("secret-customer", str(duplicate.exception))


if __name__ == "__main__":
    unittest.main()
