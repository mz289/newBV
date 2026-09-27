"""Reject release artifacts whose signing certificate differs from the fixed CI key."""

import argparse
import re
import subprocess


def verify_output(output: str, expected: str) -> None:
    """Require exactly one signer matching the expected SHA-256 certificate digest."""
    expected = expected.replace(":", "").lower()
    if not re.fullmatch(r"[0-9a-f]{64}", expected):
        raise ValueError("Expected certificate digest must contain 64 hexadecimal digits")
    digests = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)$", output, re.MULTILINE)
    if [digest.lower() for digest in digests] != [expected]:
        raise ValueError(f"Signing certificate mismatch: expected {expected}, found {digests}")


def main() -> None:
    """Verify cryptographic APK signatures before comparing their public certificates."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apksigner", required=True)
    parser.add_argument("--expected", required=True)
    parser.add_argument("apks", nargs="+")
    args = parser.parse_args()
    for apk in args.apks:
        result = subprocess.run(
            [args.apksigner, "verify", "--print-certs", apk],
            check=True, capture_output=True, text=True,
        )
        verify_output(result.stdout, args.expected)
        print(f"Verified fixed signing certificate: {apk}")


if __name__ == "__main__":
    main()
