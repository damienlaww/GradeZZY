/*
 * File: AuditService.java
 *
 * Purpose: Records instructor actions in the audit log.
 *
 * Where it fits: Called by the other services for each instructor
 * action and grading event, such as starting, retrying, resuming or
 * cancelling grading, changing a mark by hand, saving settings, and
 * backing up or restoring the database.
 */
package gradezzy.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigException;
import gradezzy.config.ConfigKeys;
import gradezzy.model.AuditEntry;
import gradezzy.persistence.PersistenceException;
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
     * Adds an entry with the current time when the audit log is switched
     * on. An entry that cannot be saved is dropped: the audit log never
     * stops the action it records. That includes a setting audit.enabled
     * that is not true or false: the entry is dropped rather than the
     * action failing, often on its way to report another error.
     *
     * @param action the action
     * @param detail the detail
     */
    public void record(String action, String detail) {
        if (repository == null || action == null) {
            return;
        }

        try {
            if (!config.getBoolean(ConfigKeys.AUDIT_ENABLED)) {
                return;
            }
        } catch (ConfigException exception) {
            // See the comment above
            return;
        }

        AuditEntry auditEntry = new AuditEntry(LocalDateTime.now(), action,
                detail);
        try {
            repository.appendAudit(auditEntry);
        } catch (PersistenceException exception) {
            // The database is switched off or cannot be written
        }
    }

    /**
     * Returns the audit log.
     *
     * @return the audit entries, oldest first, or an empty list when the
     *         database is switched off or cannot be read
     */
    public List<AuditEntry> listEntries() {
        if (repository == null) {
            return new ArrayList<>();
        }

        try {
            return repository.listAudit();
        } catch (PersistenceException exception) {
            return new ArrayList<>();
        }
    }
}
