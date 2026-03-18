<template>
    <TopNavBar :title="routeInfo.title">
        <template #additional-right>
            <ul>
                <li>
                    <router-link :to="{name: 'tools/create'}">
                        <el-button :icon="Plus" type="primary">
                            {{ $t("tools.create") }}
                        </el-button>
                    </router-link>
                </li>
            </ul>
        </template>
    </TopNavBar>
    <section class="container">
        <DataTable
            @page-changed="onPageChanged"
            ref="dataTable"
            :total="toolStore.total"
        >
            <template #navbar>
                <el-input
                    v-model="searchQuery"
                    :placeholder="$t('search')"
                    clearable
                    @input="onSearch"
                    class="mb-3"
                />
            </template>

            <template #table>
                <NoData v-if="!toolStore.tools || toolStore.tools.length === 0" />
                <el-table
                    v-else
                    :data="toolStore.tools"
                    @row-dblclick="onRowDoubleClick"
                    tableLayout="auto"
                >
                    <el-table-column :label="$t('tools.fields.name')" prop="name" sortable>
                        <template #default="scope">
                            <router-link :to="{name: 'tools/update', params: {id: scope.row.id}}">
                                {{ scope.row.name }}
                            </router-link>
                        </template>
                    </el-table-column>
                    <el-table-column :label="$t('tools.fields.title')" prop="title" />
                    <el-table-column :label="$t('description')" prop="description" />
                    <el-table-column :label="$t('namespace')" prop="namespace" />
                    <el-table-column :label="$t('tools.fields.flowId')" prop="flowId" />
                    <el-table-column :label="$t('tools.fields.tags')" prop="tags">
                        <template #default="scope">
                            <el-tag
                                v-for="tag in scope.row.tags"
                                :key="tag"
                                size="small"
                                class="me-1"
                            >
                                {{ tag }}
                            </el-tag>
                        </template>
                    </el-table-column>
                    <el-table-column :label="$t('tools.fields.enabled')" prop="enabled" width="100">
                        <template #default="scope">
                            <el-switch
                                :modelValue="scope.row.enabled"
                                @change="onToggle(scope.row)"
                            />
                        </template>
                    </el-table-column>
                    <el-table-column :label="$t('tools.fields.created')" prop="created" width="180">
                        <template #default="scope">
                            {{ scope.row.created ? new Date(scope.row.created).toLocaleString() : '' }}
                        </template>
                    </el-table-column>
                    <el-table-column :label="$t('actions')" width="120" fixed="right">
                        <template #default="scope">
                            <router-link :to="{name: 'tools/update', params: {id: scope.row.id}}">
                                <el-button :icon="Pencil" size="small" text />
                            </router-link>
                            <el-button
                                :icon="TrashCan"
                                size="small"
                                text
                                type="danger"
                                @click="onDelete(scope.row)"
                            />
                        </template>
                    </el-table-column>
                </el-table>
            </template>
        </DataTable>
    </section>
</template>

<script setup lang="ts">
    import {computed, onMounted, ref} from "vue";
    import {useI18n} from "vue-i18n";
    import {useRouter} from "vue-router";
    import {useToolStore} from "../../stores/tool";
    import useRouteContext from "../../composables/useRouteContext";
    import TopNavBar from "../layout/TopNavBar.vue";
    import DataTable from "../layout/DataTable.vue";
    import NoData from "../layout/NoData.vue";
    import Plus from "vue-material-design-icons/Plus.vue";
    import Pencil from "vue-material-design-icons/Pencil.vue";
    import TrashCan from "vue-material-design-icons/TrashCan.vue";
    import {ElMessageBox} from "element-plus";

    const {t} = useI18n({useScope: "global"});
    const router = useRouter();
    const toolStore = useToolStore();
    const searchQuery = ref("");

    const routeInfo = computed(() => ({title: t("tools.name")}));
    useRouteContext(routeInfo);

    const loadData = async () => {
        await toolStore.list({q: searchQuery.value || undefined});
    };

    onMounted(loadData);

    const onPageChanged = (e: {page: number; size: number}) => {
        toolStore.list({page: e.page, size: e.size, q: searchQuery.value || undefined});
    };

    const onSearch = () => {
        loadData();
    };

    const onRowDoubleClick = (row: any) => {
        router.push({name: "tools/update", params: {id: row.id}});
    };

    const onToggle = async (row: any) => {
        await toolStore.toggle(row.id, !row.enabled);
        await loadData();
    };

    const onDelete = async (row: any) => {
        try {
            await ElMessageBox.confirm(
                t("delete confirm", {name: row.name}),
                t("confirmation"),
                {confirmButtonText: t("ok"), cancelButtonText: t("cancel"), type: "warning"}
            );
            await toolStore.delete(row.id);
            await loadData();
        } catch {
            // cancelled
        }
    };
</script>
