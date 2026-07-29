.PHONY: bootstrap build test lint run stop sbom ci-build ci-test verify verify-faz27 faz28 secret-scan dep-scan semgrep deploy-portal deploy-portal-full deploy-backend help

bootstrap:
	./scripts/bootstrap

build:
	./scripts/build-all

test:
	./scripts/test-all

lint:
	./scripts/lint-all

run:
	./scripts/run-local

stop:
	./scripts/stop-local

sbom:
	./scripts/generate-sbom

secret-scan:
	./scripts/scan-secrets

dep-scan:
	./scripts/scan-dependencies

semgrep:
	./scripts/scan-semgrep

ci-build:
	./scripts/ci-build

ci-test:
	./scripts/ci-test

deploy-portal:
	./scripts/deploy-actenora-portal.sh

deploy-portal-full:
	./scripts/deploy-portal-production.sh

deploy-backend:
	./scripts/deploy-actenora-backend.sh

help:
	@echo "Actenora monorepo targets:"
	@echo "  make bootstrap      - install toolchains & deps"
	@echo "  make build          - build all projects"
	@echo "  make test           - run all tests"
	@echo "  make lint           - lint all packages"
	@echo "  make run            - start local stack"
	@echo "  make stop           - stop local stack"
	@echo "  make verify         - FAZ-2 local infra acceptance checks"
	@echo "  make verify-faz27   - FAZ-27 security/retention acceptance"
	@echo "  make faz28          - FAZ-28 load/resilience/failover scenarios"
	@echo "  make secret-scan    - secret leak scan"
	@echo "  make dep-scan       - dependency vulnerability scan"
	@echo "  make semgrep        - Semgrep SAST security scan"
	@echo "  make sbom           - generate CycloneDX SBOMs"
	@echo "  make ci-build       - CI bootstrap+build+sbom"
	@echo "  make ci-test        - CI lint+test"
	@echo "  make deploy-portal  - build + deploy Actenora SPA to portal.nanobase.ai/actenora/"
	@echo "  make deploy-portal-full - QA hub + Actenora (full production portal deploy)"
	@echo "  make deploy-backend - build + deploy platform-backend BFF on portal.nanobase.ai"

verify:
	./scripts/verify-faz2

verify-faz27:
	./scripts/verify-faz27

faz28:
	./scripts/run-faz28-tests
