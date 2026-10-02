#!/usr/bin/env python3
"""哔哩必连 / DLNA 投屏接收端验证脚本。

用法：
    python tools/bilian_probe.py                 # 自动通过 SSDP 搜索 newBV 设备
    python tools/bilian_probe.py 192.168.5.95    # 直接指定接收端 IP

依次验证（对应手机端官方投屏的完整链路）：
  1. SSDP 发现（M-SEARCH ssdp:all → MediaRenderer/NirvanaControl 应答）
  2. GET /description.xml（官方云视听小电视特征字段）
  3. SOAP GetAppInfo（NirvanaControl/action → 官方包名 com.xiaodianshi.tv.yst）
  4. 必连通道：SETUP /projection 升级 NVA 帧协议 → GetVolume 应答
"""

import re
import socket
import struct
import sys
import time
import urllib.request

SSDP_ADDR = ("239.255.255.250", 1900)
NVA_PORT = 9958


def discover(timeout=6.0):
    msg = (
        "M-SEARCH * HTTP/1.1\r\n"
        "HOST: 239.255.255.250:1900\r\n"
        'MAN: "ssdp:discover"\r\n'
        "MX: 3\r\n"
        "ST: ssdp:all\r\n"
        "\r\n"
    ).encode()
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.settimeout(0.5)
    end = time.time() + timeout
    targets = set()
    while time.time() < end:
        sock.sendto(msg, SSDP_ADDR)
        try:
            while True:
                data, addr = sock.recvfrom(4096)
                text = data.decode(errors="replace")
                if "NirvanaControl" in text and "LOCATION" in text.upper():
                    m = re.search(r"(?i)location:\s*(\S+)", text)
                    if m:
                        targets.add((addr[0], m.group(1)))
        except socket.timeout:
            pass
    return targets


def http_request(ip, method, path, headers=None, body=b""):
    data = body if method == "POST" else None
    req = urllib.request.Request(f"http://{ip}:{NVA_PORT}{path}", data=data, method=method)
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    with urllib.request.urlopen(req, timeout=5) as resp:
        return resp.status, dict(resp.headers), resp.read().decode(errors="replace")


def nva_roundtrip(ip):
    """模拟手机端：SETUP 升级 → 发送 GetVolume 命令帧 → 读取应答帧。"""
    sock = socket.create_connection((ip, NVA_PORT), timeout=5)
    sock.settimeout(5)
    sock.sendall(
        f"SETUP /projection HTTP/1.1\r\nHost: {ip}:{NVA_PORT}\r\nsession: probe-{int(time.time())}\r\n\r\n".encode()
    )
    head = b""
    while b"\r\n\r\n" not in head:
        chunk = sock.recv(1)
        if not chunk:
            print("  [NVA] 连接在升级响应前关闭")
            return False
        head += chunk
    ok = b"200" in head.split(b"\r\n")[0]
    print(f"  [NVA] 升级响应: {head.split(b'\r\n')[0].decode(errors='replace')}")
    if not ok:
        return False

    version = 1
    command = b"Command"
    action = b"GetVolume"
    body = b"{}"
    frame = bytearray([0xE0, 0x03])
    frame += struct.pack(">I", version)
    frame.append(0x01)
    frame.append(len(command))
    frame += command
    frame.append(len(action))
    frame += action
    frame += struct.pack(">I", len(body))
    frame += body
    sock.sendall(bytes(frame))

    data = sock.recv(4096)
    print(f"  [NVA] 应答帧 ({len(data)} bytes): {data.hex()}")
    # 应答帧: C0 01 + version(4) + len(4) + json
    if len(data) >= 10 and data[0] == 0xC0:
        length = struct.unpack(">I", data[6:10])[0]
        payload = data[10 : 10 + length]
        print(f"  [NVA] 应答内容: {payload.decode(errors='replace')}")
        sock.close()
        return b"volume" in payload
    sock.close()
    return False


def main():
    ip = sys.argv[1] if len(sys.argv) > 1 else None
    print("== 1. SSDP 发现 ==")
    if ip is None:
        found = discover()
        if not found:
            print("  未发现带 NirvanaControl 的设备（检查接收端开关/同网段/组播）")
            return
        ip = sorted(found)[0][0]
        for dev_ip, loc in sorted(found):
            print(f"  发现: {dev_ip} -> {loc}")
    else:
        print(f"  使用指定 IP: {ip}")

    print("== 2. 设备描述 ==")
    status, _, body = http_request(ip, "GET", "/description.xml")
    print(f"  HTTP {status}")
    for field in ("friendlyName", "capability", "ottVersion", "NirvanaControl", "X_DLNADOC"):
        print(f"  {field}: {'OK' if field in body else 'MISSING'}")

    print("== 3. SOAP GetAppInfo ==")
    soap = (
        '<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" '
        's:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body>'
        '<u:GetAppInfo xmlns:u="urn:app-bilibili-com:service:NirvanaControl:3"></u:GetAppInfo>'
        "</s:Body></s:Envelope>"
    ).encode()
    status, _, body = http_request(
        ip,
        "POST",
        "/NirvanaControl/action",
        headers={"SOAPAction": '"urn:app-bilibili-com:service:NirvanaControl:3#GetAppInfo"'},
        body=soap,
    )
    yst = "com.xiaodianshi.tv.yst" in body
    print(f"  HTTP {status}, 官方包名上报: {'OK' if yst else 'MISSING'}")

    print("== 4. 必连 NVA 通道 ==")
    ok = nva_roundtrip(ip)
    print(f"  GetVolume 往返: {'OK' if ok else 'FAIL'}")

    all_ok = yst and ok
    print(f"\n结论: {'必连链路正常，手机端应能发现并连接' if all_ok else '存在失败项，结合接收端投屏日志排查'}")


if __name__ == "__main__":
    main()
