<template>
    <TopNavBar :title="routeInfo.title">
        <template #additional-right>
            <ul>
                <li v-if="toolStore.tool" class="d-flex align-items-center gap-2">
                    <span class="text-muted">{{ $t('tools.fields.enabled') }}</span>
                    <el-switch
                        :modelValue="toolStore.tool.enabled"
                        @change="onToggleEnabled"
                    />
                </li>
            </ul>
        </template>
    </TopNavBar>
    <section class="container padding-bottom">
        <ToolForm
            v-if="toolStore.tool"
            :initialData="toolStore.tool"
            :isCreate="false"
            @submit="onUpdate"
        />
    </section>
</template>

<script setup lang="ts">
    import {computed, onMounted} from "vue";
    import {useI18n} from "vue-i18n";
    import {useRoute, useRouter} from "vue-router";
    import {useToolStore, type ToolData} from "../../stores/tool";
    import {useCoreStore} from "../../stores/core";
    import useRouteContext from "../../composables/useRouteContext";
    import TopNavBar from "../layout/TopNavBar.vue";
    import ToolForm from "./ToolForm.vue";

    const {t} = useI18n({useScope: "global"});
    const route = useRoute();
    const router = useRouter();
    const toolStore = useToolStore();
    const coreStore = useCoreStore();

    const routeInfo = computed(() => ({title: t("tools.edit")}));
    useRouteContext(routeInfo);

    onMounted(async () => {
        const id = route.params.id as string;
        await toolStore.load(id);
    });

    const onToggleEnabled = async (value: boolean) => {
        const id = route.params.id as string;
        try {
            await toolStore.toggle(id, value);
            await toolStore.load(id);
        } catch (e: any) {
            coreStore.message = {
                variant: "error",
                title: t("error"),
                message: e?.response?.data?.message || e.message,
            };
        }
    };

    const onUpdate = async (data: ToolData) => {
        try {
            const id = route.params.id as string;
            await toolStore.update(id, data);
            coreStore.message = {
                variant: "success",
                title: t("success"),
                message: t("saved done", {name: data.name}),
            };
            router.push({name: "tools/list"});
        } catch (e: any) {
            coreStore.message = {
                variant: "error",
                title: t("error"),
                message: e?.response?.data?.message || e.message,
            };
        }
    };
</script>
