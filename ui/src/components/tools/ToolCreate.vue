<template>
    <TopNavBar :title="routeInfo.title" />
    <section class="container padding-bottom">
        <ToolForm :isCreate="true" @submit="onCreate" />
    </section>
</template>

<script setup lang="ts">
    import {computed} from "vue";
    import {useI18n} from "vue-i18n";
    import {useRouter} from "vue-router";
    import {useToolStore, type ToolData} from "../../stores/tool";
    import {useCoreStore} from "../../stores/core";
    import useRouteContext from "../../composables/useRouteContext";
    import TopNavBar from "../layout/TopNavBar.vue";
    import ToolForm from "./ToolForm.vue";

    const {t} = useI18n({useScope: "global"});
    const router = useRouter();
    const toolStore = useToolStore();
    const coreStore = useCoreStore();

    const routeInfo = computed(() => ({title: t("tools.create")}));
    useRouteContext(routeInfo);

    const onCreate = async (data: ToolData) => {
        try {
            await toolStore.create(data);
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
