# GymFlow — Backend

[![Backend CI](https://github.com/williampa182/gymflow-backend/actions/workflows/backend-ci.yml/badge.svg)](https://github.com/williampa182/gymflow-backend/actions/workflows/backend-ci.yml)
![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen)

API REST para GymFlow, un sistema de gestión de gimnasios: planes,
suscripciones, usuarios con roles y un chatbot de soporte con RAG simple
sobre los planes del gimnasio.

Proyecto de portafolio construido con un flujo de colaboración
multi-agente (ver `AGENTS.md`), documentado en detalle para servir también
como referencia de proceso, no solo de código.

**Demo en vivo:** [gymflow-backend-e3h6.onrender.com](https://gymflow-backend-e3h6.onrender.com)
· **Frontend:** [gymflow-frontend](https://github.com/williampa182/gymflow-frontend) ·
[demo](https://gymflow-frontend-ten.vercel.app)

## Stack

- **Java 21** + **Spring Boot 4.1.0**
- **Spring Security 7** — autenticación JWT vía cookie httpOnly
- **PostgreSQL** (Spring Data JPA)
- **Redis** (caché)
- **Docker Compose** para el entorno local
- **GitHub Actions** para CI/CD; despliegue en **Vercel + Render** (hosting gratuito, 2026-08-14: Neon para Postgres, Redis Cloud para Redis)

## Decisiones técnicas

- **Cookie httpOnly en vez de localStorage para el JWT**: reduce la
  superficie de ataque XSS — el token no es accesible desde JavaScript en
  el cliente.
- **Redis para caché**: los datos de planes y suscripciones se leen mucho
  más de lo que se escriben; cachear evita ida y vuelta innecesaria a
  Postgres en cada request.
- **Rehash de BCrypt on-login**: si el cost factor de hashing cambia (por
  ejemplo al subir hardware o política de seguridad), los hashes viejos se
  actualizan de forma transparente en el próximo login del usuario, sin
  forzar un reset masivo de contraseñas.
- **Proveedor de chat configurable (Gemini/Anthropic)**: el chatbot de
  soporte usa Gemini por defecto (tier gratis, sin tarjeta, mientras el
  proyecto no genera ingresos), pero el adapter de Anthropic ya está
  implementado — cambiar de proveedor es solo una variable de entorno.

## Features

- Autenticación con JWT en cookie httpOnly, roles (`ADMIN` / `CLIENTE`)
- CRUD de planes y suscripciones
- Gestión de usuarios con control de acceso por rol
- Dashboard administrativo
- Chatbot de soporte con RAG simple sobre los planes del gimnasio, con
  rate limiting
- Documentación OpenAPI/Swagger (gateada por env var, deshabilitada por
  defecto)

## Seguridad

Este proyecto pasó por una auditoría de seguridad dedicada, cerrada con
12 hallazgos resueltos en 5 commits. Algunos ejemplos concretos:

- **Autenticación timing-safe**: tiempo de respuesta constante en login
  independientemente de si el usuario existe, para prevenir enumeración
  por timing attack.
- **Privilege escalation corregida**: un campo `rol` controlado por el
  cliente en el registro permitía auto-asignarse rol de admin — detectado
  y corregido, con test de regresión.
- **Hardening de cookies y CORS**, gating de Swagger por entorno, límites
  de paginación, sanitización de `X-Request-ID`, supresión de stack
  traces en respuestas de error.
- El historial de Git fue limpiado con `git-filter-repo` para remover un
  secreto commiteado por error en una etapa temprana del desarrollo — no
  reutilizar ningún secreto de commits antiguos.

## Empezar en local

Requisitos: JDK 21, Docker y Docker Compose.

```bash
# Levantar Postgres y Redis
docker compose up -d

# Copiar y ajustar las variables de entorno
cp .env.example .env

# Correr la aplicación
./mvnw spring-boot:run
```

La API queda disponible en `http://localhost:8080`. Ver `.env.example`
para el detalle de cada variable (base de datos, Redis, JWT, CORS, chat
de soporte, etc.), con comentarios explicando el porqué de cada default.

## Tests

```bash
./mvnw test
```

## Documentación adicional

- `AGENTS.md` — convenciones para agentes de IA que colaboran en este
  repo.
- `docs/` — documentación técnica del proyecto.
