/*
 * File: AuditService.java
 *
 * Purpose: Records instructor actions in the audit log.
 *
 * Where it fits: Called by other services whenever a result is changed by hand.
 */
package gradezzy.service;

import java.util.ArrayList;
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.model.AuditEntry;
import gradezzy.persistence.ResultRepository;

/**
 * Records instructor actions in the audit log.
 *
 * <p>Tier: SHOULD. Owner: Jing Yuan.
 */
public class AuditService {

    // The application settings
    private final AppConfig config;

    // The store results are saved to
    private final ResultRepository repository;

    /**
     * Creates a new audit service.
     *
     * @param config the application settings
     * @param repository the store results are saved to
     */
    public AuditService(AppConfig config, ResultRepository repository) {
        this.config = config;
        this.repository = repository;
    }

    /**
     * Adds an entry with the current time.
     *
     * @param action the action
     * @param detail the detail
     */
    public void record(String action, String detail) {
        // TODO(Jing Yuan): implement: creating an AuditEntry and appending it
        //     when AUDIT_ENABLED is true
    }

    /**
     * Returns the audit log.
     *
     * @return the audit entries, oldest first
     */
    public List<AuditEntry> listEntries() {
        // TODO(Jing Yuan): implement: reading the entries from the repository
        return new ArrayList<>();
    }
}
