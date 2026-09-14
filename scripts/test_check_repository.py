import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("checker", Path(__file__).with_name("check_repository.py"))
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)


class RepositoryCheckTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        subprocess.run(["git", "init", "-q", str(self.root)], check=True)

    def write(self, name, body):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(body if isinstance(body, bytes) else body.encode())

    def test_rejects_reintroduced_data_and_disguised_executable(self):
        for name in ("data/jobs.db", "app/old.apk", "old.tgz"):
            self.write(name, b"fixture")
        self.write("server", b"\x7fELF" + b"\0" * 30)
        errors = checker.check(self.root)
        for name in ("data/jobs.db", "app/old.apk", "old.tgz", "server"):
            self.assertTrue(any(name in e for e in errors), errors)

    def test_links_include_images_references_and_ignore_examples(self):
        self.write("README.md", "[guide](docs/guide.md)\n![bad](missing.png)\n[ref]: missing.md\n```md\n[x](example-only.md)\n```\n")
        self.write("docs/guide.md", "[root](../README.md)\n[web](https://example.com/page)\n")
        errors = checker.check(self.root)
        self.assertEqual(2, len(errors), errors)
        self.assertTrue(any("missing.png" in e for e in errors))
        self.assertTrue(any("missing.md" in e for e in errors))

    def test_ignored_runtime_files_are_not_valid_documentation_targets(self):
        self.write(".gitignore", "data/\n")
        self.write("data/private.json", "{}")
        self.write("README.md", "[runtime](data/private.json)")
        self.assertEqual(1, len(checker.check(self.root)))

    def test_migration_schemas_fonts_and_gradle_wrapper_are_source(self):
        self.write("app/schemas/OldDatabase/1.json", "{}")
        self.write("app/src/main/res/font/example.ttf", b"\x00\x01\x00\x00font")
        self.write("gradle/wrapper/gradle-wrapper.jar", b"PK\x03\x04fixture")
        self.assertEqual([], checker.check(self.root))


if __name__ == "__main__":
    unittest.main()
