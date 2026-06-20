package com.cyrain.chatscope.admin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "chatscope.schema.verify", name = "enabled", havingValue = "true")
final class SchemaVerificationRunner implements ApplicationRunner {

    private static final Map<String, List<String>> REQUIRED_COLUMNS = Map.ofEntries(
            table("cs_users",
                    "id", "openim_user_id", "display_name", "joined_demo_group_at", "created_at", "updated_at"),
            table("raw_messages",
                    "id", "message_id", "group_id", "openim_user_id", "sender_name", "sent_at", "content_text",
                    "message_type", "mentions_json", "reply_to_message_id", "is_from_bot", "visible_to_user_ids_json",
                    "raw_payload", "created_at"),
            table("ai_quota_accounts",
                    "id", "openim_user_id", "quota_date", "daily_limit", "bonus_amount", "used_count",
                    "created_at", "updated_at"),
            table("ai_quota_ledger",
                    "id", "openim_user_id", "quota_date", "event_type", "delta", "balance_after", "reason",
                    "operator_id", "request_id", "created_at"),
            table("bot_interactions",
                    "id", "trigger_message_id", "openim_user_id", "group_id", "user_question", "intent_type",
                    "quota_ledger_id", "context_window_start", "context_window_end", "used_mini_summary_ids",
                    "used_raw_message_ids", "bot_answer", "response_message_id", "visibility",
                    "visible_to_user_ids_json", "status", "model_name", "model_latency_ms", "error_message",
                    "created_at", "updated_at"),
            table("mini_summaries",
                    "id", "group_id", "window_start", "window_end", "message_count", "participant_count",
                    "participants_json", "summary_text", "topics_json", "status", "model_name", "model_latency_ms",
                    "error_message", "created_at", "updated_at"),
            table("archive_summaries",
                    "id", "group_id", "window_start", "window_end", "online_count", "mini_summary_ids",
                    "topics_json", "summary_text", "public_push_message_id", "status", "model_name",
                    "model_latency_ms", "error_message", "created_at", "updated_at"),
            table("abuse_subjects",
                    "id", "subject_type", "subject_value_hash", "status", "reason", "expires_at", "created_by",
                    "created_at", "updated_at"),
            table("verification_events",
                    "id", "channel", "target_hash", "ip_hash", "ip_cidr_hash", "device_fingerprint_hash",
                    "openim_user_id", "action", "decision", "reason", "provider_request_id", "created_at"),
            table("registration_events",
                    "id", "openim_user_id", "email_hash", "phone_hash", "ip_hash", "ip_cidr_hash",
                    "device_fingerprint_hash", "decision", "reason", "created_at"));

    private static final Set<UniqueConstraint> REQUIRED_UNIQUE_CONSTRAINTS = Set.of(
            unique("cs_users", "openim_user_id"),
            unique("abuse_subjects", "subject_type", "subject_value_hash"),
            unique("raw_messages", "message_id"),
            unique("ai_quota_accounts", "openim_user_id", "quota_date"),
            unique("ai_quota_ledger", "request_id"),
            unique("bot_interactions", "trigger_message_id", "intent_type"),
            unique("mini_summaries", "group_id", "window_start", "window_end"),
            unique("archive_summaries", "group_id", "window_start", "window_end"));

    private static final Set<String> REQUIRED_INDEXES = Set.of(
            "uq_ai_quota_ledger_request_id",
            "idx_cs_users_joined_demo_group_at",
            "idx_abuse_subjects_status_expires_at",
            "idx_abuse_subjects_updated_at",
            "idx_verification_events_target_created_at",
            "idx_verification_events_ip_created_at",
            "idx_verification_events_openim_user_created_at",
            "idx_verification_events_decision_created_at",
            "idx_registration_events_openim_user_created_at",
            "idx_registration_events_ip_created_at",
            "idx_registration_events_email_created_at",
            "idx_registration_events_phone_created_at",
            "idx_registration_events_decision_created_at",
            "idx_raw_messages_group_sent_at",
            "idx_raw_messages_openim_user_sent_at",
            "idx_raw_messages_reply_to_message_id",
            "idx_raw_messages_is_from_bot_created_at",
            "idx_ai_quota_ledger_user_date_created_at",
            "idx_ai_quota_ledger_event_type_created_at",
            "idx_bot_interactions_user_created_at",
            "idx_bot_interactions_group_created_at",
            "idx_bot_interactions_status_created_at",
            "idx_bot_interactions_quota_ledger_id",
            "idx_mini_summaries_group_window_start",
            "idx_mini_summaries_status_updated_at",
            "idx_archive_summaries_group_window_start",
            "idx_archive_summaries_status_updated_at");

    private final JdbcTemplate jdbcTemplate;

    SchemaVerificationRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        String schema = jdbcTemplate.queryForObject("select current_schema()", String.class);
        List<String> failures = verifySchema(schema);
        if (!failures.isEmpty()) {
            throw new IllegalStateException("ChatScope schema verification failed:\n- " + String.join("\n- ", failures));
        }

        System.out.printf(
                "ChatScope schema verification passed in schema '%s': %d tables, %d unique constraints, %d indexes.%n",
                schema, REQUIRED_COLUMNS.size(), REQUIRED_UNIQUE_CONSTRAINTS.size(), REQUIRED_INDEXES.size());
    }

    private List<String> verifySchema(String schema) {
        List<String> failures = new ArrayList<>();
        Set<String> tables = loadTables(schema);
        Map<String, Set<String>> columnsByTable = loadColumns(schema);

        for (Map.Entry<String, List<String>> entry : REQUIRED_COLUMNS.entrySet()) {
            String table = entry.getKey();
            if (!tables.contains(table)) {
                failures.add("missing table " + table);
                continue;
            }

            Set<String> actualColumns = columnsByTable.getOrDefault(table, Set.of());
            List<String> missingColumns = entry.getValue().stream()
                    .filter(column -> !actualColumns.contains(column))
                    .toList();
            if (!missingColumns.isEmpty()) {
                failures.add("missing columns on " + table + ": " + String.join(", ", missingColumns));
            }
        }

        Set<UniqueConstraint> uniqueConstraints = loadUniqueConstraints(schema);
        for (UniqueConstraint expected : REQUIRED_UNIQUE_CONSTRAINTS) {
            if (!uniqueConstraints.contains(expected)) {
                failures.add("missing unique constraint " + expected);
            }
        }

        Set<String> indexes = loadIndexes(schema);
        for (String expected : REQUIRED_INDEXES) {
            if (!indexes.contains(expected)) {
                failures.add("missing index " + expected);
            }
        }

        if (!tables.contains("flyway_schema_history")) {
            failures.add("missing Flyway schema history table");
        } else {
            requireSuccessfulMigration(failures, "1", "V1__create_chatscope_core_schema.sql");
            requireSuccessfulMigration(failures, "2", "V2__promote_quota_ledger_request_id_constraint.sql");
        }

        return failures;
    }

    private Set<String> loadTables(String schema) {
        return new LinkedHashSet<>(jdbcTemplate.queryForList("""
                select table_name
                from information_schema.tables
                where table_schema = ?
                  and table_type = 'BASE TABLE'
                """, String.class, schema));
    }

    private Map<String, Set<String>> loadColumns(String schema) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                select table_name, column_name
                from information_schema.columns
                where table_schema = ?
                """, schema);
        Map<String, Set<String>> columnsByTable = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String table = String.valueOf(row.get("table_name"));
            String column = String.valueOf(row.get("column_name"));
            columnsByTable.computeIfAbsent(table, ignored -> new LinkedHashSet<>()).add(column);
        }
        return columnsByTable;
    }

    private Set<UniqueConstraint> loadUniqueConstraints(String schema) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                select tc.table_name, tc.constraint_name, kcu.column_name
                from information_schema.table_constraints tc
                join information_schema.key_column_usage kcu
                  on kcu.constraint_schema = tc.constraint_schema
                 and kcu.constraint_name = tc.constraint_name
                 and kcu.table_schema = tc.table_schema
                 and kcu.table_name = tc.table_name
                where tc.table_schema = ?
                  and tc.constraint_type = 'UNIQUE'
                order by tc.table_name, tc.constraint_name, kcu.ordinal_position
                """, schema);

        Map<String, UniqueConstraintBuilder> builders = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String table = String.valueOf(row.get("table_name"));
            String constraint = String.valueOf(row.get("constraint_name"));
            String column = String.valueOf(row.get("column_name"));
            builders.computeIfAbsent(table + "." + constraint, ignored -> new UniqueConstraintBuilder(table))
                    .addColumn(column);
        }

        Set<UniqueConstraint> constraints = new LinkedHashSet<>();
        for (UniqueConstraintBuilder builder : builders.values()) {
            constraints.add(builder.build());
        }
        return constraints;
    }

    private Set<String> loadIndexes(String schema) {
        return new LinkedHashSet<>(jdbcTemplate.queryForList("""
                select indexname
                from pg_indexes
                where schemaname = ?
                """, String.class, schema));
    }

    private void requireSuccessfulMigration(List<String> failures, String version, String script) {
        if (!hasSuccessfulMigration(version, script)) {
            failures.add("missing successful Flyway migration record for " + script);
        }
    }

    private boolean hasSuccessfulMigration(String version, String script) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from flyway_schema_history
                where version = ?
                  and script = ?
                  and success = true
                """, Integer.class, version, script);
        return count != null && count > 0;
    }

    private static Map.Entry<String, List<String>> table(String name, String... columns) {
        return Map.entry(name, List.of(columns));
    }

    private static UniqueConstraint unique(String table, String... columns) {
        return new UniqueConstraint(table, List.of(columns));
    }

    private record UniqueConstraint(String table, List<String> columns) {

        @Override
        public String toString() {
            return table + "(" + String.join(", ", columns) + ")";
        }
    }

    private static final class UniqueConstraintBuilder {

        private final String table;
        private final List<String> columns = new ArrayList<>();

        private UniqueConstraintBuilder(String table) {
            this.table = table;
        }

        private UniqueConstraintBuilder addColumn(String column) {
            columns.add(column);
            return this;
        }

        private UniqueConstraint build() {
            return new UniqueConstraint(table, List.copyOf(columns));
        }
    }
}
