package br.com.legado.repository;

import br.com.legado.domain.OutboxMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxMessage, UUID> {

    @Query("SELECT o FROM OutboxMessage o WHERE o.processado = false ORDER BY o.datCriacao ASC")
    List<OutboxMessage> findPendingMessages(Pageable pageable);
}
