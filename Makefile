# ==============================================================================
# CEFA TV Player — Makefile de desarrollo local
# Plantilla de cefa-dev-skills (reglas en docs/llm/rules/common/makefile.md).
# ==============================================================================

APP_NAME := cefa-tv-player
NETWORK_NAME := cefa-tv-net

.PHONY: help build up down rebuild format lint test check clean run logs

help: ## Muestra los comandos disponibles y una breve descripción
	@echo "Uso: make [objetivo]"
	@echo ""
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'
	@echo ""

build: ## Construye los artefactos necesarios (JAR) sin ejecutar tests
	@echo "Construyendo $(APP_NAME)..."
	@./mvnw clean package -DskipTests

up: ## Levanta el entorno local con Docker Compose (asegura la red compartida)
	@echo "Levantando entorno Docker para $(APP_NAME)..."
	@docker network inspect $(NETWORK_NAME) >/dev/null 2>&1 || docker network create $(NETWORK_NAME)
	@docker compose up -d

down: ## Detiene el entorno local de Docker
	@echo "Deteniendo entorno Docker de $(APP_NAME)..."
	@docker compose down

rebuild: ## Reconstruye las imágenes y vuelve a levantar los servicios
	@echo "Reconstruyendo y levantando servicios de $(APP_NAME)..."
	@docker network inspect $(NETWORK_NAME) >/dev/null 2>&1 || docker network create $(NETWORK_NAME)
	@docker compose up -d --build

format: ## Aplica el formateador de código configurado (Spotless)
	@echo "Aplicando formato de código con Spotless..."
	@./mvnw spotless:apply -DskipTests

lint: ## Ejecuta comprobaciones de estilo y formato sin modificar archivos
	@echo "Comprobando formato y estilo de código con Spotless..."
	@./mvnw spotless:check -DskipTests

test: ## Ejecuta la batería de pruebas disponible en el repositorio
	@echo "Ejecutando tests de $(APP_NAME)..."
	@./mvnw test

check: lint test ## Ejecuta de forma conjunta validaciones de formato, lint y pruebas
	@echo "Todas las comprobaciones han pasado satisfactoriamente."

clean: ## Elimina artefactos temporales generados por la compilación (target/)
	@echo "Limpiando artefactos de $(APP_NAME)..."
	@./mvnw clean
	@rm -rf target/

run: ## Arranca la aplicación en local con Spring Boot (perfil dev)
	@echo "Iniciando $(APP_NAME) en local (perfil dev)..."
	@SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run -DskipTests

logs: ## Muestra y sigue los logs de los contenedores Docker
	@docker compose logs -f
