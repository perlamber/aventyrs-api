# Metabase with MotherDuck's DuckDB driver. The official metabase/metabase image is Alpine
# (musl), and DuckDB's JDBC native library needs glibc, so this runs the plain Metabase jar on
# a glibc JRE instead.
#
# The driver bundles its own DuckDB (1.5.5 here). The warehouse job's duckdb is kept on 1.5.x
# (see ../pyproject.toml) so the file it writes stays readable here.

ARG DUCKDB_VERSION=v1.5.5

# The warehouse has TIMESTAMPTZ columns, which need DuckDB's icu extension. The JDBC driver
# doesn't bundle it, so the first open of a file auto-downloads it, and that first open has
# been seen to hang for good while doing so. Every later connection to the same path then
# queues behind it (the driver caches one database instance per path) and Metabase reports
# "Timed out after 10.0 s". Installing icu at build time, with the same DuckDB version the
# driver bundles, means nothing is downloaded at runtime.
FROM debian:bookworm-slim AS duckdb-extensions
ARG DUCKDB_VERSION
ARG TARGETARCH
RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates curl unzip \
 && curl -fsSL -o /tmp/duckdb.zip \
      "https://github.com/duckdb/duckdb/releases/download/${DUCKDB_VERSION}/duckdb_cli-linux-${TARGETARCH}.zip" \
 && unzip /tmp/duckdb.zip -d /usr/local/bin \
 && duckdb -c "FORCE INSTALL icu"

FROM eclipse-temurin:21-jre

ARG METABASE_VERSION=v0.63.18
ARG DUCKDB_DRIVER_VERSION=1.5.5.0

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && mkdir -p /app /plugins /metabase-data \
 && curl -fsSL -o /app/metabase.jar \
      "https://downloads.metabase.com/${METABASE_VERSION}/metabase.jar" \
 && curl -fsSL -o /plugins/duckdb.metabase-driver.jar \
      "https://github.com/motherduckdb/metabase_duckdb_driver/releases/download/${DUCKDB_DRIVER_VERSION}/duckdb.metabase-driver.jar"

COPY --from=duckdb-extensions /root/.duckdb /root/.duckdb

# Metabase's own application database (questions, dashboards, users) is H2 in a volume, kept
# apart from the warehouse it reads.
ENV MB_PLUGINS_DIR=/plugins \
    MB_DB_FILE=/metabase-data/metabase.db

EXPOSE 3000
CMD ["java", "-jar", "/app/metabase.jar"]
