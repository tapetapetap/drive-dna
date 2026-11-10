#!/usr/bin/env python3
"""DRIVE DNA 시연. Virtual Console HTTP(127.0.0.1:8088, adb forward 필요)로 운전을 흉내 낸다.

  python3 scripts/demo.py gentle    부드러운 운전 → GPSE (약 2분 15초)
  python3 scripts/demo.py dynamic   역동적인 운전 → DRFC (약 1분)
  python3 scripts/demo.py city      시내 운전 → GPFE (약 2분 10초)
  python3 scripts/demo.py straight  회전 없는 운전 → G·SE, 대응 방식 보류 (약 2분)
  python3 scripts/demo.py short     15초 운전 → 짧은 운전
  python3 scripts/demo.py reset     콘솔 신호를 0으로, 배터리 60kWh, 앱 기록 삭제
  python3 scripts/demo.py seed      앱 기록을 샘플 24개로(보조 태그 다섯이 판정됨)
  python3 scripts/demo.py highway   크루즈를 켠 고속도로 운전(약 2분 10초) → 태그 근거가 다시 계산됨
  python3 scripts/demo.py compare   3차: reset → gentle → dynamic → 운전 기록 → 비교 → 두 줄 → "두 운전 비교"(약 4분)
  python3 scripts/demo.py tour      전체 연속 시연(약 10분): reset → 시나리오 다섯 개, 사이사이 결과·기록·16유형 화면 넘기기

시나리오는 scripts/scenarios/<이름>.txt다(앱 테스트 DnaTest도 같은 파일을 읽는다).
콘솔은 속도를 줘도 주행거리를 늘리지 않으므로, 0.2초마다 시계 기준으로 주행거리를 직접 쓴다.
(SDK는 주행거리를 약 1초마다 그때 값으로 전달한다. 1초마다 쓰면 전달 시각과 어긋나 속도가 흔들린다. research R3)
"""
import json
import os
import re
import subprocess
import sys
import threading
import time
import urllib.request

VC = "http://127.0.0.1:8088"
ADB = os.path.join(os.environ.get("ANDROID_HOME", os.path.expanduser("~/Library/Android/sdk")), "platform-tools", "adb")
APP = "kr.skku.drivedna"
HERE = os.path.dirname(os.path.abspath(__file__))
STEP = 0.2


def call(method, path):
    req = urllib.request.Request(VC + path, method=method)
    return urllib.request.urlopen(req, timeout=5).read()


def put(path):
    call("PUT", "/vehicle/" + path)


def say(text):
    print(time.strftime("%H:%M:%S"), text, flush=True)


def heartbeat():
    # 콘솔은 브라우저 세션 신호가 3초 넘게 끊기면 쓰기를 거부한다(emulator_inactive)
    while True:
        try:
            call("POST", "/browser/session?client=demo")
        except OSError:
            pass
        time.sleep(1)


def adb(*args):
    subprocess.run([ADB, "shell", *args], check=False, stdout=subprocess.DEVNULL)


def load(name):
    """한 줄 = 한 구간: 초 시속 조향 브레이크 방향지시등 [C·W]. 'kwh N'은 운전이 끝날 때 줄일 배터리. 첫 줄은 설명.
    보조 태그 지시어(specs/002-tags quickstart): 'soc 시작 끝', 'seat 0|1', 'place 위도 경도'. 여섯째 칸 C = 크루즈, W = 와이퍼"""
    segs, kwh, extra = [], 0.0, {}
    with open(os.path.join(HERE, "scenarios", name + ".txt"), encoding="utf-8") as f:
        say(f.readline().strip("# \n"))
        for line in f:
            p = line.split("#")[0].split()
            if not p:
                continue
            if p[0] == "kwh":
                kwh = float(p[1])
            elif p[0] in ("soc", "seat", "place"):
                extra[p[0]] = p[1:]
            else:
                segs.append((float(p[0]), float(p[1]), float(p[2]), p[3] == "1", p[4], p[5] if len(p) > 5 else ""))
    return segs, kwh, extra


SIGNAL = {"Off": "Off", "Left": "BlinkLeft", "Right": "BlinkRight"}


def drive(name):
    segs, kwh, extra = load(name)
    state = json.loads(call("GET", "/vehicle/state"))
    km = state["odometer"]["traveled_distance_km"] + 0.001  # 줄어드는 쓰기는 무시되므로 지금 값부터
    battery = state["ev_battery"]["current_energy_kwh"]
    put("controls?speed=0&steering=0&brake=0")
    put("turn-signal?mode=Off")
    put(f"odometer?traveled_distance_km={km:.5f}")
    if "soc" in extra:
        put(f"ev-battery?field=soc&value={extra['soc'][0]}")
        say(f"  배터리 SOC {extra['soc'][0]}% (충전해 두었다)")
    if "seat" in extra:  # 이 에뮬레이터는 착좌가 콘솔 값의 반대로 앱에 온다(research R8)
        put(f"seat?kind=occupied&area=passenger&value={'false' if extra['seat'][0] == '1' else 'true'}")
    time.sleep(2)
    put("gear?gear=Drive")
    say("▶ D (앱 화면은 '운전 중이에요', Pleos가 앱을 가린다)")
    try:
        v = steer = 0.0
        signal = "Off"
        flags = None
        for secs, v1, s1, brake, sig, fl in segs:
            if sig != signal:
                put("turn-signal?mode=" + SIGNAL[sig])
                signal = sig
            if fl != flags:  # 크루즈 작동은 full_autonomous만 ACTIVATED로 온다(research R8)
                put("driving-mode?ad_mode_state=" + ("full_autonomous" if "C" in fl else "ready"))
                put("body?front_wiper=" + ("Low" if "W" in fl else "Off"))
                flags = fl
            v0, s0 = v, steer
            start = last = time.time()
            while True:
                now = time.time()
                k = min(1.0, (now - start) / secs)
                v, steer = v0 + (v1 - v0) * k, s0 + (s1 - s0) * k
                km += v * (now - last) / 3600
                last = now
                put(f"controls?speed={v:.0f}&steering={steer:.0f}&brake={40 if brake else 0}")
                put(f"odometer?traveled_distance_km={km:.5f}")
                if k >= 1.0:
                    break
                time.sleep(STEP)
            say(f"  {secs:.0f}초 · 시속 {v1:.0f} · 조향 {s1:.0f}° · 브레이크 {'밟음' if brake else '-'} · 방향지시등 {sig}"
                + (" · 크루즈" if "C" in fl else "") + (" · 와이퍼" if "W" in fl else ""))
        put("turn-signal?mode=Off")
        put("driving-mode?ad_mode_state=ready")
        put("body?front_wiper=Off")
        if "soc" in extra:
            put(f"ev-battery?field=soc&value={extra['soc'][1]}")
        if "place" in extra:  # 끝난 곳(앱은 이 한 점만 기기 안에 남긴다)
            subprocess.run([ADB, "emu", "geo", "fix", extra["place"][1], extra["place"][0]], capture_output=True)
        if kwh:
            put(f"ev-battery?field=battery_level&value={battery - kwh:.1f}")
            say(f"  배터리 {kwh}kWh 사용")
        time.sleep(2)  # 배터리·주행거리 마지막 값이 앱에 닿도록
    finally:  # 중간에 콘솔 요청이 실패해도 차를 D에 두지 않는다(앱이 결과를 보이게)
        put("gear?gear=Park")
        put("controls?speed=0&steering=0&brake=0")
        put("turn-signal?mode=Off")
    say("■ P (앱이 이번 운전 결과를 보여 준다)")


def reset():
    put("gear?gear=Park")
    put("driving-mode?ad_mode_state=ready")
    put("body?front_wiper=Off")
    put("controls?speed=0&steering=0&brake=0&accelerator=0")
    put("turn-signal?mode=Off")
    put("ev-battery?field=battery_level&value=60.0")
    time.sleep(1)
    adb("am", "force-stop", "--user", "10", "kr.skku.unusedfeature")  # 첫 앱이 P에서 음성으로 끼어들지 않게
    adb("am", "force-stop", "--user", "10", APP)
    adb("run-as", APP, "--user", "10", "rm", "-f", "shared_prefs/records.xml")
    adb("sync")
    adb("am", "start", "--user", "10", "-n", APP + "/.MainActivity")
    say("콘솔 신호와 앱 기록을 처음으로 되돌렸어요. 화면: 첫 실행")


def seed():
    """앱 기록을 scripts/seed.txt(샘플 24개)로 바꾸고 앱을 연다(specs/002-tags research R10). 태그 다섯이 판정된다"""
    from xml.sax.saxutils import escape
    lines = open(os.path.join(HERE, "seed.txt"), encoding="utf-8").read().strip()
    tmp = os.path.join(HERE, ".records.xml")
    with open(tmp, "w", encoding="utf-8") as f:
        f.write("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n"
                f"    <string name=\"drives\">{escape(lines)}</string>\n</map>\n")
    subprocess.run([ADB, "push", tmp, "/data/local/tmp/records.xml"], capture_output=True)
    os.remove(tmp)
    adb("am", "force-stop", "--user", "10", APP)
    adb("run-as", APP, "--user", "10", "sh", "-c", "'mkdir -p shared_prefs && cp /data/local/tmp/records.xml shared_prefs/records.xml'")
    adb("am", "start", "--user", "10", "-n", APP + "/.MainActivity")
    say("샘플 기록 24개를 넣었어요. 나의 DNA 칸에 보조 태그 칩, '태그 ›'를 누르면 태그 화면")


def tap(text):
    """화면 덤프에서 [text]가 들어간 첫 뷰의 가운데를 누른다. 못 찾으면 알리고 넘어간다"""
    subprocess.run([ADB, "shell", "uiautomator", "dump", "/sdcard/ui.xml"], capture_output=True)
    xml = subprocess.run([ADB, "shell", "cat", "/sdcard/ui.xml"], capture_output=True, text=True).stdout
    for m in re.finditer(r'text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
        if text in m.group(1):
            x1, y1, x2, y2 = map(int, m.groups()[1:])
            adb("input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
            return
    say(f"  (화면에서 '{text}'를 못 찾았어요)")


def scroll(down=True):
    # 가로 화면의 오른쪽 상황 칸(에뮬레이터 2560×1440 기준)
    adb("input", "swipe", "2000", "1150" if down else "250", "2000", "250" if down else "1150", "700")


def compare():
    """3차 두 운전 비교(specs/003-drive-graph research R17). 누르기는 tour의 tap을 쓴다"""
    reset(); time.sleep(4)
    drive("gentle"); time.sleep(6)
    drive("dynamic"); time.sleep(6)
    tap("운전 기록  ›"); time.sleep(3)
    tap("비교"); time.sleep(2)
    tap("DRFC · 자유로운 드라이버"); time.sleep(1)
    tap("GPSE · 차분한 계획가"); time.sleep(1)
    tap("두 운전 비교"); time.sleep(3)
    say("비교 화면: 두 유형 카드, 겹친 그래프, 축마다 두 점, '다른 축 n개 · 같은 축 m개'")


def tour():
    """전체 연속 시연. P마다 결과를 잠시 보여 주고, 화면을 넘겨 기록·다시 보기·16유형까지 보인다"""
    subprocess.run([ADB, "logcat", "-c"])
    say("── 1/7 처음 상태(첫 실행 화면)"); reset(); time.sleep(6)
    say("── 2/7 gentle"); drive("gentle"); time.sleep(8); scroll(); time.sleep(5); scroll(down=False); time.sleep(2)
    say("── 3/7 dynamic"); drive("dynamic"); time.sleep(8); scroll(); time.sleep(6); scroll(down=False); time.sleep(2)
    say("── 4/7 city"); drive("city"); time.sleep(8)
    say("── 5/7 straight → 16유형"); drive("straight"); time.sleep(8)
    tap("16유형  ›"); time.sleep(7); tap("✕  닫기"); time.sleep(2)
    say("── 6/7 운전 기록 → 다시 보기")
    tap("운전 기록  ›"); time.sleep(5); tap("GPSE · 차분한 계획가"); time.sleep(6); tap("←  운전 기록"); time.sleep(3); tap("✕  닫기"); time.sleep(2)
    say("── 7/7 short"); drive("short"); time.sleep(6)
    log = subprocess.run([ADB, "logcat", "-d", "-s", "WatchService"], capture_output=True, text=True).stdout
    say("끝. 앱이 남긴 운전 끝 지표:")
    for line in log.splitlines():
        if "운전 끝" in line:
            print("  " + line.split("운전 끝: ")[-1])


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    threading.Thread(target=heartbeat, daemon=True).start()
    time.sleep(2)
    if sys.argv[1] == "reset":
        reset()
    elif sys.argv[1] == "seed":
        seed()
    elif sys.argv[1] == "tour":
        tour()
    elif sys.argv[1] == "compare":
        compare()
    else:
        drive(sys.argv[1])
