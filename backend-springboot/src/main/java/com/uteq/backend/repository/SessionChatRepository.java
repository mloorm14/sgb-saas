package com.uteq.backend.repository;

import com.uteq.backend.entity.SessionChat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SessionChatRepository extends JpaRepository<SessionChat, UUID> {
}
