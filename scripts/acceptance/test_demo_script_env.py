#!/usr/bin/env python3
"""Executable contract for demo lifecycle environment-file selection."""

import os
import json
import shutil
import stat
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
SCRIPT_NAMES = ("start-demo", "stop-demo", "reset-demo")
CASES = (
    ("local-dotenv", {"create_dotenv": True}, ".env"),
    ("explicit-override", {"create_dotenv": True, "override": "config/demo environment.env"},
     "config/demo environment.env"),
    ("example-fallback", {}, ".env.example"),
)
FAILURE_CASES = (
    ("missing-explicit-override", {"override": "config/missing.env", "create_override": False},
     "config/missing.env"),
    ("whitespace-explicit-override", {"override": "   "}, "   "),
    ("missing-example-fallback", {"create_example": False}, ".env.example"),
)


class DemoScriptEnvironmentContractTest(unittest.TestCase):
    def test_posix_scripts_honor_environment_file_precedence(self):
        shells = self._available_interpreters(
            "sh",
            str(Path(os.environ.get("ProgramFiles", r"C:\Program Files")) / "Git" / "bin" / "bash.exe"),
        )
        if not shells:
            self.skipTest("sh is not available")
        for shell in shells:
            with self.subTest(interpreter=shell):
                self._assert_interpreter_contract("posix", shell)
                self._assert_failure_contract("posix", shell)
                self._assert_reset_requires_confirmation("posix", shell)

    def test_powershell_scripts_honor_environment_file_precedence(self):
        shells = self._available_interpreters("pwsh", "powershell")
        if not shells:
            self.skipTest("PowerShell is not available")
        for shell in shells:
            with self.subTest(interpreter=shell):
                self._assert_interpreter_contract("powershell", shell)
                self._assert_failure_contract("powershell", shell)
                self._assert_reset_requires_confirmation("powershell", shell)

    def _assert_interpreter_contract(self, kind, interpreter):
        for case_name, setup, expected_env_file in CASES:
            for script_name in SCRIPT_NAMES:
                with self.subTest(interpreter=kind, case=case_name, script=script_name):
                    with tempfile.TemporaryDirectory() as directory:
                        root = Path(directory)
                        self._copy_demo_scripts(root)
                        command_log, environment = self._configure_environment(root, setup)
                        completed = self._run_script(kind, interpreter, root, script_name, environment, confirmed=True)

                        self.assertEqual(0, completed.returncode, completed.stderr)
                        self.assertEqual(self._expected_commands(script_name, expected_env_file),
                                         self._captured_commands(command_log))
                        self.assertIn("Using environment file: " + expected_env_file, completed.stdout)

    def _assert_failure_contract(self, kind, interpreter):
        for case_name, setup, expected_env_file in FAILURE_CASES:
            for script_name in SCRIPT_NAMES:
                with self.subTest(interpreter=kind, case=case_name, script=script_name):
                    with tempfile.TemporaryDirectory() as directory:
                        root = Path(directory)
                        self._copy_demo_scripts(root)
                        command_log, environment = self._configure_environment(root, setup)
                        completed = self._run_script(kind, interpreter, root, script_name, environment, confirmed=True)

                        self.assertNotEqual(0, completed.returncode)
                        self.assertIn("Environment file not found: " + expected_env_file,
                                      completed.stdout + completed.stderr)
                        self.assertEqual([], self._captured_commands(command_log))

    def _assert_reset_requires_confirmation(self, kind, interpreter):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self._copy_demo_scripts(root)
            command_log, environment = self._configure_environment(root, {})
            completed = self._run_script(kind, interpreter, root, "reset-demo", environment, confirmed=False)

            self.assertNotEqual(0, completed.returncode)
            self.assertEqual([], self._captured_commands(command_log))

    @staticmethod
    def _expected_commands(script_name, environment_file):
        prefix = ["compose", "--env-file", environment_file]
        if script_name == "start-demo":
            return [prefix + ["up", "-d", "--build", "--wait", "--wait-timeout", "360"], prefix + ["ps"]]
        if script_name == "stop-demo":
            return [prefix + ["down"]]
        return [prefix + ["down", "--volumes", "--remove-orphans"]]

    def _configure_environment(self, root, setup):
        if setup.get("create_example", True):
            (root / ".env.example").write_text("EXAMPLE=true\n", encoding="utf-8")
        if setup.get("create_dotenv"):
            (root / ".env").write_text("LOCAL=true\n", encoding="utf-8")
        if setup.get("create_override", True) and setup.get("override") and setup["override"].strip():
            override = root / setup["override"]
            override.parent.mkdir(parents=True, exist_ok=True)
            override.write_text("OVERRIDE=true\n", encoding="utf-8")

        command_log = root / "docker-commands.log"
        environment = os.environ.copy()
        environment["PATH"] = str(root / "bin") + os.pathsep + environment.get("PATH", "")
        environment["DEMO_DOCKER_LOG"] = str(command_log)
        environment["DEMO_PYTHON"] = sys.executable
        if "override" in setup:
            environment["ORDER_PLATFORM_ENV_FILE"] = setup["override"]
        else:
            environment.pop("ORDER_PLATFORM_ENV_FILE", None)
        return command_log, environment

    @staticmethod
    def _run_script(kind, interpreter, root, script_name, environment, confirmed):
        extension = ".sh" if kind == "posix" else ".ps1"
        command = [interpreter, str(root / "scripts" / (script_name + extension))]
        if script_name == "reset-demo" and confirmed:
            command.append("--yes" if kind == "posix" else "-Force")
        return subprocess.run(command, cwd=root, env=environment, text=True, capture_output=True, check=False)

    @staticmethod
    def _captured_commands(command_log):
        if not command_log.exists():
            return []
        return [json.loads(line) for line in command_log.read_text(encoding="utf-8").splitlines()]

    def _copy_demo_scripts(self, root):
        source, target = REPOSITORY_ROOT / "scripts", root / "scripts"
        target.mkdir()
        for helper in ("demo-env.sh", "DemoEnvironment.ps1"):
            helper_path = source / helper
            if helper_path.exists():
                shutil.copy2(helper_path, target / helper)
        for script_name in SCRIPT_NAMES:
            for extension in (".sh", ".ps1"):
                shutil.copy2(source / (script_name + extension), target / (script_name + extension))
        binary_directory = root / "bin"
        binary_directory.mkdir()
        capture_script = binary_directory / "docker-capture.py"
        capture_script.write_text(
            "import json, os, sys\n"
            "with open(os.environ['DEMO_DOCKER_LOG'], 'a', encoding='utf-8') as capture:\n"
            "    capture.write(json.dumps(sys.argv[1:]) + '\\n')\n",
            encoding="utf-8",
        )
        docker = binary_directory / "docker"
        docker.write_text(
            "#!/usr/bin/env sh\n\"$DEMO_PYTHON\" \"$(dirname \"$0\")/docker-capture.py\" \"$@\"\n",
            encoding="utf-8",
        )
        docker.chmod(docker.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)
        (binary_directory / "docker.cmd").write_text(
            "@echo off\r\n\"%DEMO_PYTHON%\" \"%~dp0docker-capture.py\" %*\r\n",
            encoding="utf-8",
        )

    @staticmethod
    def _available_interpreters(*candidates):
        interpreters = []
        for candidate in candidates:
            resolved = shutil.which(candidate) or (candidate if Path(candidate).is_file() else None)
            if resolved and resolved not in interpreters:
                interpreters.append(resolved)
        return interpreters


if __name__ == "__main__":
    unittest.main()
