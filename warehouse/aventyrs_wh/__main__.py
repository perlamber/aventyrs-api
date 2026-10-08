"""CLI: python -m aventyrs_wh [extract|build|all]."""

from __future__ import annotations

import argparse
import os
import sys

from .build import build
from .extract import extract

DEFAULT_MONGO_URI = "mongodb://localhost:27017/aventyrs?replicaSet=rs0&directConnection=true"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="aventyrs_wh", description=__doc__)
    parser.add_argument("step", choices=["extract", "build", "all"], nargs="?", default="all")
    parser.add_argument("--mongo-uri", default=os.environ.get("MONGODB_URI", DEFAULT_MONGO_URI))
    parser.add_argument("--data-dir", default=os.environ.get("WAREHOUSE_DATA_DIR", "data"),
                        help="where raw.duckdb and warehouse.duckdb live")
    args = parser.parse_args(argv)

    os.makedirs(args.data_dir, exist_ok=True)
    raw_path = os.path.join(args.data_dir, "raw.duckdb")
    out_path = os.path.join(args.data_dir, "warehouse.duckdb")

    if args.step in ("extract", "all"):
        counts = extract(args.mongo_uri, raw_path)
        print("extracted " + ", ".join(f"{k}={v}" for k, v in counts.items()))
    if args.step in ("build", "all"):
        tables = build(raw_path, out_path)
        print(f"built {out_path}: {', '.join(tables)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
