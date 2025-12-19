<template>
    <span>
        {{ humanReadableCron }}
    </span>
</template>

<script lang="ts" setup>
    import {computed} from "vue";
    import Utils from "../../utils/utils";
    import cronstrue from "cronstrue";
    import "cronstrue/locales/fr";
    const props = defineProps<{
        cronExpression?: string
    }>()

    const humanReadableCron = computed(() => {
        return props.cronExpression ? cronstrue.toString(props.cronExpression, {locale: Utils.getLang()}) : "";
    });
</script>