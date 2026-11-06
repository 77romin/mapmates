"""SSAFY_TRIP 스키마와 덤프를 검증용 스키마(mapmates_erd_check)로 이름만 바꿔 00_base.sql로 만든다."""
import pathlib
import sys

ref = pathlib.Path(sys.argv[1])
out_dir = pathlib.Path(sys.argv[2])
target = "mapmates_erd_check"

schema = (ref / "SSAFY_TRIP_Schema.sql").read_text(encoding="utf-8")
dump = (ref / "SSAFY_TRIP_Dump.sql").read_text(encoding="utf-8")

parts = [
    f"DROP DATABASE IF EXISTS {target};",
    f"CREATE DATABASE {target} DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;",
    schema.replace("ssafy_trip", target),
    dump.replace("USE ssafy_trip;", f"USE {target};"),
]
(out_dir / "00_base.sql").write_text("\n".join(parts), encoding="utf-8")
print("wrote", out_dir / "00_base.sql")
