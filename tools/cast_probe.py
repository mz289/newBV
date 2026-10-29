#!/usr/bin/env python3
"""B 站 DLNA 投屏接收端验证脚本。

用法：
    python tools/cast_probe.py                 # 自动通过 SSDP 搜索 newBV 设备
    python tools/cast_probe.py 192.168.5.95    # 直接指定接收端 IP

依次验证（对应手机端 B 站 DLNA 投屏的完整链路）：
  1. SSDP 发现（M-SEARCH ssdp:all → MediaRenderer 应答）
  2. GET /description.xml（官方云视听小电视特征字段）
  3. SOAP GetAppInfo（NirvanaControl/action → 官方包名 com.xiaodianshi.tv.yst）
"""

import re
import socket
import sys
import time
import urllib.request

HTTP_PORT = 9958


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
        sock.sendto(msg, ("239.255.255.250", 1900))
        try:
            while True:
                data, addr = sock.recvfrom(4096)
                text = data.decode(errors="replace")
                if "MediaRenderer" in text and "LOCATION" in text.upper():
                    m = re.search(r"(?i)location:\s*(\S+)", text)
                    if m:
                        targets.add((addr[0], m.group(1)))
        except socket.timeout:
            pass
    return targets


def http_request(ip, method, path, headers=None, body=b""):
    data = body if method == "POST" else None
    req = urllib.request.Request(f"http://{ip}:{HTTP_PORT}{path}", data=data, method=method)
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    with urllib.request.urlopen(req, timeout=5) as resp:
        return resp.status, dict(resp.headers), resp.read().decode(errors="replace")


def main():
    ip = sys.argv[1] if len(sys.argv) > 1 else None
    print("== 1. SSDP 发现 ==")
    if ip is None:
        found = discover()
        if not found:
            print("  未发现 MediaRenderer 设备（检查接收端开关/同网段/组播）")
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

    print(f"\n结论: {'DLNA 链路正常，手机端应能发现并投屏' if yst else '存在失败项，结合接收端投屏日志排查'}")


if __name__ == "__main__":
    main()
