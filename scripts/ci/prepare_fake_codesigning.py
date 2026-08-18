#!/usr/bin/env python3
import json
import os
import plistlib
import shutil
import subprocess
import sys
import tempfile
import urllib.request

TEMPLATE_BUNDLE_ID = "ph.telegra.Telegraph"
UPSTREAM_PROFILES = [
    "Telegram.mobileprovision",
    "Intents.mobileprovision",
    "NotificationContent.mobileprovision",
    "NotificationService.mobileprovision",
    "Share.mobileprovision",
    "WatchApp.mobileprovision",
    "WatchExtension.mobileprovision",
    "Widget.mobileprovision",
    "BroadcastUpload.mobileprovision",
]
UPSTREAM_BASE = "https://raw.githubusercontent.com/Swiftgram/Telegram-iOS/master/build-system/fake-codesigning/profiles"


def repo_root():
    return os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))


def load_configuration(configuration_path):
    with open(configuration_path, "r", encoding="utf-8") as file:
        return json.load(file)


def download_upstream_profiles(profiles_dir):
    os.makedirs(profiles_dir, exist_ok=True)
    for name in UPSTREAM_PROFILES:
        destination = os.path.join(profiles_dir, name)
        if os.path.exists(destination):
            continue
        url = f"{UPSTREAM_BASE}/{name}"
        print(f"Downloading {url}")
        with urllib.request.urlopen(url) as response:
            data = response.read()
        with open(destination, "wb") as file:
            file.write(data)


def decode_profile(path):
    data = subprocess.check_output(
        [
            "openssl",
            "smime",
            "-inform",
            "der",
            "-verify",
            "-noverify",
            "-in",
            path,
        ]
    )
    return plistlib.loads(data)


def patch_entitlements(entitlements, team_id, old_bundle_id, new_bundle_id):
    old_prefix = f"{team_id}.{old_bundle_id}"
    new_prefix = f"{team_id}.{new_bundle_id}"
    old_group = f"group.{old_bundle_id}"
    new_group = f"group.{new_bundle_id}"

    def patch_value(value):
        if isinstance(value, str):
            return value.replace(old_prefix, new_prefix).replace(old_group, new_group)
        if isinstance(value, list):
            return [patch_value(item) for item in value]
        return value

    patched = {}
    for key, value in entitlements.items():
        patched[key] = patch_value(value)
    return patched


def write_xml_plist(path, payload):
    with open(path, "wb") as file:
        plistlib.dump(payload, file, fmt=plistlib.FMT_XML)


def sign_patched_plist(plist_path, destination, p12_path):
    make_dir = os.path.join(repo_root(), "build-system", "Make")
    sys.path.insert(0, make_dir)
    from BuildEnvironment import run_executable_with_output  # pylint: disable=import-outside-toplevel
    from GenerateProfiles import (  # pylint: disable=import-outside-toplevel
        cleanup_temp_keychain,
        get_certificate_base64_from_p12,
        get_signing_identity_from_p12,
        setup_temp_keychain,
    )

    signing_identity = get_signing_identity_from_p12(p12_path, "")
    certificate_data = get_certificate_base64_from_p12(p12_path, "")
    if not signing_identity:
        raise RuntimeError(f"Could not read signing identity from {p12_path}")

    keychain_name = setup_temp_keychain(p12_path, "")
    try:
        while True:
            result = run_executable_with_output(
                "plutil",
                arguments=["-remove", "DeveloperCertificates.0", plist_path],
                check_result=False,
            )
            if result is None or "Could not" in str(result) or result == "":
                check = run_executable_with_output(
                    "plutil",
                    arguments=["-extract", "DeveloperCertificates.0", "raw", plist_path],
                    check_result=False,
                )
                if check is None or "Could not" in str(check):
                    break

        run_executable_with_output(
            "plutil",
            arguments=["-insert", "DeveloperCertificates.0", "-data", certificate_data, plist_path],
        )
        run_executable_with_output(
            "plutil",
            arguments=["-remove", "DER-Encoded-Profile", plist_path],
            check_result=False,
        )
        run_executable_with_output(
            "security",
            arguments=[
                "cms",
                "-S",
                "-k",
                keychain_name,
                "-N",
                signing_identity,
                "-i",
                plist_path,
                "-o",
                destination,
            ],
        )
    finally:
        cleanup_temp_keychain(keychain_name)


def patch_profiles(profiles_dir, team_id, new_bundle_id, p12_path):
    if new_bundle_id == TEMPLATE_BUNDLE_ID:
        print("Bundle id matches template profiles; no patch needed.")
        return

    patched_dir = tempfile.mkdtemp(prefix="telelarp-profiles-")
    try:
        for name in os.listdir(profiles_dir):
            if not name.endswith(".mobileprovision"):
                continue

            source_path = os.path.join(profiles_dir, name)
            profile_dict = decode_profile(source_path)
            profile_dict["Entitlements"] = patch_entitlements(
                profile_dict["Entitlements"],
                team_id=team_id,
                old_bundle_id=TEMPLATE_BUNDLE_ID,
                new_bundle_id=new_bundle_id,
            )

            temp_plist = os.path.join(patched_dir, name + ".plist")
            temp_signed = os.path.join(patched_dir, name)
            write_xml_plist(temp_plist, profile_dict)
            sign_patched_plist(temp_plist, temp_signed, p12_path)
            shutil.copyfile(temp_signed, source_path)
            print(f"Patched profile: {name}")
    finally:
        shutil.rmtree(patched_dir, ignore_errors=True)


def main():
    root = repo_root()
    configuration_path = os.environ.get(
        "TELELARP_CONFIGURATION_PATH",
        os.path.join(root, "build-system", "ci-sideload-configuration.json"),
    )
    codesigning_dir = os.path.join(root, "build-system", "fake-codesigning")
    profiles_dir = os.path.join(codesigning_dir, "profiles")
    p12_path = os.path.join(codesigning_dir, "certs", "SelfSigned.p12")

    configuration = load_configuration(configuration_path)
    team_id = configuration["team_id"]
    bundle_id = configuration["bundle_id"]

    if not os.path.exists(p12_path):
        print(f"Missing certificate: {p12_path}", file=sys.stderr)
        return 1

    download_upstream_profiles(profiles_dir)
    patch_profiles(profiles_dir, team_id, bundle_id, p12_path)
    print(f"Fake codesigning ready for {bundle_id}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
