<template>
    <BlockBoolean
        v-if="isBoolean"
        v-bind="componentProps"
    />
</template>

<script setup lang="ts">
    import BlockBoolean from "./BlockBoolean.vue";

    interface Props {
        type?: string
        isBoolean?: boolean
        componentProps?: Record<string, any>
    }

    withDefaults(defineProps<Props>(), {
        type: "",
        isBoolean: false,
        componentProps: () => ({})
    })
</script>
