# The Mongo -> DuckDB job. Run with `docker compose --profile analytics run --rm warehouse`
# (defaults to `all`: extract, then build). Writes raw.duckdb, warehouse.duckdb and tables/ to
# /warehouse, the path docker-compose mounts the warehouse-data volume at.
FROM python:3.12-slim

WORKDIR /app
COPY pyproject.toml ./
COPY aventyrs_wh ./aventyrs_wh
RUN pip install --no-cache-dir .

ENV WAREHOUSE_DATA_DIR=/warehouse
ENTRYPOINT ["python", "-m", "aventyrs_wh"]
CMD ["all"]
