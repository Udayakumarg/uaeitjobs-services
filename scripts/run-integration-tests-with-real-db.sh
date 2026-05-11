#!/usr/bin/env sh
set -eu

docker compose -f docker-compose.test.yml up -d

echo "Waiting for PostgreSQL test database to become healthy..."
for i in $(seq 1 30); do
  status="$(docker inspect --format='{{.State.Health.Status}}' uaeitjobs-postgres-test 2>/dev/null || true)"
  if [ "$status" = "healthy" ]; then
    break
  fi
  if [ "$i" -eq 30 ]; then
    echo "PostgreSQL test database did not become healthy in time" >&2
    docker compose -f docker-compose.test.yml logs postgres-test >&2
    exit 1
  fi
  sleep 2
done

DB_URL=jdbc:postgresql://localhost:5433/uaeitjobs_test \
DB_USERNAME=postgres \
DB_PASSWORD=test \
mvn test -Dspring.profiles.active=test
