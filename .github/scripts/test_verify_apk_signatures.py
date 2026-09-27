"""Regression coverage for the release signing gate."""

import unittest
from verify_apk_signatures import verify_output


class SignatureGateTest(unittest.TestCase):
    """The gate must reject random keys, unsigned files and unexpected extra signers."""

    def test_matching_certificate(self):
        verify_output("Signer #1 certificate SHA-256 digest: " + "ab" * 32, "AB:" * 31 + "AB")

    def test_different_certificate(self):
        with self.assertRaises(ValueError):
            verify_output("Signer #1 certificate SHA-256 digest: " + "cd" * 32, "ab" * 32)

    def test_no_signer(self):
        with self.assertRaises(ValueError):
            verify_output("", "ab" * 32)

    def test_multiple_signers(self):
        with self.assertRaises(ValueError):
            verify_output("\n".join(f"Signer #{i} certificate SHA-256 digest: " + ("ab" if i == 1 else "cd") * 32 for i in (1, 2)), "ab" * 32)

    def test_build_tools_37(self):
        verify_output("V2 Signer: certificate SHA-256 digest: " + "ab" * 32, "ab" * 32)

    def test_same_certificate_across_schemes(self):
        output = "\n".join(f"V{i} Signer: certificate SHA-256 digest: " + "ab" * 32 for i in (1, 2, 3))
        verify_output(output, "ab" * 32)

    def test_different_certificate_across_schemes(self):
        with self.assertRaises(ValueError):
            verify_output("V2 Signer: certificate SHA-256 digest: " + "cd" * 32, "ab" * 32)

    def test_invalid_expected_digest(self):
        with self.assertRaises(ValueError):
            verify_output("", "not-a-digest")


if __name__ == "__main__":
    unittest.main()
