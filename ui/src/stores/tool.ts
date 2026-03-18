import {ref} from "vue";
import {defineStore} from "pinia";
import {apiUrl} from "override/utils/route";
import {useAxios} from "../utils/axios";

export interface ToolAnnotations {
    readOnlyHint?: boolean;
    destructiveHint?: boolean;
    idempotentHint?: boolean;
    openWorldHint?: boolean;
}

export interface ToolData {
    id?: string;
    name: string;
    title?: string;
    description?: string;
    tags?: string[];
    namespace: string;
    flowId: string;
    annotations?: ToolAnnotations;
    enabled: boolean;
    tenantId?: string;
    deleted?: boolean;
    created?: string;
    updated?: string;
}

export interface McpServer {
    enabled: boolean;
    serverName?: string;
    instructions?: string;
}

export const useToolStore = defineStore("tool", () => {
    const tools = ref<ToolData[]>([]);
    const tool = ref<ToolData | undefined>();
    const total = ref(0);
    const mcpServer = ref<McpServer>({enabled: false});

    const axios = useAxios();

    async function list(options: Record<string, any> = {}) {
        const {sort, ...params} = options;
        const response = await axios.get(
            `${apiUrl()}/tools${sort ? `?sort=${sort}` : ""}`,
            {params}
        );
        tools.value = response.data.results;
        total.value = response.data.total;
        return response.data;
    }

    async function load(id: string) {
        const response = await axios.get(`${apiUrl()}/tools/${id}`);
        tool.value = response.data;
        return tool.value;
    }

    async function create(data: ToolData) {
        const {id: _id, ...payload} = data;
        const response = await axios.post(`${apiUrl()}/tools`, payload, {
            headers: {"Content-Type": "application/json"}
        });
        return response.data;
    }

    async function update(id: string, data: ToolData) {
        const response = await axios.put(`${apiUrl()}/tools/${id}`, data, {
            headers: {"Content-Type": "application/json"}
        });
        return response.data;
    }

    async function deleteTool(id: string) {
        const response = await axios.delete(`${apiUrl()}/tools/${id}`);
        return response.data;
    }

    async function toggle(id: string, enabled: boolean) {
        const response = await axios.patch(
            `${apiUrl()}/tools/${id}/toggle`,
            {enabled},
            {headers: {"Content-Type": "application/json"}}
        );
        return response.data;
    }

    async function validate(data: ToolData) {
        const response = await axios.post(`${apiUrl()}/tools/validate`, data, {
            headers: {"Content-Type": "application/json"}
        });
        return response.data;
    }

    async function loadMcpServer() {
        const response = await axios.get(`${apiUrl()}/tools/settings/mcp`);
        mcpServer.value = response.data;
        return mcpServer.value;
    }

    async function saveMcpServer(server: McpServer) {
        const response = await axios.post(`${apiUrl()}/tools/settings/mcp`, server, {
            headers: {"Content-Type": "application/json"}
        });
        mcpServer.value = response.data;
        return mcpServer.value;
    }

    return {
        tools,
        tool,
        total,
        mcpServer,
        list,
        load,
        create,
        update,
        delete: deleteTool,
        toggle,
        validate,
        loadMcpServer,
        saveMcpServer,
    };
});
