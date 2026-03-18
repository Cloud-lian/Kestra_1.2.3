<template>
    <TopNavBar :title="routeInfo.title">
        <template #additional-right v-if="user && user.hasAnyAction(permission.TEMPLATE, action.CREATE)">
            <ul>
                <li>
                    <div class="el-input el-input-file el-input--large custom-upload">
                        <div class="el-input__wrapper">
                            <label for="importTemplates">
                                <Upload />
                                {{ $t('import') }}
                            </label>
                            <input
                                id="importTemplates"
                                class="el-input__inner"
                                type="file"
                                @change="importTemplates()"
                                ref="file"
                            >
                        </div>
                    </div>
                </li>
                <li>
                    <router-link :to="{name: 'templates/create'}">
                        <ks-button :icon="Plus" type="primary" size="large">
                            {{ $t('create') }}
                        </ks-button>
                    </router-link>
                </li>
            </ul>
        </template>
    </TopNavBar>
    <TemplatesDeprecated />
    <section class="container" v-if="ready">
        <div>
            <DataTable
                @page-changed="onPageChanged"
                ref="dataTable"
                :total="templateStore.total"
            >
                <template #navbar>
                    <ks-form-item>
                        <SearchField />
                    </ks-form-item>
                    <ks-form-item>
                        <NamespaceSelect
                            data-type="flow"
                            :value="$route.query.namespace"
                            @update:model-value="onDataTableValue('namespace', $event)"
                        />
                    </ks-form-item>
                </template>

                <template #table>
                    <SelectTable
                        ref="selectTable"
                        :data="templateStore.templates"
                        :defaultSort="{prop: 'id', order: 'ascending'}"
                        tableLayout="auto"
                        fixed
                        @row-dblclick="onRowDoubleClick"
                        @sort-change="onSort"
                        @selection-change="handleSelectionChange"
                        :selectable="canRead || canDelete"
                        :no-data-text="$t('no_results.templates')"
                        :rowKey="(row) => `${row.namespace}-${row.id}`"
                    >
                        <template #select-actions>
                            <BulkSelect
                                :selectAll="queryBulkAction"
                                :selections="selection"
                                :total="templateStore.total"
                                @update:select-all="toggleAllSelection"
                                @unselect="toggleAllUnselected"
                            >
                                <ks-button v-if="canRead" :icon="Download" @click="exportTemplates()">
                                    {{ $t('export') }}
                                </ks-button>
                                <ks-button v-if="canDelete" @click="deleteTemplates" :icon="TrashCan">
                                    {{ $t('delete') }}
                                </ks-button>
                            </BulkSelect>
                        </template>
                        <template #default>
                            <ks-table-column
                                prop="id"
                                sortable="custom"
                                :sortOrders="['ascending', 'descending']"
                                :label="$t('id')"
                            >
                                <template #default="scope">
                                    <router-link
                                        :to="{name: 'templates/update', params: {namespace: scope.row.namespace, id: scope.row.id}}"
                                    >
                                        {{ scope.row.id }}
                                    </router-link>
                                    &nbsp;<MarkdownTooltip
                                        :id="scope.row.namespace + '-' + scope.row.id"
                                        :description="scope.row.description"
                                        :title="scope.row.namespace + '.' + scope.row.id"
                                    />
                                </template>
                            </ks-table-column>

                            <ks-table-column
                                prop="namespace"
                                sortable="custom"
                                :sortOrders="['ascending', 'descending']"
                                :label="$t('namespace')"
                                :formatter="(_, __, cellValue) => $filters.invisibleSpace(cellValue)"
                            />

                            <ks-table-column columnKey="action" className="row-action">
                                <template #default="scope">
                                    <IconButton
                                        :tooltip="$t('details')"
                                        :to="{name: 'templates/update', params : {namespace: scope.row.namespace, id: scope.row.id}}"
                                    >
                                        <TextSearch />
                                    </IconButton>
                                </template>
                            </ks-table-column>
                        </template>
                    </SelectTable>
                </template>
            </DataTable>
        </div>
    </section>
</template>

<script setup>
    import BulkSelect from "../layout/BulkSelect.vue";
    import SelectTable from "../layout/SelectTable.vue";
    import Plus from "vue-material-design-icons/Plus.vue";
    import Download from "vue-material-design-icons/Download.vue";
    import TrashCan from "vue-material-design-icons/TrashCan.vue";
    import TemplatesDeprecated from "./TemplatesDeprecated.vue";
</script>

<script>
    import {mapStores} from "pinia";
    import {useTemplateStore} from "../../stores/template";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import NamespaceSelect from "../namespaces/components/NamespaceSelect.vue";
    import TextSearch from "vue-material-design-icons/TextSearch.vue";
    import RouteContext from "../../mixins/routeContext";
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import DataTableActions from "../../mixins/dataTableActions";
    import DataTable from "../layout/DataTable.vue";
    import SearchField from "../layout/SearchField.vue";
    import IconButton from "../IconButton.vue"
    import RestoreUrl from "../../mixins/restoreUrl";
    import _merge from "lodash/merge";
    import MarkdownTooltip from "../../components/layout/MarkdownTooltip.vue";
    import Upload from "vue-material-design-icons/Upload.vue";
    import SelectTableActions from "../../mixins/selectTableActions";
    import {useAuthStore} from "override/stores/auth"

    export default {
        mixins: [RouteContext, RestoreUrl, DataTableActions, SelectTableActions],
        components: {
            TextSearch,
            DataTable,
            SearchField,
            NamespaceSelect,
            IconButton,
            MarkdownTooltip,
            Upload,
            TopNavBar
        },
        data() {
            return {
                isDefaultNamespaceAllow: true,
                permission: permission,
                action: action,
            };
        },
        computed: {
            ...mapStores(useTemplateStore, useAuthStore),
            routeInfo() {
                return {
                    title: this.$t("templates")
                };
            },
            canRead() {
                return this.authStore.user?.isAllowed(permission.FLOW, action.READ);
            },
            canDelete() {
                return this.authStore.user?.isAllowed(permission.FLOW, action.DELETE);
            },
        },
        methods: {
            loadQuery(base) {
                let queryFilter = this.queryWithFilter();

                return _merge(base, queryFilter)
            },
            loadData(callback) {
                this.templateStore
                    .findTemplates(this.loadQuery({
                        size: parseInt(this.$route.query.size || 25),
                        page: parseInt(this.$route.query.page || 1),
                        sort: this.$route.query.sort || "id:asc",
                    }))
                    .then(() => {
                        callback();
                    });
            },
            selectionMapper(element) {
                return {
                    id: element.id,
                    namespace: element.namespace
                };
            },
            exportTemplates() {
                this.$toast().confirm(
                    this.$t("template export", {"templateCount": this.queryBulkAction ? this.templateStore.total : this.selection.length}),
                    () => {
                        if (this.queryBulkAction) {
                            return this.templateStore
                                .exportTemplateByQuery(this.loadQuery({
                                    namespace: this.$route.query.namespace ? [this.$route.query.namespace] : undefined,
                                    q: this.$route.query.q ? [this.$route.query.q] : undefined,
                                }, false))
                                .then(_ => {
                                    this.$toast().success(this.$t("templates exported"));
                                })
                        } else {
                            return this.templateStore
                                .exportTemplateByIds({ids: this.selection})
                                .then(_ => {
                                    this.$toast().success(this.$t("templates exported"));
                                })
                        }
                    },
                    () => {
                    }
                )
            },
            importTemplates() {
                const formData = new FormData();
                formData.append("fileUpload", this.$refs.file.files[0]);
                this.templateStore
                    .importTemplates(formData)
                    .then(_ => {
                        this.$toast().success(this.$t("templates imported"));
                        this.loadData(() => {
                        })
                    })
            },
            deleteTemplates() {
                this.$toast().confirm(
                    this.$t("template delete", {"templateCount": this.queryBulkAction ? this.templateStore.total : this.selection.length}),
                    () => {
                        if (this.queryBulkAction) {
                            return this.templateStore
                                .deleteTemplateByQuery(this.loadQuery({
                                    namespace: this.$route.query.namespace ? [this.$route.query.namespace] : undefined,
                                    q: this.$route.query.q ? [this.$route.query.q] : undefined,
                                }, false))
                                .then(r => {
                                    this.$toast().success(this.$t("templates deleted", {count: r.data.count}));
                                    this.loadData(() => {
                                    })
                                })
                        } else {
                            return this.templateStore
                                .deleteTemplateByIds({ids: this.selection})
                                .then(r => {
                                    this.$toast().success(this.$t("templates deleted", {count: r.data.count}));
                                    this.loadData(() => {
                                    })
                                })
                        }
                    },
                    () => {
                    }
                )
            },
        },
    };
</script>
