# Resistance

A job application tracker built as a Spring Boot multi-module monorepo,
refactored from the original
[darbyluv2code Spring Boot course](https://github.com/darbyluv2code/spring-boot-3-spring-6-hibernate-for-beginners)
layout of ~150 standalone demo projects into a single Maven build with
consolidated services, shared libraries, an ETL framework, and deployment
infrastructure.

## Domain model

- **JobApplication** - the central tracked record: company, position, status
  (`applied`, `screening`, `interview`, `offer`, `rejected`, `accepted`, `withdrawn`)
- **Contact** - recruiters, referrals and hiring managers you talk to
- **Recruiter / RecruiterDetail / JobPosting / Note / Candidate** - the advanced
  JPA mapping demos (1-1, 1-N, N-N) expressed in tracker terms

## Project layout

```
Resistance/
├── services/
│   ├── core-service/              Spring Core: DI, qualifiers, scopes, Java config (port 8081)
│   ├── data-service/              JPA/Hibernate CRUD command-line demo (Contact)
│   ├── rest-api-service/          REST CRUD API for tracked job applications (port 8083)
│   ├── security-service/          REST API + JDBC users/roles/bcrypt security (port 8084)
│   ├── mvc-service/               Spring MVC + Thymeleaf application CRUD & forms (port 8085)
│   ├── mvc-security-service/      MVC form login, roles, custom tables (port 8086)
│   └── advanced-data-service/     JPA advanced mappings CLI demo (1-1, 1-N, N-N)
│
├── shared/
│   ├── shared-models/             JPA entities and DTOs
│   ├── shared-validation/         Custom Bean Validation constraints (@ReferralCode)
│   ├── shared-exceptions/         Common exceptions and API error payloads
│   └── shared-utils/              CSV parsing and normalization helpers
│
├── etl/
│   ├── etl-core/                  Extract/Transform/Load framework interfaces + pipeline
│   ├── etl-data-processors/       CSV extractor, normalizers, entity mappers
│   ├── etl-validators/            Record validation rules
│   └── etl-runner/                Spring Boot app that orchestrates the pipelines
│
├── infrastructure/
│   ├── docker-compose.yml         MySQL + services + gateway
│   ├── docker/Dockerfile          Generic multi-stage image for any module
│   ├── kubernetes/                Namespace, MySQL, and application manifests
│   └── config/db-init/            Database schemas and seed data
│
└── api-gateway/                   Routes /{service}/** to the matching service (port 8080)
```

## Building

Requires JDK 26 and Maven 3.9+ (Spring Boot 4.1).

```bash
mvn clean package          # everything
mvn -pl services/rest-api-service -am package   # one service + its dependencies
```

## Running locally

Every JPA-backed module expects MySQL with the schemas in
`infrastructure/config/db-init/` and credentials `springstudent`/`springstudent`.
The quickest way to get one:

```bash
docker compose -f infrastructure/docker-compose.yml up mysql
```

Then run any service, e.g.:

```bash
mvn -pl services/rest-api-service -am spring-boot:run
```

`DB_HOST`/`DB_PORT` environment variables override the default
`localhost:3306` in every service.

Full stack (MySQL, four web services, gateway):

```bash
docker compose -f infrastructure/docker-compose.yml up --build
```

The gateway then serves e.g. `http://localhost:8080/rest-api/api/applications`.

## ETL

`etl-runner` imports job applications from CSV through the
extract → validate → transform → load pipeline defined in `etl-core`:

```bash
mvn -pl etl/etl-runner -am spring-boot:run
```

By default it processes the bundled `sample-data/applications.csv` in dry-run
mode. Set `etl.dry-run=false` to write to the database and
`etl.input-location=file:/path/to/file.csv` to point at your own data.

## Where the course projects went

Each service is the final, most complete project of its course section,
repackaged under `com.resistance.*`, renamed into the job tracker domain
(Employee -> JobApplication, Student -> Contact, Instructor -> Recruiter,
Course -> JobPosting, Review -> Note), with entities, validation, and
exceptions extracted into `shared/`:

| Module | Origin |
|---|---|
| core-service | 02-spring-boot-spring-core / 09-java-config-bean |
| data-service | 03-spring-boot-hibernate-jpa-crud / 08-create-db-tables-automatically |
| rest-api-service | 04-spring-boot-rest-crud / 16-employee-with-spring-data-jpa (+ global exception handling from 06) |
| security-service | 05-spring-boot-rest-security / 07-jdbc-bcrypt-custom-table-names |
| mvc-service | 07-spring-boot-spring-mvc-crud / 04-01-employees-delete (+ validation demo from 06 / 20) |
| mvc-security-service | 08-spring-boot-spring-mvc-security / 13-jdbc-bcrypt-custom-tables |
| advanced-data-service | 09-spring-boot-jpa-advanced-mappings / 21-many-to-many-add-more-courses |
| shared-validation | 06-spring-boot-spring-mvc / 20-validationdemo-custom-validation-rule |

The original numbered course directories (including sections 01, 10, 11, 12
that had no target module) remain available in git history prior to this
refactor.
