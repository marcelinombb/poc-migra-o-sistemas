package br.com.legado.controller;

import br.com.legado.dto.CriarMatriculaRequest;
import br.com.legado.dto.MatriculaResponse;
import br.com.legado.service.MatriculaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/matriculas")
@RequiredArgsConstructor
@Tag(name = "Matrículas (Legado)", description = "Operações de Matrícula no Sistema Legado")
public class MatriculaController {

    private final MatriculaService matriculaService;

    @PostMapping
    @Operation(summary = "Criar nova matrícula no Legado", description = "Persiste a matrícula e agenda a publicação via Transactional Outbox")
    public ResponseEntity<MatriculaResponse> criarMatricula(@RequestBody CriarMatriculaRequest request) {
        MatriculaResponse response = matriculaService.criarMatricula(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Listar todas as matrículas do Legado")
    public ResponseEntity<List<MatriculaResponse>> listarTodas() {
        return ResponseEntity.ok(matriculaService.listarTodas());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar matrícula legada por ID")
    public ResponseEntity<MatriculaResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(matriculaService.buscarPorId(id));
    }
}
