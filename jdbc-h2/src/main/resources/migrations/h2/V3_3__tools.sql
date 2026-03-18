CREATE TABLE IF NOT EXISTS tools (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId')),
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.deleted')),
    "enabled" BOOL NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.enabled')),
    "id" VARCHAR(100) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.id')),
    "name" VARCHAR(250) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.name')),
    "title" TEXT GENERATED ALWAYS AS (JQ_STRING("value", '.title')),
    "namespace" VARCHAR(250) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.namespace')),
    "flow_id" VARCHAR(250) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.flowId')),
    "description" TEXT GENERATED ALWAYS AS (JQ_STRING("value", '.description')),
    "fulltext" TEXT NOT NULL GENERATED ALWAYS AS (
        CONCAT(
            JQ_STRING("value", '.name'), ' ',
            JQ_STRING("value", '.namespace'), ' ',
            COALESCE(JQ_STRING("value", '.description'), ''), ' ',
            COALESCE(JQ_STRING("value", '.title'), '')
        )
    ),
    "created" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS tools_tenant ON tools ("deleted", "tenant_id");
CREATE INDEX IF NOT EXISTS tools_id ON tools ("id", "deleted", "tenant_id");
CREATE INDEX IF NOT EXISTS tools_namespace_flow ON tools ("namespace", "flow_id", "deleted", "tenant_id");
CREATE INDEX IF NOT EXISTS tools_enabled ON tools ("enabled", "deleted", "tenant_id");
