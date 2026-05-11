#!/usr/bin/env sh
set -eu

docker compose -f docker-compose.test.yml up -d

DB_URL=jdbc:postgresql://localhost:5433/uaeitjobs_test \
DB_USERNAME=postgres \
DB_PASSWORD=test \
mvn test -Dspring.profiles.active=test
