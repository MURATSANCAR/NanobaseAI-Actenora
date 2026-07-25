.PHONY: bootstrap build test lint run stop sbom ci-build ci-test help

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

ci-build:
	./scripts/ci-build

ci-test:
	./scripts/ci-test

help:
	@echo "Actenora monorepo targets:"
	@echo "  make bootstrap  - install toolchains & deps"
	@echo "  make build      - build all projects"
	@echo "  make test       - run all tests"
	@echo "  make lint       - lint all packages"
	@echo "  make run        - start local stack"
	@echo "  make stop       - stop local stack"
	@echo "  make sbom       - generate CycloneDX SBOMs"
	@echo "  make ci-build   - CI bootstrap+build+sbom"
	@echo "  make ci-test    - CI lint+test"
