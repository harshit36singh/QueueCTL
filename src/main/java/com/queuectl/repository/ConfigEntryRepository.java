package com.queuectl.repository;

import com.queuectl.domain.ConfigEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfigEntryRepository extends JpaRepository<ConfigEntry, String> {
}
