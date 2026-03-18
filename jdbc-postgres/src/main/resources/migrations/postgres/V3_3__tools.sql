CREATE TABLE IF NOT EXISTS tools (
    key VARCHAR(250) NOT NULL PRIMARY KEY,
    value JSONB NOT NULL,
    tenant_id VARCHAR(250) GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    deleted BOOL NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'deleted' AS BOOL)) STORED,
    enabled BOOL NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'enabled' AS BOOL)) STORED,
    id VARCHAR(100) NOT NULL GENERATED ALWAYS AS (value ->> 'id') STORED,
    name VARCHAR(250) NOT NULL GENERATED ALWAYS AS (value ->> 'name') STORED,
    title TEXT GENERATED ALWAYS AS (value ->> 'title') STORED,
    namespace VARCHAR(250) NOT NULL GENERATED ALWAYS AS (value ->> 'namespace') STORED,
    flow_id VARCHAR(250) NOT NULL GENERATED ALWAYS AS (value ->> 'flowId') STORED,
    description TEXT GENERATED ALWAYS AS (value ->> 'description') STORED,
    fulltext TSVECTOR GENERATED ALWAYS AS (
        FULLTEXT_INDEX(CAST(value->>'name' AS VARCHAR)) ||
        FULLTEXT_INDEX(CAST(value->>'namespace' AS VARCHAR)) ||
        FULLTEXT_INDEX(CAST(value->>'description' AS VARCHAR)) ||
        FULLTEXT_INDEX(CAST(value->>'title' AS VARCHAR))
    ) STORED,
    created TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS tools_tenant ON tools ("deleted", "tenant_id");
CREATE INDEX IF NOT EXISTS tools_id ON tools ("id", "deleted", "tenant_id");
CREATE INDEX IF NOT EXISTS tools_namespace_flow ON tools ("namespace", "flow_id", "deleted", "tenant_id");
CREATE INDEX IF NOT EXISTS tools_enabled ON tools ("enabled", "deleted", "tenant_id");
CREATE INDEX IF NOT EXISTS tools_fulltext ON tools USING GIN (fulltext);

CREATE OR REPLACE TRIGGER tools_updated BEFORE UPDATE
    ON tools FOR EACH ROW EXECUTE PROCEDURE
    UPDATE_UPDATED_DATETIME();
