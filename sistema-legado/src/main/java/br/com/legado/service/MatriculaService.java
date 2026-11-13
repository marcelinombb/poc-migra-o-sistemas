package br.com.legado.service;

import br.com.legado.contracts.CloudEvent;
import br.com.legado.contracts.v1.AlunoMatriculaCriadaV1Payload;
import br.com.legado.domain.Matricula;
import br.com.legado.domain.OutboxMessage;
import br.com.legado.dto.CriarMatriculaRequest;
import br.com.legado.dto.MatriculaResponse;
import br.com.legado.repository.MatriculaRepository;
import br.com.legado.repository.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatriculaService {

    private final MatriculaRepository matriculaRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.rabbitmq.routing-keys.matricula-criada-v1}")
    private String routingKeyMatriculaCriada;

    @Transactional
    public MatriculaResponse criarMatricula(CriarMatriculaRequest request) {
        Instant agora = Instant.now();

        // 1. Salva a entidade de negócio
        Matricula matricula = Matricula.builder()
                .codAluno(request.getCodAluno())
                .numPeriodo(request.getPeriodoLetivo())
                .nomStatus("CRIADA")
                .datCriacao(agora)
                .build();
        matricula = matriculaRepository.save(matricula);

        // 2. Cria o contrato versionado CloudEvent v1
        UUID eventId = UUID.randomUUID();
        AlunoMatriculaCriadaV1Payload payload = AlunoMatriculaCriadaV1Payload.builder()
                .codMatricula(matricula.getId())
                .codAluno(matricula.getCodAluno())
                .periodoLetivo(matricula.getNumPeriodo())
                .status(matricula.getNomStatus())
                .dataMatricula(agora)
                .build();

        CloudEvent<AlunoMatriculaCriadaV1Payload> cloudEvent = CloudEvent.<AlunoMatriculaCriadaV1Payload>builder()
                .specversion("1.0")
                .id(eventId.toString())
                .source("sistema-legado")
                .type(routingKeyMatriculaCriada)
                .subject("matricula/" + matricula.getId())
                .time(agora)
                .datacontenttype("application/json")
                .data(payload)
                .build();

        // 3. Salva na Outbox na MESMA transação do banco (Garantia de consistência Transactional Outbox)
        try {
            String jsonPayload = objectMapper.writeValueAsString(cloudEvent);
            OutboxMessage outbox = OutboxMessage.builder()
                    .id(eventId)
                    .tipoEvento(routingKeyMatriculaCriada)
                    .routingKey(routingKeyMatriculaCriada)
                    .conteudoJson(jsonPayload)
                    .processado(false)
                    .datCriacao(agora)
                    .build();
            outboxRepository.save(outbox);
            log.info("[Outbox] Mensagem registrada na outbox para a matricula ID: {}", matricula.getId());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Erro ao serializar CloudEvent para Outbox", e);
        }

        return toResponse(matricula);
    }

    @Transactional(readOnly = true)
    public List<MatriculaResponse> listarTodas() {
        return matriculaRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MatriculaResponse buscarPorId(Long id) {
        return matriculaRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new RuntimeException("Matrícula não encontrada: " + id));
    }

    @Transactional
    public void atualizarStatus(Long id, String novoStatus) {
        Matricula matricula = matriculaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Matrícula não encontrada: " + id));
        matricula.setNomStatus(novoStatus);
        matricula.setDatAtualizacao(Instant.now());
        matriculaRepository.save(matricula);
        log.info("[Legado] Matrícula ID {} atualizada para status: {}", id, novoStatus);
    }

    private MatriculaResponse toResponse(Matricula m) {
        return MatriculaResponse.builder()
                .id(m.getId())
                .codAluno(m.getCodAluno())
                .periodoLetivo(m.getNumPeriodo())
                .status(m.getNomStatus())
                .dataCriacao(m.getDatCriacao())
                .dataAtualizacao(m.getDatAtualizacao())
                .build();
    }
}
