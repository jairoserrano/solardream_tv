"""Genera tramas de referencia con la librería oficial hoymiles-wifi.

Uso (desde la raíz del repo):
    python -m venv tools/.venv
    tools/.venv/Scripts/python -m pip install hoymiles-wifi
    tools/.venv/Scripts/python tools/make_golden.py
"""
import struct
from pathlib import Path

from crcmod import mkCrcFun
from hoymiles_wifi.protobuf import RealDataNew_pb2 as pb

OUT = Path(__file__).resolve().parent.parent / "app/src/test/resources/fixtures"
crc16 = mkCrcFun(0x18005, rev=True, initCrc=0xFFFF, xorOut=0x0000)


def frame(payload: bytes, seq: int = 1) -> bytes:
    return b"HM\xa3\x11" + struct.pack(">HHH", seq, crc16(payload), len(payload) + 10) + payload


def response(ap, cp, meter=False, dtu=False):
    r = pb.RealDataNewReqDTO()
    r.device_serial_number = "400000000001"
    r.timestamp = 1792463207
    r.ap = ap
    r.cp = cp
    if meter:
        m = r.meter_data.add()
        m.device_type = 1
        m.serial_number = 12000000000001
        m.phase_total_power = -143
        m.phase_A_power = -65
        m.phase_B_power = -77
        m.voltage_phase_A = 1243
        m.voltage_phase_B = 1216
        m.current_phase_A = 586
        m.current_phase_B = 721
    if dtu:
        pv = r.pv_data.add()
        pv.serial_number = 11000000000001
        pv.port_number = 1
        pv.power = 6000
        pv.energy_total = 1082806
        r.dtu_power = 24100
        r.dtu_daily_energy = 8200
    return frame(r.SerializeToString())


def main():
    OUT.mkdir(parents=True, exist_ok=True)

    req = pb.RealDataNewResDTO()
    req.time_ymd_hms = b"2026-10-19 21:30:00"
    req.offset = 28800
    req.time = 1792463400
    req.cp = 2
    (OUT / "golden_request_cp2.bin").write_bytes(frame(req.SerializeToString()))

    (OUT / "golden_response_p1of4.bin").write_bytes(response(ap=4, cp=1, meter=True, dtu=True))
    (OUT / "golden_single.bin").write_bytes(response(ap=1, cp=0, meter=True, dtu=True))
    (OUT / "golden_page0_of2.bin").write_bytes(response(ap=2, cp=0, dtu=True))
    (OUT / "golden_page1_of2.bin").write_bytes(response(ap=2, cp=1, meter=True))
    print("escrito en", OUT)


main()
