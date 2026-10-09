# The DuckDB CLI, for ad-hoc SQL over the warehouse. DuckDB is embedded (a library over a
# file), so there's no server to run: this container opens warehouse.duckdb and exits.
#
# Kept on 1.5.x like the job (../pyproject.toml) and Metabase's driver, so all three read the
# same file format.
FROM debian:bookworm-slim

ARG DUCKDB_VERSION=v1.5.6
ARG TARGETARCH

RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates curl unzip \
 && curl -fsSL -o /tmp/duckdb.zip \
      "https://github.com/duckdb/duckdb/releases/download/${DUCKDB_VERSION}/duckdb_cli-linux-${TARGETARCH}.zip" \
 && unzip /tmp/duckdb.zip -d /usr/local/bin \
 && rm /tmp/duckdb.zip \
 && apt-get purge -y curl unzip && apt-get autoremove -y \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /warehouse
ENTRYPOINT ["duckdb"]
CMD ["-readonly", "/warehouse/warehouse.duckdb"]
