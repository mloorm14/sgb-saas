package com.uteq.backend.repository;

import com.uteq.backend.entity.SesionChat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SesionChatRepository extends JpaRepository<SesionChat, UUID> {
}
