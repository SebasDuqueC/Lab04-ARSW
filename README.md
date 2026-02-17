## Laboratorio #4 – REST API Blueprints (Java 21 / Spring Boot 3.3.x)
# Escuela Colombiana de Ingeniería – Arquitecturas de Software  

---

## 📋 Requisitos
- Java 21
- Maven 3.9+

## ▶️ Ejecución del proyecto
```bash
mvn clean install
mvn spring-boot:run
```
Probar con `curl`:
```bash
curl -s http://localhost:8080/blueprints | jq
curl -s http://localhost:8080/blueprints/john | jq
curl -s http://localhost:8080/blueprints/john/house | jq
curl -i -X POST http://localhost:8080/blueprints -H 'Content-Type: application/json' -d '{ "author":"john","name":"kitchen","points":[{"x":1,"y":1},{"x":2,"y":2}] }'
curl -i -X PUT  http://localhost:8080/blueprints/john/kitchen/points -H 'Content-Type: application/json' -d '{ "x":3,"y":3 }'
```

> Si deseas activar filtros de puntos (reducción de redundancia, *undersampling*, etc.), implementa nuevas clases que implementen `BlueprintsFilter` y cámbialas por `IdentityFilter` con `@Primary` o usando configuración de Spring.
---

Abrir en navegador:  
- Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)  
- OpenAPI JSON: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)  

---

## 🗂️ Estructura de carpetas (arquitectura)

```
src/main/java/edu/eci/arsw/blueprints
  ├── model/         # Entidades de dominio: Blueprint, Point
  ├── persistence/   # Interfaz + repositorios (InMemory, Postgres)
  │    └── impl/     # Implementaciones concretas
  ├── services/      # Lógica de negocio y orquestación
  ├── filters/       # Filtros de procesamiento (Identity, Redundancy, Undersampling)
  ├── controllers/   # REST Controllers (BlueprintsAPIController)
  └── config/        # Configuración (Swagger/OpenAPI, etc.)
```

> Esta separación sigue el patrón **capas lógicas** (modelo, persistencia, servicios, controladores), facilitando la extensión hacia nuevas tecnologías o fuentes de datos.

---

## 📖 Actividades del laboratorio

### 1. Familiarización con el código base
- Revisa el paquete `model` con las clases `Blueprint` y `Point`.  
- Entiende la capa `persistence` con `InMemoryBlueprintPersistence`.  
- Analiza la capa `services` (`BlueprintsServices`) y el controlador `BlueprintsAPIController`.

### 2. Migración a persistencia en PostgreSQL
En esta parte nos pasamos de la lista en memoria a una base en Postgres para que los planos queden guardados de verdad. Lo hicimos paso a paso entre los dos:

- Primero levantamos un Postgres local con Docker para no enredarnos instalando nada en la máquina. El comando que usamos fue:
  ```bash
  docker run --name blueprints-db -e POSTGRES_DB=blueprints -e POSTGRES_USER=blueprints -e POSTGRES_PASSWORD=blueprints -p 5432:5432 -d postgres:16
  ```
- Creamos el esquema y datos de ejemplo automáticamente al iniciar la app con los archivos `schema.sql` y `data.sql` en `src/main/resources`. Así evitamos tener que correr scripts manualmente.
- Creamos el esquema y datos de ejemplo automáticamente al iniciar la app con los archivos `schema.sql` y `data.sql` en `src/main/resources`. Así evitamos tener que correr scripts manualmente. Los fragmentos clave:
  ```sql
  -- schema.sql
  CREATE TABLE IF NOT EXISTS blueprints (
      author VARCHAR(100) NOT NULL,
      name   VARCHAR(100) NOT NULL,
      PRIMARY KEY (author, name)
  );
  CREATE TABLE IF NOT EXISTS blueprint_points (
      author VARCHAR(100) NOT NULL,
      name   VARCHAR(100) NOT NULL,
      idx    INT NOT NULL,
      x      INT NOT NULL,
      y      INT NOT NULL,
      PRIMARY KEY (author, name, idx),
      FOREIGN KEY (author, name) REFERENCES blueprints(author, name) ON DELETE CASCADE
  );
  ```
  ```sql
  -- data.sql
  INSERT INTO blueprints(author, name) VALUES ('john','house'), ('john','garage'), ('jane','garden') ON CONFLICT DO NOTHING;
  INSERT INTO blueprint_points(author, name, idx, x, y) VALUES
    ('john','house',0,0,0), ('john','house',1,10,0), ('john','house',2,10,10), ('john','house',3,0,10),
    ('john','garage',0,5,5), ('john','garage',1,15,5), ('john','garage',2,15,15),
    ('jane','garden',0,2,2), ('jane','garden',1,3,4), ('jane','garden',2,6,7)
  ON CONFLICT DO NOTHING;
  ```
- Agregamos las dependencias de JDBC y el driver de Postgres en el `pom.xml` para que Spring pueda conectarse a la base.
- Escribimos un repositorio nuevo llamado `PostgresBlueprintPersistence` que implementa la misma interfaz `BlueprintPersistence`, pero ahora usa consultas SQL sencillas con `JdbcTemplate`. Lee y guarda los puntos manteniendo el orden y lanza las mismas excepciones que la versión en memoria. Ejemplo del guardado con batch:
  ```java
  public void saveBlueprint(Blueprint bp) throws BlueprintPersistenceException {
      jdbc.update("INSERT INTO blueprints(author, name) VALUES (?, ?)", bp.getAuthor(), bp.getName());
      if (!bp.getPoints().isEmpty()) {
          List<Object[]> batch = new ArrayList<>();
          for (int i = 0; i < bp.getPoints().size(); i++) {
              Point p = bp.getPoints().get(i);
              batch.add(new Object[]{bp.getAuthor(), bp.getName(), i, p.x(), p.y()});
          }
          jdbc.batchUpdate("INSERT INTO blueprint_points(author, name, idx, x, y) VALUES (?,?,?,?,?)", batch);
      }
  }
  ```
- Dejamos el repositorio en memoria activado por defecto y el de Postgres se activa solo con el perfil `postgres`. Así la app sigue corriendo sin base si alguien solo quiere probar rápido.
- Para correrlo con Postgres activamos el perfil y levantamos la app:
  ```bash
  export SPRING_PROFILES_ACTIVE=postgres
  mvn spring-boot:run
  ```

Con esto logramos que las pruebas de los endpoints sigan igual, pero ahora los datos viven en Postgres y sobreviven reinicios.

### 3. Buenas prácticas de API REST
- Cambia el path base de los controladores a `/api/v1/blueprints`.  
- Usa **códigos HTTP** correctos:  
  - `200 OK` (consultas exitosas).  
  - `201 Created` (creación).  
  - `202 Accepted` (actualizaciones).  
  - `400 Bad Request` (datos inválidos).  
  - `404 Not Found` (recurso inexistente).  
- Implementa una clase genérica de respuesta uniforme:
  ```java
  public record ApiResponse<T>(int code, String message, T data) {}
  ```
  Ejemplo JSON:
  ```json
  {
    "code": 200,
    "message": "execute ok",
    "data": { "author": "john", "name": "house", "points": [...] }
  }
  ```

### 4. OpenAPI / Swagger
- Configura `springdoc-openapi` en el proyecto.  
- Expón documentación automática en `/swagger-ui.html`.  
- Anota endpoints con `@Operation` y `@ApiResponse`.

### 5. Filtros de *Blueprints*
- Implementa filtros:
  - **RedundancyFilter**: elimina puntos duplicados consecutivos.  
  - **UndersamplingFilter**: conserva 1 de cada 2 puntos.  
- Activa los filtros mediante perfiles de Spring (`redundancy`, `undersampling`).  

---

## ✅ Entregables

1. Repositorio en GitHub con:  
   - Código fuente actualizado.  
   - Configuración PostgreSQL (`application.yml` o script SQL).  
   - Swagger/OpenAPI habilitado.  
   - Clase `ApiResponse<T>` implementada.  

2. Documentación:  
   - Informe de laboratorio con instrucciones claras.  
   - Evidencia de consultas en Swagger UI y evidencia de mensajes en la base de datos.  
   - Breve explicación de buenas prácticas aplicadas.  

---

## 📊 Criterios de evaluación

| Criterio | Peso |
|----------|------|
| Diseño de API (versionamiento, DTOs, ApiResponse) | 25% |
| Migración a PostgreSQL (repositorio y persistencia correcta) | 25% |
| Uso correcto de códigos HTTP y control de errores | 20% |
| Documentación con OpenAPI/Swagger + README | 15% |
| Pruebas básicas (unitarias o de integración) | 15% |

**Bonus**:  

- Imagen de contenedor (`spring-boot:build-image`).  
- Métricas con Actuator.  