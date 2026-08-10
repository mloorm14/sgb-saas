package com.uteq.backend.repository;

import com.uteq.backend.entity.MensajeChat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MensajeChatRepository extends JpaRepository<MensajeChat, Long> {

    List<MensajeChat> findBySesionIdOrderByCreadoEnAsc(UUID sesionId);
}
