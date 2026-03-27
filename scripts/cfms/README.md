# CFMS Startup/Test Scripts

These helper scripts let you run and test `cfms_on_websocket` from the Netdisk repo.

## Files

- `scripts/cfms/start-cfms.ps1`: bootstrap config/deps and start CFMS server
- `scripts/cfms/test-cfms.ps1`: run smoke or pytest checks
- `scripts/cfms/cfms_smoke_test.py`: protocol-level smoke (server_info/login/refresh_token)

## Quick Start

```powershell
Set-Location "D:\NetdiskProject\Netdisk"
powershell -ExecutionPolicy Bypass -File .\scripts\cfms\start-cfms.ps1
```

If you already synced dependencies:

```powershell
Set-Location "D:\NetdiskProject\Netdisk"
powershell -ExecutionPolicy Bypass -File .\scripts\cfms\start-cfms.ps1 -SkipSync
```

## Smoke Test (server must be running)

```powershell
Set-Location "D:\NetdiskProject\Netdisk"
powershell -ExecutionPolicy Bypass -File .\scripts\cfms\test-cfms.ps1 -Mode smoke
```

## Run CFMS Pytest

Basic tests:

```powershell
Set-Location "D:\NetdiskProject\Netdisk"
powershell -ExecutionPolicy Bypass -File .\scripts\cfms\test-cfms.ps1 -Mode pytest-basic
```

All tests:

```powershell
Set-Location "D:\NetdiskProject\Netdisk"
powershell -ExecutionPolicy Bypass -File .\scripts\cfms\test-cfms.ps1 -Mode pytest-all
```

## Notes

- Default CFMS root is `D:\NetdiskProject\CFMS_WebSocket\cfms_on_websocket`.
- Override root path with `-CfmsRoot` if needed.
- Smoke test uses WSS by default and ignores self-signed cert verification.
- If your CFMS server runs without TLS, pass `-NoSsl` to `test-cfms.ps1`.

