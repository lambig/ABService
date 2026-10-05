"""配布用コンテナの実flacで、合成入力の解析位置を検査する（実音源・AWS不要）。"""
import pathlib
import re
import struct
import subprocess
import sys
import tempfile


def check(image):
    with tempfile.TemporaryDirectory(prefix="flac-image-check-") as temporary:
        directory = pathlib.Path(temporary)
        # コンテナの非root UIDで合成入力の出力先へ書き込める一時領域だけを共有する。
        directory.chmod(0o777)
        raw = directory / "synthetic.raw"
        raw.write_bytes(b"".join(struct.pack("<hh", i % 30000, -(i % 30000)) for i in range(8192)))
        raw.chmod(0o644)

        def flac(*arguments):
            result = subprocess.run(
                ["docker", "run", "--rm", "--network", "none", "--read-only",
                 "--cap-drop", "ALL", "--security-opt", "no-new-privileges",
                 "--mount", f"type=bind,src={directory},dst=/work",
                 "--entrypoint", "flac", image, *arguments],
                capture_output=True, text=True, timeout=60, check=True,
            )
            return result.stdout

        version = flac("--version").strip()
        flac("--silent", "--force-raw-format", "--endian=little", "--sign=signed",
             "--channels=2", "--bps=16", "--sample-rate=44100", "--no-seektable",
             "--padding=65536", "--output-name=/work/synthetic.flac", "/work/synthetic.raw")
        flac("--test", "--silent", "--warnings-as-errors", "--stdout", "--", "/work/synthetic.flac")
        data = (directory / "synthetic.flac").read_bytes()
        offset = 4
        if data[:4] != b"fLaC":
            raise RuntimeError("合成FLACのsignatureが不正")
        while True:
            last = data[offset] & 0x80
            length = int.from_bytes(data[offset + 1:offset + 4], "big")
            offset += 4 + length
            if offset > len(data):
                raise RuntimeError("合成FLACのmetadata範囲が不正")
            if last:
                break
        expected_offset = offset
        count = samples = 0
        analysis = flac("--analyze", "--silent", "--warnings-as-errors", "--stdout",
                        "--", "/work/synthetic.flac")
        for line in analysis.splitlines():
            if not line.startswith("frame="):
                continue
            fields = re.fullmatch(
                r"frame=(\d+)\toffset=(\d+)\tbits=(\d+)\tblocksize=(\d+)"
                r"\tsample_rate=44100\tchannels=2\tchannel_assignment=[A-Z_]+", line)
            if fields is None:
                raise RuntimeError("解析形式が不正")
            index, start, bits, block = map(int, fields.groups())
            if index != count or start != expected_offset or bits <= 0 or bits % 8:
                raise RuntimeError(f"{version}: frame {index} の解析位置またはサイズが不正")
            header = int.from_bytes(data[start:start + 4], "big")
            if header & 0xfffe0001 != 0xfff80000:
                raise RuntimeError("解析位置が実frame headerと不一致")
            expected_offset = start + bits // 8
            samples += block
            count += 1
        if count == 0 or samples != 8192 or expected_offset != len(data):
            raise RuntimeError("実frame数・sample数・末尾位置が不一致")
        print(f"{version}: metadata padding付き合成FLACの全{count} frame・8192 samples・末尾位置が一致")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("使い方: python3 scripts/check-flac-image.py <local-image>")
    check(sys.argv[1])
