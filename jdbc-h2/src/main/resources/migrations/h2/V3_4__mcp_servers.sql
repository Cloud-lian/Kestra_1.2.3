CREATE TABLE IF NOT EXISTS mcp_servers (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId')),
    "enabled" BOOL NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.enabled')),
    "server_name" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.serverName')),
    "created" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS mcp_servers_tenant ON mcp_servers ("tenant_id");
