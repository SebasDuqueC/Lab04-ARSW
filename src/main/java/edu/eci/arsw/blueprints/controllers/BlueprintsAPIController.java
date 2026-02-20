package edu.eci.arsw.blueprints.controllers;

import edu.eci.arsw.blueprints.model.Blueprint;
import edu.eci.arsw.blueprints.model.Point;
import edu.eci.arsw.blueprints.persistence.BlueprintNotFoundException;
import edu.eci.arsw.blueprints.persistence.BlueprintPersistenceException;
import edu.eci.arsw.blueprints.services.BlueprintsServices;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/blueprints")
public class BlueprintsAPIController {

    private final BlueprintsServices services;

    public BlueprintsAPIController(BlueprintsServices services) { this.services = services; }

    // GET /blueprints
    @Operation(summary = "Lista todos los planos")
    @ApiResponse(responseCode = "200", description = "Listado completo",
            content = @Content(schema = @Schema(implementation = ApiResponseDto.class)))
    @GetMapping
    public ResponseEntity<ApiResponseDto<Set<Blueprint>>> getAll() {
        return ok(services.getAllBlueprints());
    }

    // GET /blueprints/{author}
    @Operation(summary = "Lista planos por autor")
        @ApiResponse(responseCode = "200", description = "Planos del autor",
            content = @Content(schema = @Schema(implementation = ApiResponseDto.class)))
    @ApiResponse(responseCode = "404", description = "No hay planos para ese autor")
    @GetMapping("/{author}")
    public ResponseEntity<ApiResponseDto<Set<Blueprint>>> byAuthor(@PathVariable String author) {
        try {
            return ok(services.getBlueprintsByAuthor(author));
        } catch (BlueprintNotFoundException e) {
            return notFound(e.getMessage());
        }
    }

    // GET /blueprints/{author}/{bpname}
    @Operation(summary = "Obtiene un plano por autor y nombre")
        @ApiResponse(responseCode = "200", description = "Plano encontrado",
            content = @Content(schema = @Schema(implementation = ApiResponseDto.class)))
    @ApiResponse(responseCode = "404", description = "Plano no existe")
    @GetMapping("/{author}/{bpname}")
    public ResponseEntity<ApiResponseDto<Blueprint>> byAuthorAndName(@PathVariable String author, @PathVariable String bpname) {
        try {
            return ok(services.getBlueprint(author, bpname));
        } catch (BlueprintNotFoundException e) {
            return notFound(e.getMessage());
        }
    }

    // POST /blueprints
    @Operation(summary = "Crea un plano nuevo")
    @ApiResponse(responseCode = "201", description = "Plano creado")
        @ApiResponse(responseCode = "400", description = "Datos inválidos o duplicados",
            content = @Content(schema = @Schema(implementation = ApiResponseDto.class)))
    @PostMapping
    public ResponseEntity<ApiResponseDto<Blueprint>> add(@Valid @RequestBody NewBlueprintRequest req) {
        try {
            Blueprint bp = new Blueprint(req.author(), req.name(), req.points());
            services.addNewBlueprint(bp);
            return created(bp);
        } catch (BlueprintPersistenceException e) {
            return badRequest(e.getMessage());
        }
    }

    // PUT /blueprints/{author}/{bpname}/points
    @Operation(summary = "Añade un punto a un plano existente")
        @ApiResponse(responseCode = "202", description = "Punto agregado",
            content = @Content(schema = @Schema(implementation = ApiResponseDto.class)))
    @ApiResponse(responseCode = "404", description = "Plano no encontrado")
    @PutMapping("/{author}/{bpname}/points")
    public ResponseEntity<ApiResponseDto<Blueprint>> addPoint(@PathVariable String author, @PathVariable String bpname,
                                      @RequestBody Point p) {
        try {
            services.addPoint(author, bpname, p.x(), p.y());
            Blueprint updated = services.getBlueprint(author, bpname);
            return accepted(updated);
        } catch (BlueprintNotFoundException e) {
            return notFound(e.getMessage());
        }
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponseDto<String>> handleValidation(MethodArgumentNotValidException ex) {
        List<FieldError> errors = ex.getBindingResult().getFieldErrors();
        String msg = errors.isEmpty() ? "Datos inválidos" : errors.getFirst().getDefaultMessage();
        return badRequest(msg);
    }

    private <T> ResponseEntity<ApiResponseDto<T>> ok(T data) {
        return ResponseEntity.ok(new ApiResponseDto<>(HttpStatus.OK.value(), "ok", data));
    }

    private <T> ResponseEntity<ApiResponseDto<T>> created(T data) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponseDto<>(HttpStatus.CREATED.value(), "created", data));
    }

    private <T> ResponseEntity<ApiResponseDto<T>> accepted(T data) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new ApiResponseDto<>(HttpStatus.ACCEPTED.value(), "accepted", data));
    }

    private <T> ResponseEntity<ApiResponseDto<T>> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponseDto<>(HttpStatus.BAD_REQUEST.value(), message, null));
    }

    private <T> ResponseEntity<ApiResponseDto<T>> notFound(String message) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponseDto<>(HttpStatus.NOT_FOUND.value(), message, null));
    }

    public record NewBlueprintRequest(
            @NotBlank String author,
            @NotBlank String name,
            @Valid java.util.List<Point> points
    ) { }
}
