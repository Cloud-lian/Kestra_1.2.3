<template>
    <TopNavBar :title="routeInfo.title">
        <template #additional-right>
            <ul>
                <li class="d-flex align-items-center gap-2">
                    <el-tag
                        :type="form.enabled ? 'success' : 'danger'"
                        size="small"
                        class="status-tag"
                    >
                        {{ form.enabled ? $t('tools.mcp.statusEnabled') : $t('tools.mcp.statusDisabled') }}
                    </el-tag>
                    <el-button
                        :loading="toggling"
                        @click="onToggleEnabled(!form.enabled)"
                    >
                        {{ form.enabled ? $t('tools.mcp.disable') : $t('tools.mcp.enable') }}
                    </el-button>
                </li>
            </ul>
        </template>
    </TopNavBar>

    <Wrapper>
        <Block :heading="$t('tools.mcp.configHeading')" :last="true">
            <template #content>
                <p class="section-description">
                    {{ $t("tools.mcp.description") }}
                </p>
                <Row>
                    <Column
                        :label="$t('tools.mcp.serverName')"
                        :overrides="{xs: 24, sm: 24, md: 12, lg: 12, xl: 12}"
                    >
                        <el-input
                            v-model="form.serverName"
                            :placeholder="$t('tools.mcp.serverNamePlaceholder')"
                        />
                    </Column>
                </Row>
                <Row>
                    <Column
                        :label="$t('tools.mcp.instructions')"
                        :overrides="{xs: 24, sm: 24, md: 24, lg: 24, xl: 24}"
                    >
                        <el-input
                            v-model="form.instructions"
                            type="textarea"
                            :rows="5"
                            :placeholder="$t('tools.mcp.instructionsPlaceholder')"
                        />
                    </Column>
                </Row>
                <div class="save-bar">
                    <el-button
                        type="primary"
                        :disabled="!dirty"
                        :loading="saving"
                        @click="onSave"
                    >
                        {{ $t("save") }}
                    </el-button>
                </div>
            </template>
        </Block>
    </Wrapper>
</template>

<script setup lang="ts">
    import {computed, onMounted, reactive, ref} from "vue";
    import {useI18n} from "vue-i18n";
    import {useToolStore, type McpServer} from "../../stores/tool";
    import {useToast} from "../../utils/toast";
    import useRouteContext from "../../composables/useRouteContext";
    import TopNavBar from "../layout/TopNavBar.vue";
    import Wrapper from "../settings/components/Wrapper.vue";
    import Block from "../settings/components/block/Block.vue";
    import Row from "../settings/components/block/Row.vue";
    import Column from "../settings/components/block/Column.vue";

    const {t} = useI18n({useScope: "global"});
    const toolStore = useToolStore();
    const toast = useToast();
    const saving = ref(false);
    const toggling = ref(false);

    const form = reactive<McpServer>({
        enabled: false,
        serverName: "",
        instructions: "",
    });

    let savedSnapshot = "";

    const dirty = computed(() => JSON.stringify(form) !== savedSnapshot);

    const routeInfo = computed(() => ({title: t("tools.mcp.title")}));
    useRouteContext(routeInfo);

    function applySnapshot() {
        savedSnapshot = JSON.stringify(form);
    }

    onMounted(async () => {
        const server = await toolStore.loadMcpServer();
        form.enabled = server.enabled;
        form.serverName = server.serverName ?? "";
        form.instructions = server.instructions ?? "";
        applySnapshot();
    });

    const onToggleEnabled = async (value: boolean) => {
        toggling.value = true;
        try {
            await toolStore.saveMcpServer({...form, enabled: value});
            form.enabled = value;
            applySnapshot();
            toast.success(
                value ? t("tools.mcp.enabledMessage") : t("tools.mcp.disabledMessage")
            );
        } catch (e: any) {
            toast.error(e?.response?.data?.message || e.message);
        } finally {
            toggling.value = false;
        }
    };

    const onSave = async () => {
        saving.value = true;
        try {
            await toolStore.saveMcpServer({...form});
            applySnapshot();
            toast.success(t("tools.mcp.savedMessage"));
        } catch (e: any) {
            toast.error(e?.response?.data?.message || e.message);
        } finally {
            saving.value = false;
        }
    };
</script>

<style scoped lang="scss">
    .status-tag {
        text-transform: uppercase;
        font-family: monospace;
        font-size: 12px;
        padding: 0 0.5rem;
        border-radius: 0.25rem;

        &:deep(.el-tag--success) {
            color: var(--ks-content-success);
            background-color: var(--ks-background-success);
            border: 1px solid var(--ks-border-success);
        }

        &:deep(.el-tag--danger) {
            color: var(--ks-content-error);
            background-color: var(--ks-background-error);
            border: 1px solid var(--ks-border-error);
        }
    }

    .section-description {
        color: var(--ks-content-secondary);
        margin-bottom: 1.5rem;
        font-size: 0.875rem;
    }

    .save-bar {
        display: flex;
        justify-content: flex-end;
        margin-top: 1.5rem;
        padding-top: 1rem;
        border-top: 1px solid var(--ks-border-primary);
    }
</style>
