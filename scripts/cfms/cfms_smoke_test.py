#!/usr/bin/env python3
import argparse
import json
import pathlib
import ssl
import struct
import time
from typing import Any, Dict, Tuple

from websockets.sync.client import connect

HEADER_FORMAT = "!IB"
HEADER_SIZE = struct.calcsize(HEADER_FORMAT)
FRAME_PROCESS = 0


def pack_frame(frame_id: int, frame_type: int, payload: bytes) -> bytes:
    header = struct.pack(HEADER_FORMAT, frame_id, frame_type)
    return header + payload


def unpack_frame(raw: bytes) -> Tuple[int, int, bytes]:
    if len(raw) < HEADER_SIZE:
        raise ValueError("Invalid frame: too short")
    frame_id, frame_type = struct.unpack_from(HEADER_FORMAT, raw)
    return frame_id, frame_type, raw[HEADER_SIZE:]


def send_action(ws, frame_id: int, action: str, data: Dict[str, Any], **kwargs) -> Dict[str, Any]:
    request: Dict[str, Any] = {"action": action, "data": data}
    request.update(kwargs)
    payload = json.dumps(request).encode("utf-8")
    ws.send(pack_frame(frame_id, FRAME_PROCESS, payload))

    raw = ws.recv()
    if isinstance(raw, str):
        raw = raw.encode("utf-8")
    _, _, body = unpack_frame(raw)
    return json.loads(body.decode("utf-8"))


def main() -> int:
    parser = argparse.ArgumentParser(description="CFMS smoke test (server_info + optional login)")
    parser.add_argument("--host", default="localhost")
    parser.add_argument("--port", type=int, default=5104)
    parser.add_argument("--no-ssl", action="store_true", help="Use ws:// instead of wss://")
    parser.add_argument("--admin-password-file", default="admin_password.txt")
    parser.add_argument("--skip-login", action="store_true")
    args = parser.parse_args()

    protocol = "ws" if args.no_ssl else "wss"
    uri = f"{protocol}://{args.host}:{args.port}"

    ssl_ctx = None
    if not args.no_ssl:
        ssl_ctx = ssl.create_default_context()
        ssl_ctx.check_hostname = False
        ssl_ctx.verify_mode = ssl.CERT_NONE

    print(f"[1/3] Connecting to {uri} ...")
    with connect(uri, ssl=ssl_ctx, open_timeout=8) as ws:
        print("[2/3] Sending server_info ...")
        info = send_action(ws, 2, "server_info", {})
        print("server_info:", json.dumps(info, ensure_ascii=False))

        if args.skip_login:
            print("[3/3] Login skipped")
            return 0

        pwd_path = pathlib.Path(args.admin_password_file)
        if not pwd_path.exists():
            print(f"[3/3] admin password file not found: {pwd_path}; skip login")
            return 0

        password = pwd_path.read_text(encoding="utf-8").strip()
        if not password:
            print("[3/3] admin password file is empty; skip login")
            return 0

        print("[3/3] Sending login ...")
        login = send_action(ws, 4, "login", {"username": "admin", "password": password})
        print("login:", json.dumps(login, ensure_ascii=False))

        login_code = int(login.get("code", 0))
        if login_code not in (200, 202, 4001, 4002):
            return 2

        if login_code in (4001, 4002):
            print("[note] Login blocked by password policy/expiration, but transport and auth path are reachable.")
            return 0

        # Optional authenticated request to validate token path quickly
        data = login.get("data", {}) if isinstance(login.get("data"), dict) else {}
        token = data.get("token")
        if token:
            print("[extra] Sending refresh_token ...")
            refresh = send_action(
                ws,
                6,
                "refresh_token",
                {},
                username="admin",
                token=token,
                nonce="smoke" + str(int(time.time() * 1000)),
                timestamp=time.time(),
            )
            print("refresh_token:", json.dumps(refresh, ensure_ascii=False))

    return 0


if __name__ == "__main__":
    raise SystemExit(main())

